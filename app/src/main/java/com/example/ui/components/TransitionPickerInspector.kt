package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MainClip
import com.example.model.TransitionCategory
import com.example.model.TransitionSpec
import com.example.model.TransitionType
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SlateDim
import com.example.ui.theme.TrackVideoBlue

data class ClipSeamInfo(
    val seamIndex: Int,
    val fromClip: MainClip,
    val toClip: MainClip
)

@Composable
fun TransitionInspector(
    seams: List<ClipSeamInfo>,
    selectedAfterClipId: String?,
    spec: TransitionSpec,
    onSelectSeam: (String) -> Unit,
    onUpdate: (TransitionType, Long) -> Unit,
    onPreviewSeam: () -> Unit,
    onApplyAll: () -> Unit,
    onClose: () -> Unit
) {
    var activeCategory by remember { mutableStateOf(TransitionCategory.ALL) }
    val filteredTypes = remember(activeCategory) {
        if (activeCategory == TransitionCategory.ALL) {
            TransitionType.entries
        } else {
            listOf(TransitionType.NONE) + TransitionType.entries.filter {
                it != TransitionType.NONE && it.category == activeCategory
            }
        }
    }

    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transition_picker_inspector")
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            InspectorHeader(
                title = "Transitions",
                onClose = onClose,
                trailingAction = {
                    if (seams.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (spec.type != TransitionType.NONE) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CinemaAmber)
                                        .clickable { onPreviewSeam() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("transition_preview_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Preview transition",
                                        tint = OnAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Preview",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnAmber
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (seams.size > 1) {
                                TextButton(
                                    onClick = onApplyAll,
                                    modifier = Modifier.testTag("transition_apply_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = CinemaAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("All Cuts", style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                                }
                            }
                        }
                    }
                }
            )

            if (seams.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = null,
                        tint = CinemaAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "No Clip Cuts Available Yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = ChalkWhite
                        )
                        Text(
                            text = "Add a second clip or split a clip at the playhead to place transitions between cuts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMuted
                        )
                    }
                }
                return@Column
            }

            // 1. Cut Seam Selector Strip (Jump directly between any two adjacent clips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                seams.forEach { seam ->
                    val isPicked = seam.fromClip.id == selectedAfterClipId
                    val hasEffect = seam.fromClip.transitionToNext.type != TransitionType.NONE
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPicked) AmberContainer else ObsidianBg)
                            .border(
                                width = 1.dp,
                                color = if (isPicked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectSeam(seam.fromClip.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("transition_seam_${seam.seamIndex}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (hasEffect) CinemaAmber else SlateDim)
                        )
                        Text(
                            text = "Cut ${seam.seamIndex + 1}: ${seam.fromClip.transitionToNext.type.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPicked) CinemaAmber else ChalkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Category Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransitionCategory.entries.forEach { category ->
                    val isCatActive = activeCategory == category
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isCatActive) CinemaAmber.copy(alpha = 0.20f) else ActiveRail)
                            .border(
                                width = 1.dp,
                                color = if (isCatActive) CinemaAmber else BorderHairline,
                                shape = CircleShape
                            )
                            .clickable { activeCategory = category }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("transition_cat_${category.name.lowercase()}")
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCatActive) CinemaAmber else SilverMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Visual Transition Effect Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                filteredTypes.forEach { type ->
                    val selected = spec.type == type
                    TransitionVisualCard(
                        type = type,
                        selected = selected,
                        onClick = { onUpdate(type, spec.durationMs) }
                    )
                }
            }

            // 4. Duration Presets + Precision Slider
            if (spec.type != TransitionType.NONE) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelMedium,
                        color = SilverMuted
                    )
                    listOf(300L, 600L, 1000L, 1500L).forEach { presetMs ->
                        val isPreset = kotlin.math.abs(spec.durationMs - presetMs) < 40L
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPreset) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (isPreset) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdate(spec.type, presetMs) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("transition_dur_${presetMs}")
                        ) {
                            Text(
                                text = String.format("%.1fs", presetMs / 1000f),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPreset) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = String.format("%.2fs", spec.durationMs / 1000f),
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaAmber
                    )
                }
                Slider(
                    value = spec.durationMs.toFloat().coerceIn(200f, 2000f),
                    onValueChange = { onUpdate(spec.type, it.toLong()) },
                    valueRange = 200f..2000f,
                    colors = SliderDefaults.colors(
                        thumbColor = CinemaAmber,
                        activeTrackColor = CinemaAmber,
                        inactiveTrackColor = ActiveRail
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("transition_duration_slider")
                )
            }
        }
    }
}

