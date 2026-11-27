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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AiCutoutSettings
import com.example.model.CutoutBackgroundMode
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
 * Dedicated AI Subject Cutout Inspector powered by on-device ML Kit Selfie Segmentation.
 * Supports Transparent, Solid Color, Gaussian Blur, and Custom Image backgrounds.
 */
@Composable
fun CutoutInspector(
    targetLabel: String,
    settings: AiCutoutSettings,
    onUpdate: (AiCutoutSettings) -> Unit,
    onPickCustomBackground: () -> Unit,
    onClose: () -> Unit,
    compactDocked: Boolean = false
) {
    val bgSwatches = remember {
        listOf(
            0xFF0A0C10L, 0xFF00D26AL, 0xFF1D4ED8L, 0xFF7C3AEDL,
            0xFFF59E0BL, 0xFFEF4444L, 0xFFF4F5F7L
        )
    }

    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cutout_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compactDocked) Modifier else Modifier.heightIn(max = 330.dp))
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "AI Cutout • $targetLabel",
                onClose = onClose
            )

            // Master Auto Subject Cutout Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(
                        width = if (settings.enabled) 1.5.dp else 1.dp,
                        color = if (settings.enabled) CinemaAmber else BorderHairline,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = CinemaAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Auto Subject Cutout",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChalkWhite
                        )
                        Text(
                            text = "On-device ML Kit background removal",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onUpdate(settings.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAmber,
                        checkedTrackColor = CinemaAmber,
                        uncheckedThumbColor = SilverMuted,
                        uncheckedTrackColor = ActiveRail
                    ),
                    modifier = Modifier.testTag("switch_cutout_enabled")
                )
            }

            if (settings.enabled) {
                Spacer(modifier = Modifier.height(6.dp))

                // Background Mode Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CutoutBackgroundMode.entries.forEach { mode ->
                        val picked = settings.backgroundMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    width = if (picked) 1.5.dp else 1.dp,
                                    color = if (picked) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onUpdate(settings.copy(backgroundMode = mode)) }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                .testTag("cutout_bg_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                if (settings.backgroundMode == CutoutBackgroundMode.SOLID_COLOR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Color", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                        bgSwatches.forEach { argb ->
                            val picked = settings.solidColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (picked) 2.5.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdate(settings.copy(solidColorArgb = argb)) }
                            )
                        }
                    }
                }

                if (settings.backgroundMode == CutoutBackgroundMode.CUSTOM_IMAGE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onPickCustomBackground,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AmberContainer,
                            contentColor = CinemaAmber
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(38.dp)
                            .testTag("btn_cutout_pick_bg")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (settings.customBackgroundUri != null) "Change Custom Background" else "Pick Custom Background Photo",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Edge Threshold & Soft Feather Sliders
                CutoutSliderRow(
                    label = "Threshold",
                    valueText = "${(settings.threshold * 100).roundToInt()}%",
                    value = settings.threshold,
                    range = 0.12f..0.88f,
                    onValueChange = { onUpdate(settings.copy(threshold = it)) },
                    testTag = "slider_cutout_threshold"
                )
                CutoutSliderRow(
                    label = "Feather",
                    valueText = "${settings.featherPx.roundToInt()}px",
                    value = settings.featherPx,
                    range = 0f..15f,
                    onValueChange = { onUpdate(settings.copy(featherPx = it)) },
                    testTag = "slider_cutout_feather"
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enable Auto Subject Cutout to cleanly remove or blur backgrounds around people and foreground subjects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun CutoutSliderRow(
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
            modifier = Modifier.width(44.dp)
        )
    }
}
