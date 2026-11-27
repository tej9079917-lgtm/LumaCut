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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.media.SpeedRampPreset
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SlateDim
import com.example.ui.theme.TrackAudioRose
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SpeedInspector(
    clip: MainClip?,
    onUpdateSpeed: (speed: Float, pitchSemitones: Float, preservePitch: Boolean) -> Unit,
    onInsertFreezeFrame: () -> Unit = {},
    onApplySpeedRamp: (SpeedRampPreset) -> Unit = {},
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speed_inspector")
    ) {
        if (clip == null || clip.mediaType != MainMediaType.VIDEO) {
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                InspectorHeader(title = "Speed & Pitch", onClose = onClose)
                Text(
                    text = "Select a video clip on the timeline to adjust playback speed (0.25x–4x) and audio pitch shift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            return@Surface
        }

        val speed = clip.safeSpeed
        val pitchSt = clip.pitchShiftSemitones.coerceIn(-12f, 12f)
        val preservePitch = clip.preservePitch
        val isModified = abs(speed - 1f) > 0.01f || abs(pitchSt) > 0.1f || !preservePitch

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Speed • ${clip.displayName}",
                onClose = onClose,
                trailingAction = {
                    TextButton(
                        onClick = onReset,
                        enabled = isModified,
                        modifier = Modifier.testTag("speed_reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset speed and pitch", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            // 1. Speed Preset Chips (0.25x .. 4.0x)
            val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                presets.forEach { preset ->
                    val picked = abs(speed - preset) < 0.03f
                    val label = if (preset % 1f == 0f) "${preset.toInt()}x" else "${preset}x"
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) AmberContainer else ActiveRail)
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) CinemaAmber else BorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onUpdateSpeed(preset, pitchSt, preservePitch) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("speed_preset_${preset.toString().replace('.', '_')}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) CinemaAmber else ChalkWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1B. 1-Tap Freeze Frame + Multi-Segment Speed Ramping Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(15.dp))
                    Text("Speed Ramp Curves", style = MaterialTheme.typography.labelMedium, color = ChalkWhite)
                }
                FilledTonalButton(
                    onClick = onInsertFreezeFrame,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmberContainer,
                        contentColor = CinemaAmber
                    ),
                    modifier = Modifier.height(32.dp).testTag("btn_freeze_frame")
                ) {
                    Icon(Icons.Default.AcUnit, contentDescription = "Insert freeze frame", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Freeze Frame", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SpeedRampPreset.entries.forEach { ramp ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                            .clickable { onApplySpeedRamp(ramp) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("speed_ramp_${ramp.name.lowercase()}")
                    ) {
                        Text(
                            text = ramp.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = CinemaAmber
                        )
                        Text(
                            text = ramp.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Continuous Playback Rate Slider (0.5x to 4.0x) + Duration Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback speed",
                    tint = CinemaAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Playback Rate",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkWhite,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format("%.2fx • %.1fs", speed, clip.durationMs / 1000f),
                    style = MaterialTheme.typography.labelMedium,
                    color = CinemaAmber,
                    modifier = Modifier.testTag("speed_value_readout")
                )
            }
            Slider(
                value = speed,
                onValueChange = { raw ->
                    val snapped = (raw * 20f).roundToInt() / 20f
                    onUpdateSpeed(snapped.coerceIn(0.25f, 4.0f), pitchSt, preservePitch)
                },
                valueRange = 0.25f..4.0f,
                colors = SliderDefaults.colors(
                    thumbColor = CinemaAmber,
                    activeTrackColor = CinemaAmber,
                    inactiveTrackColor = ActiveRail
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .testTag("slider_playback_speed")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Audio Pitch Shifting Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = TrackAudioRose,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preserve Voice Pitch",
                            style = MaterialTheme.typography.labelMedium,
                            color = ChalkWhite
                        )
                        Text(
                            text = if (preservePitch) {
                                "Independent pitch (${String.format("%.2fx", clip.effectiveAudioPitch)})"
                            } else {
                                "Tape-linked with speed (${String.format("%.2fx", clip.effectiveAudioPitch)})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                    Switch(
                        checked = preservePitch,
                        onCheckedChange = { keep -> onUpdateSpeed(speed, pitchSt, keep) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAmber,
                            checkedTrackColor = CinemaAmber,
                            uncheckedThumbColor = SilverMuted,
                            uncheckedTrackColor = ActiveRail
                        ),
                        modifier = Modifier.testTag("switch_preserve_pitch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Pitch Shift Semitones Slider (-12 st .. +12 st)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pitch Shift",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMuted,
                        modifier = Modifier.weight(1f)
                    )
                    listOf(-6f to "Deep", 0f to "Normal", 6f to "High").forEach { (st, lbl) ->
                        val picked = abs(pitchSt - st) < 0.4f
                        Box(
                            modifier = Modifier
                                .padding(start = 5.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (picked) AmberContainer else ActiveRail)
                                .border(
                                    1.dp,
                                    if (picked) CinemaAmber else BorderHairline,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdateSpeed(speed, st, preservePitch) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) CinemaAmber else ChalkWhite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (abs(pitchSt) < 0.2f) "0 st" else String.format("%+d st", pitchSt.roundToInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (abs(pitchSt) >= 0.5f) TrackAudioRose else ChalkWhite,
                        modifier = Modifier.testTag("pitch_shift_readout")
                    )
                }

                Slider(
                    value = pitchSt,
                    onValueChange = { raw ->
                        val snapped = raw.roundToInt().toFloat().coerceIn(-12f, 12f)
                        onUpdateSpeed(speed, snapped, preservePitch)
                    },
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(
                        thumbColor = TrackAudioRose,
                        activeTrackColor = TrackAudioRose,
                        inactiveTrackColor = ActiveRail
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_pitch_shift")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-12 st (-1 Oct)", style = MaterialTheme.typography.labelSmall, color = SlateDim)
                    Text("+12 st (+1 Oct)", style = MaterialTheme.typography.labelSmall, color = SlateDim)
                }
            }
        }
    }
}
