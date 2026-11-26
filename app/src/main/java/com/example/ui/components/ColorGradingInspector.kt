package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.ColorWheelOffset
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class ColorPageTab(val label: String) {
    WHEELS("Wheels"),
    PRIMARIES("Primaries"),
    CURVES("Curves"),
    LUTS("3D LUT")
}

/**
 * DaVinci Resolve-style Color Grading Inspector:
 * - Tab 1: Interactive 2D Touch Color Wheels (Lift / Gamma / Gain) + master wheel luma sliders
 * - Tab 2: Primary Sliders (Exposure, Contrast, Saturation, Temperature, Tint, Highlights, Shadows)
 * - Tab 3: Interactive 5-Point RGB Tone Curve editor + quick curve presets
 * - Tab 4: `.cube` 3D LUT file importer + intensity slider
 * - Toggleable Live Video Scopes (Waveform / RGB Parade) right from the header
 */
@Composable
fun ColorAdjustInspector(
    clipName: String?,
    adjustments: ColorAdjustments,
    showScopes: Boolean = false,
    onToggleScopes: () -> Unit = {},
    onImportCubeLut: () -> Unit = {},
    onClearCubeLut: () -> Unit = {},
    onChange: (ColorAdjustments) -> Unit,
    onReset: () -> Unit,
    onApplyAll: () -> Unit,
    onClose: () -> Unit
) {
    var activeTab by remember { mutableStateOf(ColorPageTab.WHEELS) }

    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("color_grading_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = if (clipName != null) "Color • $clipName" else "DaVinci Color Grade",
                onClose = onClose,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showScopes) AmberContainer else ActiveRail)
                                .border(1.dp, if (showScopes) CinemaAmber else BorderHairline, RoundedCornerShape(6.dp))
                                .clickable { onToggleScopes() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("color_toggle_scopes_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(
                                    Icons.Default.ShowChart,
                                    contentDescription = "Toggle Video Scopes",
                                    tint = if (showScopes) CinemaAmber else SilverMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Scopes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (showScopes) CinemaAmber else ChalkWhite
                                )
                            }
                        }
                        TextButton(
                            onClick = onApplyAll,
                            modifier = Modifier.testTag("color_apply_all_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("All", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = onReset,
                            enabled = !adjustments.isDefault,
                            modifier = Modifier.testTag("color_reset_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            // DaVinci Sub-Page Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ColorPageTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    val hasActiveDot = when (tab) {
                        ColorPageTab.WHEELS -> !adjustments.lift.isNeutral || !adjustments.gamma.isNeutral || !adjustments.gain.isNeutral
                        ColorPageTab.PRIMARIES -> abs(adjustments.brightness) > 0.005f || abs(adjustments.contrast - 1f) > 0.005f ||
                            abs(adjustments.saturation - 1f) > 0.005f || abs(adjustments.temperature) > 0.005f ||
                            abs(adjustments.tint) > 0.005f || abs(adjustments.highlights) > 0.005f || abs(adjustments.shadows) > 0.005f
                        ColorPageTab.CURVES -> adjustments.hasNonLinearCurve
                        ColorPageTab.LUTS -> !adjustments.cubeLutPath.isNullOrBlank()
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AmberContainer else ActiveRail)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { activeTab = tab }
                            .padding(vertical = 6.dp)
                            .testTag("color_tab_${tab.name.lowercase()}"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CinemaAmber else ChalkWhite
                        )
                        if (hasActiveDot) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(CinemaAmber)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                ColorPageTab.WHEELS -> ColorWheelsPage(
                    adjustments = adjustments,
                    onChange = onChange
                )
                ColorPageTab.PRIMARIES -> PrimariesPage(
                    adjustments = adjustments,
                    onChange = onChange
                )
                ColorPageTab.CURVES -> ToneCurvePage(
                    adjustments = adjustments,
                    onChange = onChange
                )
                ColorPageTab.LUTS -> CubeLutPage(
                    adjustments = adjustments,
                    onImportCubeLut = onImportCubeLut,
                    onClearCubeLut = onClearCubeLut,
                    onChange = onChange
                )
            }
        }
    }
}

