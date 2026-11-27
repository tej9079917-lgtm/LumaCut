package com.example.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Pure math helpers for PreviewStage overlay manipulation, dual-mode timeline scrubbing,
 * and full 14-type transition evaluation (shared between live preview and JVM tests).
 */
object OverlayGestureMath {
    const val FREE_PLACEMENT_MAX_OFFSET = 0.95f
    const val CENTER_SNAP_THRESHOLD = 0.018f

    /**
     * Converts a pan delta measured in a rotated & scaled overlay's local pointer space
     * back into unrotated, unscaled PreviewStage pixel deltas so dragging tracks the finger 1:1.
     */
    fun localPanToStageDelta(
        localDx: Float,
        localDy: Float,
        scale: Float,
        rotationDegrees: Float
    ): Pair<Float, Float> {
        val safeScale = scale.coerceAtLeast(0.05f)
        val rad = rotationDegrees * (PI / 180.0)
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val sx = localDx * safeScale
        val sy = localDy * safeScale
        val stageDx = sx * cosR - sy * sinR
        val stageDy = sx * sinR + sy * cosR
        return stageDx to stageDy
    }

    /**
     * Clamps normalized overlay offsets to allow free placement including partial off-screen framing.
     */
    fun clampFreePlacement(offset: Float): Float =
        offset.coerceIn(-FREE_PLACEMENT_MAX_OFFSET, FREE_PLACEMENT_MAX_OFFSET)
}

object TimelineScrubMath {
    fun scrollPxToMs(scrollPx: Int, pxPerMs: Float, maxDurationMs: Long): Long {
        if (pxPerMs <= 0f || maxDurationMs <= 0L) return 0L
        return (scrollPx.toFloat() / pxPerMs).roundToLong().coerceIn(0L, maxDurationMs)
    }

    fun msToScrollPx(timeMs: Long, pxPerMs: Float): Int {
        if (pxPerMs <= 0f) return 0
        return (timeMs.coerceAtLeast(0L).toFloat() * pxPerMs).roundToInt().coerceAtLeast(0)
    }

    fun applyDragDeltaMs(currentMsFloat: Float, dragPx: Float, pxPerMs: Float, maxDurationMs: Long): Float {
        if (pxPerMs <= 0f || maxDurationMs <= 0L) return 0f
        return (currentMsFloat + dragPx / pxPerMs).coerceIn(0f, maxDurationMs.toFloat())
    }
}

/**
 * Computed visual parameters for the live main clip and partner transition layer
 * across a transition seam (`progress` in `0f..1f`, cut occurs at `0.5f`).
 */
data class TransitionVisualState(
    val activeAlpha: Float = 1f,
    val partnerAlpha: Float = 0f,
    val activeTranslationXFrac: Float = 0f,
    val partnerTranslationXFrac: Float = 0f,
    val activeScaleX: Float = 1f,
    val activeScaleY: Float = 1f,
    val partnerScale: Float = 1f,
    val wipeClipStartFrac: Float = 0f,
    val wipeClipEndFrac: Float = 1f,
    val dipBlackAlpha: Float = 0f,
    val dipWhiteAlpha: Float = 0f,
    val blurStreakStrength: Float = 0f,
    val whipStreakStrength: Float = 0f,
    val glitchStrength: Float = 0f,
    val glitchPhase: Float = 0f,
    val filmBurnStrength: Float = 0f,
    val lightLeakStrength: Float = 0f,
    val showPartnerBehind: Boolean = false
)

object TransitionVisualEvaluator {

