package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ClipTransform
import com.example.model.MaskSettings
import com.example.model.MaskShape
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import kotlin.math.roundToInt

@Composable
fun MaskAndTransformInspector(
    targetLabel: String,
    mask: MaskSettings,
    showClipTransformActions: Boolean,
    clipTransform: ClipTransform?,
    onUpdateMask: (MaskSettings) -> Unit,
    onToggleMaskKeyframe: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onRotate90: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mask_transform_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 330.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Mask & Transform • $targetLabel",
                onClose = onClose,
                trailingAction = {
                    if (mask.shape != MaskShape.NONE) {
                        TextButton(
                            onClick = { onUpdateMask(MaskSettings()) },
                            modifier = Modifier.testTag("mask_reset_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset mask", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            // 1. Quick 1-Tap Flip & Rotate Clip Actions
            if (showClipTransformActions && clipTransform != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransformQuickPill(
                        icon = Icons.Default.Flip,
                        label = "Flip H",
                        isActive = clipTransform.flipHorizontal,
                        onClick = onFlipHorizontal,
                        modifier = Modifier.weight(1f).testTag("btn_flip_horizontal")
                    )
                    TransformQuickPill(
                        icon = Icons.Default.Flip,
                        label = "Flip V",
                        isActive = clipTransform.flipVertical,
                        iconRotation = 90f,
                        onClick = onFlipVertical,
                        modifier = Modifier.weight(1f).testTag("btn_flip_vertical")
                    )
                    TransformQuickPill(
                        icon = Icons.Default.Rotate90DegreesCw,
                        label = "Rotate 90°",
                        isActive = kotlin.math.abs(clipTransform.rotationDegrees) > 0.5f,
                        onClick = onRotate90,
                        modifier = Modifier.weight(1f).testTag("btn_rotate_90")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 2. Shape Mask Selector Chips
            val maskShapes = listOf(
                Triple(MaskShape.NONE, "None", Icons.Default.Refresh),
                Triple(MaskShape.RECTANGLE, "Rectangle", Icons.Default.CropSquare),
                Triple(MaskShape.CIRCLE, "Circle", Icons.Default.RadioButtonUnchecked),
                Triple(MaskShape.LINEAR_SPLIT, "Split", Icons.Default.VerticalSplit)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                maskShapes.forEach { (shape, label, icon) ->
                    val picked = mask.shape == shape
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onUpdateMask(mask.copy(shape = shape)) }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                            .testTag("mask_shape_${shape.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (picked) CinemaAmber else SilverMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                    }
                }
            }

            if (mask.shape != MaskShape.NONE) {
                Spacer(modifier = Modifier.height(8.dp))

                // Invert + Animated Mask Keyframe Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.InvertColors, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                        Text("Invert Mask", style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                        Switch(
                            checked = mask.invert,
                            onCheckedChange = { onUpdateMask(mask.copy(invert = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAmber,
                                checkedTrackColor = CinemaAmber,
                                uncheckedThumbColor = SilverMuted,
                                uncheckedTrackColor = ActiveRail
                            ),
                            modifier = Modifier.testTag("switch_mask_invert")
                        )
                    }

                    if (showClipTransformActions) {
                        FilledTonalButton(
                            onClick = onToggleMaskKeyframe,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AmberContainer,
                                contentColor = CinemaAmber
                            ),
                            modifier = Modifier.height(34.dp).testTag("btn_mask_keyframe")
                        ) {
                            Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Key (${mask.keyframes.size})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                MaskSliderRow(
                    label = "Center X",
                    valueText = "${(mask.centerX * 100).roundToInt()}%",
                    value = mask.centerX,
                    range = 0.05f..0.95f,
                    onValueChange = { onUpdateMask(mask.copy(centerX = it)) },
                    testTag = "slider_mask_cx"
                )
                MaskSliderRow(
                    label = "Center Y",
                    valueText = "${(mask.centerY * 100).roundToInt()}%",
                    value = mask.centerY,
                    range = 0.05f..0.95f,
                    onValueChange = { onUpdateMask(mask.copy(centerY = it)) },
                    testTag = "slider_mask_cy"
                )
                if (mask.shape != MaskShape.LINEAR_SPLIT) {
                    MaskSliderRow(
                        label = "Width",
                        valueText = "${(mask.width * 100).roundToInt()}%",
                        value = mask.width,
                        range = 0.08f..1.4f,
                        onValueChange = { onUpdateMask(mask.copy(width = it)) },
                        testTag = "slider_mask_w"
                    )
                    MaskSliderRow(
                        label = "Height",
                        valueText = "${(mask.height * 100).roundToInt()}%",
                        value = mask.height,
                        range = 0.08f..1.4f,
                        onValueChange = { onUpdateMask(mask.copy(height = it)) },
                        testTag = "slider_mask_h"
                    )
                }
                MaskSliderRow(
                    label = "Rotation",
                    valueText = "${mask.rotationDegrees.roundToInt()}°",
                    value = mask.rotationDegrees,
                    range = -180f..180f,
                    onValueChange = { onUpdateMask(mask.copy(rotationDegrees = it)) },
                    testTag = "slider_mask_rot"
                )
                if (mask.shape == MaskShape.RECTANGLE) {
                    MaskSliderRow(
                        label = "Corners",
                        valueText = "${(mask.cornerRadius * 200).roundToInt()}%",
                        value = mask.cornerRadius,
                        range = 0f..0.5f,
                        onValueChange = { onUpdateMask(mask.copy(cornerRadius = it)) },
                        testTag = "slider_mask_corner"
                    )
                }
                MaskSliderRow(
                    label = "Feather",
                    valueText = "${mask.featherPx.roundToInt()}px",
                    value = mask.featherPx,
                    range = 0f..45f,
                    onValueChange = { onUpdateMask(mask.copy(featherPx = it)) },
                    testTag = "slider_mask_feather"
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pick Rectangle, Circle, or Split to mask this layer with feathering and animated keyframes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun TransformQuickPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) AmberContainer else ActiveRail)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) CinemaAmber else ChalkWhite,
            modifier = Modifier
                .size(16.dp)
                .rotate(iconRotation)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) CinemaAmber else ChalkWhite
        )
    }
}

@Composable
private fun MaskSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SilverMuted,
            modifier = Modifier.width(74.dp)
        )
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
            color = ChalkWhite,
            modifier = Modifier.width(44.dp)
        )
    }
}