@Composable
private fun ColorWheelsPage(
    adjustments: ColorAdjustments,
    onChange: (ColorAdjustments) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InteractiveColorWheelCard(
            title = "LIFT",
            subtitle = "Shadows",
            wheel = adjustments.lift,
            onWheelChange = { onChange(adjustments.copy(lift = it)) },
            modifier = Modifier.weight(1f).testTag("wheel_lift")
        )
        InteractiveColorWheelCard(
            title = "GAMMA",
            subtitle = "Midtones",
            wheel = adjustments.gamma,
            onWheelChange = { onChange(adjustments.copy(gamma = it)) },
            modifier = Modifier.weight(1f).testTag("wheel_gamma")
        )
        InteractiveColorWheelCard(
            title = "GAIN",
            subtitle = "Highlights",
            wheel = adjustments.gain,
            onWheelChange = { onChange(adjustments.copy(gain = it)) },
            modifier = Modifier.weight(1f).testTag("wheel_gain")
        )
    }
}

@Composable
private fun InteractiveColorWheelCard(
    title: String,
    subtitle: String,
    wheel: ColorWheelOffset,
    onWheelChange: (ColorWheelOffset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianBg)
            .border(1.dp, if (!wheel.isNeutral) CinemaAmber.copy(alpha = 0.65f) else BorderHairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = if (!wheel.isNeutral) CinemaAmber else ChalkWhite)
            if (!wheel.isNeutral) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmberContainer)
                        .clickable { onWheelChange(ColorWheelOffset()) }
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            } else {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = SilverMuted)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        // 2D Touch Chromatic Wheel Canvas
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .pointerInput(wheel) {
                    detectTapGestures(
                        onDoubleTap = { onWheelChange(ColorWheelOffset()) },
                        onTap = { tap ->
                            val r = (size.width / 2f).coerceAtLeast(1f)
                            val nx = ((tap.x - r) / r).coerceIn(-1f, 1f)
                            val ny = ((tap.y - r) / r).coerceIn(-1f, 1f)
                            val mag = hypot(nx.toDouble(), ny.toDouble()).toFloat()
                            val cx = if (mag > 1f) nx / mag else nx
                            val cy = if (mag > 1f) ny / mag else ny
                            onWheelChange(wheel.copy(x = cx, y = cy))
                        }
                    )
                }
                .pointerInput(wheel) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val r = (size.width / 2f).coerceAtLeast(1f)
                        val nx = wheel.x + (dragAmount.x / r)
                        val ny = wheel.y + (dragAmount.y / r)
                        val mag = hypot(nx.toDouble(), ny.toDouble()).toFloat()
                        val cx = if (mag > 1f) nx / mag else nx
                        val cy = if (mag > 1f) ny / mag else ny
                        onWheelChange(wheel.copy(x = cx, y = cy))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                // Sweep chromatic ring + radial neutral core
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFEF4444), // +X Warm Red
                            Color(0xFFD946EF), // +Y Magenta
                            Color(0xFF3B82F6), // -X Cool Blue
                            Color(0xFF06B6D4), // Cyan
                            Color(0xFF22C55E), // -Y Green
                            Color(0xFFEAB308), // Amber
                            Color(0xFFEF4444)
                        ),
                        center = center
                    ),
                    radius = radius,
                    alpha = 0.45f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1D24), Color.Transparent),
                        center = center,
                        radius = radius * 0.92f
                    ),
                    radius = radius
                )
                drawCircle(
                    color = BorderHairline,
                    radius = radius - 1f,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Crosshair axis
                drawLine(Color.White.copy(alpha = 0.18f), Offset(center.x, 4f), Offset(center.x, size.height - 4f), 1f)
                drawLine(Color.White.copy(alpha = 0.18f), Offset(4f, center.y), Offset(size.width - 4f, center.y), 1f)

                // Active control puck
                val puckCenter = Offset(
                    center.x + wheel.x * (radius - 8.dp.toPx()),
                    center.y + wheel.y * (radius - 8.dp.toPx())
                )
                drawLine(CinemaAmber.copy(alpha = 0.7f), center, puckCenter, strokeWidth = 1.5.dp.toPx())
                drawCircle(color = CinemaAmber, radius = 6.dp.toPx(), center = puckCenter)
                drawCircle(color = Color.Black, radius = 2.5.dp.toPx(), center = puckCenter)
            }
        }

        // Master Luminance Ring Slider under each wheel
        Slider(
            value = wheel.luma,
            onValueChange = { onWheelChange(wheel.copy(luma = it.coerceIn(-0.4f, 0.4f))) },
            valueRange = -0.4f..0.4f,
            colors = SliderDefaults.colors(
                thumbColor = CinemaAmber,
                activeTrackColor = CinemaAmber,
                inactiveTrackColor = ActiveRail
            ),
            modifier = Modifier.fillMaxWidth().height(26.dp)
        )
        Text(
            text = String.format("Y %+.2f", wheel.luma),
            style = MaterialTheme.typography.labelSmall,
            color = SilverMuted
        )
    }
}

