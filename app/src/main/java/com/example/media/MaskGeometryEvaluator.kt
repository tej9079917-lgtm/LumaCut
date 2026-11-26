package com.example.media

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.model.MaskPoint
import com.example.model.MaskSettings
import com.example.model.MaskShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class EvaluatedMaskGeometry(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float
)

/**
 * Evaluates Fusion-style Shape & Bezier Pen Masks (with keyframe animation, expansion,
 * feathering, opacity, and inversion) for both live Compose preview and Media3 export.
 */
object MaskGeometryEvaluator {

    /**
     * Pure JVM-testable interpolation of animated [MaskSettings.keyframes] at clip-local [timeOffsetMs].
     */
    fun evaluateGeometry(mask: MaskSettings, timeOffsetMs: Long): EvaluatedMaskGeometry {
        val sorted = mask.keyframes.sortedBy { it.timeOffsetMs }
        if (sorted.isEmpty()) {
            return EvaluatedMaskGeometry(
                centerX = mask.centerX,
                centerY = mask.centerY,
                width = (mask.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                height = (mask.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                rotationDegrees = mask.rotationDegrees
            )
        }
        val first = sorted.first()
        if (timeOffsetMs <= first.timeOffsetMs) {
            return EvaluatedMaskGeometry(
                centerX = first.centerX,
                centerY = first.centerY,
                width = (first.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                height = (first.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                rotationDegrees = first.rotationDegrees
            )
        }
        val last = sorted.last()
        if (timeOffsetMs >= last.timeOffsetMs) {
            return EvaluatedMaskGeometry(
                centerX = last.centerX,
                centerY = last.centerY,
                width = (last.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                height = (last.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                rotationDegrees = last.rotationDegrees
            )
        }
        val nextIdx = sorted.indexOfFirst { it.timeOffsetMs >= timeOffsetMs }.coerceAtLeast(1)
        val a = sorted[nextIdx - 1]
        val b = sorted[nextIdx]
        val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L).toFloat()
        val t = ((timeOffsetMs - a.timeOffsetMs).toFloat() / span).coerceIn(0f, 1f)
        // Smoothstep ease
        val s = t * t * (3f - 2f * t)
        return EvaluatedMaskGeometry(
            centerX = a.centerX + (b.centerX - a.centerX) * s,
            centerY = a.centerY + (b.centerY - a.centerY) * s,
            width = ((a.width + (b.width - a.width) * s) + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
            height = ((a.height + (b.height - a.height) * s) + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
            rotationDegrees = a.rotationDegrees + (b.rotationDegrees - a.rotationDegrees) * s
        )
    }

    /**
     * Pure JVM-testable point-in-mask alpha test `[0..1]` for unit tests and hit detection.
     */
    fun isPointInsideMask(
        normX: Float,
        normY: Float,
        mask: MaskSettings,
        timeOffsetMs: Long = 0L
    ): Boolean {
        if (!mask.isActive) return true
        val geom = evaluateGeometry(mask, timeOffsetMs)
        val dx = normX - geom.centerX
        val dy = normY - geom.centerY
        val rad = (-geom.rotationDegrees * PI / 180.0)
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val rx = dx * cosR - dy * sinR
        val ry = dx * sinR + dy * cosR

        val inside = when (mask.shape) {
            MaskShape.NONE -> true
            MaskShape.RECTANGLE -> {
                val halfW = geom.width / 2f
                val halfH = geom.height / 2f
                rx in -halfW..halfW && ry in -halfH..halfH
            }
            MaskShape.CIRCLE -> {
                val halfW = (geom.width / 2f).coerceAtLeast(0.01f)
                val halfH = (geom.height / 2f).coerceAtLeast(0.01f)
                val nx = rx / halfW
                val ny = ry / halfH
                (nx * nx + ny * ny) <= 1f
            }
            MaskShape.LINEAR_SPLIT -> rx <= 0f
            MaskShape.FREEHAND_PEN -> pointInPolygon(normX, normY, mask.penPoints)
        }
        return if (mask.invert) !inside else inside
    }

    /**
     * Builds an Android [Path] for [mask] in pixel coordinates `(widthPx x heightPx)`.
     */
    fun buildAndroidPath(
        mask: MaskSettings,
        widthPx: Float,
        heightPx: Float,
        timeOffsetMs: Long = 0L
    ): Path {
        val path = Path()
        val geom = evaluateGeometry(mask, timeOffsetMs)
        val cx = geom.centerX * widthPx
        val cy = geom.centerY * heightPx
        val halfW = (geom.width * widthPx) / 2f
        val halfH = (geom.height * heightPx) / 2f

        when (mask.shape) {
            MaskShape.NONE -> {
                path.addRect(0f, 0f, widthPx, heightPx, Path.Direction.CW)
            }
            MaskShape.RECTANGLE -> {
                val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                val cornerPx = mask.cornerRadius.coerceIn(0f, 0.5f) * minOf(rect.width(), rect.height())
                path.addRoundRect(rect, cornerPx, cornerPx, Path.Direction.CW)
                rotatePath(path, geom.rotationDegrees, cx, cy)
            }
            MaskShape.CIRCLE -> {
                val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                path.addOval(rect, Path.Direction.CW)
                rotatePath(path, geom.rotationDegrees, cx, cy)
            }
            MaskShape.LINEAR_SPLIT -> {
                val big = maxOf(widthPx, heightPx) * 2.5f
                path.addRect(cx - big, cy - big, cx, cy + big, Path.Direction.CW)
                rotatePath(path, geom.rotationDegrees, cx, cy)
            }
            MaskShape.FREEHAND_PEN -> {
                val pts = mask.penPoints
                if (pts.size >= 3) {
                    val scale = (1f + mask.expansion).coerceIn(0.4f, 1.8f)
                    val meanX = pts.map { it.x }.average().toFloat()
                    val meanY = pts.map { it.y }.average().toFloat()
                    val scaledPts = pts.map { pt ->
                        MaskPoint(
                            x = (meanX + (pt.x - meanX) * scale) * widthPx,
                            y = (meanY + (pt.y - meanY) * scale) * heightPx
                        )
                    }
                    // Smooth quadratic Bezier closed loop through midpoints
                    val firstMidX = (scaledPts.last().x + scaledPts.first().x) / 2f
                    val firstMidY = (scaledPts.last().y + scaledPts.first().y) / 2f
                    path.moveTo(firstMidX, firstMidY)
                    for (i in scaledPts.indices) {
                        val curr = scaledPts[i]
                        val next = scaledPts[(i + 1) % scaledPts.size]
                        val midX = (curr.x + next.x) / 2f
                        val midY = (curr.y + next.y) / 2f
                        path.quadTo(curr.x, curr.y, midX, midY)
                    }
                    path.close()
                }
            }
        }
        return path
    }

    /**
     * Applies [mask] with feathering, invert, and opacity onto [source], returning a new ARGB bitmap.
     */
    fun applyMaskToBitmap(
        source: Bitmap,
        mask: MaskSettings,
        timeOffsetMs: Long = 0L
    ): Bitmap {
        if (!mask.isActive) return source
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val path = buildAndroidPath(mask, w.toFloat(), h.toFloat(), timeOffsetMs)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = (mask.opacity.coerceIn(0f, 1f) * 255f).toInt()
            if (mask.featherPx > 0.5f) {
                val radius = (mask.featherPx * (w / 360f)).coerceIn(1f, 80f)
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
        }

        if (mask.invert) {
            maskCanvas.drawColor(Color.argb((mask.opacity.coerceIn(0f, 1f) * 255f).toInt(), 255, 255, 255))
            maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            maskCanvas.drawPath(path, maskPaint)
        } else {
            maskCanvas.drawPath(path, maskPaint)
        }

        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val saveId = canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
        canvas.drawBitmap(source, 0f, 0f, layerPaint)
        layerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(maskBmp, 0f, 0f, layerPaint)
        canvas.restoreToCount(saveId)
        maskBmp.recycle()
        return output
    }

    private fun rotatePath(path: Path, degrees: Float, px: Float, py: Float) {
        if (kotlin.math.abs(degrees) > 0.1f) {
            val m = Matrix()
            m.postRotate(degrees, px, py)
            path.transform(m)
        }
    }

    private fun pointInPolygon(x: Float, y: Float, pts: List<MaskPoint>): Boolean {
        if (pts.size < 3) return false
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val xi = pts[i].x
            val yi = pts[i].y
            val xj = pts[j].x
            val yj = pts[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1e-6f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
