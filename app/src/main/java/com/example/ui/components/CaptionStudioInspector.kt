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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import com.example.ai.CaptionOutputMode
import com.example.media.FontResolver
import com.example.media.OfflineLanguageCatalog
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
import com.example.ui.theme.TrackOverlayEmerald
import kotlin.math.roundToInt

@Composable
fun CaptionStudioInspector(
    captions: List<CaptionClip>,
    selectedCaption: CaptionClip?,
    safeZone: SafeZoneOverlay,
    availableFonts: List<StudioFont>,
    activeAiProviderBadge: String = "Built-in Gemini",
    requiresGoogleSignInForCloud: Boolean = false,
    autoCaptionLoading: Boolean = false,
    autoCaptionStatus: String? = null,
    autoCaptionError: String? = null,
    isLiveMicDictating: Boolean = false,
    liveMicPartialText: String = "",
    onRunGeminiAutoCaptions: (sourceLang: String, outputMode: CaptionOutputMode, targetLang: String) -> Unit = { _, _, _ -> },
    onRunOfflineVideoAutoCaptions: (sourceLang: String, outputMode: CaptionOutputMode, targetLang: String) -> Unit = { _, _, _ -> },
    onToggleOfflineMicDictation: (sourceLang: String, outputMode: CaptionOutputMode) -> Unit = { _, _ -> },
    onTransformExistingCaptions: (outputMode: CaptionOutputMode, targetLang: String) -> Unit = { _, _ -> },
    onOpenAiLoginSetup: () -> Unit = {},
    onOpenOfflineModelsSetup: () -> Unit = {},
    onOpenLocalTtsStudio: () -> Unit = {},
    onGoogleSignIn: () -> Unit = {},
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
    var captionOutputMode by remember { mutableStateOf(CaptionOutputMode.ORIGINAL_SCRIPT) }
    var targetOutputLanguage by remember { mutableStateOf("English") }
    val languages = remember { OfflineLanguageCatalog.captionChipOptions }
    val targetLanguages = remember { OfflineLanguageCatalog.languages.map { it.displayName } }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Engine: $activeAiProviderBadge",
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveRail)
                                .border(1.dp, CinemaAmber.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .clickable { onOpenAiLoginSetup() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("btn_open_ai_login")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(12.dp))
                                Text("AI Login", style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveRail)
                                .border(1.dp, TrackOverlayEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .clickable { onOpenOfflineModelsSetup() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("btn_open_offline_models")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TrackOverlayEmerald, modifier = Modifier.size(12.dp))
                                Text("Offline Models", style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                            }
                        }
                    }
                }

                // 18+ Spoken Audio Language selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Audio", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    Row(
                        modifier = Modifier
                            .weight(1f)
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
                                    .testTag("caption_src_lang_${lang.lowercase()}")
                            ) {
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (picked) CinemaAmber else ChalkWhite
                                )
                            }
                        }
                    }
                }

                // Caption Output Mode: Original Script | Translate to Any Language | English Phonetic (Pronounce-as-Audio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Output", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CaptionOutputMode.entries.forEach { mode ->
                            val picked = captionOutputMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (picked) TrackOverlayEmerald.copy(alpha = 0.22f) else ActiveRail)
                                    .border(
                                        1.dp,
                                        if (picked) TrackOverlayEmerald else BorderHairline,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { captionOutputMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("caption_output_mode_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = mode.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (picked) TrackOverlayEmerald else ChalkWhite
                                )
                            }
                        }
                    }
                }

                // Target Translation Language picker (shown when Translate mode is selected)
                if (captionOutputMode == CaptionOutputMode.TRANSLATE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("To", style = MaterialTheme.typography.labelSmall, color = TrackOverlayEmerald)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            targetLanguages.forEach { target ->
                                val picked = target == targetOutputLanguage
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (picked) AmberContainer else ActiveRail)
                                        .border(
                                            1.dp,
                                            if (picked) CinemaAmber else BorderHairline,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { targetOutputLanguage = target }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("caption_target_lang_${target.lowercase()}")
                                ) {
                                    Text(
                                        text = target,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (picked) CinemaAmber else ChalkWhite
                                    )
                                }
                            }
                        }
                    }
                } else if (captionOutputMode == CaptionOutputMode.PHONETIC_ENGLISH) {
                    Text(
                        text = "Writes English letters pronounced like the spoken audio (e.g. Telugu 'ఎలా ఉన్నారు' → 'Ela unnaru').",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrackOverlayEmerald
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onRunGeminiAutoCaptions(selectedLanguage, captionOutputMode, targetOutputLanguage) },
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
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Working…", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Cloud AI", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }

                    FilledTonalButton(
                        onClick = { onRunOfflineVideoAutoCaptions(selectedLanguage, captionOutputMode, targetOutputLanguage) },
                        enabled = !autoCaptionLoading && !isLiveMicDictating,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = TrackOverlayEmerald.copy(alpha = 0.22f),
                            contentColor = TrackOverlayEmerald
                        ),
                        modifier = Modifier
                            .weight(1.05f)
                            .height(38.dp)
                            .testTag("btn_auto_captions_offline_video")
                    ) {
                        Icon(Icons.Default.OfflineBolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Offline AI", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = { onToggleOfflineMicDictation(selectedLanguage, captionOutputMode) },
                        enabled = !autoCaptionLoading,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isLiveMicDictating) ErrorCrimson else ActiveRail,
                            contentColor = ChalkWhite
                        ),
                        modifier = Modifier
                            .weight(0.85f)
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

                // 1-Tap Convert Existing Captions (Translate to target language OR Convert to Phonetic English)
                if (captions.isNotEmpty() && captionOutputMode != CaptionOutputMode.ORIGINAL_SCRIPT) {
                    FilledTonalButton(
                        onClick = { onTransformExistingCaptions(captionOutputMode, targetOutputLanguage) },
                        enabled = !autoCaptionLoading && !isLiveMicDictating,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ActiveRail,
                            contentColor = CinemaAmber
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("btn_transform_existing_captions")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (captionOutputMode == CaptionOutputMode.TRANSLATE) {
                                "Translate ${captions.size} Existing Captions → $targetOutputLanguage"
                            } else {
                                "Convert ${captions.size} Captions → English Phonetic"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (requiresGoogleSignInForCloud) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AmberContainer.copy(alpha = 0.55f))
                            .border(1.dp, CinemaAmber.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .clickable { onGoogleSignIn() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("caption_inline_google_signin"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Sign in with Google for Cloud Gemini AI (or use Offline AI free)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChalkWhite,
                            modifier = Modifier.weight(1f)
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
                FilledTonalButton(
                    onClick = onOpenLocalTtsStudio,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ActiveRail,
                        contentColor = TrackOverlayEmerald
                    ),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("btn_open_local_tts")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("TTS Dub", style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
