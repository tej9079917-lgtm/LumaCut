package com.example.media

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.ai.CaptionSegmentNormalizer
import com.example.ai.RawCaptionSegment
import com.example.model.CaptionClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class OfflineLanguageSpec(
    val displayName: String,
    val nativeLabel: String,
    val bcp47: String,
    val iso6391: String
)

enum class OfflinePackStatus {
    UNKNOWN,
    NOT_SUPPORTED,
    ONLINE_ONLY,
    AVAILABLE_TO_DOWNLOAD,
    DOWNLOADING,
    INSTALLED
}

data class OfflineLanguagePackState(
    val spec: OfflineLanguageSpec,
    val status: OfflinePackStatus = OfflinePackStatus.UNKNOWN,
    val downloadProgress: Int = 0,
    val deviceLocaleTag: String? = null,
    val note: String? = null
)

/**
 * Pure-JVM catalog of 18+ global & Indian languages with BCP-47 tags and ISO-639-1 codes
 * shared across Offline On-Device Speech Models, Gemini AI, and Whisper APIs.
 */
object OfflineLanguageCatalog {

    val languages: List<OfflineLanguageSpec> = listOf(
        OfflineLanguageSpec("English", "English", "en-US", "en"),
        OfflineLanguageSpec("Telugu", "తెలుగు", "te-IN", "te"),
        OfflineLanguageSpec("Hindi", "हिन्दी", "hi-IN", "hi"),
        OfflineLanguageSpec("Tamil", "தமிழ்", "ta-IN", "ta"),
        OfflineLanguageSpec("Kannada", "ಕನ್ನಡ", "kn-IN", "kn"),
        OfflineLanguageSpec("Malayalam", "മലയാളം", "ml-IN", "ml"),
        OfflineLanguageSpec("Bengali", "বাংলা", "bn-IN", "bn"),
        OfflineLanguageSpec("Marathi", "मराठी", "mr-IN", "mr"),
        OfflineLanguageSpec("Spanish", "Español", "es-ES", "es"),
        OfflineLanguageSpec("French", "Français", "fr-FR", "fr"),
        OfflineLanguageSpec("German", "Deutsch", "de-DE", "de"),
        OfflineLanguageSpec("Portuguese", "Português", "pt-BR", "pt"),
        OfflineLanguageSpec("Arabic", "العربية", "ar-SA", "ar"),
        OfflineLanguageSpec("Japanese", "日本語", "ja-JP", "ja"),
        OfflineLanguageSpec("Korean", "한국어", "ko-KR", "ko"),
        OfflineLanguageSpec("Chinese", "中文", "zh-CN", "zh"),
        OfflineLanguageSpec("Indonesian", "Bahasa Indonesia", "id-ID", "id"),
        OfflineLanguageSpec("Russian", "Русский", "ru-RU", "ru")
    )

    val captionChipOptions: List<String> = listOf("Auto") + languages.map { it.displayName }

    fun findByNameOrTag(hint: String): OfflineLanguageSpec? {
        val clean = hint.trim()
        if (clean.isEmpty() || clean.equals("Auto", ignoreCase = true)) return null
        return languages.firstOrNull {
            it.displayName.equals(clean, ignoreCase = true) ||
                it.bcp47.equals(clean, ignoreCase = true) ||
                it.iso6391.equals(clean, ignoreCase = true)
        }
    }

    fun isoCodeForName(hint: String): String? = findByNameOrTag(hint)?.iso6391

    fun bcp47ForName(hint: String): String =
        findByNameOrTag(hint)?.bcp47 ?: Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
}

/**
 * Play-Policy compliant Offline All-Language Speech Model Manager & Video-to-Caption Engine.
 * Uses Android's official SpeechRecognizer APIs (API 33+):
 * - `checkRecognitionSupport` against both On-Device & Default System Speech Services
 * - Exact device-advertised locale tag matching so `triggerModelDownload` succeeds across OEMs
 * - Automatic fallback to System Google Speech Services (`EXTRA_AUDIO_SOURCE` PCM pipe) when
 *   a specific language (such as Telugu on some OEM phones) is online-only in Google Speech Services.
 */
