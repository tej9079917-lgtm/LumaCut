package com.example.media

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
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
 * Evaluates Fusion-style Shape & Bezier Pen Masks (with keyframe interpolation, expansion,
 * invert, feathering, and opacity) for both live PreviewStage and Media3 Export.
 */
object MaskGeometryEvaluator {

    fun evaluateGeometry(mask: MaskSettings, localTimeMs: Long): EvaluatedMaskGeometry {
        val kfs = mask.keyframes.sortedBy { it.timeOffsetMs }
        if (kfs.isEmpty()) {
            return EvaluatedMaskGeometry(
                centerX = mask.centerX,
                centerY = mask.centerY,
                width = (mask.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                height = (mask.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                rotationDegrees = mask.rotationDegrees
            )
        }
        if (localTimeMs <= kfs.first().timeOffsetMs) {
            val first = kfs.first()
            return EvaluatedMaskGeometry(
                centerX = first.centerX,
                centerY = first.centerY,
                width = (first.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                height = (first.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                rotationDegrees = first.rotationDegrees
            )
        }
        if (localTimeMs >= kfs.last().timeOffsetMs) {
            val last = kfs.last()
            return EvaluatedMaskGeometry(
                centerX = last.centerX,
                centerY = last.centerY,
                width = (last.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                height = (last.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
                rotationDegrees = last.rotationDegrees
            )
        }
        for (i in 0 until kfs.lastIndex) {
            val a = kfs[i]
            val b = kfs[i + 1]
            if (localTimeMs in a.timeOffsetMs..b.timeOffsetMs) {
                val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L).toFloat()
                val rawT = ((localTimeMs - a.timeOffsetMs) / span).coerceIn(0f, 1f)
                // Smooth ease-in-out
                val t = rawT * rawT * (3f - 2f * rawT)
                val w = lerp(a.width, b.width, t) + mask.expansion * 2f
                val h = lerp(a.height, b.height, t) + mask.expansion * 2f
                return EvaluatedMaskGeometry(
                    centerX = lerp(a.centerX, b.centerX, t),
                    centerY = lerp(a.centerY, b.centerY, t),
                    width = w.coerceIn(0.04f, 1.8f),
                    height = h.coerceIn(0.04f, 1.8f),
                    rotationDegrees = lerp(a.rotationDegrees, b.rotationDegrees, t)
                )
            }
        }
        return EvaluatedMaskGeometry(
            centerX = mask.centerX,
            centerY = mask.centerY,
            width = (mask.width + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
            height = (mask.height + mask.expansion * 2f).coerceIn(0.04f, 1.8f),
            rotationDegrees = mask.rotationDegrees
        )
    }

    /**
     * Pure geometric point-in-mask check (normalized 0..1 coordinates), used by unit tests
     * and hit-testing.
     */
    fun isPointInsideMask(
        normX: Float,
        normY: Float,
        mask: MaskSettings,
        localTimeMs: Long = 0L
    ): Boolean {
        if (!mask.isActive) return true
        val geom = evaluateGeometry(mask, localTimeMs)
        val insideRaw = when (mask.shape) {
            MaskShape.NONE -> true
            MaskShape.RECTANGLE -> {
                val (lx, ly) = rotatePointAround(normX, normY, geom.centerX, geom.centerY, -geom.rotationDegrees)
                val halfW = geom.width / 2f
                val halfH = geom.height / 2f
                lx in (geom.centerX - halfW)..(geom.centerX + halfW) &&
                    ly in (geom.centerY - halfH)..(geom.centerY + halfH)
            }
            MaskShape.CIRCLE -> {
                val (lx, ly) = rotatePointAround(normX, normY, geom.centerX, geom.centerY, -geom.rotationDegrees)
                val rx = (geom.width / 2f).coerceAtLeast(0.01f)
                val ry = (geom.height / 2f).coerceAtLeast(0.01f)
                val dx = (lx - geom.centerX) / rx
                val dy = (ly - geom.centerY) / ry
                dx * dx + dy * dy <= 1f
            }
            MaskShape.LINEAR_SPLIT -> {
                val (_, ly) = rotatePointAround(normX, normY, geom.centerX, geom.centerY, -geom.rotationDegrees)
                ly <= geom.centerY
            }
            MaskShape.FREEHAND_PEN -> pointInPolygon(normX, normY, mask.penPoints)
        }
        return if (mask.invert) !insideRaw else insideRaw
    }

    fun buildAndroidMaskPath(
        mask: MaskSettings,
        widthPx: Float,
        heightPx: Float,
        localTimeMs: Long = 0L
    ): Path {
        val path = Path()
        val geom = evaluateGeometry(mask, localTimeMs)
        val cx = geom.centerX * widthPx
        val cy = geom.centerY * heightPx
        val mw = geom.width * widthPx
        val mh = geom.height * heightPx

        when (mask.shape) {
            MaskShape.NONE -> {
                path.addRect(0f, 0f, widthPx, heightPx, Path.Direction.CW)
            }
            MaskShape.RECTANGLE -> {
                val rect = RectF(cx - mw / 2f, cy - mh / 2f, cx + mw / 2f, cy + mh / 2f)
                val cr = (minOf(mw, mh) * mask.cornerRadius.coerceIn(0f, 0.5f))
                path.addRoundRect(rect, cr, cr, Path.Direction.CW)
                rotatePath(path, geom.rotationDegrees, cx, cy)
            }
            MaskShape.CIRCLE -> {
                val rect = RectF(cx - mw / 2f, cy - mh / 2f, cx + mw / 2f, cy + mh / 2f)
                path.addOval(rect, Path.Direction.CW)
                rotatePath(path, geom.rotationDegrees, cx, cy)
            }
            MaskShape.LINEAR_SPLIT -> {
                val diag = maxOf(widthPx, heightPx) * 2f
                val rect = RectF(cx - diag, cy - diag, cx + diag, cy)
                path.addRect(rect, Path.Direction.CW)
                rotatePath(path, geom.rotationDegrees, cx, cy)
            }
            MaskShape.FREEHAND_PEN -> {
                val pts = mask.penPoints
                if (pts.size >= 3) {
                    val first = pts[0]
                    val last = pts.last()
                    val startMidX = ((last.x + first.x) / 2f) * widthPx
                    val startMidY = ((last.y + first.y) / 2f) * heightPx
                    path.moveTo(startMidX, startMidY)
                    for (i in pts.indices) {
                        val curr = pts[i]
                        val next = pts[(i + 1) % pts.size]
                        val midX = ((curr.x + next.x) / 2f) * widthPx
                        val midY = ((curr.y + next.y) / 2f) * heightPx
                        path.quadTo(curr.x * widthPx, curr.y * heightPx, midX, midY)
                    }
                    path.close()
                }
            }
        }
        return path
    }

    fun applyMaskToBitmap(
        source: Bitmap,
        mask: MaskSettings,
        localTimeMs: Long = 0L
    ): Bitmap {
        if (!mask.isActive) return source
        val w = source.width
        val h = source.height
        val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        val maskPath = buildAndroidMaskPath(mask, w.toFloat(), h.toFloat(), localTimeMs)

        val feather = mask.featherPx.coerceIn(0f, 50f) * (minOf(w, h) / 360f).coerceAtLeast(1f)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            if (feather > 0.5f) {
                maskFilter = BlurMaskFilter(feather.coerceAtMost(80f), BlurMaskFilter.Blur.NORMAL)
            }
        }

        if (mask.invert) {
            maskCanvas.drawColor(0xFFFFFFFF.toInt())
            val clearPaint = Paint(maskPaint).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            maskCanvas.drawPath(maskPath, clearPaint)
        } else {
            maskCanvas.drawPath(maskPath, maskPaint)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)
        val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (mask.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        outCanvas.drawBitmap(source, 0f, 0f, srcPaint)
        val xferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        outCanvas.drawBitmap(maskBitmap, 0f, 0f, xferPaint)
        maskBitmap.recycle()
        return out
    }

    private fun rotatePath(path: Path, degrees: Float, px: Float, py: Float) {
        if (kotlin.math.abs(degrees) < 0.05f) return
        val m = android.graphics.Matrix()
        m.postRotate(degrees, px, py)
        path.transform(m)
    }

    private fun rotatePointAround(
        x: Float,
        y: Float,
        cx: Float,
        cy: Float,
        degrees: Float
    ): Pair<Float, Float> {
        val rad = degrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val dx = x - cx
        val dy = y - cy
        return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
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
            val intersects = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1e-6f) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
