package com.example

import android.speech.SpeechRecognizer
import com.example.ai.CaptionOutputMode
import com.example.ai.GeminiColoristService
import com.example.automation.EditorAutomationBridge
import com.example.automation.LocalMcpJsonRpcServer
import com.example.media.DiagnosticHealth
import com.example.media.OfflineLanguageCatalog
import com.example.media.OfflineSpeechModelManager
import com.example.media.OnlineVoiceCloneTtsEngine
import com.example.media.PhoneticTransliterator
import com.example.media.SpeechServicesDiagnosticEngine
import com.example.media.TeluguRegistrationState
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.ExportVideoCodec
import com.example.model.KeyframeEasing
import com.example.model.KeyframeEvaluator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioV5ProSuiteTest {

    @Test
    fun exportConfig_supports4k60fps_hevc_av1_and10bitColor() {
        val h264 = ExportConfig(
            resolution = ExportResolution.UHD_4K,
            fps = 60,
            bitrateMbps = 35,
            codec = ExportVideoCodec.H264,
            tenBitColor = false
        )
        val av1TenBit = h264.copy(codec = ExportVideoCodec.AV1, tenBitColor = true)
        val hevc = h264.copy(codec = ExportVideoCodec.HEVC, tenBitColor = true)

        assertEquals("video/avc", h264.codec.mimeType)
        assertEquals("video/hevc", hevc.codec.mimeType)
        assertEquals("video/av01", av1TenBit.codec.mimeType)

        // AV1 should estimate smaller output than H.264 at identical nominal bitrate
        val h264Mb = h264.estimateFileSizeMb(10_000L)
        val av1Mb = av1TenBit.estimateFileSizeMb(10_000L)
        assertTrue("Expected AV1 ($av1Mb MB) < H.264 ($h264Mb MB)", av1Mb < h264Mb)
    }

    @Test
    fun keyframeEvaluator_springAndHoldCurvesSampleProperly() {
        assertEquals(0f, KeyframeEvaluator.applyEasing(0f, KeyframeEasing.HOLD), 0.001f)
        assertEquals(0f, KeyframeEvaluator.applyEasing(0.85f, KeyframeEasing.HOLD), 0.001f)
        assertEquals(1f, KeyframeEvaluator.applyEasing(1f, KeyframeEasing.HOLD), 0.001f)

        val springMid = KeyframeEvaluator.applyEasing(0.75f, KeyframeEasing.SPRING)
        assertTrue("Spring curve should overshoot above 1.0 near end, got $springMid", springMid > 1.0f)

        val samples = KeyframeEvaluator.sampleCurve(KeyframeEasing.EASE_IN_OUT, steps = 20)
        assertEquals(21, samples.size)
        assertEquals(0f, samples.first().second, 0.001f)
        assertEquals(1f, samples.last().second, 0.001f)
    }

    @Test
    fun localMcpJsonRpcServer_enforcesBearerTokenAndExecutesMcpTools() {
        val server = LocalMcpJsonRpcServer(port = 0)
        val initialToken = server.status.value.authToken

        assertEquals("127.0.0.1", server.status.value.host)
        assertTrue(server.isAuthorizedHeader("Bearer $initialToken"))
        assertFalse(server.isAuthorizedHeader("Bearer wrong_token"))
        assertFalse(server.isAuthorizedHeader(null))

        val rotated = server.rotateToken()
        assertFalse(server.isAuthorizedHeader("Bearer $initialToken"))
        assertTrue(server.isAuthorizedHeader("Bearer $rotated"))

        val fakeBridge = object : EditorAutomationBridge {
            override fun getTimelineSummaryJson(): JSONObject =
                JSONObject().put("projectTitle", "Cinema Test").put("mainClips", 3)

            override fun addTextOverlay(text: String, durationMs: Long): JSONObject =
                JSONObject().put("status", "ok").put("text", text).put("durationMs", durationMs)

            override fun addCaption(text: String, durationMs: Long): JSONObject =
                JSONObject().put("status", "ok").put("text", text)

            override fun splitAtPlayhead(): JSONObject =
                JSONObject().put("status", "ok").put("splitAtMs", 1200L)

            override fun applyFilterPreset(presetName: String): JSONObject =
                JSONObject().put("status", "ok").put("appliedFilter", presetName)

            override fun setExportSettings(resolution: String?, fps: Int?, codec: String?, tenBit: Boolean?): JSONObject =
                JSONObject().put("status", "ok").put("resolution", resolution).put("codec", codec)

            override fun queueBackgroundRender(): JSONObject =
                JSONObject().put("status", "queued")
        }
        server.attachBridge(fakeBridge)

        val initResp = JSONObject(server.executeJsonRpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}"""))
        assertEquals("lumacut-local-mcp", initResp.getJSONObject("result").getJSONObject("serverInfo").getString("name"))

        val listResp = JSONObject(server.executeJsonRpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""))
        assertTrue(listResp.getJSONObject("result").getJSONArray("tools").length() >= 7)

        val callResp = JSONObject(
            server.executeJsonRpc(
                """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"timeline.addTextOverlay","arguments":{"text":"Hello MCP","durationMs":3500}}}"""
            )
        )
        val contentText = callResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text")
        assertTrue(contentText.contains("Hello MCP"))
    }

    @Test
    fun offlineSpeechModelManager_mapsTeluguBcp47AndDecodesSpeechErrors() {
        assertEquals("te-IN", OfflineLanguageCatalog.bcp47ForName("Telugu"))

        val unsupportedMsg = OfflineSpeechModelManager.describeSpeechError(
            "Telugu",
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
        )
        assertTrue(unsupportedMsg.contains("Telugu"))
        assertTrue(unsupportedMsg.contains("code 12"))
        assertTrue(unsupportedMsg.contains("Transcribe"))

        val cannotCheckMsg = OfflineSpeechModelManager.describeSpeechError(
            "Telugu",
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
        )
        assertTrue(cannotCheckMsg.contains("Enable Google Speech"))
        assertTrue(cannotCheckMsg.contains("code 14"))
    }

    @Test
    fun speechServicesDiagnostic_detectsTeluguInstalledOnlineOnlyAndDisabledStates() {
        val healthy = SpeechServicesDiagnosticEngine.buildReport(
            apiLevel = 34,
            googleSpeechInstalled = true,
            googleSpeechEnabled = true,
            googleSpeechVersion = "2026.05",
            hasFallbackGoogleSpeechHost = true,
            registeredServiceCount = 2,
            isSystemAvailable = true,
            isOnDeviceAvailable = true,
            installedLanguages = listOf("en-US", "te-IN"),
            pendingLanguages = emptyList(),
            supportedOfflineLanguages = listOf("hi-IN"),
            onlineLanguages = listOf("te-IN")
        )
        assertEquals(DiagnosticHealth.HEALTHY, healthy.overallHealth)
        assertEquals(TeluguRegistrationState.INSTALLED_OFFLINE, healthy.teluguState)
        assertTrue(healthy.teluguStatusMessage.contains("te-IN"))

        val onlineOnly = SpeechServicesDiagnosticEngine.buildReport(
            apiLevel = 34,
            googleSpeechInstalled = true,
            googleSpeechEnabled = true,
            googleSpeechVersion = "2026.05",
            hasFallbackGoogleSpeechHost = true,
            registeredServiceCount = 1,
            isSystemAvailable = true,
            isOnDeviceAvailable = true,
            installedLanguages = listOf("en-US"),
            pendingLanguages = emptyList(),
            supportedOfflineLanguages = listOf("hi-IN"),
            onlineLanguages = listOf("te-IN")
        )
        assertEquals(DiagnosticHealth.WARNING, onlineOnly.overallHealth)
        assertEquals(TeluguRegistrationState.ONLINE_ONLY_REGISTERED, onlineOnly.teluguState)

        val disabled = SpeechServicesDiagnosticEngine.buildReport(
            apiLevel = 34,
            googleSpeechInstalled = false,
            googleSpeechEnabled = false,
            googleSpeechVersion = "Not installed",
            hasFallbackGoogleSpeechHost = false,
            registeredServiceCount = 0,
            isSystemAvailable = false,
            isOnDeviceAvailable = false,
            installedLanguages = emptyList(),
            pendingLanguages = emptyList(),
            supportedOfflineLanguages = emptyList(),
            onlineLanguages = emptyList()
        )
        assertEquals(DiagnosticHealth.ACTION_REQUIRED, disabled.overallHealth)
        assertEquals(TeluguRegistrationState.SERVICE_UNAVAILABLE, disabled.teluguState)
    }

    @Test
    fun phoneticTransliterator_convertsTeluguAndHindiToReadablePhoneticEnglish() {
        val teluguWord = PhoneticTransliterator.toPhoneticEnglish("తెలుగు")
        assertEquals("Telugu", teluguWord)

        val teluguGreeting = PhoneticTransliterator.toPhoneticEnglish("నమస్కారం")
        assertTrue("Expected phonetic Telugu greeting, got $teluguGreeting", teluguGreeting.equals("Namaskaram", ignoreCase = true))

        val alreadyEnglish = PhoneticTransliterator.toPhoneticEnglish("Hello LumaCut 4K")
        assertEquals("Hello LumaCut 4K", alreadyEnglish)
    }

    @Test
    fun captionOutputModes_generateTranslationAndPhoneticInstructions() {
        val translatePrompt = GeminiColoristService.buildCaptionOutputPromptInstruction(
            sourceLangDesc = "the spoken audio is in Telugu",
            outputMode = CaptionOutputMode.TRANSLATE,
            targetOutputLanguage = "Japanese"
        )
        assertTrue(translatePrompt.contains("TRANSLATE"))
        assertTrue(translatePrompt.contains("Japanese"))

        val phoneticPrompt = GeminiColoristService.buildCaptionOutputPromptInstruction(
            sourceLangDesc = "the spoken audio is in Telugu",
            outputMode = CaptionOutputMode.PHONETIC_ENGLISH,
            targetOutputLanguage = "English"
        )
        assertTrue(phoneticPrompt.contains("DO NOT translate"))
        assertTrue(phoneticPrompt.contains("phonetically"))
    }

    @Test
    fun onlineVoiceCloneEndpointValidation_blocksCleartextAndPrivateHosts() {
        assertTrue(OnlineVoiceCloneTtsEngine.validateCustomHttpsEndpoint("https://api.elevenlabs.io/v1/text-to-speech").isSuccess)
        assertTrue(OnlineVoiceCloneTtsEngine.validateCustomHttpsEndpoint("http://api.example.com/v1/audio/speech").isFailure)
        assertTrue(OnlineVoiceCloneTtsEngine.validateCustomHttpsEndpoint("https://127.0.0.1/v1/audio/speech").isFailure)
        assertTrue(OnlineVoiceCloneTtsEngine.validateCustomHttpsEndpoint("https://169.254.169.254/latest/meta-data").isFailure)
        assertTrue(OnlineVoiceCloneTtsEngine.validateCustomHttpsEndpoint("https://localhost:8080/tts").isFailure)
    }
}
