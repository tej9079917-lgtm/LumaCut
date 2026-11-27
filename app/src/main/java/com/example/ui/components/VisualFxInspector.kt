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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.FxLayer
import com.example.model.FxShape
import com.example.model.FxType
import com.example.model.formatShortDuration
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
import com.example.ui.theme.TrackFxBg
import com.example.ui.theme.TrackFxViolet
import kotlin.math.roundToInt

@Composable
fun VisualFxInspector(
    fxLayers: List<FxLayer>,
    selectedFx: FxLayer?,
    onAddFx: (FxType, Boolean) -> Unit,
    onSelectFx: (String) -> Unit,
    onUpdateFx: ((FxLayer) -> FxLayer) -> Unit,
    onDeleteSelectedFx: () -> Unit,
    onClose: () -> Unit,
    compactDocked: Boolean = false
) {
    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("visual_fx_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compactDocked) Modifier else Modifier.heightIn(max = 340.dp))
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "FX Studio • Magnifier / Mosaic / Blur",
                onClose = onClose,
                trailingAction = {
                    if (selectedFx != null) {
                        IconButton(
                            onClick = onDeleteSelectedFx,
                            modifier = Modifier.size(34.dp).testTag("btn_delete_fx")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete FX layer",
                                tint = ErrorCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            // 1. Quick 1-Tap Add Magnifier, Mosaic, or Blur FX
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FxQuickAddButton(
                    icon = Icons.Default.Search,
                    label = "+ Magnifier",
                    onClick = { onAddFx(FxType.MAGNIFIER, false) },
                    modifier = Modifier.weight(1f).testTag("btn_add_fx_magnifier")
                )
                FxQuickAddButton(
                    icon = Icons.Default.GridOn,
                    label = "+ Mosaic",
                    onClick = { onAddFx(FxType.MOSAIC, false) },
                    modifier = Modifier.weight(1f).testTag("btn_add_fx_mosaic")
                )
                FxQuickAddButton(
                    icon = Icons.Default.BlurOn,
                    label = "+ Blur",
                    onClick = { onAddFx(FxType.BLUR, false) },
                    modifier = Modifier.weight(1f).testTag("btn_add_fx_blur")
                )
            }

            // 2. Existing FX Layers strip selector
            if (fxLayers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    fxLayers.sortedBy { it.startTimeMs }.forEachIndexed { idx, fx ->
                        val picked = fx.id == selectedFx?.id
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (picked) TrackFxBg else ObsidianBg)
                                .border(
                                    width = if (picked) 1.5.dp else 1.dp,
                                    color = if (picked) TrackFxViolet else BorderHairline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectFx(fx.id) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("fx_pill_$idx")
                        ) {
                            Text(
                                text = "${fx.type.displayName}${if (fx.fullFrame) " (Full)" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (picked) ChalkWhite else SilverMuted
                            )
                            Text(
                                text = "${formatShortDuration(fx.startTimeMs)}–${formatShortDuration(fx.endTimeMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrackFxViolet
                            )
                        }
                    }
                }
            }

            // 3. Active FX Layer Inspector Controls
            if (selectedFx != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Switch FX Type (Magnifier / Mosaic / Blur)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FxType.entries.forEach { fxType ->
                        val active = selectedFx.type == fxType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) AmberContainer else ActiveRail)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onUpdateFx { it.copy(type = fxType) } }
                                .padding(vertical = 7.dp)
                                .testTag("fx_type_${fxType.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fxType.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Placement Mode: Movable Spot Region vs Full-Clip Frame
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FxModeChip(
                        icon = Icons.Default.CropFree,
                        label = "Movable Spot",
                        selected = !selectedFx.fullFrame,
                        onClick = { onUpdateFx { it.copy(fullFrame = false) } },
                        modifier = Modifier.weight(1f).testTag("fx_mode_spot")
                    )
                    FxModeChip(
                        icon = Icons.Default.Fullscreen,
                        label = "Full Clip",
                        selected = selectedFx.fullFrame,
                        onClick = { onUpdateFx { it.copy(fullFrame = true) } },
                        modifier = Modifier.weight(1f).testTag("fx_mode_full")
                    )
                }

                if (!selectedFx.fullFrame) {
                    Spacer(modifier = Modifier.height(6.dp))
                    // Shape toggle (Circle vs Rounded Rect)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FxModeChip(
                            icon = Icons.Default.RadioButtonUnchecked,
                            label = "Circle Lens",
                            selected = selectedFx.shape == FxShape.CIRCLE,
                            onClick = { onUpdateFx { it.copy(shape = FxShape.CIRCLE) } },
                            modifier = Modifier.weight(1f).testTag("fx_shape_circle")
                        )
                        FxModeChip(
                            icon = Icons.Default.CropSquare,
                            label = "Rectangle",
                            selected = selectedFx.shape == FxShape.ROUNDED_RECT,
                            onClick = { onUpdateFx { it.copy(shape = FxShape.ROUNDED_RECT) } },
                            modifier = Modifier.weight(1f).testTag("fx_shape_rect")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Effect-specific primary parameter
                when (selectedFx.type) {
                    FxType.MAGNIFIER -> {
                        FxSliderRow(
                            label = "Zoom",
                            valueText = String.format("%.1fx", selectedFx.magnification),
                            value = selectedFx.magnification,
                            range = 1.2f..4.0f,
                            onValueChange = { v -> onUpdateFx { it.copy(magnification = v) } },
                            testTag = "slider_fx_magnification"
                        )
                        if (!selectedFx.fullFrame) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Glass Lens Rim", style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                                Switch(
                                    checked = selectedFx.showBorderRing,
                                    onCheckedChange = { onUpdateFx { fx -> fx.copy(showBorderRing = it) } },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = OnAmber,
                                        checkedTrackColor = CinemaAmber,
                                        uncheckedThumbColor = SilverMuted,
                                        uncheckedTrackColor = ActiveRail
                                    ),
                                    modifier = Modifier.testTag("switch_fx_lens_rim")
                                )
                            }
                        }
                    }
                    FxType.MOSAIC -> {
                        FxSliderRow(
                            label = "Block Size",
                            valueText = "${(selectedFx.intensity * 100).roundToInt()}%",
                            value = selectedFx.intensity,
                            range = 0.08f..1f,
                            onValueChange = { v -> onUpdateFx { it.copy(intensity = v) } },
                            testTag = "slider_fx_mosaic_size"
                        )
                    }
                    FxType.BLUR -> {
                        FxSliderRow(
                            label = "Blur Radius",
                            valueText = "${(selectedFx.intensity * 100).roundToInt()}%",
                            value = selectedFx.intensity,
                            range = 0.08f..1f,
                            onValueChange = { v -> onUpdateFx { it.copy(intensity = v) } },
                            testTag = "slider_fx_blur_radius"
                        )
                    }
                }

                FxSliderRow(
                    label = "Opacity",
                    valueText = "${(selectedFx.opacity * 100).roundToInt()}%",
                    value = selectedFx.opacity,
                    range = 0.1f..1f,
                    onValueChange = { v -> onUpdateFx { it.copy(opacity = v) } },
                    testTag = "slider_fx_opacity"
                )

                if (!selectedFx.fullFrame) {
                    FxSliderRow(
                        label = "Feather",
                        valueText = "${(selectedFx.feather * 100).roundToInt()}%",
                        value = selectedFx.feather,
                        range = 0f..0.28f,
                        onValueChange = { v -> onUpdateFx { it.copy(feather = v) } },
                        testTag = "slider_fx_feather"
                    )
                    FxSliderRow(
                        label = "Size W",
                        valueText = "${(selectedFx.width * 100).roundToInt()}%",
                        value = selectedFx.width,
                        range = 0.14f..0.95f,
                        onValueChange = { v -> onUpdateFx { it.copy(width = v) } },
                        testTag = "slider_fx_width"
                    )
                    FxSliderRow(
                        label = "Size H",
                        valueText = "${(selectedFx.height * 100).roundToInt()}%",
                        value = selectedFx.height,
                        range = 0.14f..0.95f,
                        onValueChange = { v -> onUpdateFx { it.copy(height = v) } },
                        testTag = "slider_fx_height"
                    )
                }

                FxSliderRow(
                    label = "Duration",
                    valueText = String.format("%.1fs", selectedFx.durationMs / 1000f),
                    value = selectedFx.durationMs.toFloat(),
                    range = 500f..15000f,
                    onValueChange = { v -> onUpdateFx { it.copy(durationMs = v.toLong()) } },
                    testTag = "slider_fx_duration"
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add a Magnifier lens, Mosaic censor block, or Gaussian Blur—drag it anywhere on the preview or switch to Full Clip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun FxQuickAddButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = TrackFxBg,
            contentColor = ChalkWhite
        ),
        modifier = modifier.height(38.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TrackFxViolet, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun FxModeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AmberContainer else ActiveRail)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) CinemaAmber else SilverMuted, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) CinemaAmber else ChalkWhite)
    }
}

@Composable
private fun FxSliderRow(
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
                thumbColor = TrackFxViolet,
                activeTrackColor = TrackFxViolet,
                inactiveTrackColor = ActiveRail
            ),
            modifier = Modifier.weight(1f).testTag(testTag)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = ChalkWhite,
            modifier = Modifier.width(46.dp)
        )
    }
}
