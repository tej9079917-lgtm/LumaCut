package com.example.model

import kotlin.math.abs

/**
 * Pure keyframe interpolation engine shared by live Compose preview and
 * Media3 Transformer export so animated position, scale, and rotation
 * match frame-for-frame.
 */
object KeyframeEvaluator {

    fun evaluate(clip: MainClip, localTimelineMs: Long): ClipTransform {
        if (clip.keyframes.isEmpty()) return clip.baseTransform
        val sorted = clip.keyframes.sortedBy { it.timeOffsetMs }
        if (sorted.size == 1) return sorted.first().toTransform()

        val clampedMs = localTimelineMs.coerceIn(0L, clip.durationMs.coerceAtLeast(1L))
        if (clampedMs <= sorted.first().timeOffsetMs) {
            return sorted.first().toTransform()
        }
        if (clampedMs >= sorted.last().timeOffsetMs) {
            return sorted.last().toTransform()
        }

        for (i in 0 until sorted.lastIndex) {
            val k0 = sorted[i]
            val k1 = sorted[i + 1]
            if (clampedMs in k0.timeOffsetMs..k1.timeOffsetMs) {
                val span = (k1.timeOffsetMs - k0.timeOffsetMs).coerceAtLeast(1L)
                val rawT = ((clampedMs - k0.timeOffsetMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                val t = applyEasing(rawT, k1.easing)
                return ClipTransform(
                    offsetX = lerpFloat(k0.offsetX, k1.offsetX, t),
                    offsetY = lerpFloat(k0.offsetY, k1.offsetY, t),
                    scale = lerpFloat(k0.scale, k1.scale, t),
                    rotationDegrees = lerpFloat(k0.rotationDegrees, k1.rotationDegrees, t),
                    opacity = lerpFloat(k0.opacity, k1.opacity, t).coerceIn(0f, 1f)
                )
            }
        }
        return sorted.last().toTransform()
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
