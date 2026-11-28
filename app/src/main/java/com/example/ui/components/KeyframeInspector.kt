package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ClipTransform
import com.example.model.KeyframeEasing
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SlateDim
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MotionPreset(val label: String) {
    KEN_BURNS_IN("Ken Burns In"),
    PAN_LEFT_RIGHT("Pan L→R"),
    SPIN_POP("Spin Pop")
}

@Composable
fun KeyframeInspector(
    clip: MainClip?,
    clipStartMs: Long,
    playheadMs: Long,
    onUpdateTransformAtPlayhead: (ClipTransform) -> Unit,
    onAddOrRemoveKeyframeAtPlayhead: () -> Unit,
    onUpdateActiveEasing: (KeyframeEasing) -> Unit,
    onJumpToKeyframe: (Long) -> Unit,
    onJumpPrevKeyframe: () -> Unit,
    onJumpNextKeyframe: () -> Unit,
    onApplyPreset: (MotionPreset) -> Unit,
    onResetAllMotion: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keyframe_inspector")
    ) {
        if (clip == null) {
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                InspectorHeader(title = "Motion & Keyframes", onClose = onClose)
                Text(
                    text = "Select a video or photo clip on the timeline to animate position, scale, and rotation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            return@Surface
        }

        val localMs = (playheadMs - clipStartMs).coerceIn(0L, clip.durationMs)
        val sortedKeyframes = remember(clip.keyframes) { clip.keyframes.sortedBy { it.timeOffsetMs } }
        val activeKeyframe = remember(clip.keyframes, localMs) {
            KeyframeEvaluator.findKeyframeNear(clip, localMs, toleranceMs = 90L)
        }
        val currentTransform = remember(clip, localMs) {
            KeyframeEvaluator.evaluate(clip, localMs)
        }
        val hasPrevKf = remember(clip.keyframes, localMs) {
            KeyframeEvaluator.previousKeyframeTimeMs(clip, localMs) != null
        }
        val hasNextKf = remember(clip.keyframes, localMs) {
            KeyframeEvaluator.nextKeyframeTimeMs(clip, localMs) != null
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Motion • ${clip.displayName}",
                onClose = onClose,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onResetAllMotion,
                            enabled = clip.keyframes.isNotEmpty() || !clip.baseTransform.isIdentity,
                            modifier = Modifier.testTag("kf_reset_all_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset motion", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            // 1. Keyframe Transport & Diamond Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onJumpPrevKeyframe,
                    enabled = hasPrevKf,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ActiveRail)
                        .testTag("kf_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous keyframe",
                        tint = if (hasPrevKf) CinemaAmber else SlateDim,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Primary Add / Remove Keyframe Diamond Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (activeKeyframe != null) ErrorCrimson.copy(alpha = 0.22f) else CinemaAmber)
                        .border(
                            width = 1.dp,
                            color = if (activeKeyframe != null) ErrorCrimson else CinemaAmber,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable { onAddOrRemoveKeyframeAtPlayhead() }
                        .padding(horizontal = 10.dp)
                        .testTag("kf_toggle_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (activeKeyframe != null) Icons.Default.DeleteOutline else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (activeKeyframe != null) ErrorCrimson else OnAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (activeKeyframe != null) {
                            "Remove Keyframe (${String.format("%.2fs", localMs / 1000f)})"
                        } else {
                            "Add Keyframe (${String.format("%.2fs", localMs / 1000f)})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeKeyframe != null) ErrorCrimson else OnAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onJumpNextKeyframe,
                    enabled = hasNextKf,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ActiveRail)
                        .testTag("kf_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next keyframe",
                        tint = if (hasNextKf) CinemaAmber else SlateDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Keyframe Timeline Pills OR Quick Motion Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sortedKeyframes.isNotEmpty()) {
                    sortedKeyframes.forEachIndexed { idx, kf ->
                        val isAtKf = activeKeyframe?.id == kf.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isAtKf) AmberContainer else ObsidianBg)
                                .border(
                                    1.dp,
                                    if (isAtKf) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(7.dp)
                                )
                                .clickable { onJumpToKeyframe(clipStartMs + kf.timeOffsetMs) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("kf_pill_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .rotate(45f)
                                    .background(CinemaAmber)
                            )
                            Text(
                                text = String.format("%.2fs", kf.timeOffsetMs / 1000f),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAtKf) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Quick Motion Presets
                MotionPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(ActiveRail)
                            .border(1.dp, BorderHairline, RoundedCornerShape(7.dp))
                            .clickable { onApplyPreset(preset) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("kf_preset_${preset.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = null,
                            tint = CinemaAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = ChalkWhite
                        )
                    }
                }
            }

            // 3. Interactive Motion Curve Editor & Easing Selector (visible when clip has keyframes)
            if (sortedKeyframes.isNotEmpty()) {
                val activeEasing = activeKeyframe?.easing
                    ?: sortedKeyframes.lastOrNull { it.timeOffsetMs <= localMs }?.easing
                    ?: KeyframeEasing.EASE_IN_OUT
                val segProgress = remember(sortedKeyframes, localMs) {
                    if (sortedKeyframes.size < 2) {
                        0.5f
                    } else {
                        val idx = sortedKeyframes.indexOfLast { it.timeOffsetMs <= localMs }.coerceIn(0, sortedKeyframes.lastIndex)
                        if (idx >= sortedKeyframes.lastIndex) 1f
                        else {
                            val k0 = sortedKeyframes[idx]
                            val k1 = sortedKeyframes[idx + 1]
                            val span = (k1.timeOffsetMs - k0.timeOffsetMs).coerceAtLeast(1L)
                            ((localMs - k0.timeOffsetMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                MotionCurveEditorCard(
                    easing = activeEasing,
                    segmentProgress = segProgress,
                    onSelectEasing = onUpdateActiveEasing
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Precision Transform Sliders (Position X, Position Y, Scale, Rotation)
            KeyframeSliderRow(
                icon = Icons.Default.OpenWith,
                label = "Pos X",
                valueText = String.format("%+d%%", (currentTransform.offsetX * 100f).roundToInt()),
                value = currentTransform.offsetX,
                range = -1f..1f,
                isModified = abs(currentTransform.offsetX) >= 0.005f,
                onValueChange = { onUpdateTransformAtPlayhead(currentTransform.copy(offsetX = it)) },
                onResetSingle = { onUpdateTransformAtPlayhead(currentTransform.copy(offsetX = 0f)) },
                testTag = "slider_kf_pos_x"
            )
            KeyframeSliderRow(
                icon = Icons.Default.OpenWith,
                label = "Pos Y",
                valueText = String.format("%+d%%", (currentTransform.offsetY * 100f).roundToInt()),
                value = currentTransform.offsetY,
                range = -1f..1f,
                isModified = abs(currentTransform.offsetY) >= 0.005f,
                onValueChange = { onUpdateTransformAtPlayhead(currentTransform.copy(offsetY = it)) },
                onResetSingle = { onUpdateTransformAtPlayhead(currentTransform.copy(offsetY = 0f)) },
                testTag = "slider_kf_pos_y"
            )
            KeyframeSliderRow(
                icon = Icons.Default.ZoomOutMap,
                label = "Scale",
                valueText = "${(currentTransform.scale * 100f).roundToInt()}%",
                value = currentTransform.scale,
                range = 0.25f..3.0f,
                isModified = abs(currentTransform.scale - 1f) >= 0.01f,
                onValueChange = { onUpdateTransformAtPlayhead(currentTransform.copy(scale = it)) },
                onResetSingle = { onUpdateTransformAtPlayhead(currentTransform.copy(scale = 1f)) },
                testTag = "slider_kf_scale"
            )
            KeyframeSliderRow(
                icon = Icons.Default.RotateRight,
                label = "Rotate",
                valueText = String.format("%+d°", currentTransform.rotationDegrees.roundToInt()),
                value = currentTransform.rotationDegrees,
                range = -180f..180f,
                isModified = abs(currentTransform.rotationDegrees) >= 0.5f,
                onValueChange = { onUpdateTransformAtPlayhead(currentTransform.copy(rotationDegrees = it)) },
                onResetSingle = { onUpdateTransformAtPlayhead(currentTransform.copy(rotationDegrees = 0f)) },
                testTag = "slider_kf_rotation"
            )
            KeyframeSliderRow(
                icon = Icons.Default.Opacity,
                label = "Opacity",
                valueText = "${(currentTransform.opacity * 100f).roundToInt()}%",
                value = currentTransform.opacity,
                range = 0f..1f,
                isModified = abs(currentTransform.opacity - 1f) >= 0.01f,
                onValueChange = { onUpdateTransformAtPlayhead(currentTransform.copy(opacity = it)) },
                onResetSingle = { onUpdateTransformAtPlayhead(currentTransform.copy(opacity = 1f)) },
                testTag = "slider_kf_opacity"
            )
        }
    }
}

@Composable
private fun KeyframeSliderRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isModified: Boolean,
    onValueChange: (Float) -> Unit,
    onResetSingle: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(86.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onResetSingle() }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isModified) CinemaAmber else SilverMuted,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isModified) ChalkWhite else SilverMuted,
                maxLines = 1
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CinemaAmber,
                activeTrackColor = CinemaAmber,
                inactiveTrackColor = ActiveRail
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = if (isModified) CinemaAmber else ChalkWhite,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun MotionCurveEditorCard(
    easing: KeyframeEasing,
    segmentProgress: Float,
    onSelectEasing: (KeyframeEasing) -> Unit
) {
    val curveSamples = remember(easing) { KeyframeEvaluator.sampleCurve(easing, steps = 44) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianBg)
            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("kf_curve_editor_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Curve Editor • ${easing.displayName}",
                style = MaterialTheme.typography.labelMedium,
                color = CinemaAmber
            )
            Text(
                text = "Velocity Graph",
                style = MaterialTheme.typography.labelSmall,
                color = SilverMuted
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(InspectorCharcoal)
                .testTag("kf_curve_canvas")
        ) {
            val padX = 14f
            val padY = 12f
            val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
            val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

            // Subtle reference grid
            for (i in 0..4) {
                val gx = padX + plotW * (i / 4f)
                val gy = padY + plotH * (i / 4f)
                drawLine(
                    color = BorderHairline.copy(alpha = 0.55f),
                    start = Offset(gx, padY),
                    end = Offset(gx, padY + plotH),
                    strokeWidth = 1f
                )
                drawLine(
                    color = BorderHairline.copy(alpha = 0.55f),
                    start = Offset(padX, gy),
                    end = Offset(padX + plotW, gy),
                    strokeWidth = 1f
                )
            }

            // Linear reference diagonal
            drawLine(
                color = SlateDim.copy(alpha = 0.45f),
                start = Offset(padX, padY + plotH),
                end = Offset(padX + plotW, padY),
                strokeWidth = 1.5f
            )

            // Plotted easing curve
            val path = Path()
            curveSamples.forEachIndexed { idx, (tx, ty) ->
                val clampedY = ty.coerceIn(-0.15f, 1.15f)
                val px = padX + tx * plotW
                val py = padY + plotH - clampedY * plotH
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = CinemaAmber,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )

            // Live playhead dot along the active keyframe curve segment
            val liveY = KeyframeEvaluator.applyEasing(segmentProgress, easing).coerceIn(-0.15f, 1.15f)
            val dotCenter = Offset(
                x = padX + segmentProgress.coerceIn(0f, 1f) * plotW,
                y = padY + plotH - liveY * plotH
            )
            drawCircle(color = ChalkWhite, radius = 6.5f, center = dotCenter)
            drawCircle(color = CinemaAmber, radius = 4f, center = dotCenter)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyframeEasing.entries.forEach { preset ->
                val picked = easing == preset
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (picked) AmberContainer else ActiveRail)
                        .border(
                            1.dp,
                            if (picked) CinemaAmber else BorderHairline,
                            CircleShape
                        )
                        .clickable { onSelectEasing(preset) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("kf_easing_${preset.name.lowercase()}")
                ) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (picked) CinemaAmber else SilverMuted
                    )
                }
            }
        }
    }
}