class OfflineSpeechModelManager private constructor(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val _packs = MutableStateFlow(
        OfflineLanguageCatalog.languages.map { OfflineLanguagePackState(it) }
    )
    val packs: StateFlow<List<OfflineLanguagePackState>> = _packs.asStateFlow()

    private val _isOnDeviceSupported = MutableStateFlow(checkDeviceCapable())
    val isOnDeviceSupported: StateFlow<Boolean> = _isOnDeviceSupported.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _needsSpeechServicesSetup = MutableStateFlow(false)
    val needsSpeechServicesSetup: StateFlow<Boolean> = _needsSpeechServicesSetup.asStateFlow()

    private val prefs = appContext.getSharedPreferences("lumacut_offline_lang_prefs", Context.MODE_PRIVATE)

    private val _disabledOfflineTags = MutableStateFlow(
        prefs.getStringSet("disabled_offline_tags", emptySet()).orEmpty().toSet()
    )
    val disabledOfflineTags: StateFlow<Set<String>> = _disabledOfflineTags.asStateFlow()

    fun isOfflineToggleOn(pack: OfflineLanguagePackState): Boolean {
        val tagKey = pack.spec.bcp47.lowercase(Locale.US)
        if (tagKey in _disabledOfflineTags.value) return false
        return pack.status == OfflinePackStatus.INSTALLED || pack.status == OfflinePackStatus.DOWNLOADING
    }

    /**
     * Toggles offline model download status from Settings -> Language Management:
     * - Enabling starts the official Android SpeechRecognizer on-device model download.
     * - Disabling clears active download UI state and disables offline preference for this language
     *   in LumaCut (system packs managed by Google Speech Services can also be removed in Speech Settings).
     */
    fun toggleLanguageDownload(spec: OfflineLanguageSpec, enable: Boolean) {
        val tagKey = spec.bcp47.lowercase(Locale.US)
        if (enable) {
            val updated = _disabledOfflineTags.value - tagKey
            _disabledOfflineTags.value = updated
            prefs.edit().putStringSet("disabled_offline_tags", updated).apply()
            val current = _packs.value.firstOrNull { it.spec.bcp47.equals(spec.bcp47, ignoreCase = true) }
            if (current?.status == OfflinePackStatus.INSTALLED) {
                _statusMessage.value = "${spec.displayName} offline pack re-enabled."
            } else {
                downloadLanguagePack(spec)
            }
        } else {
            val updated = _disabledOfflineTags.value + tagKey
            _disabledOfflineTags.value = updated
            prefs.edit().putStringSet("disabled_offline_tags", updated).apply()
            val current = _packs.value.firstOrNull { it.spec.bcp47.equals(spec.bcp47, ignoreCase = true) }
            if (current?.status == OfflinePackStatus.DOWNLOADING) {
                updatePack(
                    spec.bcp47,
                    OfflinePackStatus.AVAILABLE_TO_DOWNLOAD,
                    0,
                    "Offline download paused — toggle ON anytime to resume"
                )
            }
            _statusMessage.value = "${spec.displayName} offline mode turned off (use Speech Settings to remove system pack)."
        }
    }

    private fun checkDeviceCapable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) ||
            SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    fun refreshInstalledModels() {
        val hasOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val hasSystem = SpeechRecognizer.isRecognitionAvailable(appContext)
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (hasOnDevice || hasSystem)
        _isOnDeviceSupported.value = supported

        if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _needsSpeechServicesSetup.value = true
            _packs.value = OfflineLanguageCatalog.languages.map {
                OfflineLanguagePackState(it, OfflinePackStatus.NOT_SUPPORTED, 0)
            }
            _statusMessage.value =
                "Enable 'Speech Recognition & Synthesis from Google' (Android 13+) for offline models, or use Cloud AI."
            return
        }

        mainHandler.post {
            querySupportWithRecognizer(preferOnDevice = hasOnDevice, allowRetryFallback = hasOnDevice && hasSystem)
        }
    }

    private fun querySupportWithRecognizer(preferOnDevice: Boolean, allowRetryFallback: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val recognizer = runCatching {
            if (preferOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        }.getOrNull()

        if (recognizer == null) {
            if (allowRetryFallback) {
                querySupportWithRecognizer(preferOnDevice = false, allowRetryFallback = false)
            } else {
                _needsSpeechServicesSetup.value = true
                _statusMessage.value = "Speech engine unavailable. Tap 'Enable Google Speech' below."
            }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }

        runCatching {
            recognizer.checkRecognitionSupport(
                intent,
                callbackExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        val installedRaw = recognitionSupport.installedOnDeviceLanguages
                        val pendingRaw = recognitionSupport.pendingOnDeviceLanguages
                        val supportedRaw = recognitionSupport.supportedOnDeviceLanguages
                        val onlineRaw = recognitionSupport.onlineLanguages

                        val allOnDeviceEmpty = installedRaw.isEmpty() && pendingRaw.isEmpty() && supportedRaw.isEmpty()
                        if (allOnDeviceEmpty && allowRetryFallback) {
                            mainHandler.post {
                                runCatching { recognizer.destroy() }
                                querySupportWithRecognizer(preferOnDevice = false, allowRetryFallback = false)
                            }
                            return
                        }

                        _needsSpeechServicesSetup.value = allOnDeviceEmpty && onlineRaw.isEmpty()
                        _packs.value = OfflineLanguageCatalog.languages.map { spec ->
                            val tag = normalizeLocaleTag(spec.bcp47)
                            val base = spec.iso6391.lowercase(Locale.US)

                            val matchedInstalled = installedRaw.firstOrNull { matchesLocale(normalizeLocaleTag(it), tag, base) }
                            val matchedPending = pendingRaw.firstOrNull { matchesLocale(normalizeLocaleTag(it), tag, base) }
                            val matchedSupported = supportedRaw.firstOrNull { matchesLocale(normalizeLocaleTag(it), tag, base) }
                            val matchedOnline = onlineRaw.firstOrNull { matchesLocale(normalizeLocaleTag(it), tag, base) }

                            val (status, exactTag, note) = when {
                                matchedInstalled != null -> Triple(
                                    OfflinePackStatus.INSTALLED,
                                    matchedInstalled,
                                    "Installed • 100% Offline"
                                )
                                matchedPending != null -> Triple(
                                    OfflinePackStatus.DOWNLOADING,
                                    matchedPending,
                                    "Download in progress…"
                                )
                                matchedSupported != null -> Triple(
                                    OfflinePackStatus.AVAILABLE_TO_DOWNLOAD,
                                    matchedSupported,
                                    "Offline pack ready to download ($matchedSupported)"
                                )
                                matchedOnline != null -> Triple(
                                    OfflinePackStatus.ONLINE_ONLY,
                                    matchedOnline,
                                    "No offline pack from Google on this phone — Device Speech & Cloud AI supported"
                                )
                                supportedRaw.isEmpty() -> Triple(
                                    OfflinePackStatus.AVAILABLE_TO_DOWNLOAD,
                                    spec.bcp47,
                                    "Tap Download or check Google Speech Services"
                                )
                                else -> Triple(
                                    OfflinePackStatus.ONLINE_ONLY,
                                    spec.bcp47,
                                    "Google doesn't ship an offline ${spec.displayName} pack on this device — use Transcribe or Cloud AI"
                                )
                            }

                            OfflineLanguagePackState(
                                spec = spec,
                                status = status,
                                downloadProgress = if (status == OfflinePackStatus.INSTALLED) 100 else 0,
                                deviceLocaleTag = exactTag,
                                note = note
                            )
                        }

                        val installedCount = _packs.value.count { it.status == OfflinePackStatus.INSTALLED }
                        val downloadableCount = _packs.value.count { it.status == OfflinePackStatus.AVAILABLE_TO_DOWNLOAD }
                        _statusMessage.value =
                            "$installedCount offline pack${if (installedCount == 1) "" else "s"} installed • $downloadableCount downloadable on this device"
                        mainHandler.post { runCatching { recognizer.destroy() } }
                    }

                    override fun onError(error: Int) {
                        mainHandler.post {
                            runCatching { recognizer.destroy() }
                            if (allowRetryFallback) {
                                querySupportWithRecognizer(preferOnDevice = false, allowRetryFallback = false)
                            } else {
                                _needsSpeechServicesSetup.value = true
                                _packs.value = OfflineLanguageCatalog.languages.map {
                                    OfflineLanguagePackState(
                                        spec = it,
                                        status = OfflinePackStatus.AVAILABLE_TO_DOWNLOAD,
                                        downloadProgress = 0,
                                        deviceLocaleTag = it.bcp47,
                                        note = "Tap Download or enable Google Speech Services"
                                    )
                                }
                                _statusMessage.value = describeSpeechError("Model check", error)
                            }
                        }
                    }
                }
            )
        }.onFailure {
            runCatching { recognizer.destroy() }
            if (allowRetryFallback) {
                querySupportWithRecognizer(preferOnDevice = false, allowRetryFallback = false)
            }
        }
    }

    fun downloadLanguagePack(spec: OfflineLanguageSpec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _statusMessage.value = "Offline pack downloads require Android 13+."
            return
        }
        val currentPack = _packs.value.firstOrNull { it.spec.bcp47.equals(spec.bcp47, ignoreCase = true) }
        val primaryTag = currentPack?.deviceLocaleTag?.takeIf { it.isNotBlank() } ?: spec.bcp47
        val candidateTags = listOf(primaryTag, spec.bcp47, spec.iso6391)
            .distinctBy { it.lowercase(Locale.US) }

        updatePack(spec.bcp47, OfflinePackStatus.DOWNLOADING, 5, "Requesting ${spec.displayName} model…")
        _statusMessage.value = "Downloading ${spec.displayName} (${spec.nativeLabel}) offline model…"

        mainHandler.post {
            val attempts = buildList {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                    candidateTags.forEach { tag -> add(true to tag) }
                }
                if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                    add(false to primaryTag)
                }
            }
            if (attempts.isEmpty()) {
                _needsSpeechServicesSetup.value = true
                updatePack(spec.bcp47, OfflinePackStatus.NOT_SUPPORTED, 0, "Google Speech Services disabled")
                _statusMessage.value = "Enable 'Speech Recognition & Synthesis from Google' in Android Settings."
                return@post
            }
            runDownloadAttempt(spec = spec, attempts = attempts, attemptIndex = 0)
        }
    }

    private fun runDownloadAttempt(
        spec: OfflineLanguageSpec,
        attempts: List<Pair<Boolean, String>>,
        attemptIndex: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (attemptIndex >= attempts.size) {
            updatePack(
                spec.bcp47,
                OfflinePackStatus.ONLINE_ONLY,
                0,
                "Offline pack not offered on this phone — tap Transcribe (Device Speech) or Cloud AI"
            )
            return
        }

        val (useOnDevice, localeTag) = attempts[attemptIndex]
        val recognizer = runCatching {
            if (useOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        }.getOrNull()

        if (recognizer == null) {
            runDownloadAttempt(spec, attempts, attemptIndex + 1)
            return
        }

        // Per Android SpeechRecognizer docs, triggerModelDownload should pass EXTRA_LANGUAGE cleanly
        // without conflicting flags like EXTRA_PREFER_OFFLINE.
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                recognizer.triggerModelDownload(
                    intent,
                    callbackExecutor,
                    object : ModelDownloadListener {
                        override fun onProgress(completedPercent: Int) {
                            updatePack(
                                spec.bcp47,
                                OfflinePackStatus.DOWNLOADING,
                                completedPercent.coerceIn(1, 99),
                                "Downloading… ${completedPercent.coerceIn(1, 99)}%"
                            )
                        }

                        override fun onSuccess() {
                            _needsSpeechServicesSetup.value = false
                            updatePack(spec.bcp47, OfflinePackStatus.INSTALLED, 100, "Installed • 100% Offline")
                            _statusMessage.value = "${spec.displayName} offline model installed!"
                            mainHandler.post { runCatching { recognizer.destroy() } }
                        }

                        override fun onScheduled() {
                            updatePack(
                                spec.bcp47,
                                OfflinePackStatus.DOWNLOADING,
                                20,
                                "Scheduled by Google Speech Services (Wi-Fi)"
                            )
                            _statusMessage.value =
                                "${spec.displayName} download scheduled in background by Google Speech Services."
                            mainHandler.post { runCatching { recognizer.destroy() } }
                        }

                        override fun onError(error: Int) {
                            mainHandler.post {
                                runCatching { recognizer.destroy() }
                                val hasMore = attemptIndex + 1 < attempts.size
                                val retryableByTagOrBinder =
                                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                                        error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ||
                                        error == SpeechRecognizer.ERROR_CLIENT ||
                                        error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                                if (hasMore && retryableByTagOrBinder) {
                                    runDownloadAttempt(spec, attempts, attemptIndex + 1)
                                } else {
                                    val isUnsupportedOffline =
                                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                                    _needsSpeechServicesSetup.value =
                                        error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ||
                                            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                                            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                                    val nextStatus = if (isUnsupportedOffline) {
                                        OfflinePackStatus.ONLINE_ONLY
                                    } else {
                                        OfflinePackStatus.AVAILABLE_TO_DOWNLOAD
                                    }
                                    val humanMsg = describeSpeechError(spec.displayName, error)
                                    updatePack(spec.bcp47, nextStatus, 0, humanMsg)
                                    _statusMessage.value = humanMsg
                                }
                            }
                        }
                    }
                )
            }.onFailure {
                runCatching { recognizer.destroy() }
                if (attemptIndex + 1 < attempts.size) {
                    runDownloadAttempt(spec, attempts, attemptIndex + 1)
                } else {
                    updatePack(spec.bcp47, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0, it.localizedMessage)
                    _statusMessage.value = "Model download failed: ${it.localizedMessage}"
                }
            }
        } else {
            // Android 13 (API 33): fire-and-forget triggerModelDownload
            runCatching {
                recognizer.triggerModelDownload(intent)
                _statusMessage.value =
                    "Requested ${spec.displayName} offline model download. Refresh status once complete."
            }.onFailure {
                updatePack(spec.bcp47, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0, it.localizedMessage)
                _statusMessage.value = "Could not request download: ${it.localizedMessage}"
            }
            mainHandler.postDelayed({
                runCatching { recognizer.destroy() }
                refreshInstalledModels()
            }, 4_000L)
        }
    }

    /**
     * Transcribes a video or audio clip using Android 13+'s SpeechRecognizer fed via
     * ParcelFileDescriptor audio pipe (`RecognizerIntent.EXTRA_AUDIO_SOURCE`).
     * Uses 100% on-device offline recognition when the language pack is installed, and automatically
     * falls back to system Google Speech Services when an offline pack isn't offered for that language
     * (e.g. Telugu on OEM devices without an offline `te-IN` pack).
     */
    suspend fun transcribeClipOffline(
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long,
        timelineOffsetMs: Long,
        languageHint: String,
        onStatus: (String) -> Unit
    ): Result<List<CaptionClip>> {
        val hasOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val hasSystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isRecognitionAvailable(appContext)

        if (!hasOnDevice && !hasSystem) {
            return Result.failure(
                IllegalStateException(
                    "Direct video speech transcription requires Android 13+ with Google Speech Services enabled. Use Offline Mic or Cloud AI."
                )
            )
        }

        val packState = _packs.value.firstOrNull {
            it.spec.displayName.equals(languageHint, ignoreCase = true) ||
                it.spec.bcp47.equals(languageHint, ignoreCase = true)
        }
        val bcp47 = packState?.deviceLocaleTag?.takeIf { it.isNotBlank() }
            ?: OfflineLanguageCatalog.bcp47ForName(languageHint)

        // Prefer 100% on-device offline recognizer when installed/available, else system Google Speech
        var useStrictOffline = hasOnDevice && packState?.status != OfflinePackStatus.ONLINE_ONLY

        onStatus("Decoding clip audio to 16kHz PCM…")
        val segmentsRes = AudioClipExtractor.decodeToMono16kPcmSegments(
            context = appContext,
            uriString = uriString,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
        val pcmSegments = segmentsRes.getOrElse { return Result.failure(it) }
        if (pcmSegments.isEmpty()) {
            return Result.failure(IllegalStateException("No audio samples found in clip."))
        }

        val rawCaptions = mutableListOf<RawCaptionSegment>()
        val total = pcmSegments.size
        var lastErrorCode: Int? = null

        for (idx in pcmSegments.indices) {
            val seg = pcmSegments[idx]
            val modeLabel = if (useStrictOffline) "Offline AI" else "Device Speech"
            onStatus("$modeLabel ($languageHint) • segment ${idx + 1}/$total…")

            var attempt = recognizeSinglePcmSegment(
                pcm16kMono = seg.pcm16kMonoBytes,
                bcp47Language = bcp47,
                preferOffline = useStrictOffline
            )

            // If on-device offline pack isn't installed/supported on this phone (e.g., Telugu code 12/13),
            // switch seamlessly to system Google Speech Services for this and remaining segments!
            val shouldFallbackOnline = useStrictOffline &&
                hasSystem &&
                attempt.text.isNullOrBlank() &&
                (attempt.errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    attempt.errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    attempt.errorCode == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ||
                    attempt.errorCode == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)

            if (shouldFallbackOnline) {
                useStrictOffline = false
                onStatus("No offline $languageHint pack on phone — switching to Google Speech…")
                attempt = recognizeSinglePcmSegment(
                    pcm16kMono = seg.pcm16kMonoBytes,
                    bcp47Language = bcp47,
                    preferOffline = false
                )
            }

            if (attempt.errorCode != null) lastErrorCode = attempt.errorCode
            if (!attempt.text.isNullOrBlank()) {
                rawCaptions.add(RawCaptionSegment(seg.startMs, seg.endMs, attempt.text))
            }
        }

        val clipDurMs = (trimEndMs - trimStartMs).coerceAtLeast(1000L)
        val normalized = CaptionSegmentNormalizer.normalize(
            rawSegments = rawCaptions,
            timelineOffsetMs = timelineOffsetMs,
            clipDurationMs = clipDurMs,
            maxWordsPerLine = 5
        )
        return if (normalized.isEmpty()) {
            val reason = lastErrorCode?.let { describeSpeechError(languageHint, it) }
                ?: "No clear $languageHint speech detected. Try Cloud AI (Gemini) in AI Login for best Telugu accuracy."
            Result.failure(IllegalStateException(reason))
        } else {
            Result.success(normalized)
        }
    }

    private data class SegmentRecognitionOutcome(
        val text: String? = null,
        val errorCode: Int? = null
    )

    private suspend fun recognizeSinglePcmSegment(
        pcm16kMono: ByteArray,
        bcp47Language: String,
        preferOffline: Boolean
    ): SegmentRecognitionOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return SegmentRecognitionOutcome()
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
            ?: return SegmentRecognitionOutcome()
        val readFd = pipe[0]
        val writeFd = pipe[1]

        val writerThread = Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    val chunkSize = 3200 // 100ms of 16kHz mono 16-bit PCM
                    var offset = 0
                    while (offset < pcm16kMono.size) {
                        val len = minOf(chunkSize, pcm16kMono.size - offset)
                        out.write(pcm16kMono, offset, len)
                        out.flush()
                        offset += len
                        Thread.sleep(12L)
                    }
                }
            } catch (_: IOException) {
            } catch (_: InterruptedException) {
            }
        }.apply { start() }

        val outcome = withTimeoutOrNull(18_000L) {
            suspendCancellableCoroutine<SegmentRecognitionOutcome> { cont ->
                mainHandler.post {
                    var recognizer: SpeechRecognizer? = null
                    fun finish(result: SegmentRecognitionOutcome) {
                        runCatching { recognizer?.destroy() }
                        runCatching { readFd.close() }
                        if (cont.isActive) cont.resume(result)
                    }

                    try {
                        recognizer = if (preferOffline && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                        } else {
                            SpeechRecognizer.createSpeechRecognizer(appContext)
                        }
                        var latestPartial: String? = null
                        recognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) = Unit
                            override fun onBeginningOfSpeech() = Unit
                            override fun onRmsChanged(rmsdB: Float) = Unit
                            override fun onBufferReceived(buffer: ByteArray?) = Unit
                            override fun onEndOfSpeech() = Unit
                            override fun onEvent(eventType: Int, params: Bundle?) = Unit
                            override fun onPartialResults(partialResults: Bundle?) {
                                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val p = list?.firstOrNull()?.trim()
                                if (!p.isNullOrEmpty()) latestPartial = p
                            }

                            override fun onResults(results: Bundle?) {
                                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val best = list?.firstOrNull()?.trim().orEmpty().ifBlank { latestPartial.orEmpty() }
                                finish(SegmentRecognitionOutcome(text = best.ifBlank { null }))
                            }

                            override fun onError(error: Int) {
                                finish(
                                    SegmentRecognitionOutcome(
                                        text = latestPartial?.takeIf { it.isNotBlank() },
                                        errorCode = error
                                    )
                                )
                            }
                        })

                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47Language)
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                        }
                        recognizer.startListening(intent)
                        cont.invokeOnCancellation {
                            mainHandler.post {
                                runCatching { recognizer?.cancel() }
                                runCatching { recognizer?.destroy() }
                                runCatching { readFd.close() }
                            }
                        }
                    } catch (_: Exception) {
                        finish(SegmentRecognitionOutcome(errorCode = SpeechRecognizer.ERROR_CLIENT))
                    }
                }
            }
        } ?: SegmentRecognitionOutcome()

        withContext(Dispatchers.IO) {
            runCatching { writerThread.interrupt() }
            runCatching { readFd.close() }
            runCatching { writeFd.close() }
        }
        return outcome
    }

    private fun updatePack(
        bcp47: String,
        status: OfflinePackStatus,
        progress: Int,
        note: String? = null
    ) {
        _packs.update { list ->
            list.map { pack ->
                if (pack.spec.bcp47.equals(bcp47, ignoreCase = true)) {
                    pack.copy(
                        status = status,
                        downloadProgress = progress,
                        note = note ?: pack.note
                    )
                } else pack
            }
        }
    }

    private fun normalizeLocaleTag(tag: String): String =
        tag.replace('_', '-').lowercase(Locale.US)

    private fun matchesLocale(candidate: String, targetTag: String, baseIso: String): Boolean {
        return candidate == targetTag || candidate == baseIso || candidate.startsWith("$baseIso-")
    }

    companion object {
        @Volatile
        private var INSTANCE: OfflineSpeechModelManager? = null

        fun getInstance(context: Context): OfflineSpeechModelManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineSpeechModelManager(context.applicationContext).also { INSTANCE = it }
            }

        /**
         * Decodes Android [SpeechRecognizer] error codes into clear, actionable guidance.
         */
        fun describeSpeechError(languageLabel: String, errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "Google Speech Services doesn't offer a downloadable offline $languageLabel pack on this device (code 12). Tap Transcribe to use Device Speech or switch to Cloud Gemini AI."
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "Offline $languageLabel pack is unavailable right now (code 13). Tap 'Speech Settings' or use Transcribe / Cloud AI."
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
                "Google Speech Services is disabled or outdated on this phone (code 14). Tap 'Enable Google Speech' below."
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "Google Speech Services disconnected (code 11). Tap 'Enable Google Speech' or retry."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Connect to Wi-Fi or mobile data once to download the $languageLabel model (code $errorCode)."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Speech Services needs Microphone permission (code 9). Grant mic permission and retry."
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                "Download queue is busy (code 15). Wait a moment and tap Download again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech engine is busy (code 8). Wait a second and retry."
            SpeechRecognizer.ERROR_NO_MATCH ->
                "No clear $languageLabel speech detected (code 7)."
            else ->
                "Could not download $languageLabel pack (code $errorCode). Check Google Speech Services or tap Transcribe / Cloud AI."
        }
    }
}
