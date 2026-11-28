package com.example.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

enum class DiagnosticHealth {
    HEALTHY,
    WARNING,
    ACTION_REQUIRED
}

enum class TeluguRegistrationState {
    INSTALLED_OFFLINE,
    DOWNLOADING_OFFLINE,
    DOWNLOADABLE_OFFLINE,
    ONLINE_ONLY_REGISTERED,
    NOT_REGISTERED,
    SERVICE_UNAVAILABLE
}

data class SpeechDiagnosticCheckItem(
    val title: String,
    val detail: String,
    val passed: Boolean,
    val warningOnly: Boolean = false
)

data class SpeechDiagnosticReport(
    val isChecking: Boolean = false,
    val checkedAtMs: Long = 0L,
    val overallHealth: DiagnosticHealth = DiagnosticHealth.WARNING,
    val androidApiLevel: Int = Build.VERSION.SDK_INT,
    val googleSpeechPackageInstalled: Boolean = false,
    val googleSpeechPackageEnabled: Boolean = false,
    val googleSpeechVersion: String = "Not detected",
    val registeredServiceCount: Int = 0,
    val isSystemRecognitionAvailable: Boolean = false,
    val isOnDeviceRecognitionAvailable: Boolean = false,
    val teluguState: TeluguRegistrationState = TeluguRegistrationState.SERVICE_UNAVAILABLE,
    val teluguMatchedTag: String? = null,
    val englishInstalledOffline: Boolean = false,
    val statusHeadline: String = "Tap Re-run Check to inspect Google Speech Services.",
    val teluguStatusMessage: String = "Checking Telugu (te-IN) registration…",
    val checks: List<SpeechDiagnosticCheckItem> = emptyList()
)

/**
 * Inspects device Google Speech Services presence, package validity, RecognitionService registration,
 * and specifically verifies Telugu (`te-IN` / `te`) language pack registration for offline & online captions.
 */
object SpeechServicesDiagnosticEngine {

    private const val PKG_GOOGLE_SPEECH = "com.google.android.tts"
    private const val PKG_ANDROID_SYSTEM_INTELLIGENCE = "com.google.android.as"
    private const val PKG_GOOGLE_APP = "com.google.android.googlequicksearchbox"

    private val callbackExecutor = Executors.newSingleThreadExecutor()

    suspend fun runFullDiagnostic(context: Context): SpeechDiagnosticReport = withContext(Dispatchers.Main) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val apiLevel = Build.VERSION.SDK_INT

        val googlePkgInfo = inspectPackage(pm, PKG_GOOGLE_SPEECH)
        val systemIntelInfo = inspectPackage(pm, PKG_ANDROID_SYSTEM_INTELLIGENCE)
        val googleAppInfo = inspectPackage(pm, PKG_GOOGLE_APP)

        val recognitionServices = runCatching {
            val queryIntent = Intent("android.speech.RecognitionService")
            if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(queryIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(queryIntent, 0)
            }
        }.getOrDefault(emptyList())

