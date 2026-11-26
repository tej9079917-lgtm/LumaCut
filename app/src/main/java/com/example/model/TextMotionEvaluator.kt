package com.example.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Computed per-frame visual state for a text overlay motion preset.
 * Shared across Compose live preview, Compose preset cards, and Media3 export.
 */
data class TextMotionFrame(
    val alpha: Float = 1f,
    val offsetXFraction: Float = 0f,   // Fraction of stage width (-1..1)
    val offsetYFraction: Float = 0f,   // Fraction of stage height (-1..1)
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val letterSpacingSp: Float = 0f,
    val visibleCharCount: Int = Int.MAX_VALUE,
    val showCursor: Boolean = false
)

object TextMotionEvaluator {

    /**
     * Evaluates a [TextMotionPreset] at normalized animation progress [rawProgress] in [0f..1f].
     */
    fun evaluate(
        preset: TextMotionPreset,
        fullText: String,
        rawProgress: Float
    ): TextMotionFrame {
        val t = rawProgress.coerceIn(0f, 1f)
        val totalChars = fullText.length.coerceAtLeast(1)

        return when (preset) {
            TextMotionPreset.NONE -> TextMotionFrame(visibleCharCount = totalChars)

            TextMotionPreset.TYPEWRITER -> {
                val count = (t * totalChars).roundToInt().coerceIn(if (t > 0.02f) 1 else 0, totalChars)
                // Blinking cursor while typing and briefly right after completion
                val blinkPhase = ((t * 8f).toInt() % 2 == 0)
                TextMotionFrame(
                    alpha = 1f,
                    visibleCharCount = count,
                    showCursor = t < 0.99f && blinkPhase
                )
            }

            TextMotionPreset.FADE_IN -> {
                val eased = easeOutCubic(t)
                TextMotionFrame(
                    alpha = eased,
                    scale = 0.94f + 0.06f * eased,
                    visibleCharCount = totalChars
                )
            }

            TextMotionPreset.SLIDE_UP -> {
                val eased = easeOutCubic(t)
                TextMotionFrame(
                    alpha = (t * 2.2f).coerceIn(0f, 1f),
                    offsetYFraction = (1f - eased) * 0.14f,
                    visibleCharCount = totalChars
                )
            }

            TextMotionPreset.SLIDE_LEFT -> {
                val eased = easeOutCubic(t)
                TextMotionFrame(
                    alpha = (t * 2.2f).coerceIn(0f, 1f),
                    offsetXFraction = (1f - eased) * 0.24f,
                    visibleCharCount = totalChars
                )
            }

            TextMotionPreset.POP_SPRING -> {
                // Damped spring overshoot curve matching Compose Spring.DampingRatioMediumBouncy
                val springValue = if (t >= 0.999f) {
                    1f
                } else {
                    1f - exp(-6.2 * t).toFloat() * cos(11.5 * t).toFloat()
                }
                val s = (0.28f + 0.72f * springValue).coerceAtLeast(0.15f)
                TextMotionFrame(
                    alpha = (t * 4.5f).coerceIn(0f, 1f),
                    scale = s,
                    visibleCharCount = totalChars
                )
            }

            TextMotionPreset.TRACKING_EXPAND -> {
                val eased = easeOutCubic(t)
                TextMotionFrame(
                    alpha = (t * 1.6f).coerceIn(0f, 1f),
                    letterSpacingSp = (1f - eased) * 14f,
                    scale = 1.08f - 0.08f * eased,
                    visibleCharCount = totalChars
                )
            }

            TextMotionPreset.KINETIC_PULSE -> {
                val eased = easeOutCubic(t)
                val swing = sin(t * PI * 2.0).toFloat() * (1f - t) * 7f
                val pulseScale = 0.75f + 0.25f * eased + sin(t * PI).toFloat() * 0.10f * (1f - t)
                TextMotionFrame(
                    alpha = (t * 3.0f).coerceIn(0f, 1f),
                    scale = pulseScale,
                    rotationDegrees = swing,
                    visibleCharCount = totalChars
                )
            }
        }
    }

    /**
     * Evaluates a text overlay's animation state at a global timeline timestamp [globalTimeMs].
     */
    fun evaluateAtTimelineMs(
        overlay: OverlayLayer,
        globalTimeMs: Long
    ): TextMotionFrame {
        if (overlay.textMotionPreset == TextMotionPreset.NONE) {
            return TextMotionFrame(visibleCharCount = overlay.text.length)
        }
        val duration = overlay.textMotionDurationMs
            .coerceIn(250L, overlay.durationMs.coerceAtLeast(300L))
        val localMs = (globalTimeMs - overlay.startTimeMs).coerceAtLeast(0L)
        val progress = (localMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        return evaluate(
            preset = overlay.textMotionPreset,
            fullText = overlay.text.ifBlank { "Title" },
            rawProgress = progress
        )
    }

    fun sliceVisibleText(fullText: String, frame: TextMotionFrame): String {
        val base = fullText.ifBlank { "Title" }
        val count = frame.visibleCharCount.coerceIn(0, base.length)
        val sliced = base.substring(0, count)
        return if (frame.showCursor) "$sliced|" else sliced
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }
}
