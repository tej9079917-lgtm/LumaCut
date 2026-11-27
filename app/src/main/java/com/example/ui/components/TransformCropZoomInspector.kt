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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.media.VisualFxMath
import com.example.model.ClipTransform
import com.example.model.CropRect
import com.example.model.PanZoomSpec
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

/**
 * Unified KineMaster-style Transform, Crop, Pan & Zoom, Mirror, Flip Full & Opacity inspector.
 * Works on both Main Clips (with Pan & Zoom) and Overlays (Position, Size, Rotate, Mirror, Flip Full, Crop, Opacity).
 */
@Composable
fun TransformCropZoomInspector(
    targetLabel: String,
    stageAspectRatio: Float,
    transform: ClipTransform,
    panZoom: PanZoomSpec?,
    initialSubTab: Int = 0, // 0 = Transform, 1 = Crop, 2 = Pan & Zoom
    onUpdateTransform: (ClipTransform) -> Unit,
    onUpdatePanZoom: ((PanZoomSpec) -> Unit)? = null,
    onResetAll: () -> Unit,
    onClose: () -> Unit,
    compactDocked: Boolean = false
) {
    var activeSubTab by remember(initialSubTab, panZoom != null) {
        mutableIntStateOf(if (initialSubTab == 2 && panZoom == null) 0 else initialSubTab)
    }

    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transform_crop_zoom_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compactDocked) Modifier else Modifier.heightIn(max = 350.dp))
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Transform & Crop • $targetLabel",
                onClose = onClose,
                trailingAction = {
                    TextButton(
                        onClick = onResetAll,
                        modifier = Modifier.testTag("btn_transform_reset")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset geometry", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            // Sub-tab bar: Transform | Crop | Pan & Zoom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SubTabPill(
                    icon = Icons.Default.Transform,
                    label = "Transform",
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    modifier = Modifier.weight(1f).testTag("subtab_transform")
                )
                SubTabPill(
                    icon = Icons.Default.Crop,
                    label = "Crop",
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    modifier = Modifier.weight(1f).testTag("subtab_crop")
                )
                if (panZoom != null && onUpdatePanZoom != null) {
                    SubTabPill(
                        icon = Icons.Default.ZoomIn,
                        label = "Pan & Zoom",
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        modifier = Modifier.weight(1f).testTag("subtab_pan_zoom")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (activeSubTab) {
                0 -> {
                    // Quick 1-Tap Mirror, Flip V, Flip Full, Rotate ±90°
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        GeoActionPill(
                            icon = Icons.Default.Flip,
                            label = "Mirror",
                            active = transform.flipHorizontal,
                            onClick = { onUpdateTransform(transform.copy(flipHorizontal = !transform.flipHorizontal)) },
                            testTag = "btn_geo_mirror"
                        )
                        GeoActionPill(
                            icon = Icons.Default.Flip,
                            label = "Flip V",
                            iconRotation = 90f,
                            active = transform.flipVertical,
                            onClick = { onUpdateTransform(transform.copy(flipVertical = !transform.flipVertical)) },
                            testTag = "btn_geo_flip_v"
                        )
                        GeoActionPill(
                            icon = Icons.Default.FitScreen,
                            label = "Flip Full",
                            active = transform.fullFrameFill,
                            onClick = {
                                val nextFull = !transform.fullFrameFill
                                onUpdateTransform(
                                    transform.copy(
                                        fullFrameFill = nextFull,
                                        offsetX = if (nextFull) 0f else transform.offsetX,
                                        offsetY = if (nextFull) 0f else transform.offsetY
                                    )
                                )
                            },
                            testTag = "btn_geo_flip_full"
                        )
                        GeoActionPill(
                            icon = Icons.Default.Rotate90DegreesCcw,
                            label = "-90°",
                            active = false,
                            onClick = {
                                val next = normalizeDegrees(transform.rotationDegrees - 90f)
                                onUpdateTransform(transform.copy(rotationDegrees = next))
                            },
                            testTag = "btn_geo_rot_ccw"
                        )
                        GeoActionPill(
                            icon = Icons.Default.Rotate90DegreesCw,
                            label = "+90°",
                            active = false,
                            onClick = {
                                val next = normalizeDegrees(transform.rotationDegrees + 90f)
                                onUpdateTransform(transform.copy(rotationDegrees = next))
                            },
                            testTag = "btn_geo_rot_cw"
                        )
                        GeoActionPill(
                            icon = Icons.Default.CenterFocusStrong,
                            label = "Center",
                            active = false,
                            onClick = { onUpdateTransform(transform.copy(offsetX = 0f, offsetY = 0f)) },
                            testTag = "btn_geo_center"
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    GeoSliderRow(
                        label = "Size",
                        valueText = "${(transform.scale * 100).roundToInt()}%",
                        value = transform.scale,
                        range = 0.25f..4.0f,
                        onValueChange = { onUpdateTransform(transform.copy(scale = it)) },
                        testTag = "slider_geo_size"
                    )
                    GeoSliderRow(
                        label = "Pos X",
                        valueText = "${(transform.offsetX * 100).roundToInt()}%",
                        value = transform.offsetX,
                        range = -1.0f..1.0f,
                        onValueChange = { onUpdateTransform(transform.copy(offsetX = it)) },
                        testTag = "slider_geo_pos_x"
                    )
                    GeoSliderRow(
                        label = "Pos Y",
                        valueText = "${(transform.offsetY * 100).roundToInt()}%",
                        value = transform.offsetY,
                        range = -1.0f..1.0f,
                        onValueChange = { onUpdateTransform(transform.copy(offsetY = it)) },
                        testTag = "slider_geo_pos_y"
                    )
                    GeoSliderRow(
                        label = "Rotate",
                        valueText = "${transform.rotationDegrees.roundToInt()}°",
                        value = transform.rotationDegrees,
                        range = -180f..180f,
                        onValueChange = { onUpdateTransform(transform.copy(rotationDegrees = it)) },
                        testTag = "slider_geo_rotate"
                    )
                    GeoSliderRow(
                        label = "Opacity",
                        valueText = "${(transform.opacity * 100).roundToInt()}%",
                        value = transform.opacity,
                        range = 0.05f..1.0f,
                        onValueChange = { onUpdateTransform(transform.copy(opacity = it)) },
                        testTag = "slider_geo_opacity"
                    )
                }

                1 -> {
                    // Crop Presets (Free, 16:9, 9:16, 1:1, 4:5, 4:3)
                    val presets = listOf("Free", "16:9", "9:16", "1:1", "4:5", "4:3")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        presets.forEach { preset ->
                            val picked = transform.crop.aspectPreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (picked) AmberContainer else ActiveRail)
                                    .border(
                                        width = if (picked) 1.5.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val nextCrop = VisualFxMath.cropPresetForRatio(preset, stageAspectRatio)
                                        onUpdateTransform(transform.copy(crop = nextCrop))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("crop_preset_${preset.replace(":", "_")}")
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (picked) CinemaAmber else ChalkWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val crop = transform.crop
                    GeoSliderRow(
                        label = "Left",
                        valueText = "${(crop.left * 100).roundToInt()}%",
                        value = crop.left,
                        range = 0f..0.42f,
                        onValueChange = { v -> onUpdateTransform(transform.copy(crop = crop.copy(left = v, aspectPreset = "Free"))) },
                        testTag = "slider_crop_left"
                    )
                    GeoSliderRow(
                        label = "Right",
                        valueText = "${(crop.right * 100).roundToInt()}%",
                        value = crop.right,
                        range = 0f..0.42f,
                        onValueChange = { v -> onUpdateTransform(transform.copy(crop = crop.copy(right = v, aspectPreset = "Free"))) },
                        testTag = "slider_crop_right"
                    )
                    GeoSliderRow(
                        label = "Top",
                        valueText = "${(crop.top * 100).roundToInt()}%",
                        value = crop.top,
                        range = 0f..0.42f,
                        onValueChange = { v -> onUpdateTransform(transform.copy(crop = crop.copy(top = v, aspectPreset = "Free"))) },
                        testTag = "slider_crop_top"
                    )
                    GeoSliderRow(
                        label = "Bottom",
                        valueText = "${(crop.bottom * 100).roundToInt()}%",
                        value = crop.bottom,
                        range = 0f..0.42f,
                        onValueChange = { v -> onUpdateTransform(transform.copy(crop = crop.copy(bottom = v, aspectPreset = "Free"))) },
                        testTag = "slider_crop_bottom"
                    )
                }

                2 -> if (panZoom != null && onUpdatePanZoom != null) {
                    // KineMaster-style Pan & Zoom Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Enable Pan & Zoom", style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                            Switch(
                                checked = panZoom.enabled,
                                onCheckedChange = { onUpdatePanZoom(panZoom.copy(enabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnAmber,
                                    checkedTrackColor = CinemaAmber,
                                    uncheckedThumbColor = SilverMuted,
                                    uncheckedTrackColor = ActiveRail
                                ),
                                modifier = Modifier.testTag("switch_pan_zoom")
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (panZoom.linked) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (panZoom.linked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val nextLink = !panZoom.linked
                                    onUpdatePanZoom(
                                        panZoom.copy(
                                            enabled = true,
                                            linked = nextLink,
                                            endScale = if (nextLink) panZoom.startScale else panZoom.endScale,
                                            endOffsetX = if (nextLink) panZoom.startOffsetX else panZoom.endOffsetX,
                                            endOffsetY = if (nextLink) panZoom.startOffsetY else panZoom.endOffsetY
                                        )
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("btn_pan_zoom_link"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (panZoom.linked) Icons.Default.Link else Icons.Default.LinkOff,
                                contentDescription = "Link start and end framing",
                                tint = if (panZoom.linked) CinemaAmber else SilverMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (panZoom.linked) "Linked =" else "Start → End",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (panZoom.linked) CinemaAmber else ChalkWhite
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Ken Burns Zoom Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val quickPresets = listOf(
                            "Zoom In" to PanZoomSpec(enabled = true, startScale = 1.0f, endScale = 1.35f),
                            "Zoom Out" to PanZoomSpec(enabled = true, startScale = 1.38f, endScale = 1.0f),
                            "Punch Zoom" to PanZoomSpec(enabled = true, startScale = 1.0f, endScale = 1.65f),
                            "Pan L → R" to PanZoomSpec(enabled = true, startScale = 1.25f, startOffsetX = -0.14f, endScale = 1.25f, endOffsetX = 0.14f),
                            "Pan R → L" to PanZoomSpec(enabled = true, startScale = 1.25f, startOffsetX = 0.14f, endScale = 1.25f, endOffsetX = -0.14f)
                        )
                        quickPresets.forEach { (label, spec) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ActiveRail)
                                    .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                    .clickable { onUpdatePanZoom(spec) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("pz_preset_${label.lowercase().replace(" ", "_")}")
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Start Position",
                        style = MaterialTheme.typography.labelSmall,
                        color = CinemaAmber,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    GeoSliderRow(
                        label = "Start Zoom",
                        valueText = "${(panZoom.startScale * 100).roundToInt()}%",
                        value = panZoom.startScale,
                        range = 0.5f..3.0f,
                        onValueChange = { v ->
                            onUpdatePanZoom(
                                panZoom.copy(
                                    enabled = true,
                                    startScale = v,
                                    endScale = if (panZoom.linked) v else panZoom.endScale
                                )
                            )
                        },
                        testTag = "slider_pz_start_scale"
                    )
                    GeoSliderRow(
                        label = "Start X",
                        valueText = "${(panZoom.startOffsetX * 100).roundToInt()}%",
                        value = panZoom.startOffsetX,
                        range = -0.5f..0.5f,
                        onValueChange = { v ->
                            onUpdatePanZoom(
                                panZoom.copy(
                                    enabled = true,
                                    startOffsetX = v,
                                    endOffsetX = if (panZoom.linked) v else panZoom.endOffsetX
                                )
                            )
                        },
                        testTag = "slider_pz_start_x"
                    )
                    GeoSliderRow(
                        label = "Start Y",
                        valueText = "${(panZoom.startOffsetY * 100).roundToInt()}%",
                        value = panZoom.startOffsetY,
                        range = -0.5f..0.5f,
                        onValueChange = { v ->
                            onUpdatePanZoom(
                                panZoom.copy(
                                    enabled = true,
                                    startOffsetY = v,
                                    endOffsetY = if (panZoom.linked) v else panZoom.endOffsetY
                                )
                            )
                        },
                        testTag = "slider_pz_start_y"
                    )

                    if (!panZoom.linked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "End Position",
                            style = MaterialTheme.typography.labelSmall,
                            color = CinemaAmber,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        GeoSliderRow(
                            label = "End Zoom",
                            valueText = "${(panZoom.endScale * 100).roundToInt()}%",
                            value = panZoom.endScale,
                            range = 0.5f..3.0f,
                            onValueChange = { v -> onUpdatePanZoom(panZoom.copy(enabled = true, endScale = v)) },
                            testTag = "slider_pz_end_scale"
                        )
                        GeoSliderRow(
                            label = "End X",
                            valueText = "${(panZoom.endOffsetX * 100).roundToInt()}%",
                            value = panZoom.endOffsetX,
                            range = -0.5f..0.5f,
                            onValueChange = { v -> onUpdatePanZoom(panZoom.copy(enabled = true, endOffsetX = v)) },
                            testTag = "slider_pz_end_x"
                        )
                        GeoSliderRow(
                            label = "End Y",
                            valueText = "${(panZoom.endOffsetY * 100).roundToInt()}%",
                            value = panZoom.endOffsetY,
                            range = -0.5f..0.5f,
                            onValueChange = { v -> onUpdatePanZoom(panZoom.copy(enabled = true, endOffsetY = v)) },
                            testTag = "slider_pz_end_y"
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeDegrees(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

@Composable
private fun SubTabPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AmberContainer else ActiveRail)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) CinemaAmber else SilverMuted, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) CinemaAmber else ChalkWhite, maxLines = 1)
    }
}

@Composable
private fun GeoActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) AmberContainer else ActiveRail)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) CinemaAmber else ChalkWhite,
            modifier = Modifier.size(15.dp).rotate(iconRotation)
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) CinemaAmber else ChalkWhite)
    }
}

@Composable
private fun GeoSliderRow(
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
