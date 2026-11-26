package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.model.MotionTrackData
import com.example.model.TrackedPositionKeyframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "OpticalPointTracker"

data class TrackMatchResult(
    val centerX: Int,
    val centerY: Int,
    val confidence: Float
)

/**
 * Pure JVM-testable 2D template matching engine using Sum of Squared Differences (SSD)
 * refined by Normalized Cross-Correlation (NCC) on 8-bit grayscale luminance buffers.
 *
 * Uses a `24x24` inner pattern patch searched within a `64x64` outer search window.
 */
object LuminancePatchMatcher {

    const val PATTERN_SIZE = 24
    const val SEARCH_WINDOW_SIZE = 64

    fun extractLuminance(argbPixels: IntArray): FloatArray = FloatArray(argbPixels.size) { i ->
        val px = argbPixels[i]
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        0.299f * r + 0.587f * g + 0.114f * b
    }

    fun extractPatch(
        luma: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        patchSize: Int = PATTERN_SIZE
    ): FloatArray {
        val half = patchSize / 2
        val patch = FloatArray(patchSize * patchSize)
        for (py in 0 until patchSize) {
            val sy = (centerY - half + py).coerceIn(0, height - 1)
            val rowOff = sy * width
            val dstOff = py * patchSize
            for (px in 0 until patchSize) {
                val sx = (centerX - half + px).coerceIn(0, width - 1)
                patch[dstOff + px] = luma[rowOff + sx]
            }
        }
        return patch
    }

    /**
     * Searches around `(prevCenterX, prevCenterY)` inside a `searchWindowSize x searchWindowSize`
     * region to find the best matching patch position via coarse-to-fine SSD + NCC scoring.
     */
    fun trackPatch(
        templatePatch: FloatArray,
        nextLuma: FloatArray,
        width: Int,
        height: Int,
        prevCenterX: Int,
        prevCenterY: Int,
        patchSize: Int = PATTERN_SIZE,
        searchWindowSize: Int = SEARCH_WINDOW_SIZE
    ): TrackMatchResult {
        val halfPatch = patchSize / 2
        val searchRadius = ((searchWindowSize - patchSize) / 2).coerceAtLeast(8)

        // Precompute template mean & variance for NCC
        var tMean = 0f
        for (v in templatePatch) tMean += v
        tMean /= templatePatch.size
        var tVar = 0f
        for (v in templatePatch) {
            val d = v - tMean
            tVar += d * d
        }
        val tStd = sqrt(tVar.coerceAtLeast(1e-4f))

        fun scoreAt(cx: Int, cy: Int): Pair<Float, Float> {
            var ssd = 0f
            var sumI = 0f
            val n = patchSize * patchSize
            for (py in 0 until patchSize) {
                val sy = (cy - halfPatch + py).coerceIn(0, height - 1)
                val rowOff = sy * width
                val tOff = py * patchSize
                for (px in 0 until patchSize) {
                    val sx = (cx - halfPatch + px).coerceIn(0, width - 1)
                    val iv = nextLuma[rowOff + sx]
                    val tv = templatePatch[tOff + px]
                    val diff = iv - tv
                    ssd += diff * diff
                    sumI += iv
                }
            }
            val iMean = sumI / n
            var cross = 0f
            var iVar = 0f
            for (py in 0 until patchSize) {
                val sy = (cy - halfPatch + py).coerceIn(0, height - 1)
                val rowOff = sy * width
                val tOff = py * patchSize
                for (px in 0 until patchSize) {
                    val sx = (cx - halfPatch + px).coerceIn(0, width - 1)
                    val di = nextLuma[rowOff + sx] - iMean
                    val dt = templatePatch[tOff + px] - tMean
                    cross += di * dt
                    iVar += di * di
                }
            }
            val ncc = cross / (tStd * sqrt(iVar.coerceAtLeast(1e-4f)))
            // Combined score: maximize NCC while penalizing normalized SSD
            val normSsd = ssd / (n * 255f * 255f)
            val combined = ncc - normSsd * 1.5f
            return combined to ncc.coerceIn(0f, 1f)
        }

        val minX = (prevCenterX - searchRadius).coerceIn(halfPatch, (width - 1 - halfPatch).coerceAtLeast(halfPatch))
        val maxX = (prevCenterX + searchRadius).coerceIn(halfPatch, (width - 1 - halfPatch).coerceAtLeast(halfPatch))
        val minY = (prevCenterY - searchRadius).coerceIn(halfPatch, (height - 1 - halfPatch).coerceAtLeast(halfPatch))
        val maxY = (prevCenterY + searchRadius).coerceIn(halfPatch, (height - 1 - halfPatch).coerceAtLeast(halfPatch))

        // Coarse pass (stride = 2)
        var bestX = prevCenterX.coerceIn(minX, maxX)
        var bestY = prevCenterY.coerceIn(minY, maxY)
        var bestScore = Float.NEGATIVE_INFINITY
        var bestConf = 0f

        var cy = minY
        while (cy <= maxY) {
            var cx = minX
            while (cx <= maxX) {
                val (score, conf) = scoreAt(cx, cy)
                if (score > bestScore) {
                    bestScore = score
                    bestConf = conf
                    bestX = cx
                    bestY = cy
                }
                cx += 2
            }
            cy += 2
        }

        // Fine refinement pass (stride = 1 in 5x5 neighborhood)
        val fineMinX = (bestX - 2).coerceAtLeast(minX)
        val fineMaxX = (bestX + 2).coerceAtMost(maxX)
        val fineMinY = (bestY - 2).coerceAtLeast(minY)
        val fineMaxY = (bestY + 2).coerceAtMost(maxY)
        for (fy in fineMinY..fineMaxY) {
            for (fx in fineMinX..fineMaxX) {
                val (score, conf) = scoreAt(fx, fy)
                if (score > bestScore) {
                    bestScore = score
                    bestConf = conf
                    bestX = fx
                    bestY = fy
                }
            }
        }

        return TrackMatchResult(bestX, bestY, bestConf)
    }
}

