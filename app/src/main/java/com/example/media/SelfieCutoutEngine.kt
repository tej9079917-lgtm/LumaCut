package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.AiCutoutSettings
import com.example.model.CutoutBackgroundMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pure JVM-testable alpha mask refinement & background compositing engine for AI Cutout.
 * Handles mask thresholding (`0.1..0.9`), separable box-blur edge feathering (`0..15px`),
 * and compositing over Transparent, Solid Color, Gaussian Blur, or Custom Image backgrounds.
 */
object CutoutMaskProcessor {

    /**
     * Converts raw ML confidence floats `[0..1]` into refined alpha weights `[0..1]` using
     * soft thresholding and separable horizontal + vertical box blur feathering.
     */
    fun refineConfidenceMask(
        rawConfidence: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        featherPx: Float
    ): FloatArray {
        val size = width * height
        if (size <= 0 || rawConfidence.size < size) return FloatArray(0)
        val cut = threshold.coerceIn(0.10f, 0.90f)
        val softBand = 0.08f
        val thresholded = FloatArray(size) { i ->
            val v = rawConfidence[i].coerceIn(0f, 1f)
            when {
                v <= cut - softBand -> 0f
                v >= cut + softBand -> 1f
                else -> ((v - (cut - softBand)) / (2f * softBand)).coerceIn(0f, 1f)
            }
        }
        val radius = featherPx.roundToInt().coerceIn(0, 15)
        if (radius == 0) return thresholded
        return separableBoxBlur(thresholded, width, height, radius)
    }

