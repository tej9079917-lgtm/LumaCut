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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.FontResolver
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import com.example.model.SafeZoneOverlay
import com.example.model.StudioFont
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
import kotlin.math.roundToInt

@Composable
fun CaptionStudioInspector(
    captions: List<CaptionClip>,
    selectedCaption: CaptionClip?,
    safeZone: SafeZoneOverlay,
    availableFonts: List<StudioFont>,
    autoCaptionLoading: Boolean = false,
    autoCaptionStatus: String? = null,
    autoCaptionError: String? = null,
    isLiveMicDictating: Boolean = false,
    liveMicPartialText: String = "",
    onRunGeminiAutoCaptions: (String) -> Unit = {},
    onToggleOfflineMicDictation: () -> Unit = {},
    onAddCaption: () -> Unit,
    onSelectCaption: (String) -> Unit,
    onUpdateCaption: ((CaptionClip) -> CaptionClip) -> Unit,
    onDeleteSelectedCaption: () -> Unit,
    onImportSrt: () -> Unit,
    onExportSrt: () -> Unit,
    onUpdateSafeZone: (SafeZoneOverlay) -> Unit,
    onClose: () -> Unit,
    compactDocked: Boolean = false
) {
    val highlightSwatches = remember {
        listOf(0xFFFACC15L, 0xFF38BDF8L, 0xFF34D399L, 0xFFFB7185L, 0xFFF97316L, 0xFFC084FCL)
    }
    val textSwatches = remember {
        listOf(0xFFFFFFFFL, 0xFFFDE047L, 0xFF38BDF8L, 0xFF34D399L, 0xFF0B0C0EL)
    }
    var selectedLanguage by remember { mutableStateOf("Auto") }
    val languages = remember { listOf("Auto", "English", "Hindi", "Telugu", "Spanish", "Tamil") }

    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("caption_studio_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compactDocked) Modifier else Modifier.heightIn(max = 360.dp))
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Auto Captions & SRT (${captions.size})",
                onClose = onClose,
                trailingAction = {
                    if (selectedCaption != null) {
                        IconButton(
                            onClick = onDeleteSelectedCaption,
                            modifier = Modifier.size(34.dp).testTag("btn_delete_caption")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete caption",
                                tint = ErrorCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            // 0. Dual-Engine Auto Captions Panel (Cloud Gemini AI + Offline Live Mic)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(
                        width = if (isLiveMicDictating) 1.5.dp else 1.dp,
                        color = if (isLiveMicDictating) ErrorCrimson else BorderHairline,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = "Auto Captions (Gemini AI + Offline Mic)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaAmber
                )

                // Language hint chips for Gemini transcription
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        val picked = lang == selectedLanguage
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (picked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedLanguage = lang }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onRunGeminiAutoCaptions(selectedLanguage) },
                        enabled = !autoCaptionLoading && !isLiveMicDictating,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaAmber,
                            contentColor = OnAmber
                        ),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(38.dp)
                            .testTag("btn_auto_captions_gemini")
                    ) {
                        if (autoCaptionLoading) {
                            CircularProgressIndicator(
                                color = OnAmber,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Transcribing…", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Transcribe", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }

                    FilledTonalButton(
                        onClick = onToggleOfflineMicDictation,
                        enabled = !autoCaptionLoading,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isLiveMicDictating) ErrorCrimson else ActiveRail,
                            contentColor = ChalkWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_auto_captions_mic")
                    ) {
                        Icon(
                            imageVector = if (isLiveMicDictating) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLiveMicDictating) "Stop Mic" else "Offline Mic",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                if (isLiveMicDictating) {
                    Text(
                        text = if (liveMicPartialText.isNotBlank()) "Listening: \"$liveMicPartialText\"" else "Listening offline… speak while playing or scrubbing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorCrimson
                    )
                } else if (!autoCaptionStatus.isNullOrBlank()) {
                    Text(
                        text = autoCaptionStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted
                    )
                }

                if (!autoCaptionError.isNullOrBlank()) {
                    Text(
                        text = autoCaptionError,
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorCrimson
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 1. Primary Action Row: + Caption, Import .SRT, Export .SRT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onAddCaption,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ActiveRail,
                        contentColor = CinemaAmber
                    ),
                    modifier = Modifier.weight(1.1f).height(36.dp).testTag("btn_add_caption")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Manual", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onImportSrt,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ActiveRail,
                        contentColor = ChalkWhite
                    ),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("btn_import_srt")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Import SRT", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onExportSrt,
                    enabled = captions.isNotEmpty(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ActiveRail,
                        contentColor = ChalkWhite
                    ),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("btn_export_srt")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Export SRT", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Quick Social Safe Zone Guides Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Safe Zone", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                SafeZoneOverlay.entries.forEach { zone ->
                    val picked = zone == safeZone
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(
                                1.dp,
                                if (picked) CinemaAmber else BorderHairline,
                                RoundedCornerShape(7.dp)
                            )
                            .clickable { onUpdateSafeZone(zone) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("safe_zone_${zone.name.lowercase()}")
                    ) {
                        Text(
                            text = zone.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                    }
                }
            }

            // 3. Caption Cue Strip Selector
            if (captions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    captions.sortedBy { it.startTimeMs }.forEachIndexed { idx, cap ->
                        val picked = cap.id == selectedCaption?.id
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (picked) AmberContainer else ObsidianBg)
                                .border(
                                    width = if (picked) 1.5.dp else 1.dp,
                                    color = if (picked) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCaption(cap.id) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("caption_pill_$idx")
                        ) {
                            Text(
                                text = cap.text.take(18),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (picked) CinemaAmber else ChalkWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatShortDuration(cap.startTimeMs)}–${formatShortDuration(cap.endTimeMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted
                            )
                        }
                    }
                }
            }

            // 4. Selected Caption Editor
            if (selectedCaption != null) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = selectedCaption.text,
                    onValueChange = { txt -> onUpdateCaption { it.copy(text = txt) } },
                    placeholder = { Text("Enter subtitle phrase…") },
                    singleLine = false,
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinemaAmber,
                        unfocusedBorderColor = BorderHairline,
                        focusedTextColor = ChalkWhite,
                        unfocusedTextColor = ChalkWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("caption_text_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Animation Style Preset Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CaptionAnimationPreset.entries.forEach { preset ->
                        val picked = selectedCaption.animationPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (picked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onUpdateCaption { it.copy(animationPreset = preset) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("caption_anim_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Font Picker Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    availableFonts.forEach { font ->
                        val selected = font.id == selectedCaption.fontId
                        val family = remember(font.id, availableFonts) {
                            FontResolver.resolveComposeFont(font.id, availableFonts)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (selected) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onUpdateCaption { it.copy(fontId = font.id) } }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = font.displayName,
                                fontFamily = family,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Text Color, Karaoke Highlight Color & Pill Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Text", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    textSwatches.forEach { argb ->
                        val picked = selectedCaption.textColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (picked) 2.5.dp else 1.dp, if (picked) CinemaAmber else BorderHairline, CircleShape)
                                .clickable { onUpdateCaption { it.copy(textColorArgb = argb) } }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pop", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    highlightSwatches.forEach { argb ->
                        val picked = selectedCaption.highlightColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (picked) 2.5.dp else 1.dp, if (picked) ChalkWhite else BorderHairline, CircleShape)
                                .clickable { onUpdateCaption { it.copy(highlightColorArgb = argb) } }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = selectedCaption.hasBackgroundPill,
                        onClick = { onUpdateCaption { it.copy(hasBackgroundPill = !it.hasBackgroundPill) } },
                        label = { Text("Pill", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // Size, Vertical Position & Duration Sliders
                CaptionSliderRow(
                    label = "Size",
                    valueText = "${selectedCaption.fontSizeSp.roundToInt()}sp",
                    value = selectedCaption.fontSizeSp,
                    range = 16f..48f,
                    onValueChange = { sz -> onUpdateCaption { it.copy(fontSizeSp = sz) } }
                )
                CaptionSliderRow(
                    label = "Vertical",
                    valueText = "${(selectedCaption.offsetY * 100).roundToInt()}%",
                    value = selectedCaption.offsetY,
                    range = -0.42f..0.44f,
                    onValueChange = { y -> onUpdateCaption { it.copy(offsetY = y) } }
                )
                CaptionSliderRow(
                    label = "Duration",
                    valueText = String.format("%.1fs", selectedCaption.durationMs / 1000f),
                    value = selectedCaption.durationMs.toFloat(),
                    range = 500f..8000f,
                    onValueChange = { dur ->
                        onUpdateCaption { it.copy(endTimeMs = it.startTimeMs + dur.toLong()) }
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap + Caption to add a karaoke subtitle at the playhead, or import an .SRT file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun CaptionSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
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
            modifier = Modifier.width(70.dp)
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
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = ChalkWhite,
            modifier = Modifier.width(44.dp)
        )
    }
}