/**
 * Client-side Point Motion Tracker that samples V1 video frames via [MediaMetadataRetriever]
 * and records `(x, y)` tracked keyframes so PNG stickers, callouts, and captions automatically
 * follow moving objects in both preview and exported MP4.
 */
object OpticalPointTracker {

    private const val ANALYSIS_WIDTH = 240

    fun evaluateTrackedPosition(
        trackData: MotionTrackData,
        globalTimeMs: Long,
        fallbackOffsetX: Float,
        fallbackOffsetY: Float
    ): Pair<Float, Float> {
        if (!trackData.hasTrack) return fallbackOffsetX to fallbackOffsetY
        val kfs = trackData.keyframes.sortedBy { it.timeMs }
        if (kfs.isEmpty()) return fallbackOffsetX to fallbackOffsetY

        val (rawX, rawY) = when {
            globalTimeMs <= kfs.first().timeMs -> kfs.first().normX to kfs.first().normY
            globalTimeMs >= kfs.last().timeMs -> kfs.last().normX to kfs.last().normY
            else -> {
                var rx = kfs.last().normX
                var ry = kfs.last().normY
                for (i in 0 until kfs.lastIndex) {
                    val a = kfs[i]
                    val b = kfs[i + 1]
                    if (globalTimeMs in a.timeMs..b.timeMs) {
                        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                        val t = ((globalTimeMs - a.timeMs) / span).coerceIn(0f, 1f)
                        rx = a.normX + (b.normX - a.normX) * t
                        ry = a.normY + (b.normY - a.normY) * t
                        break
                    }
                }
                rx to ry
            }
        }
        return (rawX + trackData.offsetX).coerceIn(-0.6f, 0.6f) to
            (rawY + trackData.offsetY).coerceIn(-0.6f, 0.6f)
    }

