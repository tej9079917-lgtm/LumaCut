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
    AVAILABLE_TO_DOWNLOAD,
    DOWNLOADING,
    INSTALLED
}

data class OfflineLanguagePackState(
    val spec: OfflineLanguageSpec,
    val status: OfflinePackStatus = OfflinePackStatus.UNKNOWN,
    val downloadProgress: Int = 0
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
 * Uses Android's official on-device SpeechRecognizer APIs (API 33+):
 * - `checkRecognitionSupport` to detect installed vs downloadable offline language packs
 * - `triggerModelDownload` with live `ModelDownloadListener` progress (ZERO dynamic `.so`/`.dex` loading)
 * - `RecognizerIntent.EXTRA_AUDIO_SOURCE` (`ParcelFileDescriptor` pipe) to transcribe extracted 16kHz PCM
 *   video audio 100% offline without requiring a microphone or cloud key!
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

    private fun checkDeviceCapable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    }

    fun refreshInstalledModels() {
        val supported = checkDeviceCapable()
        _isOnDeviceSupported.value = supported
        if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _packs.value = OfflineLanguageCatalog.languages.map {
                OfflineLanguagePackState(it, OfflinePackStatus.NOT_SUPPORTED, 0)
            }
            _statusMessage.value =
                "On-device video transcription requires Android 13+ with Google Speech Services. Offline Mic & AI Login work on all devices."
            return
        }

        mainHandler.post {
            val recognizer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            }.getOrNull()
            if (recognizer == null) {
                _statusMessage.value = "On-device speech engine unavailable on this device."
                return@post
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            runCatching {
                recognizer.checkRecognitionSupport(
                    intent,
                    callbackExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                            val installed = recognitionSupport.installedOnDeviceLanguages
                                .map { normalizeLocaleTag(it) }.toSet()
                            val pending = recognitionSupport.pendingOnDeviceLanguages
                                .map { normalizeLocaleTag(it) }.toSet()
                            val supportedAll = recognitionSupport.supportedOnDeviceLanguages
                                .map { normalizeLocaleTag(it) }.toSet()

                            _packs.value = OfflineLanguageCatalog.languages.map { spec ->
                                val tag = normalizeLocaleTag(spec.bcp47)
                                val base = spec.iso6391.lowercase(Locale.US)
                                val status = when {
                                    installed.any { matchesLocale(it, tag, base) } -> OfflinePackStatus.INSTALLED
                                    pending.any { matchesLocale(it, tag, base) } -> OfflinePackStatus.DOWNLOADING
                                    supportedAll.any { matchesLocale(it, tag, base) } -> OfflinePackStatus.AVAILABLE_TO_DOWNLOAD
                                    else -> OfflinePackStatus.AVAILABLE_TO_DOWNLOAD
                                }
                                OfflineLanguagePackState(
                                    spec = spec,
                                    status = status,
                                    downloadProgress = if (status == OfflinePackStatus.INSTALLED) 100 else 0
                                )
                            }
                            val installedCount = _packs.value.count { it.status == OfflinePackStatus.INSTALLED }
                            _statusMessage.value = "$installedCount offline language pack${if (installedCount == 1) "" else "s"} ready on device"
                            mainHandler.post { runCatching { recognizer.destroy() } }
                        }

                        override fun onError(error: Int) {
                            _packs.value = OfflineLanguageCatalog.languages.map {
                                OfflineLanguagePackState(it, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0)
                            }
                            _statusMessage.value = "Tap Download on any language to install its offline model"
                            mainHandler.post { runCatching { recognizer.destroy() } }
                        }
                    }
                )
            }.onFailure {
                runCatching { recognizer.destroy() }
            }
        }
    }

    fun downloadLanguagePack(spec: OfflineLanguageSpec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _statusMessage.value = "Offline pack downloads require Android 13+."
            return
        }
        updatePack(spec.bcp47, OfflinePackStatus.DOWNLOADING, 5)
        _statusMessage.value = "Downloading ${spec.displayName} (${spec.nativeLabel}) offline model…"

        mainHandler.post {
            val recognizer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            }.getOrNull()
            if (recognizer == null) {
                updatePack(spec.bcp47, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0)
                _statusMessage.value = "On-device Speech Services not available on this phone."
                return@post
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, spec.bcp47)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
                                    completedPercent.coerceIn(1, 99)
                                )
                            }

                            override fun onSuccess() {
                                updatePack(spec.bcp47, OfflinePackStatus.INSTALLED, 100)
                                _statusMessage.value = "${spec.displayName} offline model installed!"
                                mainHandler.post { runCatching { recognizer.destroy() } }
                            }

                            override fun onScheduled() {
                                updatePack(spec.bcp47, OfflinePackStatus.DOWNLOADING, 15)
                                _statusMessage.value =
                                    "${spec.displayName} model scheduled in background by Android Speech Services."
                                mainHandler.post { runCatching { recognizer.destroy() } }
                            }

                            override fun onError(error: Int) {
                                updatePack(spec.bcp47, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0)
                                _statusMessage.value =
                                    "Could not download ${spec.displayName} (code $error). Ensure Google Speech Services is enabled."
                                mainHandler.post { runCatching { recognizer.destroy() } }
                            }
                        }
                    )
                }.onFailure {
                    updatePack(spec.bcp47, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0)
                    _statusMessage.value = "Model download failed: ${it.localizedMessage}"
                    runCatching { recognizer.destroy() }
                }
            } else {
                // Android 13 (API 33): fire-and-forget triggerModelDownload
                runCatching {
                    recognizer.triggerModelDownload(intent)
                    _statusMessage.value =
                        "Requested ${spec.displayName} offline model download. Refresh status once complete."
                }.onFailure {
                    updatePack(spec.bcp47, OfflinePackStatus.AVAILABLE_TO_DOWNLOAD, 0)
                    _statusMessage.value = "Could not request download: ${it.localizedMessage}"
                }
                mainHandler.postDelayed({
                    runCatching { recognizer.destroy() }
                    refreshInstalledModels()
                }, 4_000L)
            }
        }
    }

    /**
     * Transcribes a video or audio clip 100% offline using Android 13+'s on-device SpeechRecognizer
     * fed via ParcelFileDescriptor audio pipe (`RecognizerIntent.EXTRA_AUDIO_SOURCE`).
     */
    suspend fun transcribeClipOffline(
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long,
        timelineOffsetMs: Long,
        languageHint: String,
        onStatus: (String) -> Unit
    ): Result<List<CaptionClip>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            return Result.failure(
                IllegalStateException(
                    "Direct offline video transcription requires Android 13+ with Speech Services. Use Offline Mic or connect a free AI key in AI Login."
                )
            )
        }

        val bcp47 = OfflineLanguageCatalog.bcp47ForName(languageHint)
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
        for (idx in pcmSegments.indices) {
            val seg = pcmSegments[idx]
            onStatus("Offline AI (${languageHint}) • segment ${idx + 1}/$total…")
            val recognized = recognizeSinglePcmSegmentOffline(seg.pcm16kMonoBytes, bcp47)
            if (!recognized.isNullOrBlank()) {
                rawCaptions.add(RawCaptionSegment(seg.startMs, seg.endMs, recognized))
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
            Result.failure(
                IllegalStateException(
                    "Offline engine detected no clear speech in $languageHint. Make sure the $languageHint offline pack is downloaded in Offline Models, or try AI Login."
                )
            )
        } else {
            Result.success(normalized)
        }
    }

    private suspend fun recognizeSinglePcmSegmentOffline(
        pcm16kMono: ByteArray,
        bcp47Language: String
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return null
        val readFd = pipe[0]
        val writeFd = pipe[1]

        // Stream PCM bytes asynchronously into the write end of the pipe, then close to signal EOF
        val writerThread = Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    // Pace slightly in chunks so recognizer buffer does not overflow
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

        val text = withTimeoutOrNull(18_000L) {
            suspendCancellableCoroutine<String?> { cont ->
                mainHandler.post {
                    var recognizer: SpeechRecognizer? = null
                    fun finish(value: String?) {
                        runCatching { recognizer?.destroy() }
                        runCatching { readFd.close() }
                        if (cont.isActive) cont.resume(value)
                    }

                    try {
                        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
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
                                finish(best.ifBlank { null })
                            }
                            override fun onError(error: Int) {
                                finish(latestPartial?.takeIf { it.isNotBlank() })
                            }
                        })

                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47Language)
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
                        finish(null)
                    }
                }
            }
        }

        withContext(Dispatchers.IO) {
            runCatching { writerThread.interrupt() }
            runCatching { readFd.close() }
            runCatching { writeFd.close() }
        }
        return text
    }

    private fun updatePack(bcp47: String, status: OfflinePackStatus, progress: Int) {
        _packs.update { list ->
            list.map { pack ->
                if (pack.spec.bcp47.equals(bcp47, ignoreCase = true)) {
                    pack.copy(status = status, downloadProgress = progress)
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
    }
}
