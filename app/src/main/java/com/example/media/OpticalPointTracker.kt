package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.MotionTrackData
import com.example.model.TrackedPositionKeyframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

data class PatchMatchResult(
    val bestX: Int,
    val bestY: Int,
    val confidence: Float
)

/**
 * Pure JVM-testable optical grayscale luminance template matcher using combined
 * Sum of Squared Differences (SSD) & Normalized Cross-Correlation (NCC).
 * Matches a 24x24 inner pattern patch inside a 64x64 outer search window.
 */
object LuminancePatchMatcher {

    const val PATCH_SIZE = 24
    const val SEARCH_WINDOW = 64

    fun extractLuminance(pixels: IntArray): FloatArray = FloatArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    fun extractPatch(
        luma: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        patchSize: Int = PATCH_SIZE
    ): FloatArray {
        val half = patchSize / 2
        val patch = FloatArray(patchSize * patchSize)
        for (py in 0 until patchSize) {
            val sy = (centerY - half + py).coerceIn(0, height - 1)
            for (px in 0 until patchSize) {
                val sx = (centerX - half + px).coerceIn(0, width - 1)
                patch[py * patchSize + px] = luma[sy * width + sx]
            }
        }
        return patch
    }

    /**
     * Searches within a [searchWindow] x [searchWindow] box centered at `(prevCenterX, prevCenterY)`
     * using a coarse-to-fine (stride 2 -> stride 1 refinement) SSD + NCC score.
     */
    fun findBestMatch(
        template: FloatArray,
        frameLuma: FloatArray,
        width: Int,
        height: Int,
        prevCenterX: Int,
        prevCenterY: Int,
        patchSize: Int = PATCH_SIZE,
        searchWindow: Int = SEARCH_WINDOW
    ): PatchMatchResult {
        val halfPatch = patchSize / 2
        val searchRadius = ((searchWindow - patchSize) / 2).coerceAtLeast(4)

        // Precompute template mean and variance for NCC
        var tMean = 0f
        for (v in template) tMean += v
        tMean /= template.size.coerceAtLeast(1)
        var tVar = 0f
        for (v in template) {
            val d = v - tMean
            tVar += d * d
        }
        val tNorm = sqrt(tVar.coerceAtLeast(1e-6f))

        fun scoreAt(cx: Int, cy: Int): Float {
            var cMean = 0f
            var idx = 0
            for (py in 0 until patchSize) {
                val sy = (cy - halfPatch + py).coerceIn(0, height - 1) * width
                for (px in 0 until patchSize) {
                    val sx = (cx - halfPatch + px).coerceIn(0, width - 1)
                    cMean += frameLuma[sy + sx]
                    idx++
                }
            }
            cMean /= idx.coerceAtLeast(1)

            var dot = 0f
            var cVar = 0f
            var ssd = 0f
            var k = 0
            for (py in 0 until patchSize) {
                val sy = (cy - halfPatch + py).coerceIn(0, height - 1) * width
                for (px in 0 until patchSize) {
                    val sx = (cx - halfPatch + px).coerceIn(0, width - 1)
                    val cv = frameLuma[sy + sx]
                    val tv = template[k++]
                    val dc = cv - cMean
                    val dt = tv - tMean
                    dot += dc * dt
                    cVar += dc * dc
                    val diff = cv - tv
                    ssd += diff * diff
                }
            }
            val ncc = dot / (tNorm * sqrt(cVar.coerceAtLeast(1e-6f)))
            val ssdScore = 1f - (ssd / template.size).coerceIn(0f, 1f)
            return ncc * 0.65f + ssdScore * 0.35f
        }

        val minX = (prevCenterX - searchRadius).coerceIn(halfPatch, (width - 1 - halfPatch).coerceAtLeast(halfPatch))
        val maxX = (prevCenterX + searchRadius).coerceIn(minX, (width - 1 - halfPatch).coerceAtLeast(minX))
        val minY = (prevCenterY - searchRadius).coerceIn(halfPatch, (height - 1 - halfPatch).coerceAtLeast(halfPatch))
        val maxY = (prevCenterY + searchRadius).coerceIn(minY, (height - 1 - halfPatch).coerceAtLeast(minY))

        var bestX = prevCenterX.coerceIn(minX, maxX)
        var bestY = prevCenterY.coerceIn(minY, maxY)
        var bestScore = Float.NEGATIVE_INFINITY

        // Coarse pass (step 2)
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val s = scoreAt(x, y)
                if (s > bestScore) {
                    bestScore = s
                    bestX = x
                    bestY = y
                }
                x += 2
            }
            y += 2
        }

        // Fine refinement (+/- 2 px around coarse peak)
        val refMinX = (bestX - 2).coerceAtLeast(minX)
        val refMaxX = (bestX + 2).coerceAtMost(maxX)
        val refMinY = (bestY - 2).coerceAtLeast(minY)
        val refMaxY = (bestY + 2).coerceAtMost(maxY)
        for (ry in refMinY..refMaxY) {
            for (rx in refMinX..refMaxX) {
                val s = scoreAt(rx, ry)
                if (s > bestScore) {
                    bestScore = s
                    bestX = rx
                    bestY = ry
                }
            }
        }

        return PatchMatchResult(
            bestX = bestX,
            bestY = bestY,
            confidence = ((bestScore + 1f) * 0.5f).coerceIn(0f, 1f)
        )
    }

    /**
     * Evaluates tracked normalized position `(offsetX, offsetY)` in `[-0.5..0.5]` at [timelineMs],
     * including the user's manual [MotionTrackData.offsetX] and [MotionTrackData.offsetY] fine-tuning.
     */
    fun evaluateTrackedPosition(
        track: MotionTrackData,
        timelineMs: Long
    ): Pair<Float, Float>? {
        if (!track.hasTrack) return null
        val sorted = track.keyframes.sortedBy { it.timeMs }
        val first = sorted.first()
        val last = sorted.last()
        val (baseX, baseY) = when {
            timelineMs <= first.timeMs -> first.normX to first.normY
            timelineMs >= last.timeMs -> last.normX to last.normY
            else -> {
                val nextIdx = sorted.indexOfFirst { it.timeMs >= timelineMs }.coerceAtLeast(1)
                val a = sorted[nextIdx - 1]
                val b = sorted[nextIdx]
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                val t = ((timelineMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
                (a.normX + (b.normX - a.normX) * t) to (a.normY + (b.normY - a.normY) * t)
            }
        }
        return (baseX + track.offsetX).coerceIn(-0.49f, 0.49f) to
            (baseY + track.offsetY).coerceIn(-0.49f, 0.49f)
    }
}

/**
 * Video-level optical point tracker extracting frames via [MediaMetadataRetriever]
 * and running [LuminancePatchMatcher] forward or backward across the clip duration.
 */
object OpticalPointTracker {

    private const val ANALYSIS_WIDTH = 240

    suspend fun trackVideoPoint(
        context: Context,
        videoUri: String,
        clipGlobalStartMs: Long,
        clipTrimStartMs: Long,
        clipSpeed: Float,
        trackStartGlobalMs: Long,
        trackEndGlobalMs: Long,
        initialAnchorX: Float, // 0..1 on stage
        initialAnchorY: Float, // 0..1 on stage
        forward: Boolean,
        stepMs: Long = 100L,
        onProgress: (Float) -> Unit
    ): Result<List<TrackedPositionKeyframe>> = withContext(Dispatchers.Default) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(videoUri)
                if (uri.scheme == "file" && uri.path != null) {
                    retriever.setDataSource(File(uri.path!!).absolutePath)
                } else {
                    retriever.setDataSource(context, uri)
                }

                val safeStart = minOf(trackStartGlobalMs, trackEndGlobalMs)
                val safeEnd = maxOf(trackStartGlobalMs, trackEndGlobalMs)
                val timestamps = mutableListOf<Long>()
                if (forward) {
                    var t = safeStart
                    while (t <= safeEnd) {
                        timestamps.add(t)
                        t += stepMs
                    }
                    if (timestamps.lastOrNull() != safeEnd) timestamps.add(safeEnd)
                } else {
                    var t = safeEnd
                    while (t >= safeStart) {
                        timestamps.add(t)
                        t -= stepMs
                    }
                    if (timestamps.lastOrNull() != safeStart) timestamps.add(safeStart)
                }

                if (timestamps.isEmpty()) return@runCatching emptyList()

                var template: FloatArray? = null
                var curX = 0
                var curY = 0
                var frameW = ANALYSIS_WIDTH
                var frameH = ANALYSIS_WIDTH
                val keyframes = mutableListOf<TrackedPositionKeyframe>()

                timestamps.forEachIndexed { idx, globalMs ->
                    coroutineContext.ensureActive()
                    val localMs = (globalMs - clipGlobalStartMs).coerceAtLeast(0L)
                    val sourceUs = (clipTrimStartMs + (localMs * clipSpeed).toLong()).coerceAtLeast(0L) * 1000L
                    val rawBmp = retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)

                    if (rawBmp != null) {
                        val ratio = rawBmp.height.toFloat() / rawBmp.width.coerceAtLeast(1).toFloat()
                        val w = ANALYSIS_WIDTH
                        val h = (w * ratio).toInt().coerceIn(80, 420)
                        val scaled = Bitmap.createScaledBitmap(rawBmp, w, h, true)
                        if (scaled !== rawBmp) rawBmp.recycle()

                        val pixels = IntArray(w * h)
                        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                        scaled.recycle()

                        val luma = LuminancePatchMatcher.extractLuminance(pixels)
                        frameW = w
                        frameH = h

                        if (template == null) {
                            curX = (initialAnchorX.coerceIn(0.05f, 0.95f) * w).toInt()
                            curY = (initialAnchorY.coerceIn(0.05f, 0.95f) * h).toInt()
                            template = LuminancePatchMatcher.extractPatch(luma, w, h, curX, curY)
                        } else {
                            val match = LuminancePatchMatcher.findBestMatch(
                                template = template!!,
                                frameLuma = luma,
                                width = w,
                                height = h,
                                prevCenterX = curX,
                                prevCenterY = curY
                            )
                            curX = match.bestX
                            curY = match.bestY
                            // Adaptive template blend when confidence is high to survive lighting drift
                            if (match.confidence > 0.72f) {
                                val fresh = LuminancePatchMatcher.extractPatch(luma, w, h, curX, curY)
                                val prevT = template!!
                                for (k in prevT.indices) {
                                    prevT[k] = prevT[k] * 0.85f + fresh[k] * 0.15f
                                }
                            }
                        }

                        val normX = (curX.toFloat() / frameW.coerceAtLeast(1)) - 0.5f
                        val normY = (curY.toFloat() / frameH.coerceAtLeast(1)) - 0.5f
                        keyframes.add(
                            TrackedPositionKeyframe(
                                timeMs = globalMs,
                                normX = normX.coerceIn(-0.48f, 0.48f),
                                normY = normY.coerceIn(-0.48f, 0.48f)
                            )
                        )
                    }
                    onProgress((idx + 1).toFloat() / timestamps.size.coerceAtLeast(1))
                }
                keyframes.sortedBy { it.timeMs }
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}
