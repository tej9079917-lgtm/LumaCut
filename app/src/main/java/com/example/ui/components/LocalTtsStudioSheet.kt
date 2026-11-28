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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.LocalVoiceCloneProfile
import com.example.media.OfflineLanguageCatalog
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
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackOverlayEmerald
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTtsStudioBottomSheet(
    initialScript: String,
    captionsScript: String,
    isSynthesizing: Boolean,
    errorMessage: String?,
    onSynthesizeToTimeline: (
        script: String,
        language: String,
        profile: LocalVoiceCloneProfile,
        pitch: Float,
        speed: Float
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var script by remember(initialScript) { mutableStateOf(initialScript) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedProfile by remember { mutableStateOf(LocalVoiceCloneProfile.NATURAL) }
    var pitch by remember { mutableFloatStateOf(LocalVoiceCloneProfile.NATURAL.defaultPitch) }
    var speed by remember { mutableFloatStateOf(LocalVoiceCloneProfile.NATURAL.defaultSpeed) }
    val languages = remember { OfflineLanguageCatalog.captionChipOptions }

    ModalBottomSheet(
        onDismissRequest = { if (!isSynthesizing) onDismiss() },
        sheetState = sheetState,
        containerColor = InspectorCharcoal,
        contentColor = ChalkWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .testTag("local_tts_studio_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = TrackOverlayEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Local AI Voice & TTS Studio", style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    Text(
                        text = "100% On-Device Voice Synthesis & Timbre Cloning • Zero Cloud Upload",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrackOverlayEmerald
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Voice TTS Studio", tint = SilverMuted)
                }
            }

            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                label = { Text("Narration or Dubbing Script") },
                placeholder = { Text("Type dialogue or pull from timeline captions…") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAmber,
                    unfocusedBorderColor = BorderHairline,
                    focusedTextColor = ChalkWhite,
                    unfocusedTextColor = ChalkWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_tts_script_input")
            )

            if (captionsScript.isNotBlank()) {
                OutlinedButton(
                    onClick = { script = captionsScript },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("local_tts_pull_captions_button")
                ) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pull Text From Timeline Captions", color = ChalkWhite, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Voice Style / Timbre Clone Profile Selector
            Text("Voice Style & Timbre Profile", style = MaterialTheme.typography.labelLarge, color = SilverMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalVoiceCloneProfile.entries.forEach { profile ->
                    val picked = selectedProfile == profile
                    Column(
                        modifier = Modifier
                            .width(128.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (picked) AmberContainer else StudioSlate)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedProfile = profile
                                pitch = profile.defaultPitch
                                speed = profile.defaultSpeed
                            }
                            .padding(10.dp)
                            .testTag("tts_profile_${profile.name.lowercase()}")
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (picked) CinemaAmber else ChalkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Offline Language Selector
            Text("Voice Language (On-Device)", style = MaterialTheme.typography.labelLarge, color = SilverMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                languages.forEach { lang ->
                    val picked = selectedLanguage == lang
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(1.dp, if (picked) CinemaAmber else BorderHairline, RoundedCornerShape(7.dp))
                            .clickable { selectedLanguage = lang }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                    }
                }
            }

            // Custom Voice Timbre Pitch & Cadence Tuning
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Voice Timbre Pitch", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                    Text("${(pitch * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                }
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.55f..1.75f,
                    colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speech Cadence Speed", style = MaterialTheme.typography.labelMedium, color = SilverMuted)
                    Text(String.format("%.2fx", speed), style = MaterialTheme.typography.labelMedium, color = CinemaAmber)
                }
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.60f..1.65f,
                    colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = ErrorCrimson)
            }

            Button(
                enabled = !isSynthesizing && script.isNotBlank(),
                onClick = {
                    onSynthesizeToTimeline(script, selectedLanguage, selectedProfile, pitch, speed)
                },
                colors = ButtonDefaults.buttonColors(containerColor = TrackOverlayEmerald, contentColor = ObsidianBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("synthesize_tts_to_timeline_button")
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(
                        color = ObsidianBg,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synthesizing WAV Offline…", style = MaterialTheme.typography.titleSmall)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synthesize & Add to Audio Track", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
