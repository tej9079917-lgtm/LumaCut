package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.example.model.AiCutoutSettings
import com.example.model.CutoutBackgroundMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private const val TAG = "SelfieCutoutEngine"

/**
 * Pure JVM-testable mask refinement & compositing processor for AI Background Removal:
 * - Soft threshold ramp controlled by `threshold` (0.10 .. 0.90)
 * - Separable horizontal + vertical box blur edge feathering (`0 .. 15 px`)
 * - Compositing modes: Transparent, Solid Color, Gaussian Blur background, or Custom Image
 */
object CutoutMaskProcessor {

    fun refineAlphaMask(
        rawConfidence: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        featherPx: Float
    ): FloatArray {
        val count = width * height
        if (count == 0 || rawConfidence.size < count) return FloatArray(count)
        val t = threshold.coerceIn(0.10f, 0.90f)
        val halfBand = 0.08f
        val lo = (t - halfBand).coerceAtLeast(0f)
        val hi = (t + halfBand).coerceAtMost(1f)
        val invBand = if (hi - lo > 1e-4f) 1f / (hi - lo) else 1f

        val thresholded = FloatArray(count) { i ->
            val v = rawConfidence[i].coerceIn(0f, 1f)
            when {
                v <= lo -> 0f
                v >= hi -> 1f
                else -> {
                    val s = (v - lo) * invBand
                    s * s * (3f - 2f * s) // smoothstep
                }
            }
        }

        val radius = featherPx.roundToInt().coerceIn(0, 15)
        if (radius == 0) return thresholded
        return boxBlur2D(thresholded, width, height, radius)
    }

