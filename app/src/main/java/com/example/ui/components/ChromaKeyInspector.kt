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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChromaKeyColor
import com.example.model.ChromaKeySettings
import com.example.model.OverlayLayer
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.TrackOverlayEmerald
import kotlin.math.roundToInt

@Composable
fun VideoOverlayChromaInspector(
    overlay: OverlayLayer,
    isSamplingColor: Boolean = false,
    onToggleColorPicker: () -> Unit = {},
    onUpdateOverlay: ((OverlayLayer) -> OverlayLayer) -> Unit,
    onMoveLayerZ: (Boolean) -> Unit,
    onResetTransform: () -> Unit,
    onClose: () -> Unit
) {
    val chroma = overlay.chromaKey

    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 310.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Chroma Key • Z${overlay.zIndex}",
                onClose = onClose,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chroma.enabled) {
                            TextButton(
                                onClick = {
                                    onUpdateOverlay {
                                        it.copy(
                                            chromaKey = ChromaKeySettings(
                                                enabled = true,
                                                keyColor = chroma.keyColor
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("chroma_key_reset_button")
                            ) {
                                Text("Reset Key", color = CinemaAmber, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        IconButton(onClick = { onMoveLayerZ(false) }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Send overlay backward",
                                tint = SilverMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = { onMoveLayerZ(true) }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Bring overlay forward",
                                tint = SilverMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onResetTransform, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset overlay transform",
                                tint = SilverMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )

            // Master Background Removal (Media3 GlEffect) Toggle Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (chroma.enabled) AmberContainer.copy(alpha = 0.7f) else ActiveRail)
                    .border(
                        width = 1.dp,
                        color = if (chroma.enabled) CinemaAmber else BorderHairline,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (chroma.enabled) TrackOverlayEmerald.copy(alpha = 0.22f)
                            else ObsidianBg.copy(alpha = 0.7f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = if (chroma.enabled) TrackOverlayEmerald else SilverMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remove Green / Blue Screen",
                        style = MaterialTheme.typography.titleSmall,
                        color = ChalkWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (chroma.enabled) {
                            val srcLabel = if (chroma.customColorArgb != null) {
                                String.format("Sampled #%06X", chroma.customColorArgb and 0xFFFFFFL)
                            } else {
                                chroma.keyColor.displayName
                            }
                            "$srcLabel • Real-Time GPU Shader"
                        } else {
                            "Enable GPU chroma key on video overlay"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chroma.enabled) CinemaAmber else SilverMuted
                    )
                }
                Switch(
                    checked = chroma.enabled,
                    onCheckedChange = { enabled ->
                        onUpdateOverlay { it.copy(chromaKey = it.chromaKey.copy(enabled = enabled)) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAmber,
                        checkedTrackColor = CinemaAmber,
                        uncheckedThumbColor = SilverMuted,
                        uncheckedTrackColor = ObsidianBg
                    ),
                    modifier = Modifier.testTag("chroma_key_toggle")
                )
            }

            if (chroma.enabled) {
                Spacer(modifier = Modifier.height(10.dp))

                // Key Screen Color Selector Strip + Eyedropper Tap-to-Sample Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Eyedropper Tap-to-Sample Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSamplingColor || chroma.customColorArgb != null) AmberContainer else ActiveRail)
                            .border(
                                width = if (isSamplingColor || chroma.customColorArgb != null) 1.5.dp else 1.dp,
                                color = if (isSamplingColor || chroma.customColorArgb != null) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { onToggleColorPicker() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("chroma_eyedropper_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(chroma.effectiveSwatchHex))
                                .border(1.dp, ChalkWhite, CircleShape)
                        )
                        Text(
                            text = if (isSamplingColor) "Tap Video…" else "Sample Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSamplingColor || chroma.customColorArgb != null) CinemaAmber else ChalkWhite
                        )
                    }

                    ChromaKeyColor.entries.forEach { keyColor ->
                        val isSelected = chroma.customColorArgb == null && chroma.keyColor == keyColor
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) AmberContainer else ActiveRail)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .clickable {
                                    onUpdateOverlay {
                                        it.copy(
                                            chromaKey = it.chromaKey.copy(
                                                keyColor = keyColor,
                                                customColorArgb = null
                                            )
                                        )
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("chroma_color_${keyColor.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(keyColor.swatchHex))
                                    .border(1.dp, ChalkWhite.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected key color",
                                        tint = Color.Black,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                            Text(
                                text = keyColor.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                ChromaSliderRow(
                    label = "Similarity",
                    valueText = "${(chroma.similarity * 100).roundToInt()}%",
                    value = chroma.similarity,
                    range = 0.05f..0.85f,
                    onValueChange = { v ->
                        onUpdateOverlay { it.copy(chromaKey = it.chromaKey.copy(similarity = v)) }
                    },
                    testTag = "slider_chroma_similarity"
                )
                ChromaSliderRow(
                    label = "Edge Smooth",
                    valueText = "${(chroma.smoothness * 100).roundToInt()}%",
                    value = chroma.smoothness,
                    range = 0.01f..0.40f,
                    onValueChange = { v ->
                        onUpdateOverlay { it.copy(chromaKey = it.chromaKey.copy(smoothness = v)) }
                    },
                    testTag = "slider_chroma_smoothness"
                )
                ChromaSliderRow(
                    label = "Spill Fix",
                    valueText = "${(chroma.spillSuppression * 100).roundToInt()}%",
                    value = chroma.spillSuppression,
                    range = 0f..1f,
                    onValueChange = { v ->
                        onUpdateOverlay { it.copy(chromaKey = it.chromaKey.copy(spillSuppression = v)) }
                    },
                    testTag = "slider_chroma_spill"
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            ChromaSliderRow(
                label = "Opacity",
                valueText = "${(overlay.opacity * 100).roundToInt()}%",
                value = overlay.opacity,
                range = 0.05f..1f,
                onValueChange = { v -> onUpdateOverlay { it.copy(opacity = v) } },
                testTag = "slider_video_overlay_opacity"
            )
            ChromaSliderRow(
                label = "Corners",
                valueText = "${overlay.cornerRadiusDp.roundToInt()}dp",
                value = overlay.cornerRadiusDp,
                range = 0f..48f,
                onValueChange = { v -> onUpdateOverlay { it.copy(cornerRadiusDp = v) } },
                testTag = "slider_video_overlay_corners"
            )
        }
    }
}

@Composable
private fun ChromaSliderRow(
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
            modifier = Modifier.width(86.dp)
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
            modifier = Modifier.width(46.dp)
        )
    }
}
