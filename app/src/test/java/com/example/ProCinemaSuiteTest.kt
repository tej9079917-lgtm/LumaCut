package com.example

import com.example.media.BiquadFilter
import com.example.media.ChromaKeyMath
import com.example.model.ChromaKeySettings
import com.example.model.ClipTransform
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.ColorWheelOffset
import com.example.model.CubeLutParser
import com.example.model.FilterPreset
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.TransformKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class ProCinemaSuiteTest {

    @Test
    fun cubeLutParser_parsesStandardCubeAndInterpolatesTrilinearly() {
        // Warm-tinted 2x2x2 .cube LUT where R is boosted and B is slightly compressed
        val cubeText = """
            # Test Cinema LUT
            TITLE "Cinema Amber Test"
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 1.0 1.0 1.0
            LUT_3D_SIZE 2
            0.05 0.00 0.00
            1.00 0.05 0.00
            0.05 0.95 0.00
            1.00 1.00 0.05
            0.05 0.00 0.85
            1.00 0.05 0.85
            0.05 0.95 0.85
            1.00 1.00 0.90
        """.trimIndent()

        val lut = CubeLutParser.parse(cubeText)
        assertEquals("Cinema Amber Test", lut.title)
        assertEquals(2, lut.size)

        // Mid-gray (0.5, 0.5, 0.5) should interpolate cleanly between all 8 corners
        val (r, g, b) = lut.sample(0.5f, 0.5f, 0.5f)
        assertTrue("Expected red boost > 0.50, got $r", r > 0.50f)
        assertTrue("Expected blue compression < 0.48, got $b", b < 0.48f)
        assertEquals(0.5f, g, 0.06f)

        // Packed 2D RGBA texture dimensions: width = size * size = 4, height = size = 2 => 32 bytes
        val packed = lut.toPackedRgbaBytes()
        assertEquals(2 * 2 * 2 * 4, packed.size)
    }

    @Test
    fun colorMatrixEngine_appliesWheelsTintAndToneCurve() {
        val neutral = ColorAdjustments()
        val (nr, ng, nb) = ColorMatrixEngine.applyFullGradeToRgb(0.5f, 0.5f, 0.5f, FilterPreset.ORIGINAL, 1f, neutral)
        assertEquals(0.5f, nr, 0.02f)
        assertEquals(0.5f, ng, 0.02f)
        assertEquals(0.5f, nb, 0.02f)

        // Warm Gain wheel + magenta tint + lifted midtone curve
        val graded = ColorAdjustments(
            tint = 0.6f,
            gain = ColorWheelOffset(x = 0.8f, y = 0.2f, luma = 0.2f),
            curvePoints = listOf(0f, 0.25f, 0.68f, 0.82f, 1f)
        )
        val (gr, gg, gb) = ColorMatrixEngine.applyFullGradeToRgb(0.5f, 0.5f, 0.5f, FilterPreset.ORIGINAL, 1f, graded)
        assertTrue("Expected midtone lift > 0.58 on R, got $gr", gr > 0.58f)
        assertTrue("Expected warm/magenta shift R > G, got R=$gr G=$gg", gr > gg)
    }

    @Test
    fun chromaKeyMath_supportsCustomEyedropperSampledColor() {
        // Sampled bright red backdrop (#E61919)
        val settings = ChromaKeySettings(
            enabled = true,
            customColorArgb = 0xFFE61919L,
            similarity = 0.35f,
            smoothness = 0.12f,
            spillSuppression = 0.7f
        )

        // Exact sampled red pixel should key out to transparent
        val exactAlpha = ChromaKeyMath.computeChromaAlpha(0.90f, 0.10f, 0.10f, settings)
        assertTrue("Expected exact custom key color alpha < 0.05, got $exactAlpha", exactAlpha < 0.05f)

        // Neutral cyan foreground pixel should remain opaque
        val fgAlpha = ChromaKeyMath.computeChromaAlpha(0.15f, 0.82f, 0.88f, settings)
        assertTrue("Expected foreground alpha > 0.95, got $fgAlpha", fgAlpha > 0.95f)

        // Red-dominant spill suppression should resolve to mode 4 and clamp excess red
        assertEquals(4, ChromaKeyMath.resolveSpillMode(settings))
        val (sr, sg, sb) = ChromaKeyMath.suppressSpill(0.9f, 0.4f, 0.4f, settings)
        assertTrue("Expected red spill reduced below 0.6, got $sr", sr < 0.6f)
        assertEquals(0.4f, sg, 0.01f)
        assertEquals(0.4f, sb, 0.01f)
    }

    @Test
    fun biquadFilter_threeBandEqBoostsAndCutsCleanly() {
        val sampleRate = 48_000.0
        val lowBoost = BiquadFilter.lowShelf(sampleRate, freqHz = 120.0, gainDb = 9.0)
        val highCut = BiquadFilter.highShelf(sampleRate, freqHz = 6000.0, gainDb = -9.0)

        // Feed 80 Hz sine into low-shelf boost and measure RMS after settling
        var inSq = 0.0
        var outSq = 0.0
        for (i in 0 until 2000) {
            val x = sin(2.0 * PI * 80.0 * i / sampleRate)
            val y = lowBoost.processSample(x)
            if (i >= 500) {
                inSq += x * x
                outSq += y * y
            }
        }
        val lowGainRatio = sqrt(outSq / inSq)
        assertTrue("Expected +9dB low-shelf boost ratio > 1.8, got $lowGainRatio", lowGainRatio > 1.8)

        // Feed 10 kHz sine into high-shelf cut and measure attenuation
        var hiInSq = 0.0
        var hiOutSq = 0.0
        for (i in 0 until 2000) {
            val x = sin(2.0 * PI * 10_000.0 * i / sampleRate)
            val y = highCut.processSample(x)
            if (i >= 500) {
                hiInSq += x * x
                hiOutSq += y * y
            }
        }
        val hiGainRatio = sqrt(hiOutSq / hiInSq)
        assertTrue("Expected -9dB high-shelf attenuation ratio < 0.55, got $hiGainRatio", hiGainRatio < 0.55)
    }

    @Test
    fun speedQuarterXAndKeyframeOpacityInterpolateCorrectly() {
        val clip = MainClip(
            uri = "file:///sample.mp4",
            displayName = "SlowMoClip",
            mediaType = MainMediaType.VIDEO,
            sourceDurationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            playbackSpeed = 0.25f,
            baseTransform = ClipTransform(opacity = 1f),
            keyframes = listOf(
                TransformKeyframe(timeOffsetMs = 0L, scale = 1.0f, opacity = 0.0f),
                TransformKeyframe(timeOffsetMs = 2_000L, scale = 1.5f, opacity = 1.0f)
            )
        )

        // 4s source at 0.25x speed => 16s timeline duration
        assertEquals(0.25f, clip.safeSpeed, 0.001f)
        assertEquals(16_000L, clip.durationMs)

        // Midpoint at 1000ms should interpolate opacity to ~0.5 and scale to ~1.25
        val mid = KeyframeEvaluator.evaluate(clip, 1_000L)
        assertEquals(0.5f, mid.opacity, 0.02f)
        assertEquals(1.25f, mid.scale, 0.02f)
    }
}