@Composable
private fun TransitionVisualCard(
    type: TransitionType,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(82.dp)
            .defaultMinSize(minHeight = 78.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AmberContainer else ActiveRail)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(6.dp)
            .testTag("transition_${type.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ObsidianBg)
        ) {
            TransitionDiagramCanvas(
                type = type,
                isSelected = selected,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = type.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CinemaAmber else ChalkWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransitionDiagramCanvas(
    type: TransitionType,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isSelected) CinemaAmber else TrackVideoBlue
    val secondary = if (isSelected) CinemaAmber.copy(alpha = 0.45f) else SilverMuted.copy(alpha = 0.5f)
    Canvas(modifier = modifier.padding(5.dp)) {
        val w = size.width
        val h = size.height
        val cr = CornerRadius(4.dp.toPx())
        when (type) {
            TransitionType.NONE -> {
                // Hard cut: two adjacent crisp frames separated by a cut line
                drawRoundRect(
                    color = secondary.copy(alpha = 0.35f),
                    topLeft = Offset(0f, h * 0.12f),
                    size = Size(w * 0.44f, h * 0.76f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.45f),
                    topLeft = Offset(w * 0.56f, h * 0.12f),
                    size = Size(w * 0.44f, h * 0.76f),
                    cornerRadius = cr
                )
                drawLine(
                    color = ChalkWhite.copy(alpha = 0.7f),
                    start = Offset(w * 0.5f, 0f),
                    end = Offset(w * 0.5f, h),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            TransitionType.CROSSFADE -> {
                // Dissolve: smooth overlapping translucent rectangles + gradient blend
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(TrackVideoBlue.copy(alpha = 0.85f), Color.Transparent)),
                    topLeft = Offset(0f, h * 0.10f),
                    size = Size(w * 0.70f, h * 0.80f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.9f))),
                    topLeft = Offset(w * 0.30f, h * 0.10f),
                    size = Size(w * 0.70f, h * 0.80f),
                    cornerRadius = cr
                )
            }
            TransitionType.DIP_TO_BLACK -> {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.75f), Color.Black, accent.copy(alpha = 0.75f))
                    ),
                    topLeft = Offset(0f, h * 0.12f),
                    size = Size(w, h * 0.76f),
                    cornerRadius = cr
                )
            }
            TransitionType.DIP_TO_WHITE -> {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.7f), Color.White, accent.copy(alpha = 0.7f))
                    ),
                    topLeft = Offset(0f, h * 0.12f),
                    size = Size(w, h * 0.76f),
                    cornerRadius = cr
                )
            }
            TransitionType.WIPE_RIGHT -> {
                drawRoundRect(
                    color = secondary.copy(alpha = 0.25f),
                    topLeft = Offset(0f, h * 0.10f),
                    size = Size(w, h * 0.80f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.75f),
                    topLeft = Offset(0f, h * 0.10f),
                    size = Size(w * 0.58f, h * 0.80f),
                    cornerRadius = cr
                )
                drawLine(
                    color = ChalkWhite,
                    start = Offset(w * 0.58f, h * 0.04f),
                    end = Offset(w * 0.58f, h * 0.96f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Small rightward chevron
                val p = Path().apply {
                    moveTo(w * 0.68f, h * 0.34f)
                    lineTo(w * 0.82f, h * 0.5f)
                    lineTo(w * 0.68f, h * 0.66f)
                }
                drawPath(p, color = ChalkWhite, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            TransitionType.WIPE_LEFT -> {
                drawRoundRect(
                    color = secondary.copy(alpha = 0.25f),
                    topLeft = Offset(0f, h * 0.10f),
                    size = Size(w, h * 0.80f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.75f),
                    topLeft = Offset(w * 0.42f, h * 0.10f),
                    size = Size(w * 0.58f, h * 0.80f),
                    cornerRadius = cr
                )
                drawLine(
                    color = ChalkWhite,
                    start = Offset(w * 0.42f, h * 0.04f),
                    end = Offset(w * 0.42f, h * 0.96f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val p = Path().apply {
                    moveTo(w * 0.32f, h * 0.34f)
                    lineTo(w * 0.18f, h * 0.5f)
                    lineTo(w * 0.32f, h * 0.66f)
                }
                drawPath(p, color = ChalkWhite, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            TransitionType.SLIDE_LEFT -> {
                drawRoundRect(
                    color = secondary.copy(alpha = 0.3f),
                    topLeft = Offset(0f, h * 0.16f),
                    size = Size(w * 0.5f, h * 0.68f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.82f),
                    topLeft = Offset(w * 0.38f, h * 0.10f),
                    size = Size(w * 0.62f, h * 0.80f),
                    cornerRadius = cr
                )
                drawLine(
                    color = OnAmber,
                    start = Offset(w * 0.80f, h * 0.5f),
                    end = Offset(w * 0.52f, h * 0.5f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            TransitionType.SLIDE_RIGHT -> {
                drawRoundRect(
                    color = secondary.copy(alpha = 0.3f),
                    topLeft = Offset(w * 0.5f, h * 0.16f),
                    size = Size(w * 0.5f, h * 0.68f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.82f),
                    topLeft = Offset(0f, h * 0.10f),
                    size = Size(w * 0.62f, h * 0.80f),
                    cornerRadius = cr
                )
                drawLine(
                    color = OnAmber,
                    start = Offset(w * 0.20f, h * 0.5f),
                    end = Offset(w * 0.48f, h * 0.5f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            TransitionType.ZOOM_IN -> {
                drawRoundRect(
                    color = secondary.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.04f, h * 0.06f),
                    size = Size(w * 0.92f, h * 0.88f),
                    cornerRadius = cr,
                    style = Stroke(width = 1.3.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.8f),
                    topLeft = Offset(w * 0.24f, h * 0.22f),
                    size = Size(w * 0.52f, h * 0.56f),
                    cornerRadius = cr
                )
            }
        }
    }
}
