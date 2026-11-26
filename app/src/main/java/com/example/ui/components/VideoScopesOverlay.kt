package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.CubeLutParser
import com.example.model.FilterPreset
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SilverMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class VideoScopeMode(val label: String) {
    WAVEFORM("Waveform"),
    RGB_PARADE("RGB Parade")
}

/**
 * Downsampled RGB frame sample grid (`cols x rows`) ready for instant live scope evaluation.
 */
data class ScopeFrameGrid(
    val cols: Int,
    val rows: Int,
    val r: FloatArray,
    val g: FloatArray,
    val b: FloatArray
)

/**
 * Real-time DaVinci-style Video Scopes overlay (Luma Waveform & RGB Parade)
 * rendered over the preview corner so color wheels, primaries, curves, and `.cube` LUTs
 * visibly shift IRE traces in real time.
 */
@Composable
fun VideoScopesOverlay(
    clip: MainClip?,
    mode: VideoScopeMode,
    onToggleMode: (VideoScopeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rawGrid by remember { mutableStateOf<ScopeFrameGrid?>(null) }

    val sourcePath = clip?.thumbnailPath ?: if (clip?.mediaType == MainMediaType.IMAGE) clip.uri else null
    LaunchedEffect(sourcePath) {
        rawGrid = withContext(Dispatchers.IO) {
            decodeSampleGrid(context, sourcePath) ?: createNeutralReferenceGrid()
        }
    }

    val preset = clip?.filterPreset ?: FilterPreset.ORIGINAL
    val intensity = clip?.filterIntensity ?: 1f
    val adjustments = clip?.colorAdjustments ?: ColorAdjustments()

    // Evaluate graded pixels whenever grade parameters change
    val gradedGrid = remember(rawGrid, preset, intensity, adjustments) {
        val src = rawGrid ?: createNeutralReferenceGrid()
        val lut = CubeLutParser.loadCached(adjustments.cubeLutPath)
        val total = src.cols * src.rows
        val outR = FloatArray(total)
        val outG = FloatArray(total)
        val outB = FloatArray(total)
        for (i in 0 until total) {
            val (gr, gg, gb) = ColorMatrixEngine.applyFullGradeToRgb(
                rIn = src.r[i],
                gIn = src.g[i],
                bIn = src.b[i],
                preset = preset,
                intensity = intensity,
                adjustments = adjustments,
                lut = lut
            )
            outR[i] = gr
            outG[i] = gg
            outB[i] = gb
        }
        ScopeFrameGrid(src.cols, src.rows, outR, outG, outB)
    }

    Column(
        modifier = modifier
            .width(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianBg.copy(alpha = 0.88f))
            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .testTag("video_scopes_hud")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoScopeMode.entries.forEach { m ->
                val picked = m == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (picked) AmberContainer else ActiveRail)
                        .border(1.dp, if (picked) CinemaAmber else BorderHairline, RoundedCornerShape(5.dp))
                        .clickable { onToggleMode(m) }
                        .padding(vertical = 2.dp)
                        .testTag("scope_mode_${m.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = m.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (picked) CinemaAmber else SilverMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF060709))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // IRE graticule lines (0, 25, 50, 75, 100)
                val gratColor = Color.White.copy(alpha = 0.14f)
                for (step in 0..4) {
                    val y = size.height * (step / 4f)
                    drawLine(gratColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
                }

                val cols = gradedGrid.cols
                val rows = gradedGrid.rows
                val dotR = 1.15.dp.toPx()

                if (mode == VideoScopeMode.WAVEFORM) {
                    val colW = size.width / cols.coerceAtLeast(1)
                    for (c in 0 until cols) {
                        val x = (c + 0.5f) * colW
                        for (rIdx in 0 until rows) {
                            val idx = rIdx * cols + c
                            val pr = gradedGrid.r[idx]
                            val pg = gradedGrid.g[idx]
                            val pb = gradedGrid.b[idx]
                            val luma = (0.2126f * pr + 0.7152f * pg + 0.0722f * pb).coerceIn(0f, 1f)
                            val y = size.height * (1f - luma)
                            drawCircle(
                                color = Color(
                                    red = (0.35f + pr * 0.65f).coerceIn(0f, 1f),
                                    green = (0.45f + pg * 0.55f).coerceIn(0f, 1f),
                                    blue = (0.35f + pb * 0.65f).coerceIn(0f, 1f),
                                    alpha = 0.42f
                                ),
                                radius = dotR,
                                center = Offset(x, y)
                            )
                        }
                    }
                } else {
                    // RGB Parade: 3 side-by-side channel columns
                    val thirdW = size.width / 3f
                    drawLine(gratColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 0.8.dp.toPx())
                    drawLine(gratColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 0.8.dp.toPx())

                    val colW = thirdW / cols.coerceAtLeast(1)
                    val redDot = Color(0xFFEF4444).copy(alpha = 0.46f)
                    val greenDot = Color(0xFF22C55E).copy(alpha = 0.46f)
                    val blueDot = Color(0xFF3B82F6).copy(alpha = 0.46f)

                    for (c in 0 until cols) {
                        val localX = (c + 0.5f) * colW
                        for (rIdx in 0 until rows) {
                            val idx = rIdx * cols + c
                            drawCircle(redDot, dotR, Offset(localX, size.height * (1f - gradedGrid.r[idx])))
                            drawCircle(greenDot, dotR, Offset(thirdW + localX, size.height * (1f - gradedGrid.g[idx])))
                            drawCircle(blueDot, dotR, Offset(thirdW * 2f + localX, size.height * (1f - gradedGrid.b[idx])))
                        }
                    }
                }
            }
            Text(
                text = "100",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkWhite.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp)
            )
            Text(
                text = "0 IRE",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkWhite.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 3.dp)
            )
        }
    }
}

