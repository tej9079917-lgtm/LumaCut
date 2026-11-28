package com.example

import com.example.automation.EditorAutomationBridge
import com.example.automation.LocalMcpJsonRpcServer
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
}
