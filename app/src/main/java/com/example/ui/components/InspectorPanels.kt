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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.CinemaSfxPreset
import com.example.media.FontResolver
import com.example.model.AspectRatio
import com.example.model.AudioSettings
import com.example.model.CanvasBackdrop
import com.example.model.CanvasBackdropMode
import com.example.model.CanvasGradientPreset
import com.example.model.FilterPreset
import com.example.model.OverlayBlendMode
import com.example.model.OverlayLayer
import com.example.model.SafeZoneOverlay
import com.example.model.StudioFont
import com.example.model.TextMotionPreset
import com.example.model.VoiceEffectPreset
import com.example.model.formatTimecode
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
fun InspectorHeader(
    title: String,
    onClose: () -> Unit,
    trailingAction: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = ChalkWhite,
            modifier = Modifier.weight(1f)
        )
        trailingAction?.invoke()
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close inspector",
                tint = SilverMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FilterInspector(
    currentPreset: FilterPreset,
    intensity: Float,
    onSelectFilter: (FilterPreset, Float) -> Unit,
    onApplyAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            InspectorHeader(
                title = "Cinema Filters",
                onClose = onClose,
                trailingAction = {
                    TextButton(onClick = onApplyAll) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterPreset.entries.forEach { preset ->
                    val selected = preset == currentPreset
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectFilter(preset, intensity) }
                            .padding(vertical = 2.dp)
                            .testTag("filter_${preset.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(preset.swatchHex), ObsidianBg)
                                    )
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) CinemaAmber else BorderHairline,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = CinemaAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CinemaAmber else SilverMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (currentPreset != FilterPreset.ORIGINAL) {
                StudioSliderRow(
                    label = "Intensity",
                    valueText = "${(intensity * 100).roundToInt()}%",
                    value = intensity,
                    range = 0f..1f,
                    onValueChange = { onSelectFilter(currentPreset, it) }
                )
            }
        }
    }
}