private fun decodeSampleGrid(context: Context, pathOrUri: String?): ScopeFrameGrid? {
    if (pathOrUri.isNullOrBlank()) return null
    val bmp = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
            val uri = Uri.parse(pathOrUri)
            if (uri.scheme == "file" && uri.path != null) {
                BitmapFactory.decodeFile(uri.path, opts)
            } else {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } else {
            val f = File(pathOrUri)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, opts) else null
        }
    }.getOrNull() ?: return null

    val cols = 32
    val rows = 18
    val scaled = Bitmap.createScaledBitmap(bmp, cols, rows, true)
    if (scaled !== bmp) bmp.recycle()
    val pixels = IntArray(cols * rows)
    scaled.getPixels(pixels, 0, cols, 0, 0, cols, rows)
    scaled.recycle()

    val r = FloatArray(pixels.size)
    val g = FloatArray(pixels.size)
    val b = FloatArray(pixels.size)
    for (i in pixels.indices) {
        val px = pixels[i]
        r[i] = ((px shr 16) and 0xFF) / 255f
        g[i] = ((px shr 8) and 0xFF) / 255f
        b[i] = (px and 0xFF) / 255f
    }
    return ScopeFrameGrid(cols, rows, r, g, b)
}

private fun createNeutralReferenceGrid(): ScopeFrameGrid {
    val cols = 32
    val rows = 18
    val r = FloatArray(cols * rows)
    val g = FloatArray(cols * rows)
    val b = FloatArray(cols * rows)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val idx = row * cols + col
            val ramp = col / (cols - 1f)
            val v = (0.12f + ramp * 0.76f + (row - rows / 2f) * 0.015f).coerceIn(0.05f, 0.95f)
            r[idx] = (v * 1.04f).coerceIn(0f, 1f)
            g[idx] = v
            b[idx] = (v * 0.96f).coerceIn(0f, 1f)
        }
    }
    return ScopeFrameGrid(cols, rows, r, g, b)
}