    private fun smoothStep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }

    private fun seamBell(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return (1f - abs(p - 0.5f) * 2f).coerceIn(0f, 1f)
    }

    fun evaluate(type: TransitionType, rawProgress: Float): TransitionVisualState {
        val p = rawProgress.coerceIn(0f, 1f)
        val inIncomingHalf = p >= 0.5f
        val bell = seamBell(p)

        return when (type) {
            TransitionType.NONE -> TransitionVisualState()

            TransitionType.CROSSFADE -> {
                val blend = smoothStep(p)
                if (!inIncomingHalf) {
                    // Active = outgoing clip fading down, partner = incoming preview fading in underneath/over
                    TransitionVisualState(
                        activeAlpha = (1f - blend).coerceIn(0f, 1f),
                        partnerAlpha = blend,
                        showPartnerBehind = true
                    )
                } else {
                    // Active = incoming clip fading up, partner = outgoing clip fading out behind
                    TransitionVisualState(
                        activeAlpha = blend,
                        partnerAlpha = (1f - blend).coerceIn(0f, 1f),
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.DIP_TO_BLACK -> TransitionVisualState(
                dipBlackAlpha = smoothStep(bell)
            )

            TransitionType.DIP_TO_WHITE -> TransitionVisualState(
                dipWhiteAlpha = smoothStep(bell)
            )

            TransitionType.SLIDE_LEFT -> {
                val e = easeOutCubic(p)
                if (!inIncomingHalf) {
                    TransitionVisualState(
                        activeTranslationXFrac = -e,
                        partnerTranslationXFrac = 1f - e,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                } else {
                    TransitionVisualState(
                        activeTranslationXFrac = 1f - e,
                        partnerTranslationXFrac = -e,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.SLIDE_RIGHT -> {
                val e = easeOutCubic(p)
                if (!inIncomingHalf) {
                    TransitionVisualState(
                        activeTranslationXFrac = e,
                        partnerTranslationXFrac = -(1f - e),
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                } else {
                    TransitionVisualState(
                        activeTranslationXFrac = -(1f - e),
                        partnerTranslationXFrac = e,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.ZOOM_IN -> {
                if (!inIncomingHalf) {
                    val t = smoothStep(p * 2f)
                    TransitionVisualState(
                        activeScaleX = 1f + 0.38f * t,
                        activeScaleY = 1f + 0.38f * t,
                        activeAlpha = (1f - t * 0.45f).coerceIn(0f, 1f),
                        partnerAlpha = t * 0.45f,
                        partnerScale = 0.85f + 0.15f * t,
                        showPartnerBehind = true
                    )
                } else {
                    val t = easeOutCubic((p - 0.5f) * 2f)
                    TransitionVisualState(
                        activeScaleX = 1.32f - 0.32f * t,
                        activeScaleY = 1.32f - 0.32f * t,
                        activeAlpha = (0.55f + 0.45f * t).coerceIn(0f, 1f),
                        partnerAlpha = (1f - t) * 0.45f,
                        partnerScale = 1.38f,
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.WIPE_RIGHT -> {
                val e = smoothStep(p)
                if (!inIncomingHalf) {
                    // Outgoing clip gets cropped from the left edge as incoming reveals from left -> right
                    TransitionVisualState(
                        wipeClipStartFrac = e,
                        wipeClipEndFrac = 1f,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                } else {
                    // Incoming clip reveals from 0..e over outgoing behind
                    TransitionVisualState(
                        wipeClipStartFrac = 0f,
                        wipeClipEndFrac = e,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.WIPE_LEFT -> {
                val e = smoothStep(p)
                if (!inIncomingHalf) {
                    // Outgoing clip gets cropped from right edge as incoming reveals right -> left
                    TransitionVisualState(
                        wipeClipStartFrac = 0f,
                        wipeClipEndFrac = 1f - e,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                } else {
                    // Incoming clip reveals from (1-e)..1 over outgoing behind
                    TransitionVisualState(
                        wipeClipStartFrac = 1f - e,
                        wipeClipEndFrac = 1f,
                        partnerAlpha = 1f,
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.DIRECTIONAL_BLUR -> {
                val b = smoothStep(bell)
                val blend = smoothStep(p)
                TransitionVisualState(
                    activeAlpha = if (!inIncomingHalf) (1f - blend * 0.55f) else (0.45f + blend * 0.55f),
                    partnerAlpha = if (!inIncomingHalf) blend * 0.55f else (1f - blend) * 0.55f,
                    activeScaleX = 1f + 0.28f * b,
                    activeScaleY = 1f - 0.04f * b,
                    activeTranslationXFrac = (if (!inIncomingHalf) -0.08f else 0.08f) * b,
                    blurStreakStrength = b,
                    showPartnerBehind = true
                )
            }

            TransitionType.WHIP_PAN -> {
                val b = smoothStep(bell)
                if (!inIncomingHalf) {
                    val t = p * 2f
                    val accel = t * t * t
                    TransitionVisualState(
                        activeTranslationXFrac = -accel * 0.95f,
                        activeScaleX = 1f + 0.35f * b,
                        partnerTranslationXFrac = 1f - accel * 0.95f,
                        partnerAlpha = 1f,
                        whipStreakStrength = b,
                        showPartnerBehind = true
                    )
                } else {
                    val t = (p - 0.5f) * 2f
                    val decel = 1f - (1f - t) * (1f - t) * (1f - t)
                    TransitionVisualState(
                        activeTranslationXFrac = (1f - decel) * 0.95f,
                        activeScaleX = 1f + 0.35f * b,
                        partnerTranslationXFrac = -decel * 0.95f,
                        partnerAlpha = (1f - decel).coerceIn(0f, 1f),
                        whipStreakStrength = b,
                        showPartnerBehind = true
                    )
                }
            }

            TransitionType.GLITCH -> {
                val b = smoothStep(bell)
                val step = ((p * 14f).toInt() % 5) - 2 // -2..+2 deterministic jitter
                val jitterX = step * 0.032f * b
                TransitionVisualState(
                    activeTranslationXFrac = jitterX,
                    activeScaleX = 1f + 0.06f * b,
                    activeScaleY = 1f + 0.02f * b,
                    glitchStrength = b,
                    glitchPhase = p,
                    partnerAlpha = if (b > 0.35f) b * 0.35f else 0f,
                    showPartnerBehind = true
                )
            }

            TransitionType.FILM_BURN -> {
                val b = smoothStep(bell)
                val blend = smoothStep(p)
                TransitionVisualState(
                    activeAlpha = if (!inIncomingHalf) (1f - blend * 0.35f) else (0.65f + blend * 0.35f),
                    partnerAlpha = if (!inIncomingHalf) blend * 0.35f else (1f - blend) * 0.35f,
                    activeScaleX = 1f + 0.05f * b,
                    activeScaleY = 1f + 0.05f * b,
                    filmBurnStrength = b,
                    showPartnerBehind = true
                )
            }

            TransitionType.LIGHT_LEAK -> {
                val b = smoothStep(bell)
                val blend = smoothStep(p)
                TransitionVisualState(
                    activeAlpha = if (!inIncomingHalf) (1f - blend * 0.4f) else (0.6f + blend * 0.4f),
                    partnerAlpha = if (!inIncomingHalf) blend * 0.4f else (1f - blend) * 0.4f,
                    lightLeakStrength = b,
                    glitchPhase = p,
                    showPartnerBehind = true
                )
            }
        }
    }
}