@Composable
fun AudioMixerInspector(
    trackLabel: String,
    settings: AudioSettings,
    canExtractAudio: Boolean = false,
    isRecordingVoiceover: Boolean = false,
    voiceoverLevel: Float = 0f,
    voiceoverElapsedMs: Long = 0L,
    onToggleVoiceoverRecording: () -> Unit = {},
    onExtractAudio: () -> Unit = {},
    onPreviewSfx: (CinemaSfxPreset) -> Unit = {},
    onInsertSfx: (CinemaSfxPreset) -> Unit = {},
    onChange: (AudioSettings) -> Unit,
    onClose: () -> Unit
) {
    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 310.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 10.dp)
        ) {
            InspectorHeader(
                title = "Fairlight • $trackLabel",
                onClose = onClose,
                trailingAction = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (settings.isSolo) AmberContainer else ActiveRail)
                                .border(1.dp, if (settings.isSolo) CinemaAmber else BorderHairline, RoundedCornerShape(7.dp))
                                .clickable { onChange(settings.copy(isSolo = !settings.isSolo)) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("audio_solo_toggle")
                        ) {
                            Text(
                                text = "S",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.isSolo) CinemaAmber else ChalkWhite
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (settings.isMuted) ErrorCrimson.copy(alpha = 0.25f) else ActiveRail)
                                .border(1.dp, if (settings.isMuted) ErrorCrimson else BorderHairline, RoundedCornerShape(7.dp))
                                .clickable { onChange(settings.copy(isMuted = !settings.isMuted)) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("audio_mute_toggle")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(
                                    imageVector = if (settings.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Mute toggle",
                                    tint = if (settings.isMuted) ErrorCrimson else ChalkWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "M",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (settings.isMuted) ErrorCrimson else ChalkWhite
                                )
                            }
                        }
                    }
                }
            )

            // Direct Voiceover Recording Strip onto Audio Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isRecordingVoiceover) ErrorCrimson.copy(alpha = 0.16f) else ObsidianBg)
                    .border(
                        1.dp,
                        if (isRecordingVoiceover) ErrorCrimson else BorderHairline,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onToggleVoiceoverRecording,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isRecordingVoiceover) ErrorCrimson else CinemaAmber,
                        contentColor = if (isRecordingVoiceover) ChalkWhite else OnAmber
                    ),
                    modifier = Modifier.height(34.dp).testTag("voiceover_record_button")
                ) {
                    Icon(
                        imageVector = if (isRecordingVoiceover) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecordingVoiceover) "Stop voiceover" else "Record voiceover",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecordingVoiceover) "Stop VO" else "Voiceover",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRecordingVoiceover) "REC ${formatTimecode(voiceoverElapsedMs)}" else "Record live mic onto track",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecordingVoiceover) ErrorCrimson else SilverMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(ActiveRail)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = if (isRecordingVoiceover) voiceoverLevel.coerceIn(0.05f, 1f) else 0f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (voiceoverLevel > 0.8f) ErrorCrimson else CinemaAmber)
                        )
                    }
                }

                if (canExtractAudio) {
                    FilledTonalButton(
                        onClick = onExtractAudio,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ActiveRail,
                            contentColor = ChalkWhite
                        ),
                        modifier = Modifier.height(34.dp).testTag("btn_extract_audio")
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Extract audio", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Extract", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            StudioSliderRow(
                label = "Volume",
                valueText = "${(settings.volume * 100).roundToInt()}%",
                value = settings.volume,
                range = 0f..2.0f,
                onValueChange = { onChange(settings.copy(volume = it, isMuted = it <= 0.005f)) },
                testTag = "slider_audio_volume"
            )
            StudioSliderRow(
                label = "Fade In",
                valueText = String.format("%.1fs", settings.fadeInMs / 1000f),
                value = settings.fadeInMs.toFloat(),
                range = 0f..5000f,
                onValueChange = { onChange(settings.copy(fadeInMs = it.toLong())) },
                testTag = "slider_fade_in"
            )
            StudioSliderRow(
                label = "Fade Out",
                valueText = String.format("%.1fs", settings.fadeOutMs / 1000f),
                value = settings.fadeOutMs.toFloat(),
                range = 0f..5000f,
                onValueChange = { onChange(settings.copy(fadeOutMs = it.toLong())) },
                testTag = "slider_fade_out"
            )

            // Fairlight 3-Band EQ (Low 120Hz, Mid 1kHz, High 6kHz)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "3-Band Equalizer (Low / Mid / High)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaAmber
                )
                if (settings.hasActiveEq) {
                    Text(
                        text = "Flat EQ",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(ActiveRail)
                            .clickable { onChange(settings.copy(eqLowDb = 0f, eqMidDb = 0f, eqHighDb = 0f)) }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .testTag("audio_eq_reset_button")
                    )
                }
            }
            StudioSliderRow(
                label = "EQ Low",
                valueText = String.format("%+.0fdB", settings.eqLowDb),
                value = settings.eqLowDb,
                range = -12f..12f,
                onValueChange = { onChange(settings.copy(eqLowDb = it.roundToInt().toFloat())) },
                testTag = "slider_eq_low"
            )
            StudioSliderRow(
                label = "EQ Mid",
                valueText = String.format("%+.0fdB", settings.eqMidDb),
                value = settings.eqMidDb,
                range = -12f..12f,
                onValueChange = { onChange(settings.copy(eqMidDb = it.roundToInt().toFloat())) },
                testTag = "slider_eq_mid"
            )
            StudioSliderRow(
                label = "EQ High",
                valueText = String.format("%+.0fdB", settings.eqHighDb),
                value = settings.eqHighDb,
                range = -12f..12f,
                onValueChange = { onChange(settings.copy(eqHighDb = it.roundToInt().toFloat())) },
                testTag = "slider_eq_high"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Audio Pro DSP: Auto-Ducking, Voice Isolation & Noise Gate
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Duck Under Voice", style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                        Text("Lowers music automatically when voice speaks", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    }
                    Switch(
                        checked = settings.autoDuckMusic,
                        onCheckedChange = { onChange(settings.copy(autoDuckMusic = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAmber,
                            checkedTrackColor = CinemaAmber,
                            uncheckedThumbColor = SilverMuted,
                            uncheckedTrackColor = ActiveRail
                        ),
                        modifier = Modifier.testTag("switch_auto_duck")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Isolation DSP", style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                        Text("85Hz rumble filter + vocal clarity boost", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    }
                    Switch(
                        checked = settings.voiceIsolation,
                        onCheckedChange = { onChange(settings.copy(voiceIsolation = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAmber,
                            checkedTrackColor = CinemaAmber,
                            uncheckedThumbColor = SilverMuted,
                            uncheckedTrackColor = ActiveRail
                        ),
                        modifier = Modifier.testTag("switch_voice_isolation")
                    )
                }
            }

            StudioSliderRow(
                label = "Denoise",
                valueText = if (settings.denoiseStrength <= 0.02f) "Off" else "${(settings.denoiseStrength * 100).roundToInt()}%",
                value = settings.denoiseStrength,
                range = 0f..1f,
                onValueChange = { onChange(settings.copy(denoiseStrength = it)) },
                testTag = "slider_audio_denoise"
            )
            StudioSliderRow(
                label = "Noise Gate",
                valueText = if (settings.noiseGateDb <= -58f) "Off" else "${settings.noiseGateDb.roundToInt()}dB",
                value = settings.noiseGateDb,
                range = -60f..-12f,
                onValueChange = { onChange(settings.copy(noiseGateDb = it.roundToInt().toFloat())) },
                testTag = "slider_noise_gate"
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Voice Effects (Voice Changer DSP Presets)
            Text(
                text = "Voice Effects (${settings.voiceEffect.displayName})",
                style = MaterialTheme.typography.labelSmall,
                color = CinemaAmber,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                VoiceEffectPreset.entries.forEach { effect ->
                    val picked = settings.voiceEffect == effect
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (picked) AmberContainer else ObsidianBg)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { onChange(settings.copy(voiceEffect = effect)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("voice_fx_${effect.name.lowercase()}")
                    ) {
                        Text(
                            text = effect.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                        Text(
                            text = effect.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Offline Cinema SFX Library (Tap play to audition, tap + to drop at playhead)
            Text(
                text = "Built-in Cinema SFX (Offline)",
                style = MaterialTheme.typography.labelSmall,
                color = CinemaAmber,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CinemaSfxPreset.entries.forEach { sfx ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderHairline, RoundedCornerShape(9.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("sfx_card_${sfx.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ActiveRail)
                                .clickable { onPreviewSfx(sfx) }
                                .testTag("sfx_preview_${sfx.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Audition ${sfx.displayName}",
                                tint = CinemaAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(sfx.displayName, style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                            Text(sfx.category, style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AmberContainer)
                                .border(1.dp, CinemaAmber, RoundedCornerShape(6.dp))
                                .clickable { onInsertSfx(sfx) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("sfx_insert_${sfx.name.lowercase()}")
                        ) {
                            Text("+ Add", style = MaterialTheme.typography.labelSmall, color = CinemaAmber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextStyleInspector(
    overlay: OverlayLayer,
    availableFonts: List<StudioFont>,
    onUpdate: ((OverlayLayer) -> OverlayLayer) -> Unit,
    onReplayTextMotion: () -> Unit,
    onImportFont: () -> Unit,
    onMoveLayerZ: (Boolean) -> Unit,
    onResetTransform: () -> Unit,
    onClose: () -> Unit
) {
    val colorSwatches = remember {
        listOf(
            0xFFF4F5F7L, 0xFFF59E0BL, 0xFFFDE047L, 0xFF38BDF8L,
            0xFF34D399L, 0xFFFB7185L, 0xFFC084FCL, 0xFF0B0C0EL
        )
    }
    var activeSubTab by remember(overlay.id) { mutableStateOf(0) } // 0 = Style, 1 = Motion

    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            InspectorHeader(
                title = "Text Layer • Z${overlay.zIndex}",
                onClose = onClose,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMoveLayerZ(false) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Send backward", tint = SilverMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onMoveLayerZ(true) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Bring forward", tint = SilverMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onResetTransform, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset transform", tint = SilverMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            // Style vs Motion Preset Sub-Tab Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(0, "Style & Font", Icons.Default.TextFields),
                    Triple(
                        1,
                        if (overlay.textMotionPreset == TextMotionPreset.NONE) "Motion Presets"
                        else "Motion • ${overlay.textMotionPreset.displayName}",
                        Icons.Default.Animation
                    )
                ).forEach { (tabIdx, label, icon) ->
                    val selected = activeSubTab == tabIdx
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
                            .clickable { activeSubTab = tabIdx }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag(if (tabIdx == 0) "text_subtab_style" else "text_subtab_motion"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) CinemaAmber else SilverMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) CinemaAmber else ChalkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeSubTab == 1) {
                TextMotionPresetLibrary(
                    selectedPreset = overlay.textMotionPreset,
                    durationMs = overlay.textMotionDurationMs,
                    onSelectPreset = { preset ->
                        val nextDur = if (preset != TextMotionPreset.NONE && overlay.textMotionPreset != preset) {
                            preset.defaultDurationMs
                        } else {
                            overlay.textMotionDurationMs
                        }
                        onUpdate {
                            it.copy(
                                textMotionPreset = preset,
                                textMotionDurationMs = nextDur
                            )
                        }
                        if (preset != TextMotionPreset.NONE) {
                            onReplayTextMotion()
                        }
                    },
                    onChangeDurationMs = { dur ->
                        onUpdate { it.copy(textMotionDurationMs = dur.coerceIn(300L, 2500L)) }
                    },
                    onReplayPreview = onReplayTextMotion
                )
            } else {
                OutlinedTextField(
                    value = overlay.text,
                    onValueChange = { txt -> onUpdate { it.copy(text = txt) } },
                    placeholder = { Text("Enter title text…") },
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
                        .testTag("text_overlay_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Font Picker Strip + Custom Font Import Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onImportFont,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaAmber,
                            contentColor = OnAmber
                        ),
                        modifier = Modifier.height(34.dp).testTag("inspector_import_font_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Import .TTF", style = MaterialTheme.typography.labelMedium)
                    }

                    availableFonts.forEach { font ->
                        val selected = font.id == overlay.fontId
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
                                .clickable { onUpdate { it.copy(fontId = font.id) } }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = font.displayName,
                                fontFamily = family,
                                color = if (selected) CinemaAmber else ChalkWhite,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Color Swatches + Style Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorSwatches.forEach { argb ->
                        val isPicked = overlay.textColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) CinemaAmber else BorderHairline,
                                    shape = CircleShape
                                )
                                .clickable { onUpdate { it.copy(textColorArgb = argb) } }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = overlay.hasStroke,
                        onClick = { onUpdate { it.copy(hasStroke = !it.hasStroke) } },
                        label = { Text("Stroke", style = MaterialTheme.typography.labelMedium) }
                    )
                    FilterChip(
                        selected = overlay.hasDropShadow,
                        onClick = { onUpdate { it.copy(hasDropShadow = !it.hasDropShadow) } },
                        label = { Text("Shadow", style = MaterialTheme.typography.labelMedium) }
                    )
                    FilterChip(
                        selected = overlay.hasBackground,
                        onClick = { onUpdate { it.copy(hasBackground = !it.hasBackground) } },
                        label = { Text("Backdrop", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                StudioSliderRow(
                    label = "Font Size",
                    valueText = "${overlay.fontSizeSp.roundToInt()}sp",
                    value = overlay.fontSizeSp,
                    range = 14f..72f,
                    onValueChange = { size -> onUpdate { it.copy(fontSizeSp = size) } }
                )
                StudioSliderRow(
                    label = "Opacity",
                    valueText = "${(overlay.opacity * 100).roundToInt()}%",
                    value = overlay.opacity,
                    range = 0.1f..1f,
                    onValueChange = { op -> onUpdate { it.copy(opacity = op) } }
                )
            }
        }
    }
}

@Composable
fun ImageOverlayInspector(
    overlay: OverlayLayer,
    onUpdate: (Float, Float, OverlayBlendMode) -> Unit,
    onMoveLayerZ: (Boolean) -> Unit,
    onResetTransform: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            InspectorHeader(
                title = "PNG / Image Layer • Z${overlay.zIndex}",
                onClose = onClose,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMoveLayerZ(false) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Send backward", tint = SilverMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onMoveLayerZ(true) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Bring forward", tint = SilverMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onResetTransform, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset transform", tint = SilverMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )
            // Blend Mode Selector Chips (Normal, Screen, Multiply, Overlay)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Blend", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                OverlayBlendMode.entries.forEach { mode ->
                    val picked = overlay.blendMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onUpdate(overlay.opacity, overlay.cornerRadiusDp, mode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("blend_mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                    }
                }
            }
            StudioSliderRow(
                label = "Opacity",
                valueText = "${(overlay.opacity * 100).roundToInt()}%",
                value = overlay.opacity,
                range = 0.05f..1f,
                onValueChange = { onUpdate(it, overlay.cornerRadiusDp, overlay.blendMode) }
            )
            StudioSliderRow(
                label = "Corners",
                valueText = "${overlay.cornerRadiusDp.roundToInt()}dp",
                value = overlay.cornerRadiusDp,
                range = 0f..48f,
                onValueChange = { onUpdate(overlay.opacity, it, overlay.blendMode) }
            )
        }
    }
}

@Composable
fun CanvasAspectInspector(
    current: AspectRatio,
    backdrop: CanvasBackdrop = CanvasBackdrop(),
    safeZone: SafeZoneOverlay = SafeZoneOverlay.NONE,
    onSelect: (AspectRatio) -> Unit,
    onUpdateBackdrop: (CanvasBackdrop) -> Unit = {},
    onUpdateSafeZone: (SafeZoneOverlay) -> Unit = {},
    onClose: () -> Unit
) {
    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 14.dp)
        ) {
            InspectorHeader(title = "Canvas, Backdrop & Safe Zones", onClose = onClose)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AspectRatio.entries.forEach { ratio ->
                    val selected = ratio == current
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) AmberContainer else ActiveRail)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(ratio) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("aspect_${ratio.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = ratio.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) CinemaAmber else ChalkWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = ratio.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas Backdrop Mode (Obsidian, Blur Fill, Studio Gradient)
            Text(
                text = "Canvas Backdrop",
                style = MaterialTheme.typography.labelSmall,
                color = CinemaAmber,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CanvasBackdropMode.entries.forEach { mode ->
                    val picked = backdrop.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onUpdateBackdrop(backdrop.copy(mode = mode)) }
                            .padding(vertical = 7.dp)
                            .testTag("backdrop_mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                    }
                }
            }

            if (backdrop.mode == CanvasBackdropMode.GRADIENT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CanvasGradientPreset.entries.forEach { preset ->
                        val picked = backdrop.gradientPreset == preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdateBackdrop(backdrop.copy(gradientPreset = preset)) }
                                .testTag("backdrop_grad_${preset.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(preset.startArgb), Color(preset.endArgb))
                                        )
                                    )
                                    .border(
                                        width = if (picked) 2.dp else 1.dp,
                                        color = if (picked) CinemaAmber else BorderHairline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = preset.displayName.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) CinemaAmber else SilverMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Social Safe Zone Overlay Selector
            Text(
                text = "Social Safe Zone Guides",
                style = MaterialTheme.typography.labelSmall,
                color = CinemaAmber,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SafeZoneOverlay.entries.forEach { zone ->
                    val picked = zone == safeZone
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onUpdateSafeZone(zone) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("canvas_safe_zone_${zone.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = zone.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) CinemaAmber else ChalkWhite,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FontLibraryInspector(
    fonts: List<StudioFont>,
    onImportFont: () -> Unit,
    onDeleteFont: (StudioFont) -> Unit,
    onClose: () -> Unit
) {
    Surface(color = InspectorCharcoal, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            InspectorHeader(
                title = "Studio Fonts (${fonts.size})",
                onClose = onClose,
                trailingAction = {
                    FilledTonalButton(
                        onClick = onImportFont,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaAmber,
                            contentColor = OnAmber
                        ),
                        modifier = Modifier.height(32.dp).testTag("font_library_import_button")
                    ) {
                        Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .TTF / .OTF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                fonts.forEach { font ->
                    val family = remember(font.id, fonts) {
                        FontResolver.resolveComposeFont(font.id, fonts)
                    }
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActiveRail)
                            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = family,
                                style = MaterialTheme.typography.headlineSmall,
                                color = CinemaAmber,
                                modifier = Modifier.weight(1f)
                            )
                            if (font.isCustom) {
                                IconButton(
                                    onClick = { onDeleteFont(font) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete custom font",
                                        tint = ErrorCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = font.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChalkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (font.isCustom) "Imported" else "Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String? = null
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
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = ChalkWhite,
            modifier = Modifier.width(46.dp)
        )
    }
}
