package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.StudioFont
import java.io.File

/**
 * Renders Text and Image overlays to ARGB Bitmaps at export resolution so
 * Media3 BitmapOverlay reproduces custom fonts, strokes, pill backgrounds,
 * and rounded photo overlays accurately in the exported MP4.
 */
object OverlayBitmapRenderer {

    fun createTransparentPlaceholder(): Bitmap =
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    fun renderOverlayBitmap(
        context: Context,
        overlay: OverlayLayer,
        exportWidth: Int,
        exportHeight: Int,
        availableFonts: List<StudioFont>
    ): Bitmap {
        return when (overlay.type) {
            OverlayType.TEXT -> renderTextBitmap(
                context = context,
                overlay = overlay,
                exportWidth = exportWidth,
                availableFonts = availableFonts
            )
            OverlayType.IMAGE -> renderImageBitmap(context, overlay, exportWidth, exportHeight)
            OverlayType.VIDEO -> createTransparentPlaceholder()
        }
    }

    fun renderTextBitmap(
        context: Context,
        overlay: OverlayLayer,
        exportWidth: Int,
        availableFonts: List<StudioFont>,
        textOverride: String? = null,
        letterSpacingSp: Float = 0f
    ): Bitmap {
        val text = (textOverride ?: overlay.text).ifBlank { " " }
        val typeface = FontResolver.resolveTypeface(context, overlay.fontId, availableFonts)
        // Scale text size relative to a 360dp reference stage width
        val pixelScale = (exportWidth / 360f).coerceAtLeast(1.5f)
        val textSizePx = overlay.fontSizeSp * pixelScale
        val letterSpacingEm = if (overlay.fontSizeSp > 0f) (letterSpacingSp / overlay.fontSizeSp).coerceIn(0f, 0.8f) else 0f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            this.letterSpacing = letterSpacingEm
            color = overlay.textColorArgb.toInt()
            textAlign = Paint.Align.CENTER
            if (overlay.hasDropShadow) {
                setShadowLayer(
                    (textSizePx * 0.14f).coerceAtLeast(4f),
                    0f,
                    (textSizePx * 0.08f).coerceAtLeast(2f),
                    0xCC000000.toInt()
                )
            }
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            this.letterSpacing = letterSpacingEm
            color = overlay.strokeColorArgb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = (textSizePx * 0.08f).coerceAtLeast(3f)
            strokeJoin = Paint.Join.ROUND
            textAlign = Paint.Align.CENTER
        }

        val lines = text.split("\n")
        val fm = fillPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.15f
        val maxLineWidth = lines.maxOfOrNull { line ->
            val bounds = Rect()
            fillPaint.getTextBounds(line, 0, line.length, bounds)
            maxOf(bounds.width().toFloat(), fillPaint.measureText(line))
        } ?: textSizePx

        val padH = (24f * pixelScale).toInt()
        val padV = (16f * pixelScale).toInt()
        val bmpW = (maxLineWidth + padH * 2).toInt().coerceIn(32, exportWidth * 2)
        val bmpH = (lineHeight * lines.size + padV * 2).toInt().coerceIn(32, exportWidth * 2)

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (overlay.hasBackground) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlay.backgroundColorArgb.toInt()
                style = Paint.Style.FILL
            }
            val radius = 14f * pixelScale
            canvas.drawRoundRect(RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat()), radius, radius, bgPaint)
        }

        val centerX = bmpW / 2f
        var baselineY = padV - fm.ascent
        for (line in lines) {
            if (overlay.hasStroke) {
                canvas.drawText(line, centerX, baselineY, strokePaint)
            }
            canvas.drawText(line, centerX, baselineY, fillPaint)
            baselineY += lineHeight
        }
        return bitmap
    }

    private fun renderImageBitmap(
        context: Context,
        overlay: OverlayLayer,
        exportWidth: Int,
        exportHeight: Int
    ): Bitmap {
        val uriStr = overlay.imageUri ?: return createTransparentPlaceholder()
        val uri = Uri.parse(uriStr)

        val source = runCatching {
            val input = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            input?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return createTransparentPlaceholder()

        val maxSide = (minOf(exportWidth, exportHeight) * 0.48f).toInt().coerceAtLeast(200)
        val ratio = source.width.toFloat() / source.height.coerceAtLeast(1).toFloat()
        val targetW: Int
        val targetH: Int
        if (ratio >= 1f) {
            targetW = maxSide
            targetH = (maxSide / ratio).toInt().coerceAtLeast(32)
        } else {
            targetH = maxSide
            targetW = (maxSide * ratio).toInt().coerceAtLeast(32)
        }

        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (scaled !== source) source.recycle()

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val pixelScale = (exportWidth / 360f).coerceAtLeast(1.5f)
        if (overlay.cornerRadiusDp > 0.5f) {
            val cornerPx = overlay.cornerRadiusDp * pixelScale
            val rect = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())
            val clipPath = Path().apply { addRoundRect(rect, cornerPx, cornerPx, Path.Direction.CW) }
            canvas.clipPath(clipPath)
        }
        canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        scaled.recycle()
        return out
    }
}