@Composable
private fun PrimariesPage(
    adjustments: ColorAdjustments,
    onChange: (ColorAdjustments) -> Unit
) {
    // Quick Cinematic Grade Presets
    val quickGrades = remember {
        listOf(
            "Natural" to ColorAdjustments(),
            "Warm Gold" to adjustments.copy(brightness = 0.04f, contrast = 1.12f, saturation = 1.18f, temperature = 0.42f, tint = 0.06f),
            "Cool Cinema" to adjustments.copy(brightness = -0.02f, contrast = 1.16f, saturation = 0.92f, temperature = -0.45f, tint = -0.05f),
            "Teal Matte" to adjustments.copy(brightness = 0.03f, contrast = 0.90f, saturation = 0.85f, shadows = 0.25f, highlights = -0.20f, temperature = -0.18f),
            "Punchy HDR" to adjustments.copy(brightness = 0.02f, contrast = 1.32f, saturation = 1.38f, highlights = 0.18f, shadows = -0.12f)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickGrades.forEach { (label, presetAdj) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveRail)
                    .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                    .clickable { onChange(presetAdj) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("color_preset_${label.lowercase().replace(' ', '_')}"),
                contentAlignment = Alignment.Center
            ) {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    ColorGradingSliderRow(
        icon = Icons.Default.Brightness6,
        label = "Exposure",
        valueText = String.format("%+d", (adjustments.brightness * 200).roundToInt()),
        value = adjustments.brightness,
        range = -0.5f..0.5f,
        isModified = abs(adjustments.brightness) >= 0.005f,
        onValueChange = { onChange(adjustments.copy(brightness = it)) },
        onResetSingle = { onChange(adjustments.copy(brightness = 0f)) },
        testTag = "slider_brightness"
    )
    ColorGradingSliderRow(
        icon = Icons.Default.Contrast,
        label = "Contrast",
        valueText = "${(adjustments.contrast * 100).roundToInt()}%",
        value = adjustments.contrast,
        range = 0.5f..1.8f,
        isModified = abs(adjustments.contrast - 1f) >= 0.005f,
        onValueChange = { onChange(adjustments.copy(contrast = it)) },
        onResetSingle = { onChange(adjustments.copy(contrast = 1f)) },
        testTag = "slider_contrast"
    )
    ColorGradingSliderRow(
        icon = Icons.Default.InvertColors,
        label = "Saturation",
        valueText = "${(adjustments.saturation * 100).roundToInt()}%",
        value = adjustments.saturation,
        range = 0f..2.0f,
        isModified = abs(adjustments.saturation - 1f) >= 0.005f,
        onValueChange = { onChange(adjustments.copy(saturation = it)) },
        onResetSingle = { onChange(adjustments.copy(saturation = 1f)) },
        testTag = "slider_saturation"
    )
    ColorGradingSliderRow(
        icon = Icons.Default.Thermostat,
        label = "Temp",
        valueText = String.format("%+d", (adjustments.temperature * 100).roundToInt()),
        value = adjustments.temperature,
        range = -1f..1f,
        isModified = abs(adjustments.temperature) >= 0.005f,
        trackAccent = when {
            adjustments.temperature > 0.03f -> CinemaAmber
            adjustments.temperature < -0.03f -> Color(0xFF38BDF8)
            else -> CinemaAmber
        },
        onValueChange = { onChange(adjustments.copy(temperature = it)) },
        onResetSingle = { onChange(adjustments.copy(temperature = 0f)) },
        testTag = "slider_temperature"
    )
    ColorGradingSliderRow(
        icon = Icons.Default.Palette,
        label = "Tint",
        valueText = String.format("%+d", (adjustments.tint * 100).roundToInt()),
        value = adjustments.tint,
        range = -1f..1f,
        isModified = abs(adjustments.tint) >= 0.005f,
        trackAccent = when {
            adjustments.tint > 0.03f -> Color(0xFFE879F9)
            adjustments.tint < -0.03f -> Color(0xFF4ADE80)
            else -> CinemaAmber
        },
        onValueChange = { onChange(adjustments.copy(tint = it)) },
        onResetSingle = { onChange(adjustments.copy(tint = 0f)) },
        testTag = "slider_tint"
    )
    ColorGradingSliderRow(
        icon = Icons.Default.WbSunny,
        label = "Highlights",
        valueText = String.format("%+d", (adjustments.highlights * 100).roundToInt()),
        value = adjustments.highlights,
        range = -1f..1f,
        isModified = abs(adjustments.highlights) >= 0.005f,
        onValueChange = { onChange(adjustments.copy(highlights = it)) },
        onResetSingle = { onChange(adjustments.copy(highlights = 0f)) },
        testTag = "slider_highlights"
    )
    ColorGradingSliderRow(
        icon = Icons.Default.Tonality,
        label = "Shadows",
        valueText = String.format("%+d", (adjustments.shadows * 100).roundToInt()),
        value = adjustments.shadows,
        range = -1f..1f,
        isModified = abs(adjustments.shadows) >= 0.005f,
        onValueChange = { onChange(adjustments.copy(shadows = it)) },
        onResetSingle = { onChange(adjustments.copy(shadows = 0f)) },
        testTag = "slider_shadows"
    )
}

@Composable
private fun ToneCurvePage(
    adjustments: ColorAdjustments,
    onChange: (ColorAdjustments) -> Unit
) {
    val pts = adjustments.normalizedCurvePoints
    var activeHandleIdx by remember { mutableIntStateOf(2) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Linear" to listOf(0f, 0.25f, 0.50f, 0.75f, 1.0f),
                "S-Contrast" to listOf(0f, 0.17f, 0.50f, 0.83f, 1.0f),
                "Matte Fade" to listOf(0.10f, 0.28f, 0.52f, 0.76f, 0.94f),
                "Bright Pop" to listOf(0.02f, 0.34f, 0.60f, 0.84f, 1.0f)
            ).forEach { (label, curve) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ActiveRail)
                        .border(1.dp, BorderHairline, RoundedCornerShape(7.dp))
                        .clickable { onChange(adjustments.copy(curvePoints = curve)) }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkWhite, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive 5-Point RGB Tone Curve Editor Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBg)
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .pointerInput(pts) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val fracX = (startOffset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            activeHandleIdx = (fracX * 4f).roundToInt().coerceIn(0, 4)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            val newY = (1f - (pos.y / size.height.coerceAtLeast(1))).coerceIn(0f, 1f)
                            val updated = pts.toMutableList()
                            updated[activeHandleIdx] = newY
                            onChange(adjustments.copy(curvePoints = updated))
                        }
                    )
                }
                .pointerInput(pts) {
                    detectTapGestures(
                        onTap = { tap ->
                            val idx = ((tap.x / size.width.coerceAtLeast(1)) * 4f).roundToInt().coerceIn(0, 4)
                            activeHandleIdx = idx
                            val newY = (1f - (tap.y / size.height.coerceAtLeast(1))).coerceIn(0f, 1f)
                            val updated = pts.toMutableList()
                            updated[idx] = newY
                            onChange(adjustments.copy(curvePoints = updated))
                        }
                    )
                }
                .testTag("rgb_tone_curve_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                // 4x4 grid + diagonal identity reference
                val gridColor = Color.White.copy(alpha = 0.12f)
                for (i in 1..3) {
                    val gx = size.width * (i / 4f)
                    val gy = size.height * (i / 4f)
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f)
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f)
                }
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )

                // Smooth spline curve path
                val path = Path()
                val steps = 48
                for (s in 0..steps) {
                    val xNorm = s / steps.toFloat()
                    val yNorm = ColorMatrixEngine.evaluateToneCurve(xNorm, pts)
                    val px = xNorm * size.width
                    val py = (1f - yNorm) * size.height
                    if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color = CinemaAmber, style = Stroke(width = 2.4.dp.toPx()))

                // 5 Draggable Control Points
                pts.forEachIndexed { idx, yNorm ->
                    val cx = (idx / 4f) * size.width
                    val cy = (1f - yNorm) * size.height
                    val isSelected = idx == activeHandleIdx
                    drawCircle(
                        color = if (isSelected) CinemaAmber else ChalkWhite,
                        radius = if (isSelected) 6.5.dp.toPx() else 5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = ObsidianBg,
                        radius = 2.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Drag curve points: Blacks • Shadows • Midtones • Highlights • Whites",
            style = MaterialTheme.typography.labelSmall,
            color = SilverMuted
        )
    }
}