    fun boxBlur2D(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        if (radius <= 0 || width <= 0 || height <= 0) return src.copyOf()
        val temp = FloatArray(width * height)
        val dst = FloatArray(width * height)
        val window = (radius * 2 + 1).toFloat()

        // Horizontal pass
        for (y in 0 until height) {
            val rowOff = y * width
            var sum = 0f
            for (dx in -radius..radius) {
                val x = dx.coerceIn(0, width - 1)
                sum += src[rowOff + x]
            }
            for (x in 0 until width) {
                temp[rowOff + x] = (sum / window).coerceIn(0f, 1f)
                val outX = (x - radius).coerceIn(0, width - 1)
                val inX = (x + radius + 1).coerceIn(0, width - 1)
                sum += src[rowOff + inX] - src[rowOff + outX]
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            var sum = 0f
            for (dy in -radius..radius) {
                val y = dy.coerceIn(0, height - 1)
                sum += temp[y * width + x]
            }
            for (y in 0 until height) {
                dst[y * width + x] = (sum / window).coerceIn(0f, 1f)
                val outY = (y - radius).coerceIn(0, height - 1)
                val inY = (y + radius + 1).coerceIn(0, height - 1)
                sum += temp[inY * width + x] - temp[outY * width + x]
            }
        }
        return dst
    }

    fun compositePixels(
        srcArgb: IntArray,
        alphaMask: FloatArray,
        width: Int,
        height: Int,
        settings: AiCutoutSettings,
        customBgArgb: IntArray? = null
    ): IntArray {
        val count = width * height
        val out = IntArray(count)
        when (settings.backgroundMode) {
            CutoutBackgroundMode.TRANSPARENT -> {
                for (i in 0 until count) {
                    val px = srcArgb[i]
                    val origA = (px ushr 24) and 0xFF
                    val maskA = (alphaMask[i].coerceIn(0f, 1f) * origA).roundToInt().coerceIn(0, 255)
                    out[i] = (maskA shl 24) or (px and 0x00FFFFFF)
                }
            }
            CutoutBackgroundMode.SOLID_COLOR -> {
                val bg = settings.solidColorArgb.toInt()
                val bgR = (bg ushr 16) and 0xFF
                val bgG = (bg ushr 8) and 0xFF
                val bgB = bg and 0xFF
                for (i in 0 until count) {
                    val fg = srcArgb[i]
                    val a = alphaMask[i].coerceIn(0f, 1f)
                    val inv = 1f - a
                    val r = (((fg ushr 16) and 0xFF) * a + bgR * inv).roundToInt().coerceIn(0, 255)
                    val g = (((fg ushr 8) and 0xFF) * a + bgG * inv).roundToInt().coerceIn(0, 255)
                    val b = ((fg and 0xFF) * a + bgB * inv).roundToInt().coerceIn(0, 255)
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            CutoutBackgroundMode.GAUSSIAN_BLUR -> {
                val blurredBg = blurArgbChannels(srcArgb, width, height, radius = 8)
                for (i in 0 until count) {
                    val fg = srcArgb[i]
                    val bg = blurredBg[i]
                    val a = alphaMask[i].coerceIn(0f, 1f)
                    val inv = 1f - a
                    val r = (((fg ushr 16) and 0xFF) * a + ((bg ushr 16) and 0xFF) * inv).roundToInt().coerceIn(0, 255)
                    val g = (((fg ushr 8) and 0xFF) * a + ((bg ushr 8) and 0xFF) * inv).roundToInt().coerceIn(0, 255)
                    val b = ((fg and 0xFF) * a + (bg and 0xFF) * inv).roundToInt().coerceIn(0, 255)
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            CutoutBackgroundMode.CUSTOM_IMAGE -> {
                val bgArr = if (customBgArgb != null && customBgArgb.size >= count) customBgArgb else null
                for (i in 0 until count) {
                    val fg = srcArgb[i]
                    val a = alphaMask[i].coerceIn(0f, 1f)
                    if (bgArr == null) {
                        val maskA = (a * 255f).roundToInt().coerceIn(0, 255)
                        out[i] = (maskA shl 24) or (fg and 0x00FFFFFF)
                    } else {
                        val bg = bgArr[i]
                        val inv = 1f - a
                        val r = (((fg ushr 16) and 0xFF) * a + ((bg ushr 16) and 0xFF) * inv).roundToInt().coerceIn(0, 255)
                        val g = (((fg ushr 8) and 0xFF) * a + ((bg ushr 8) and 0xFF) * inv).roundToInt().coerceIn(0, 255)
                        val b = ((fg and 0xFF) * a + (bg and 0xFF) * inv).roundToInt().coerceIn(0, 255)
                        out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        return out
    }

    private fun blurArgbChannels(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val count = width * height
        val rCh = FloatArray(count) { ((src[it] ushr 16) and 0xFF) / 255f }
        val gCh = FloatArray(count) { ((src[it] ushr 8) and 0xFF) / 255f }
        val bCh = FloatArray(count) { (src[it] and 0xFF) / 255f }
        val rBlur = boxBlur2D(rCh, width, height, radius)
        val gBlur = boxBlur2D(gCh, width, height, radius)
        val bBlur = boxBlur2D(bCh, width, height, radius)
        return IntArray(count) { i ->
            val r = (rBlur[i] * 255f).roundToInt().coerceIn(0, 255)
            val g = (gBlur[i] * 255f).roundToInt().coerceIn(0, 255)
            val b = (bBlur[i] * 255f).roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/**
 * On-device AI Background Removal engine powered by ML Kit Selfie Segmentation.
 * Runs locally without requiring a physical green screen, with graceful fallback if the
 * device ML Kit runtime is unavailable.
 */
object SelfieCutoutEngine {

    private val streamSegmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
        Segmentation.getClient(options)
    }

    suspend fun applyAiCutout(
        context: Context,
        source: Bitmap,
        settings: AiCutoutSettings
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        if (!settings.enabled) return@withContext Result.success(source)
        try {
            val maxEdge = 480
            val working = if (source.width > maxEdge || source.height > maxEdge) {
                val ratio = source.width.toFloat() / source.height.coerceAtLeast(1).toFloat()
                val w = if (ratio >= 1f) maxEdge else (maxEdge * ratio).toInt().coerceAtLeast(32)
                val h = if (ratio >= 1f) (maxEdge / ratio).toInt().coerceAtLeast(32) else maxEdge
                Bitmap.createScaledBitmap(source, w, h, true)
            } else {
                source
            }

            val inputImage = InputImage.fromBitmap(working, 0)
            val mask = suspendCancellableCoroutine { cont ->
                streamSegmenter.process(inputImage)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { err ->
                        if (cont.isActive) cont.resumeWithException(err)
                    }
            }

            val w = working.width
            val h = working.height
            val maskW = mask.width
            val maskH = mask.height
            val buf: ByteBuffer = mask.buffer
            buf.rewind()
            val rawMask = FloatArray(maskW * maskH) {
                if (buf.remaining() >= 4) buf.float else 0f
            }

            // Resample mask to working bitmap dimensions if ML Kit returned a smaller buffer
            val alignedMask = if (maskW == w && maskH == h) {
                rawMask
            } else {
                FloatArray(w * h) { idx ->
                    val x = idx % w
                    val y = idx / w
                    val mx = (x * maskW / w).coerceIn(0, maskW - 1)
                    val my = (y * maskH / h).coerceIn(0, maskH - 1)
                    rawMask[my * maskW + mx]
                }
            }

            val refinedAlpha = CutoutMaskProcessor.refineAlphaMask(
                rawConfidence = alignedMask,
                width = w,
                height = h,
                threshold = settings.threshold,
                featherPx = settings.featherPx
            )

            val srcPixels = IntArray(w * h)
            working.getPixels(srcPixels, 0, w, 0, 0, w, h)

            val customBgPixels = if (
                settings.backgroundMode == CutoutBackgroundMode.CUSTOM_IMAGE &&
                !settings.customBackgroundUri.isNullOrBlank()
            ) {
                decodeCustomBgPixels(context, settings.customBackgroundUri, w, h)
            } else null

            val composited = CutoutMaskProcessor.compositePixels(
                srcArgb = srcPixels,
                alphaMask = refinedAlpha,
                width = w,
                height = h,
                settings = settings,
                customBgArgb = customBgPixels
            )

            val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            resultBitmap.setPixels(composited, 0, w, 0, 0, w, h)
            if (working !== source && !working.isRecycled) {
                working.recycle()
            }
            Result.success(resultBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Selfie segmentation failed", e)
            Result.failure(e)
        }
    }

    private fun decodeCustomBgPixels(
        context: Context,
        uriStr: String,
        targetW: Int,
        targetH: Int
    ): IntArray? = runCatching {
        val uri = Uri.parse(uriStr)
        val input = if (uri.scheme == "file" && uri.path != null) {
            File(uri.path!!).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        val decoded = input?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) decoded.recycle()
        val px = IntArray(targetW * targetH)
        scaled.getPixels(px, 0, targetW, 0, 0, targetW, targetH)
        scaled.recycle()
        px
    }.getOrNull()
}