    /**
     * Fast O(N) separable 2-pass box blur on a 2D float mask.
     */
    fun separableBoxBlur(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int
    ): FloatArray {
        if (radius <= 0 || width <= 1 || height <= 1) return src.copyOf()
        val temp = FloatArray(width * height)
        val out = FloatArray(width * height)
        val windowSize = (radius * 2 + 1).toFloat()

        // Horizontal pass
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (k in -radius..radius) {
                sum += src[row + k.coerceIn(0, width - 1)]
            }
            for (x in 0 until width) {
                temp[row + x] = sum / windowSize
                val removeIdx = (x - radius).coerceIn(0, width - 1)
                val addIdx = (x + radius + 1).coerceIn(0, width - 1)
                sum += src[row + addIdx] - src[row + removeIdx]
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            var sum = 0f
            for (k in -radius..radius) {
                sum += temp[k.coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                out[y * width + x] = (sum / windowSize).coerceIn(0f, 1f)
                val removeRow = (y - radius).coerceIn(0, height - 1)
                val addRow = (y + radius + 1).coerceIn(0, height - 1)
                sum += temp[addRow * width + x] - temp[removeRow * width + x]
            }
        }
        return out
    }

    /**
     * Composites foreground ARGB pixels with refined alpha mask `[0..1]` over either:
     * - Transparent alpha
     * - Solid color ARGB
     * - Blurred background pixels
     * - Custom background image pixels
     */
    fun compositePixels(
        fgPixels: IntArray,
        alphaMask: FloatArray,
        width: Int,
        height: Int,
        mode: CutoutBackgroundMode,
        solidColorArgb: Long,
        bgPixels: IntArray? = null
    ): IntArray {
        val count = width * height
        val out = IntArray(count)
        val blurredBg = if (mode == CutoutBackgroundMode.GAUSSIAN_BLUR) {
            blurArgbPixels(fgPixels, width, height, radius = 8)
        } else null

        val solidA = ((solidColorArgb ushr 24) and 0xFFL).toInt()
        val solidR = ((solidColorArgb ushr 16) and 0xFFL).toInt()
        val solidG = ((solidColorArgb ushr 8) and 0xFFL).toInt()
        val solidB = (solidColorArgb and 0xFFL).toInt()

        for (i in 0 until count) {
            val fg = fgPixels[i]
            val fgA = (fg ushr 24) and 0xFF
            val fgR = (fg ushr 16) and 0xFF
            val fgG = (fg ushr 8) and 0xFF
            val fgB = fg and 0xFF
            val m = alphaMask[i].coerceIn(0f, 1f)

            when (mode) {
                CutoutBackgroundMode.TRANSPARENT -> {
                    val outA = (fgA * m).roundToInt().coerceIn(0, 255)
                    out[i] = (outA shl 24) or (fgR shl 16) or (fgG shl 8) or fgB
                }
                CutoutBackgroundMode.SOLID_COLOR -> {
                    val inv = 1f - m
                    val r = (fgR * m + solidR * inv).roundToInt().coerceIn(0, 255)
                    val g = (fgG * m + solidG * inv).roundToInt().coerceIn(0, 255)
                    val b = (fgB * m + solidB * inv).roundToInt().coerceIn(0, 255)
                    val a = (fgA * m + solidA * inv).roundToInt().coerceIn(0, 255)
                    out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                CutoutBackgroundMode.GAUSSIAN_BLUR -> {
                    val bg = blurredBg?.get(i) ?: fg
                    val bgR = (bg ushr 16) and 0xFF
                    val bgG = (bg ushr 8) and 0xFF
                    val bgB = bg and 0xFF
                    val inv = 1f - m
                    val r = (fgR * m + bgR * inv).roundToInt().coerceIn(0, 255)
                    val g = (fgG * m + bgG * inv).roundToInt().coerceIn(0, 255)
                    val b = (fgB * m + bgB * inv).roundToInt().coerceIn(0, 255)
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                CutoutBackgroundMode.CUSTOM_IMAGE -> {
                    val bg = bgPixels?.getOrNull(i) ?: solidColorArgb.toInt()
                    val bgR = (bg ushr 16) and 0xFF
                    val bgG = (bg ushr 8) and 0xFF
                    val bgB = bg and 0xFF
                    val inv = 1f - m
                    val r = (fgR * m + bgR * inv).roundToInt().coerceIn(0, 255)
                    val g = (fgG * m + bgG * inv).roundToInt().coerceIn(0, 255)
                    val b = (fgB * m + bgB * inv).roundToInt().coerceIn(0, 255)
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return out
    }

    /**
     * Graceful offline fallback subject-salience mask when ML Kit Selfie Segmentation
     * is unavailable on a device without Google Play Services.
     * Estimates foreground via border-color distance + center portrait prior.
     */
    fun estimateFallbackSalienceMask(
        pixels: IntArray,
        width: Int,
        height: Int
    ): FloatArray {
        val count = width * height
        if (count <= 0) return FloatArray(0)
        // Average border color
        var bR = 0L
        var bG = 0L
        var bB = 0L
        var samples = 0
        for (x in 0 until width) {
            for (y in listOf(0, height - 1)) {
                val p = pixels[y * width + x]
                bR += (p ushr 16) and 0xFF
                bG += (p ushr 8) and 0xFF
                bB += p and 0xFF
                samples++
            }
        }
        val avgR = (bR / samples.coerceAtLeast(1)).toFloat()
        val avgG = (bG / samples.coerceAtLeast(1)).toFloat()
        val avgB = (bB / samples.coerceAtLeast(1)).toFloat()

        val mask = FloatArray(count)
        for (y in 0 until height) {
            val ny = (y.toFloat() / height.coerceAtLeast(1)) - 0.52f
            for (x in 0 until width) {
                val nx = (x.toFloat() / width.coerceAtLeast(1)) - 0.5f
                val p = pixels[y * width + x]
                val r = ((p ushr 16) and 0xFF).toFloat()
                val g = ((p ushr 8) and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                val colorDist = (abs(r - avgR) + abs(g - avgG) + abs(b - avgB)) / 380f
                val radial = hypot(nx / 0.38f, ny / 0.46f)
                val centerPrior = (1.15f - radial).coerceIn(0f, 1f)
                mask[y * width + x] = (colorDist * 0.55f + centerPrior * 0.65f).coerceIn(0f, 1f)
            }
        }
        return mask
    }

    private fun blurArgbPixels(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val rCh = FloatArray(width * height) { ((src[it] ushr 16) and 0xFF).toFloat() }
        val gCh = FloatArray(width * height) { ((src[it] ushr 8) and 0xFF).toFloat() }
        val bCh = FloatArray(width * height) { (src[it] and 0xFF).toFloat() }
        val br = separableBoxBlur(rCh, width, height, radius)
        val bg = separableBoxBlur(gCh, width, height, radius)
        val bb = separableBoxBlur(bCh, width, height, radius)
        return IntArray(width * height) { i ->
            val r = br[i].roundToInt().coerceIn(0, 255)
            val g = bg[i].roundToInt().coerceIn(0, 255)
            val b = bb[i].roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/**
 * On-device ML Kit Selfie Segmentation wrapper with graceful offline/error fallback.
 */
object SelfieCutoutEngine {

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    suspend fun applyCutoutToBitmap(
        context: Context,
        source: Bitmap,
        settings: AiCutoutSettings
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!settings.enabled) return@withContext source

        // Process at a smooth, responsive resolution (max 512px) for fast live preview & export compositing
        val maxSide = 512
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val scale = if (maxOf(w, h) > maxSide) maxSide.toFloat() / maxOf(w, h) else 1f
        val workW = (w * scale).roundToInt().coerceAtLeast(32)
        val workH = (h * scale).roundToInt().coerceAtLeast(32)
        val working = if (scale < 0.99f) Bitmap.createScaledBitmap(source, workW, workH, true) else source

        val pixels = IntArray(workW * workH)
        working.getPixels(pixels, 0, workW, 0, 0, workW, workH)

        val rawConfidence = runCatching {
            runMlKitSegmentation(working, workW, workH)
        }.getOrElse {
            CutoutMaskProcessor.estimateFallbackSalienceMask(pixels, workW, workH)
        }

        val refinedMask = CutoutMaskProcessor.refineConfidenceMask(
            rawConfidence = rawConfidence,
            width = workW,
            height = workH,
            threshold = settings.threshold,
            featherPx = settings.featherPx
        )

        val customBgPixels = if (settings.backgroundMode == CutoutBackgroundMode.CUSTOM_IMAGE &&
            !settings.customBackgroundUri.isNullOrBlank()
        ) {
            loadScaledBackgroundPixels(context, settings.customBackgroundUri, workW, workH)
        } else null

        val composited = CutoutMaskProcessor.compositePixels(
            fgPixels = pixels,
            alphaMask = refinedMask,
            width = workW,
            height = workH,
            mode = settings.backgroundMode,
            solidColorArgb = settings.solidColorArgb,
            bgPixels = customBgPixels
        )

        val result = Bitmap.createBitmap(workW, workH, Bitmap.Config.ARGB_8888)
        result.setPixels(composited, 0, workW, 0, 0, workW, workH)
        if (working !== source) working.recycle()
        result
    }

    private suspend fun runMlKitSegmentation(bitmap: Bitmap, targetW: Int, targetH: Int): FloatArray {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val mask = suspendCancellableCoroutine { cont ->
            segmenter.process(inputImage)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { err ->
                    if (cont.isActive) cont.resumeWithException(err)
                }
        }
        val maskW = mask.width
        val maskH = mask.height
        val buffer = mask.buffer
        buffer.rewind()
        val raw = FloatArray(maskW * maskH) { buffer.float }
        if (maskW == targetW && maskH == targetH) return raw

        // Nearest/bilinear resample mask to match working dimensions
        val resampled = FloatArray(targetW * targetH)
        for (y in 0 until targetH) {
            val sy = ((y.toFloat() / targetH) * maskH).toInt().coerceIn(0, maskH - 1)
            for (x in 0 until targetW) {
                val sx = ((x.toFloat() / targetW) * maskW).toInt().coerceIn(0, maskW - 1)
                resampled[y * targetW + x] = raw[sy * maskW + sx]
            }
        }
        return resampled
    }

    private fun loadScaledBackgroundPixels(
        context: Context,
        uriString: String,
        targetW: Int,
        targetH: Int
    ): IntArray? = runCatching {
        val uri = Uri.parse(uriString)
        val stream = if (uri.scheme == "file" && uri.path != null) {
            File(uri.path!!).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        val decoded = stream?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) decoded.recycle()
        val out = IntArray(targetW * targetH)
        scaled.getPixels(out, 0, targetW, 0, 0, targetW, targetH)
        scaled.recycle()
        out
    }.getOrNull()
}
