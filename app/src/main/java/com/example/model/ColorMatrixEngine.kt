package com.example.model

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.abs
import kotlin.math.pow

/**
 * Shared DaVinci-style color grading math engine supporting:
 * - Cinema filter presets
 * - Primaries: Exposure/Brightness, Contrast, Saturation, Temperature, Tint, Highlights, Shadows
 * - Lift / Gamma / Gain 2D color wheels + master luma offsets
 * - 5-point RGB tone curve interpolation
 * - Optional `.cube` 3D LUT trilinear sampling
 */
object ColorMatrixEngine {

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    fun presetMatrix(preset: FilterPreset): FloatArray = when (preset) {
        FilterPreset.ORIGINAL -> identity()
        FilterPreset.NOIR -> floatArrayOf(
            0.34f, 0.59f, 0.11f, 0f, -12f,
            0.34f, 0.59f, 0.11f, 0f, -12f,
            0.34f, 0.59f, 0.11f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.TEAL_ORANGE -> floatArrayOf(
            1.16f, 0.04f, -0.08f, 0f, 6f,
            0.00f, 1.03f, 0.04f, 0f, 2f,
            -0.10f, 0.10f, 1.12f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.VINTAGE_WARM -> floatArrayOf(
            1.12f, 0.14f, 0.04f, 0f, 8f,
            0.05f, 1.00f, 0.05f, 0f, 4f,
            0.00f, 0.06f, 0.78f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.KODAK_FADE -> floatArrayOf(
            1.02f, 0.06f, 0.02f, 0f, 14f,
            0.02f, 0.98f, 0.02f, 0f, 12f,
            0.02f, 0.04f, 0.88f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.CRISP_VIVID -> floatArrayOf(
            1.22f, -0.08f, -0.06f, 0f, -4f,
            -0.06f, 1.22f, -0.08f, 0f, -4f,
            -0.06f, -0.08f, 1.24f, 0f, -4f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.MIDNIGHT_COOL -> floatArrayOf(
            0.84f, 0.00f, 0.06f, 0f, -8f,
            0.00f, 0.94f, 0.08f, 0f, -2f,
            0.04f, 0.08f, 1.18f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.GOLDEN_HOUR -> floatArrayOf(
            1.20f, 0.08f, 0.00f, 0f, 10f,
            0.04f, 1.06f, 0.00f, 0f, 4f,
            0.00f, 0.00f, 0.82f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.MATTE_FILM -> floatArrayOf(
            0.90f, 0.04f, 0.04f, 0f, 18f,
            0.04f, 0.90f, 0.04f, 0f, 18f,
            0.04f, 0.04f, 0.90f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    /**
     * Converts a 2D Color Wheel offset `(x, y, luma)` into RGB channel deltas:
     * - `+x` pushes Warm/Red-Amber, `-x` pushes Cool/Cyan-Blue
     * - `+y` pushes Magenta/Red-Blue, `-y` pushes Green
     */
    fun wheelToRgbDelta(wheel: ColorWheelOffset, strength: Float): Triple<Float, Float, Float> {
        val x = wheel.x.coerceIn(-1f, 1f)
        val y = wheel.y.coerceIn(-1f, 1f)
        val l = wheel.luma.coerceIn(-0.5f, 0.5f)
        val dR = (x * 0.65f + y * 0.35f) * strength + l
        val dG = (-y * 0.70f - x * 0.12f) * strength + l
        val dB = (-x * 0.65f + y * 0.35f) * strength + l
        return Triple(dR, dG, dB)
    }

    /**
     * Evaluates the 5-point RGB tone curve (`x in {0, 0.25, 0.5, 0.75, 1.0}`) smoothly via
     * piecewise Catmull-Rom / Hermite interpolation.
     */
    fun evaluateToneCurve(xIn: Float, curvePoints: List<Float>): Float {
        val pts = if (curvePoints.size == 5) curvePoints else ColorAdjustments.DEFAULT_CURVE_POINTS
        val x = xIn.coerceIn(0f, 1f)
        val scaled = x * 4f
        val seg = scaled.toInt().coerceIn(0, 3)
        val t = (scaled - seg).coerceIn(0f, 1f)
        val p0 = pts[(seg - 1).coerceAtLeast(0)]
        val p1 = pts[seg]
        val p2 = pts[seg + 1]
        val p3 = pts[(seg + 2).coerceAtMost(4)]
        val t2 = t * t
        val t3 = t2 * t
        val y = 0.5f * (
            (2f * p1) +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
        return y.coerceIn(0f, 1f)
    }

    fun buildCombinedMatrix(
        preset: FilterPreset,
        intensity: Float,
        adjustments: ColorAdjustments
    ): FloatArray {
        val id = identity()
        val targetPreset = presetMatrix(preset)
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        val blendedPreset = FloatArray(20) { i ->
            id[i] + (targetPreset[i] - id[i]) * clampedIntensity
        }

        // Saturation matrix
        val s = adjustments.saturation.coerceIn(0f, 2f)
        val invSat = 1f - s
        val rLum = 0.213f * invSat
        val gLum = 0.715f * invSat
        val bLum = 0.072f * invSat
        val satMatrix = floatArrayOf(
            rLum + s, gLum, bLum, 0f, 0f,
            rLum, gLum + s, bLum, 0f, 0f,
            rLum, gLum, bLum + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // Temperature (Cool Blue .. Warm Amber) + Tint (Green .. Magenta)
        val temp = adjustments.temperature.coerceIn(-1f, 1f)
        val tint = adjustments.tint.coerceIn(-1f, 1f)
        val rGain = (1f + temp * 0.18f + tint * 0.08f).coerceIn(0.65f, 1.35f)
        val gGain = (1f + temp * 0.03f - tint * 0.16f).coerceIn(0.65f, 1.35f)
        val bGain = (1f - temp * 0.20f + tint * 0.09f).coerceIn(0.65f, 1.35f)
        val rOffset = (temp * 10f + tint * 4f)
        val gOffset = -tint * 10f
        val bOffset = (-temp * 12f + tint * 5f)
        val tempTintMatrix = floatArrayOf(
            rGain, 0f, 0f, 0f, rOffset,
            0f, gGain, 0f, 0f, gOffset,
            0f, 0f, bGain, 0f, bOffset,
            0f, 0f, 0f, 1f, 0f
        )

        // Lift / Gamma / Gain + Shadows / Highlights linear approximation for Compose ColorMatrix
        val (liftR, liftG, liftB) = wheelToRgbDelta(adjustments.lift, 0.28f)
        val (gamR, gamG, gamB) = wheelToRgbDelta(adjustments.gamma, 0.24f)
        val (gainR, gainG, gainB) = wheelToRgbDelta(adjustments.gain, 0.36f)
        val shadowLift = adjustments.shadows.coerceIn(-1f, 1f) * 0.16f
        val highGain = adjustments.highlights.coerceIn(-1f, 1f) * 0.20f

        val curvePts = adjustments.normalizedCurvePoints
        val curveBlack = curvePts[0]
        val curveWhite = curvePts[4]
        val curveMidShift = (curvePts[2] - 0.5f) * 0.65f
        val curveSlope = (curveWhite - curveBlack).coerceIn(0.25f, 2.2f)

        val lggScaleR = (1f + gainR + highGain - liftR * 0.35f + gamR * 0.45f).coerceIn(0.35f, 2.2f) * curveSlope
        val lggScaleG = (1f + gainG + highGain - liftG * 0.35f + gamG * 0.45f).coerceIn(0.35f, 2.2f) * curveSlope
        val lggScaleB = (1f + gainB + highGain - liftB * 0.35f + gamB * 0.45f).coerceIn(0.35f, 2.2f) * curveSlope
        val lggOffR = (liftR + shadowLift + gamR * 0.28f + curveBlack + curveMidShift) * 255f
        val lggOffG = (liftG + shadowLift + gamG * 0.28f + curveBlack + curveMidShift) * 255f
        val lggOffB = (liftB + shadowLift + gamB * 0.28f + curveBlack + curveMidShift) * 255f

        val lggMatrix = floatArrayOf(
            lggScaleR, 0f, 0f, 0f, lggOffR,
            0f, lggScaleG, 0f, 0f, lggOffG,
            0f, 0f, lggScaleB, 0f, lggOffB,
            0f, 0f, 0f, 1f, 0f
        )

        // Contrast & Exposure/Brightness matrix
        val c = adjustments.contrast.coerceIn(0.5f, 1.8f)
        val bOffsetBright = adjustments.brightness.coerceIn(-0.5f, 0.5f) * 255f
        val cOffset = 128f * (1f - c) + bOffsetBright
        val cbMatrix = floatArrayOf(
            c, 0f, 0f, 0f, cOffset,
            0f, c, 0f, 0f, cOffset,
            0f, 0f, c, 0f, cOffset,
            0f, 0f, 0f, 1f, 0f
        )

        val step1 = multiply(satMatrix, blendedPreset)
        val step2 = multiply(tempTintMatrix, step1)
        val step3 = multiply(cbMatrix, step2)
        var combined = multiply(lggMatrix, step3)

        // Blend approximate 3D LUT response if a .cube LUT is loaded
        val lut = CubeLutParser.loadCached(adjustments.cubeLutPath)
        if (lut != null && adjustments.cubeLutIntensity > 0.01f) {
            val lutApprox = lut.toApproximateColorMatrix()
            val lutBlend = adjustments.cubeLutIntensity.coerceIn(0f, 1f)
            val blendedLut = FloatArray(20) { i ->
                id[i] + (lutApprox[i] - id[i]) * lutBlend
            }
            combined = multiply(blendedLut, combined)
        }

        return combined
    }

    /**
     * Exact per-pixel DaVinci color pipeline matching [com.example.media.ColorGradeGlEffect],
     * used by live Video Scopes (Waveform / RGB Parade) and unit tests.
     */
    fun applyFullGradeToRgb(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        preset: FilterPreset,
        intensity: Float,
        adjustments: ColorAdjustments,
        lut: CubeLut3D? = CubeLutParser.loadCached(adjustments.cubeLutPath)
    ): Triple<Float, Float, Float> {
        // 1. Preset blend
        val id = identity()
        val pm = presetMatrix(preset)
        val pi = intensity.coerceIn(0f, 1f)
        val m = FloatArray(20) { idx -> id[idx] + (pm[idx] - id[idx]) * pi }
        var r = (m[0] * rIn + m[1] * gIn + m[2] * bIn + m[4] / 255f).coerceIn(0f, 1f)
        var g = (m[5] * rIn + m[6] * gIn + m[7] * bIn + m[9] / 255f).coerceIn(0f, 1f)
        var b = (m[10] * rIn + m[11] * gIn + m[12] * bIn + m[14] / 255f).coerceIn(0f, 1f)

        // 2. Exposure / Brightness & Contrast
        val c = adjustments.contrast.coerceIn(0.5f, 1.8f)
        val br = adjustments.brightness.coerceIn(-0.5f, 0.5f)
        r = ((r - 0.5f) * c + 0.5f + br).coerceIn(0f, 1f)
        g = ((g - 0.5f) * c + 0.5f + br).coerceIn(0f, 1f)
        b = ((b - 0.5f) * c + 0.5f + br).coerceIn(0f, 1f)

        // 3. Temperature & Tint
        val temp = adjustments.temperature.coerceIn(-1f, 1f)
        val tint = adjustments.tint.coerceIn(-1f, 1f)
        r = (r * (1f + temp * 0.18f + tint * 0.08f) + temp * 0.04f).coerceIn(0f, 1f)
        g = (g * (1f + temp * 0.03f - tint * 0.16f) - tint * 0.04f).coerceIn(0f, 1f)
        b = (b * (1f - temp * 0.20f + tint * 0.09f) - temp * 0.045f).coerceIn(0f, 1f)

        // 4. Highlights & Shadows tonal masks
        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val shadowMask = (1f - luma).pow(2f)
        val highlightMask = luma.pow(2f)
        val tonalDelta = adjustments.shadows.coerceIn(-1f, 1f) * 0.28f * shadowMask +
            adjustments.highlights.coerceIn(-1f, 1f) * 0.28f * highlightMask
        r = (r + tonalDelta).coerceIn(0f, 1f)
        g = (g + tonalDelta).coerceIn(0f, 1f)
        b = (b + tonalDelta).coerceIn(0f, 1f)

        // 5. DaVinci Lift (shadows), Gamma (midtones power), Gain (highlights)
        val (liftR, liftG, liftB) = wheelToRgbDelta(adjustments.lift, 0.30f)
        val (gamR, gamG, gamB) = wheelToRgbDelta(adjustments.gamma, 0.50f)
        val (gainR, gainG, gainB) = wheelToRgbDelta(adjustments.gain, 0.45f)

        r = (r * (1f + gainR) + liftR * (1f - r)).coerceIn(0f, 1f)
        g = (g * (1f + gainG) + liftG * (1f - g)).coerceIn(0f, 1f)
        b = (b * (1f + gainB) + liftB * (1f - b)).coerceIn(0f, 1f)

        val expR = (1f / (1f + gamR).coerceIn(0.45f, 1.85f))
        val expG = (1f / (1f + gamG).coerceIn(0.45f, 1.85f))
        val expB = (1f / (1f + gamB).coerceIn(0.45f, 1.85f))
        r = r.coerceAtLeast(1e-4f).pow(expR).coerceIn(0f, 1f)
        g = g.coerceAtLeast(1e-4f).pow(expG).coerceIn(0f, 1f)
        b = b.coerceAtLeast(1e-4f).pow(expB).coerceIn(0f, 1f)

        // 6. Saturation
        val sat = adjustments.saturation.coerceIn(0f, 2f)
        if (abs(sat - 1f) > 0.005f) {
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = (l + (r - l) * sat).coerceIn(0f, 1f)
            g = (l + (g - l) * sat).coerceIn(0f, 1f)
            b = (l + (b - l) * sat).coerceIn(0f, 1f)
        }

        // 7. RGB Tone Curve
        if (adjustments.hasNonLinearCurve) {
            val pts = adjustments.normalizedCurvePoints
            r = evaluateToneCurve(r, pts)
            g = evaluateToneCurve(g, pts)
            b = evaluateToneCurve(b, pts)
        }

        // 8. 3D .cube LUT
        if (lut != null && adjustments.cubeLutIntensity > 0.005f) {
            val (lr, lg, lb) = lut.sample(r, g, b)
            val amt = adjustments.cubeLutIntensity.coerceIn(0f, 1f)
            r = (r + (lr - r) * amt).coerceIn(0f, 1f)
            g = (g + (lg - g) * amt).coerceIn(0f, 1f)
            b = (b + (lb - b) * amt).coerceIn(0f, 1f)
        }

        return Triple(r, g, b)
    }

    fun toComposeColorFilter(
        preset: FilterPreset,
        intensity: Float,
        adjustments: ColorAdjustments
    ): ColorFilter? {
        if (preset == FilterPreset.ORIGINAL && adjustments.isDefault) return null
        return ColorFilter.colorMatrix(ColorMatrix(buildCombinedMatrix(preset, intensity, adjustments)))
    }

    /**
     * Converts a 4x5 Android ColorMatrix (with 0..255 offsets in column 4) into a
     * column-major OpenGL 4x4 matrix expected by Media3 RgbMatrix (normalized 0..1 space).
     */
    fun toGlColumnMajor4x4(matrix4x5: FloatArray): FloatArray {
        val tR = matrix4x5[4] / 255f
        val tG = matrix4x5[9] / 255f
        val tB = matrix4x5[14] / 255f
        return floatArrayOf(
            matrix4x5[0], matrix4x5[5], matrix4x5[10], 0f,
            matrix4x5[1], matrix4x5[6], matrix4x5[11], 0f,
            matrix4x5[2], matrix4x5[7], matrix4x5[12], 0f,
            tR, tG, tB, 1f
        )
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            val r = row * 5
            for (col in 0 until 4) {
                out[r + col] =
                    a[r + 0] * b[0 + col] +
                    a[r + 1] * b[5 + col] +
                    a[r + 2] * b[10 + col] +
                    a[r + 3] * b[15 + col]
            }
            out[r + 4] =
                a[r + 0] * b[4] +
                a[r + 1] * b[9] +
                a[r + 2] * b[14] +
                a[r + 3] * b[19] +
                a[r + 4]
        }
        return out
    }
}