        val isSystemAvail = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)
        val isOnDeviceAvail = if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
        } else {
            false
        }

        val supportSnapshot = if (apiLevel >= Build.VERSION_CODES.TIRAMISU && (isOnDeviceAvail || isSystemAvail)) {
            querySupportSnapshot(appContext, preferOnDevice = isOnDeviceAvail)
        } else {
            null
        }

        buildReport(
            apiLevel = apiLevel,
            googleSpeechInstalled = googlePkgInfo.installed,
            googleSpeechEnabled = googlePkgInfo.enabled,
            googleSpeechVersion = googlePkgInfo.versionName,
            hasFallbackGoogleSpeechHost = systemIntelInfo.enabled || googleAppInfo.enabled,
            registeredServiceCount = recognitionServices.size,
            isSystemAvailable = isSystemAvail,
            isOnDeviceAvailable = isOnDeviceAvail,
            installedLanguages = supportSnapshot?.installed.orEmpty(),
            pendingLanguages = supportSnapshot?.pending.orEmpty(),
            supportedOfflineLanguages = supportSnapshot?.supported.orEmpty(),
            onlineLanguages = supportSnapshot?.online.orEmpty(),
            supportQueryError = supportSnapshot?.errorCode
        )
    }

    /**
     * Pure-logic builder exposed for deterministic Robolectric/JVM unit testing of diagnostic states.
     */
    fun buildReport(
        apiLevel: Int,
        googleSpeechInstalled: Boolean,
        googleSpeechEnabled: Boolean,
        googleSpeechVersion: String,
        hasFallbackGoogleSpeechHost: Boolean,
        registeredServiceCount: Int,
        isSystemAvailable: Boolean,
        isOnDeviceAvailable: Boolean,
        installedLanguages: List<String>,
        pendingLanguages: List<String>,
        supportedOfflineLanguages: List<String>,
        onlineLanguages: List<String>,
        supportQueryError: Int? = null
    ): SpeechDiagnosticReport {
        val teluguInstalled = installedLanguages.firstOrNull { isLocaleMatch(it, "te-in", "te") }
        val teluguPending = pendingLanguages.firstOrNull { isLocaleMatch(it, "te-in", "te") }
        val teluguSupported = supportedOfflineLanguages.firstOrNull { isLocaleMatch(it, "te-in", "te") }
        val teluguOnline = onlineLanguages.firstOrNull { isLocaleMatch(it, "te-in", "te") }
        val englishInstalled = installedLanguages.any { isLocaleMatch(it, "en-us", "en") }

        val (teluguState, teluguTag, teluguMsg) = when {
            !isSystemAvailable && !isOnDeviceAvailable -> Triple(
                TeluguRegistrationState.SERVICE_UNAVAILABLE,
                null,
                "Google Speech Services is disabled or missing — Telugu (te-IN) cannot register until Speech Services is enabled."
            )
            teluguInstalled != null -> Triple(
                TeluguRegistrationState.INSTALLED_OFFLINE,
                teluguInstalled,
                "Telugu ($teluguInstalled) offline speech pack is installed and registered for 100% offline captions."
            )
            teluguPending != null -> Triple(
                TeluguRegistrationState.DOWNLOADING_OFFLINE,
                teluguPending,
                "Telugu ($teluguPending) offline speech pack download is currently in progress."
            )
            teluguSupported != null -> Triple(
                TeluguRegistrationState.DOWNLOADABLE_OFFLINE,
                teluguSupported,
                "Telugu ($teluguSupported) is registered and ready to download offline. Toggle Telugu ON below."
            )
            teluguOnline != null -> Triple(
                TeluguRegistrationState.ONLINE_ONLY_REGISTERED,
                teluguOnline,
                "Telugu ($teluguOnline) is registered as Online-Only by Google on this phone. LumaCut will automatically use Device Speech or Cloud AI for Telugu."
            )
            apiLevel < Build.VERSION_CODES.TIRAMISU -> Triple(
                TeluguRegistrationState.ONLINE_ONLY_REGISTERED,
                "te-IN",
                "Android $apiLevel does not expose offline pack queries (requires Android 13+). Device Speech & Cloud AI work for Telugu."
            )
            supportQueryError != null -> Triple(
                TeluguRegistrationState.NOT_REGISTERED,
                null,
                OfflineSpeechModelManager.describeSpeechError("Telugu", supportQueryError)
            )
            else -> Triple(
                TeluguRegistrationState.NOT_REGISTERED,
                null,
                "Telugu (te-IN) was not reported in this device's offline registry. Update 'Speech Recognition & Synthesis from Google' or use Cloud AI."
            )
        }

        val googleServiceValid = (googleSpeechInstalled && googleSpeechEnabled) ||
            (hasFallbackGoogleSpeechHost && (isSystemAvailable || isOnDeviceAvailable))

        val checks = listOf(
            SpeechDiagnosticCheckItem(
                title = "Google Speech Services App",
                detail = when {
                    googleSpeechInstalled && googleSpeechEnabled -> "Installed & Enabled (v$googleSpeechVersion)"
                    googleSpeechInstalled && !googleSpeechEnabled -> "Installed but DISABLED in Android Settings"
                    hasFallbackGoogleSpeechHost -> "System Google Speech host active (com.google.android.tts not standalone)"
                    else -> "Not installed — install 'Speech Recognition & Synthesis from Google'"
                },
                passed = googleServiceValid,
                warningOnly = !googleSpeechInstalled && hasFallbackGoogleSpeechHost
            ),
            SpeechDiagnosticCheckItem(
                title = "RecognitionService Registration",
                detail = if (isSystemAvailable || isOnDeviceAvailable) {
                    "Active ($registeredServiceCount service(s) visible; On-Device=${if (isOnDeviceAvailable) "Yes" else "No"})"
                } else {
                    "No active Speech RecognitionService detected"
                },
                passed = isSystemAvailable || isOnDeviceAvailable
            ),
            SpeechDiagnosticCheckItem(
                title = "Android Offline Model API",
                detail = if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
                    "API $apiLevel supported (Android 13+ on-device pack manager)"
                } else {
                    "API $apiLevel (requires API 33+ for downloadable offline packs)"
                },
                passed = apiLevel >= Build.VERSION_CODES.TIRAMISU,
                warningOnly = apiLevel < Build.VERSION_CODES.TIRAMISU
            ),
            SpeechDiagnosticCheckItem(
                title = "Telugu (te-IN) Pack Registration",
                detail = when (teluguState) {
                    TeluguRegistrationState.INSTALLED_OFFLINE -> "Registered & Installed Offline ($teluguTag)"
                    TeluguRegistrationState.DOWNLOADING_OFFLINE -> "Downloading Offline ($teluguTag)"
                    TeluguRegistrationState.DOWNLOADABLE_OFFLINE -> "Registered & Ready to Download ($teluguTag)"
                    TeluguRegistrationState.ONLINE_ONLY_REGISTERED -> "Registered Online-Only ($teluguTag)"
                    TeluguRegistrationState.NOT_REGISTERED -> "Not found in device offline catalog"
                    TeluguRegistrationState.SERVICE_UNAVAILABLE -> "Unavailable (enable Speech Services)"
                },
                passed = teluguState == TeluguRegistrationState.INSTALLED_OFFLINE ||
                    teluguState == TeluguRegistrationState.DOWNLOADABLE_OFFLINE ||
                    teluguState == TeluguRegistrationState.DOWNLOADING_OFFLINE,
                warningOnly = teluguState == TeluguRegistrationState.ONLINE_ONLY_REGISTERED
            )
        )

        val overallHealth = when {
            !googleServiceValid || (!isSystemAvailable && !isOnDeviceAvailable) -> DiagnosticHealth.ACTION_REQUIRED
            teluguState == TeluguRegistrationState.INSTALLED_OFFLINE ||
                teluguState == TeluguRegistrationState.DOWNLOADABLE_OFFLINE ||
                teluguState == TeluguRegistrationState.DOWNLOADING_OFFLINE -> DiagnosticHealth.HEALTHY
            else -> DiagnosticHealth.WARNING
        }

        val headline = when (overallHealth) {
            DiagnosticHealth.HEALTHY -> "Google Speech Services valid • Telugu offline registration confirmed"
            DiagnosticHealth.WARNING -> "Google Speech Services active • Telugu uses Device Speech / Cloud AI fallback"
            DiagnosticHealth.ACTION_REQUIRED -> "Action needed • Enable or update Google Speech Services"
        }

        return SpeechDiagnosticReport(
            isChecking = false,
            checkedAtMs = System.currentTimeMillis(),
            overallHealth = overallHealth,
            androidApiLevel = apiLevel,
            googleSpeechPackageInstalled = googleSpeechInstalled,
            googleSpeechPackageEnabled = googleSpeechEnabled,
            googleSpeechVersion = googleSpeechVersion,
            registeredServiceCount = registeredServiceCount,
            isSystemRecognitionAvailable = isSystemAvailable,
            isOnDeviceRecognitionAvailable = isOnDeviceAvailable,
            teluguState = teluguState,
            teluguMatchedTag = teluguTag,
            englishInstalledOffline = englishInstalled,
            statusHeadline = headline,
            teluguStatusMessage = teluguMsg,
            checks = checks
        )
    }

    private data class PackageCheck(
        val installed: Boolean,
        val enabled: Boolean,
        val versionName: String
    )

    private fun inspectPackage(pm: PackageManager, pkg: String): PackageCheck = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        val enabled = info.applicationInfo?.enabled ?: true
        val ver = info.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
        PackageCheck(installed = true, enabled = enabled, versionName = ver)
    }.getOrDefault(PackageCheck(installed = false, enabled = false, versionName = "Not installed"))

    private data class SupportSnapshot(
        val installed: List<String> = emptyList(),
        val pending: List<String> = emptyList(),
        val supported: List<String> = emptyList(),
        val online: List<String> = emptyList(),
        val errorCode: Int? = null
    )

    private suspend fun querySupportSnapshot(
        appContext: Context,
        preferOnDevice: Boolean
    ): SupportSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val firstAttempt = querySingleRecognizer(appContext, onDevice = preferOnDevice)
        if (firstAttempt != null &&
            (firstAttempt.installed.isNotEmpty() || firstAttempt.supported.isNotEmpty() || firstAttempt.online.isNotEmpty())
        ) {
            return firstAttempt
        }
        if (preferOnDevice && SpeechRecognizer.isRecognitionAvailable(appContext)) {
            return querySingleRecognizer(appContext, onDevice = false) ?: firstAttempt
        }
        return firstAttempt
    }

    private suspend fun querySingleRecognizer(appContext: Context, onDevice: Boolean): SupportSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { cont ->
                val recognizer = runCatching {
                    if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    else SpeechRecognizer.createSpeechRecognizer(appContext)
                }.getOrNull()

                if (recognizer == null) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation { runCatching { recognizer.destroy() } }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }

                runCatching {
                    recognizer.checkRecognitionSupport(
                        intent,
                        callbackExecutor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                val snap = SupportSnapshot(
                                    installed = recognitionSupport.installedOnDeviceLanguages,
                                    pending = recognitionSupport.pendingOnDeviceLanguages,
                                    supported = recognitionSupport.supportedOnDeviceLanguages,
                                    online = recognitionSupport.onlineLanguages
                                )
                                runCatching { recognizer.destroy() }
                                if (cont.isActive) cont.resume(snap)
                            }

                            override fun onError(error: Int) {
                                runCatching { recognizer.destroy() }
                                if (cont.isActive) cont.resume(SupportSnapshot(errorCode = error))
                            }
                        }
                    )
                }.onFailure {
                    runCatching { recognizer.destroy() }
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun isLocaleMatch(candidate: String, fullNormalized: String, baseIso: String): Boolean {
        val norm = candidate.replace('_', '-').lowercase(Locale.US)
        return norm == fullNormalized || norm == baseIso || norm.startsWith("$baseIso-")
    }
}
