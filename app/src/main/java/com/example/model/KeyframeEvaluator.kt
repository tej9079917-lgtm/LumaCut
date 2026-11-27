package com.example.model

import kotlin.math.abs

/**
 * Pure keyframe interpolation engine shared by live Compose preview and
 * Media3 Transformer export so animated position, scale, and rotation
 * match frame-for-frame.
 */
object KeyframeEvaluator {

    fun evaluate(clip: MainClip, localTimelineMs: Long): ClipTransform {
        val base = clip.baseTransform
        val clampedMs = localTimelineMs.coerceIn(0L, clip.durationMs.coerceAtLeast(1L))

        val animated: ClipTransform = if (clip.keyframes.isNotEmpty()) {
            val sorted = clip.keyframes.sortedBy { it.timeOffsetMs }
            val raw = when {
                sorted.size == 1 -> sorted.first().toTransform()
                clampedMs <= sorted.first().timeOffsetMs -> sorted.first().toTransform()
                clampedMs >= sorted.last().timeOffsetMs -> sorted.last().toTransform()
                else -> {
                    var found = sorted.last().toTransform()
                    for (i in 0 until sorted.lastIndex) {
                        val k0 = sorted[i]
                        val k1 = sorted[i + 1]
                        if (clampedMs in k0.timeOffsetMs..k1.timeOffsetMs) {
                            val span = (k1.timeOffsetMs - k0.timeOffsetMs).coerceAtLeast(1L)
                            val rawT = ((clampedMs - k0.timeOffsetMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                            val t = applyEasing(rawT, k1.easing)
                            found = ClipTransform(
                                offsetX = lerpFloat(k0.offsetX, k1.offsetX, t),
                                offsetY = lerpFloat(k0.offsetY, k1.offsetY, t),
                                scale = lerpFloat(k0.scale, k1.scale, t),
                                rotationDegrees = lerpFloat(k0.rotationDegrees, k1.rotationDegrees, t),
                                opacity = lerpFloat(k0.opacity, k1.opacity, t).coerceIn(0f, 1f)
                            )
                            break
                        }
                    }
                    found
                }
            }
            raw.copy(
                flipHorizontal = base.flipHorizontal,
                flipVertical = base.flipVertical,
                fullFrameFill = base.fullFrameFill,
                crop = base.crop
            )
        } else {
            base
        }

        // Apply KineMaster-style Pan & Zoom smoothly across clip duration when enabled
        val pz = clip.panZoom
        if (!pz.enabled) return animated

        val progress = (clampedMs.toFloat() / clip.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val easedT = applyEasing(progress, KeyframeEasing.EASE_IN_OUT)
        val endScale = if (pz.linked) pz.startScale else pz.endScale
        val endX = if (pz.linked) pz.startOffsetX else pz.endOffsetX
        val endY = if (pz.linked) pz.startOffsetY else pz.endOffsetY

        val pzScale = lerpFloat(pz.startScale, endScale, easedT).coerceIn(0.35f, 4.0f)
        val pzOffsetX = lerpFloat(pz.startOffsetX, endX, easedT)
        val pzOffsetY = lerpFloat(pz.startOffsetY, endY, easedT)

        return animated.copy(
            scale = (animated.scale * pzScale).coerceIn(0.25f, 5.0f),
            offsetX = (animated.offsetX + pzOffsetX).coerceIn(-1.5f, 1.5f),
            offsetY = (animated.offsetY + pzOffsetY).coerceIn(-1.5f, 1.5f)
        )
    }

    fun applyEasing(t: Float, easing: KeyframeEasing): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            KeyframeEasing.LINEAR -> x
            KeyframeEasing.EASE_IN -> x * x * x
            KeyframeEasing.EASE_OUT -> {
                val inv = 1f - x
                1f - inv * inv * inv
            }
            KeyframeEasing.EASE_IN_OUT -> {
                if (x < 0.5f) {
                    4f * x * x * x
                } else {
                    val f = -2f * x + 2f
                    1f - (f * f * f) / 2f
                }
            }
        }
    }

    fun findKeyframeNear(
        clip: MainClip,
        localTimelineMs: Long,
        toleranceMs: Long = 90L
    ): TransformKeyframe? = clip.keyframes.minByOrNull { abs(it.timeOffsetMs - localTimelineMs) }
        ?.takeIf { abs(it.timeOffsetMs - localTimelineMs) <= toleranceMs }

    fun previousKeyframeTimeMs(clip: MainClip, localTimelineMs: Long, toleranceMs: Long = 40L): Long? =
        clip.keyframes
            .map { it.timeOffsetMs }
            .sorted()
            .lastOrNull { it < localTimelineMs - toleranceMs }

    fun nextKeyframeTimeMs(clip: MainClip, localTimelineMs: Long, toleranceMs: Long = 40L): Long? =
        clip.keyframes
            .map { it.timeOffsetMs }
            .sorted()
            .firstOrNull { it > localTimelineMs + toleranceMs }

    private fun lerpFloat(start: Float, end: Float, t: Float): Float =
        start + (end - start) * t
}
