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
import com.example.model.CaptionClip
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

    suspend fun renderOverlayBitmapWithCutout(
        context: Context,
        overlay: OverlayLayer,
        exportWidth: Int,
        exportHeight: Int,
        availableFonts: List<StudioFont>
    ): Bitmap {
        var bmp = renderOverlayBitmap(context, overlay, exportWidth, exportHeight, availableFonts)
        if (overlay.type == OverlayType.IMAGE && overlay.aiCutout.enabled) {
            val cutout = SelfieCutoutEngine.applyCutoutToBitmap(context, bmp, overlay.aiCutout)
            if (cutout !== bmp) bmp.recycle()
            bmp = cutout
        }
        return bmp
    }

    fun renderOverlayBitmap(
        context: Context,
        overlay: OverlayLayer,
        exportWidth: Int,
        exportHeight: Int,
        availableFonts: List<StudioFont>
    ): Bitmap {
        val raw = when (overlay.type) {
            OverlayType.TEXT -> renderTextBitmap(
                context = context,
                overlay = overlay,
                exportWidth = exportWidth,
                availableFonts = availableFonts
            )
            OverlayType.IMAGE -> renderImageBitmap(context, overlay, exportWidth, exportHeight)
            OverlayType.VIDEO -> createTransparentPlaceholder()
        }
        if (overlay.mask.isActive && overlay.type != OverlayType.VIDEO) {
            val masked = MaskGeometryEvaluator.applyMaskToBitmap(raw, overlay.mask, timeOffsetMs = 0L)
            if (masked !== raw) raw.recycle()
            return masked
        }
        return raw
    }

    /**
     * Renders a styled karaoke [CaptionClip] bitmap at export resolution, highlighting
     * [activeWordIndex] (or rendering cleanly with uniform color when < 0).
     */
    fun renderCaptionBitmap(
        context: Context,
        caption: CaptionClip,
        exportWidth: Int,
        availableFonts: List<StudioFont>,
        activeWordIndex: Int = -1
    ): Bitmap {
        val text = caption.text.trim().ifBlank { " " }
        val typeface = FontResolver.resolveTypeface(context, caption.fontId, availableFonts)
        val pixelScale = (exportWidth / 360f).coerceAtLeast(1.5f)
        val textSizePx = caption.fontSizeSp.coerceIn(14f, 64f) * pixelScale

        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            color = caption.textColorArgb.toInt()
            textAlign = Paint.Align.LEFT
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx * 1.07f
            color = caption.highlightColorArgb.toInt()
            textAlign = Paint.Align.LEFT
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            color = caption.strokeColorArgb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = (caption.strokeWidthDp * pixelScale).coerceAtLeast(2f)
            strokeJoin = Paint.Join.ROUND
            textAlign = Paint.Align.LEFT
        }

        // Wrap words into lines that stay safely inside 86% of export width
        val maxContentW = (exportWidth * 0.84f).coerceAtLeast(240f)
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val spaceW = normalPaint.measureText(" ")
        val lines = mutableListOf<List<Pair<Int, String>>>()
        var currentLine = mutableListOf<Pair<Int, String>>()
        var currentLineW = 0f

        words.forEachIndexed { idx, w ->
            val wWidth = normalPaint.measureText(w)
            if (currentLine.isNotEmpty() && currentLineW + spaceW + wWidth > maxContentW) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentLineW = 0f
            }
            currentLine.add(idx to w)
            currentLineW += if (currentLine.size == 1) wWidth else (spaceW + wWidth)
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        if (lines.isEmpty()) lines.add(listOf(0 to " "))

        val fm = highlightPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.18f
        val measuredLineWidths = lines.map { line ->
            line.foldIndexed(0f) { i, acc, (wordIdx, word) ->
                val p = if (wordIdx == activeWordIndex) highlightPaint else normalPaint
                acc + p.measureText(word) + if (i > 0) spaceW else 0f
            }
        }
        val maxLineW = measuredLineWidths.maxOrNull() ?: textSizePx
        val padH = (18f * pixelScale).toInt()
        val padV = (12f * pixelScale).toInt()
        val bmpW = (maxLineW + padH * 2).toInt().coerceIn(48, (exportWidth * 0.96f).toInt().coerceAtLeast(64))
        val bmpH = (lineHeight * lines.size + padV * 2).toInt().coerceIn(48, exportWidth)

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (caption.hasBackgroundPill) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = caption.backgroundPillArgb.toInt()
                style = Paint.Style.FILL
            }
            val radius = 14f * pixelScale
            canvas.drawRoundRect(RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat()), radius, radius, bgPaint)
        }

        var baselineY = padV - fm.ascent
        lines.forEachIndexed { lineIdx, lineWords ->
            val lineW = measuredLineWidths[lineIdx]
            var cursorX = (bmpW - lineW) / 2f
            lineWords.forEachIndexed { i, (wordIdx, word) ->
                if (i > 0) cursorX += spaceW
                val isActive = wordIdx == activeWordIndex
                val fillP = if (isActive) highlightPaint else normalPaint
                if (caption.strokeWidthDp > 0.2f) {
                    strokePaint.textSize = fillP.textSize
                    canvas.drawText(word, cursorX, baselineY, strokePaint)
                }
                canvas.drawText(word, cursorX, baselineY, fillP)
                cursorX += fillP.measureText(word)
            }
            baselineY += lineHeight
        }
        return bitmap
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

        val croppedSource = if (overlay.crop.isActive) {
            val cLeft = (source.width * overlay.crop.left.coerceIn(0f, 0.45f)).toInt()
            val cTop = (source.height * overlay.crop.top.coerceIn(0f, 0.45f)).toInt()
            val cRight = (source.width * overlay.crop.right.coerceIn(0f, 0.45f)).toInt()
            val cBottom = (source.height * overlay.crop.bottom.coerceIn(0f, 0.45f)).toInt()
            val cW = (source.width - cLeft - cRight).coerceAtLeast(16)
            val cH = (source.height - cTop - cBottom).coerceAtLeast(16)
            val sub = Bitmap.createBitmap(source, cLeft, cTop, cW, cH)
            if (sub !== source) source.recycle()
            sub
        } else {
            source
        }

        val maxSide = (minOf(exportWidth, exportHeight) * if (overlay.fullFrameFill) 0.96f else 0.48f)
            .toInt()
            .coerceAtLeast(200)
        val ratio = croppedSource.width.toFloat() / croppedSource.height.coerceAtLeast(1).toFloat()
        val targetW: Int
        val targetH: Int
        if (ratio >= 1f) {
            targetW = maxSide
            targetH = (maxSide / ratio).toInt().coerceAtLeast(32)
        } else {
            targetH = maxSide
            targetW = (maxSide * ratio).toInt().coerceAtLeast(32)
        }

        val scaled = Bitmap.createScaledBitmap(croppedSource, targetW, targetH, true)
        if (scaled !== croppedSource) croppedSource.recycle()

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val pixelScale = (exportWidth / 360f).coerceAtLeast(1.5f)
        if (overlay.cornerRadiusDp > 0.5f) {
            val cornerPx = overlay.cornerRadiusDp * pixelScale
            val rect = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())
            val clipPath = Path().apply { addRoundRect(rect, cornerPx, cornerPx, Path.Direction.CW) }
            canvas.clipPath(clipPath)
        }
        if (overlay.flipHorizontal || overlay.flipVertical) {
            canvas.scale(
                if (overlay.flipHorizontal) -1f else 1f,
                if (overlay.flipVertical) -1f else 1f,
                targetW / 2f,
                targetH / 2f
            )
        }
        canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        scaled.recycle()
        return out
    }
}
