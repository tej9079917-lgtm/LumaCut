package com.example

import com.example.media.SilentAudioGapHelper
import com.example.model.OverlayGestureMath
import com.example.model.TimelineScrubMath
import com.example.model.TransitionType
import com.example.model.TransitionVisualEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StabilizationAndInteractionTest {

    @Test
    fun overlayGestureMath_rotatesAndScalesLocalPanToStageSpace() {
        // Identity (scale = 1, rot = 0)
        val (dx0, dy0) = OverlayGestureMath.localPanToStageDelta(10f, -5f, scale = 1f, rotationDegrees = 0f)
        assertEquals(10f, dx0, 0.001f)
        assertEquals(-5f, dy0, 0.001f)

        // 2x scale without rotation
        val (dxS, dyS) = OverlayGestureMath.localPanToStageDelta(10f, -5f, scale = 2f, rotationDegrees = 0f)
        assertEquals(20f, dxS, 0.001f)
        assertEquals(-10f, dyS, 0.001f)

        // 90 degrees clockwise: local +X moves down (+Y) in stage space
        val (dx90, dy90) = OverlayGestureMath.localPanToStageDelta(10f, 0f, scale = 1f, rotationDegrees = 90f)
        assertEquals(0f, dx90, 0.01f)
        assertEquals(10f, dy90, 0.01f)

        // 180 degrees: local (+X, +Y) inverts to (-X, -Y)
        val (dx180, dy180) = OverlayGestureMath.localPanToStageDelta(6f, 4f, scale = 1.5f, rotationDegrees = 180f)
        assertEquals(-9f, dx180, 0.01f)
        assertEquals(-6f, dy180, 0.01f)
    }

    @Test
    fun overlayGestureMath_freePlacementClampsToPartialOffScreenBounds() {
        assertEquals(0.85f, OverlayGestureMath.clampFreePlacement(0.85f), 0.0001f)
        assertEquals(-0.85f, OverlayGestureMath.clampFreePlacement(-0.85f), 0.0001f)
        assertEquals(OverlayGestureMath.FREE_PLACEMENT_MAX_OFFSET, OverlayGestureMath.clampFreePlacement(1.6f), 0.0001f)
        assertEquals(-OverlayGestureMath.FREE_PLACEMENT_MAX_OFFSET, OverlayGestureMath.clampFreePlacement(-2.0f), 0.0001f)
    }

    @Test
    fun timelineScrubMath_roundTripsScrollPxAndTimeMsAndClampsDrag() {
        val pxPerMs = 0.08f
        val maxMs = 15_000L

        val px = TimelineScrubMath.msToScrollPx(4250L, pxPerMs)
        assertEquals(340, px)
        assertEquals(4250L, TimelineScrubMath.scrollPxToMs(px, pxPerMs, maxMs))

        // Out-of-bounds scroll clamps to maxDurationMs
        assertEquals(maxMs, TimelineScrubMath.scrollPxToMs(999_999, pxPerMs, maxMs))
        assertEquals(0L, TimelineScrubMath.scrollPxToMs(-50, pxPerMs, maxMs))

        // Fractional drag accumulates smoothly and clamps to [0, maxDurationMs]
        val moved = TimelineScrubMath.applyDragDeltaMs(1000f, dragPx = 80f, pxPerMs = pxPerMs, maxDurationMs = maxMs)
        assertEquals(2000f, moved, 0.01f)
        val clampedHigh = TimelineScrubMath.applyDragDeltaMs(14_900f, dragPx = 500f, pxPerMs = pxPerMs, maxDurationMs = maxMs)
        assertEquals(15_000f, clampedHigh, 0.01f)
        val clampedLow = TimelineScrubMath.applyDragDeltaMs(50f, dragPx = -500f, pxPerMs = pxPerMs, maxDurationMs = maxMs)
        assertEquals(0f, clampedLow, 0.01f)
    }

    @Test
    fun transitionVisualEvaluator_coversAll14TransitionTypesWithValidMidpoints() {
        for (type in TransitionType.entries) {
            val mid = TransitionVisualEvaluator.evaluate(type, 0.5f)
            assertTrue("Alpha in [0,1] for $type", mid.activeAlpha in 0f..1f)
            assertTrue("Partner alpha in [0,1] for $type", mid.partnerAlpha in 0f..1f)

            when (type) {
                TransitionType.NONE -> {
                    assertEquals(1f, mid.activeAlpha, 0.001f)
                    assertEquals(0f, mid.dipBlackAlpha, 0.001f)
                }
                TransitionType.CROSSFADE -> {
                    assertEquals(0.5f, mid.activeAlpha, 0.01f)
                    assertTrue(mid.showPartnerBehind)
                }
                TransitionType.DIP_TO_BLACK -> assertEquals(1f, mid.dipBlackAlpha, 0.01f)
                TransitionType.DIP_TO_WHITE -> assertEquals(1f, mid.dipWhiteAlpha, 0.01f)
                TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT -> {
                    assertTrue(mid.showPartnerBehind)
                    assertTrue(kotlin.math.abs(mid.activeTranslationXFrac) > 0.05f)
                }
                TransitionType.ZOOM_IN -> {
                    assertTrue(mid.activeScaleX > 1.15f)
                    assertTrue(mid.showPartnerBehind)
                }
                TransitionType.WIPE_RIGHT -> {
                    assertEquals(0f, mid.wipeClipStartFrac, 0.01f)
                    assertEquals(0.5f, mid.wipeClipEndFrac, 0.01f)
                }
                TransitionType.WIPE_LEFT -> {
                    assertEquals(0.5f, mid.wipeClipStartFrac, 0.01f)
                    assertEquals(1f, mid.wipeClipEndFrac, 0.01f)
                }
                TransitionType.DIRECTIONAL_BLUR -> {
                    assertEquals(1f, mid.blurStreakStrength, 0.01f)
                    assertTrue(mid.activeScaleX > 1.2f)
                }
                TransitionType.WHIP_PAN -> {
                    assertEquals(1f, mid.whipStreakStrength, 0.01f)
                    assertTrue(mid.activeScaleX > 1.25f)
                }
                TransitionType.GLITCH -> {
                    assertEquals(1f, mid.glitchStrength, 0.01f)
                }
                TransitionType.FILM_BURN -> {
                    assertEquals(1f, mid.filmBurnStrength, 0.01f)
                }
                TransitionType.LIGHT_LEAK -> {
                    assertEquals(1f, mid.lightLeakStrength, 0.01f)
                }
            }
        }
    }

    @Test
    fun silentAudioGapHelper_plansChunksAndBuildsValidPcmWavHeader() {
        assertTrue(SilentAudioGapHelper.planGapChunkDurationsMs(0L).isEmpty())
        assertTrue(SilentAudioGapHelper.planGapChunkDurationsMs(15L).isEmpty())

        val chunks = SilentAudioGapHelper.planGapChunkDurationsMs(12_350L, maxChunkMs = 5_000L)
        assertEquals(listOf(5_000L, 5_000L, 2_350L), chunks)

        val wav = SilentAudioGapHelper.buildSilentWavBytes(durationMs = 1_000L)
        val expectedDataSize = SilentAudioGapHelper.SAMPLE_RATE * SilentAudioGapHelper.CHANNELS * 2
        assertEquals(44 + expectedDataSize, wav.size)

        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(36 + expectedDataSize, buf.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("fmt ", String(wav, 12, 4))
        assertEquals(16, buf.getInt(16))
        assertEquals(1.toShort(), buf.getShort(20)) // PCM
        assertEquals(2.toShort(), buf.getShort(22)) // stereo
        assertEquals(44100, buf.getInt(24))
        assertEquals("data", String(wav, 36, 4))
        assertEquals(expectedDataSize, buf.getInt(40))
        // Verify payload samples are pure silence (0)
        assertEquals(0.toByte(), wav[44])
        assertEquals(0.toByte(), wav[wav.lastIndex])
    }
}
