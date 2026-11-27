package com.example

import com.example.media.AutoDuckEvaluator
import com.example.media.CinemaSfxPreset
import com.example.media.CinemaSfxSynthesizer
import com.example.media.SpeedRampPreset
import com.example.media.SrtCodec
import com.example.model.CaptionClip
import com.example.model.ClipTransform
import com.example.model.LowerThirdTemplate
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.OverlayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProCreativeStudioV2Test {

    @Test
    fun srtCodec_roundTripsStandardAndMalformedSeparators() {
        val raw = """
            1
            00:00:01,250 --> 00:00:03,750
            Welcome to LumaCut v2.0!

            2
            00:00:04.50 --> 00:00:07.125
            <i>Karaoke captions</i> & <b>SRT</b>
            Second line here
        """.trimIndent()

        val parsed = SrtCodec.parseSrt(raw)
        assertEquals(2, parsed.size)
        assertEquals(1250L, parsed[0].startTimeMs)
        assertEquals(3750L, parsed[0].endTimeMs)
        assertEquals("Welcome to LumaCut v2.0!", parsed[0].text)

        assertEquals(4500L, parsed[1].startTimeMs)
        assertEquals(7125L, parsed[1].endTimeMs)
        assertEquals("Karaoke captions & SRT\nSecond line here", parsed[1].text)

        val formatted = SrtCodec.formatSrt(parsed)
        val reparsed = SrtCodec.parseSrt(formatted)
        assertEquals(2, reparsed.size)
        assertEquals(parsed[0].startTimeMs, reparsed[0].startTimeMs)
        assertEquals(parsed[1].endTimeMs, reparsed[1].endTimeMs)
        assertEquals(parsed[1].text, reparsed[1].text)
    }

    @Test
    fun cinemaSfxSynthesizer_producesValidWavWithNonSilentPcmForAllPresets() {
        for (preset in CinemaSfxPreset.entries) {
            val wav = CinemaSfxSynthesizer.generateWavBytes(preset)
            assertTrue("WAV too short for ${preset.name}", wav.size > 44)
            assertEquals('R'.code.toByte(), wav[0])
            assertEquals('I'.code.toByte(), wav[1])
            assertEquals('F'.code.toByte(), wav[2])
            assertEquals('F'.code.toByte(), wav[3])
            assertEquals('W'.code.toByte(), wav[8])
            assertEquals('A'.code.toByte(), wav[9])
            assertEquals('V'.code.toByte(), wav[10])
            assertEquals('E'.code.toByte(), wav[11])

            var nonZeroBytes = 0
            for (i in 44 until wav.size step 16) {
                if (wav[i] != 0.toByte()) nonZeroBytes++
            }
            assertTrue("Expected non-silent PCM payload for ${preset.name}", nonZeroBytes > 10)
        }
    }

    @Test
    fun speedRampPresets_preserveFullSourceTrimSpanForAllSixCurves() {
        val source = MainClip(
            uri = "file:///sample.mp4",
            displayName = "HeroClip",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 10_000L,
            trimStartMs = 1_000L,
            trimEndMs = 9_000L
        )
        for (preset in SpeedRampPreset.entries) {
            val segments = preset.splitClip(source)
            assertTrue("Expected multi-segment ramp for ${preset.name}", segments.size >= 2)
            assertEquals(source.trimStartMs, segments.first().trimStartMs)
            assertEquals(source.trimEndMs, segments.last().trimEndMs)
            for (i in 0 until segments.lastIndex) {
                assertEquals(segments[i].trimEndMs, segments[i + 1].trimStartMs)
                assertTrue(segments[i].playbackSpeed in 0.25f..4.0f)
            }
        }
    }

    @Test
    fun autoDuckEvaluator_ducksDuringVoiceWindowAndRecoversOutside() {
        val voiceWindows = listOf(2000L..5000L)
        val before = AutoDuckEvaluator.evaluateGainMultiplier(500L, voiceWindows, duckFloorGain = 0.25f)
        val inside = AutoDuckEvaluator.evaluateGainMultiplier(3500L, voiceWindows, duckFloorGain = 0.25f)
        val attackRamp = AutoDuckEvaluator.evaluateGainMultiplier(1910L, voiceWindows, duckFloorGain = 0.25f)
        val after = AutoDuckEvaluator.evaluateGainMultiplier(6500L, voiceWindows, duckFloorGain = 0.25f)

        assertEquals(1.0f, before, 0.001f)
        assertEquals(0.25f, inside, 0.001f)
        assertTrue("Expected smooth attack attenuation, got $attackRamp", attackRamp in 0.26f..0.99f)
        assertEquals(1.0f, after, 0.001f)
    }

    @Test
    fun lowerThirdTemplatesAndClipFlipTransform_workProperly() {
        assertEquals(6, LowerThirdTemplate.entries.size)
        for (template in LowerThirdTemplate.entries) {
            val layer = template.toOverlayLayer(startTimeMs = 1200L, zIndex = 3)
            assertEquals(OverlayType.TEXT, layer.type)
            assertEquals(1200L, layer.startTimeMs)
            assertEquals(3, layer.zIndex)
            assertTrue(layer.text.isNotBlank())
        }

        val identity = ClipTransform()
        assertTrue(identity.isIdentity)
        assertFalse(identity.copy(flipHorizontal = true).isIdentity)
        assertFalse(identity.copy(flipVertical = true).isIdentity)
    }
}