@Composable
private fun CubeLutPage(
    adjustments: ColorAdjustments,
    onImportCubeLut: () -> Unit,
    onClearCubeLut: () -> Unit,
    onChange: (ColorAdjustments) -> Unit
) {
    val hasLut = !adjustments.cubeLutPath.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBg)
                .border(1.dp, if (hasLut) CinemaAmber else BorderHairline, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterVintage,
                contentDescription = null,
                tint = if (hasLut) CinemaAmber else SilverMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = adjustments.cubeLutName ?: "No 3D LUT Loaded",
                    style = MaterialTheme.typography.titleSmall,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasLut) "Standard .cube 3D LUT active on preview & MP4 export"
                    else "Import any standard Adobe / DaVinci .cube 3D LUT file",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverMuted
                )
            }
            if (hasLut) {
                TextButton(onClick = onClearCubeLut, modifier = Modifier.testTag("clear_cube_lut_btn")) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove LUT", tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                }
            }
        }

        FilledTonalButton(
            onClick = onImportCubeLut,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = CinemaAmber,
                contentColor = OnAmber
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("import_cube_lut_button")
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasLut) "Replace .cube 3D LUT File" else "Import .cube 3D LUT File",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (hasLut) {
            ColorGradingSliderRow(
                icon = Icons.Default.FilterVintage,
                label = "LUT Blend",
                valueText = "${(adjustments.cubeLutIntensity * 100).roundToInt()}%",
                value = adjustments.cubeLutIntensity,
                range = 0f..1f,
                isModified = adjustments.cubeLutIntensity < 0.99f,
                onValueChange = { onChange(adjustments.copy(cubeLutIntensity = it)) },
                onResetSingle = { onChange(adjustments.copy(cubeLutIntensity = 1f)) },
                testTag = "slider_cube_lut_intensity"
            )
        }
    }
}

@Composable
private fun ColorGradingSliderRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isModified: Boolean,
    trackAccent: Color = CinemaAmber,
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
                .width(96.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onResetSingle() }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isModified) trackAccent else SilverMuted,
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
                thumbColor = trackAccent,
                activeTrackColor = trackAccent,
                inactiveTrackColor = ActiveRail
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = if (isModified) trackAccent else ChalkWhite,
            modifier = Modifier.width(46.dp)
        )
    }
}