    suspend fun trackVideoFrames(
        context: Context,
        videoUri: String,
        clipGlobalStartMs: Long,
        clipTrimStartMs: Long,
        clipPlaybackSpeed: Float,
        startGlobalMs: Long,
        endGlobalMs: Long,
        stepMs: Long = 100L,
        initialAnchorX: Float,
        initialAnchorY: Float,
        forward: Boolean,
        onProgress: (Float) -> Unit
    ): Result<List<TrackedPositionKeyframe>> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(videoUri)
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(File(uri.path!!).absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }

            val timestamps = mutableListOf<Long>()
            val safeStep = stepMs.coerceIn(66L, 250L)
            if (forward) {
                var t = startGlobalMs
                while (t <= endGlobalMs) {
                    timestamps.add(t)
                    t += safeStep
                }
            } else {
                var t = startGlobalMs
                while (t >= endGlobalMs) {
                    timestamps.add(t)
                    t -= safeStep
                }
            }
            if (timestamps.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Nothing to track in this time range."))
            }

            val keyframes = mutableListOf<TrackedPositionKeyframe>()
            var templatePatch: FloatArray? = null
            var curX = 0
            var curY = 0
            var frameW = ANALYSIS_WIDTH
            var frameH = ANALYSIS_WIDTH

            for (idx in timestamps.indices) {
                ensureActive()
                val globalMs = timestamps[idx]
                val localTimelineMs = (globalMs - clipGlobalStartMs).coerceAtLeast(0L)
                val sourceMs = clipTrimStartMs + (localTimelineMs * clipPlaybackSpeed.coerceIn(0.25f, 4f)).toLong()
                val rawFrame = retriever.getFrameAtTime(sourceMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(sourceMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (rawFrame == null) {
                    onProgress((idx + 1f) / timestamps.size.toFloat())
                    continue
                }

                val ratio = rawFrame.width.toFloat() / rawFrame.height.coerceAtLeast(1).toFloat()
                frameW = ANALYSIS_WIDTH
                frameH = (ANALYSIS_WIDTH / ratio).roundToInt().coerceIn(96, 420)
                val scaled = Bitmap.createScaledBitmap(rawFrame, frameW, frameH, true)
                if (scaled !== rawFrame) rawFrame.recycle()

                val pixels = IntArray(frameW * frameH)
                scaled.getPixels(pixels, 0, frameW, 0, 0, frameW, frameH)
                scaled.recycle()
                val luma = LuminancePatchMatcher.extractLuminance(pixels)

                if (templatePatch == null) {
                    curX = (initialAnchorX.coerceIn(0.05f, 0.95f) * frameW).roundToInt()
                    curY = (initialAnchorY.coerceIn(0.05f, 0.95f) * frameH).roundToInt()
                    templatePatch = LuminancePatchMatcher.extractPatch(luma, frameW, frameH, curX, curY)
                } else {
                    val match = LuminancePatchMatcher.trackPatch(
                        templatePatch = templatePatch,
                        nextLuma = luma,
                        width = frameW,
                        height = frameH,
                        prevCenterX = curX,
                        prevCenterY = curY
                    )
                    curX = match.centerX
                    curY = match.centerY
                    // Adaptive template blend (25%) to handle gradual lighting/scale changes
                    if (match.confidence > 0.55f) {
                        val fresh = LuminancePatchMatcher.extractPatch(luma, frameW, frameH, curX, curY)
                        for (i in templatePatch.indices) {
                            templatePatch[i] = templatePatch[i] * 0.75f + fresh[i] * 0.25f
                        }
                    }
                }

                val normX = (curX.toFloat() / frameW.toFloat()) - 0.5f
                val normY = (curY.toFloat() / frameH.toFloat()) - 0.5f
                keyframes.add(
                    TrackedPositionKeyframe(
                        timeMs = globalMs,
                        normX = normX.coerceIn(-0.5f, 0.5f),
                        normY = normY.coerceIn(-0.5f, 0.5f)
                    )
                )
                onProgress((idx + 1f) / timestamps.size.toFloat())
            }

            Result.success(keyframes.sortedBy { it.timeMs })
        } catch (e: Exception) {
            Log.e(TAG, "Motion tracking failed", e)
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
