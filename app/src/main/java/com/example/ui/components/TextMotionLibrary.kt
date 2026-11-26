package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TextMotionEvaluator
import com.example.model.TextMotionPreset
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted

/**
 * Interactive Text Motion Preset Library powered by Jetpack Compose animation APIs.
 * Each preset card runs a live looping preview animation so editors can audition
 * Typewriter, Fade In, Slide Up, Slide In, Bounce Pop, Cinema Spread, and Kinetic Pulse.
 */
@Composable
fun TextMotionPresetLibrary(
    selectedPreset: TextMotionPreset,
    durationMs: Long,
    onSelectPreset: (TextMotionPreset) -> Unit,
    onChangeDurationMs: (Long) -> Unit,
    onReplayPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Shared Compose InfiniteTransition drives live motion audition previews across all tiles
    val infiniteTransition = rememberInfiniteTransition(label = "text_motion_library_loop")
    val loopPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1.45f, // 0..1 animates entrance, 1..1.45 holds cleanly before looping
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "preset_card_phase"
    )
    val cardProgress = loopPhase.coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Motion Presets",
                style = MaterialTheme.typography.labelMedium,
                color = SilverMuted,
                modifier = Modifier.weight(1f)
            )
            if (selectedPreset != TextMotionPreset.NONE) {
                FilledTonalButton(
                    onClick = onReplayPreview,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmberContainer,
                        contentColor = CinemaAmber
                    ),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("text_motion_replay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay text animation",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replay", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextMotionPreset.entries.forEach { preset ->
                val isSelected = preset == selectedPreset
                TextMotionPresetCard(
                    preset = preset,
                    isSelected = isSelected,
                    previewProgress = cardProgress,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }

        if (selectedPreset != TextMotionPreset.NONE) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverMuted,
                    modifier = Modifier.width(74.dp)
                )
                Slider(
                    value = durationMs.toFloat().coerceIn(300f, 2500f),
                    onValueChange = { onChangeDurationMs(it.toLong()) },
                    valueRange = 300f..2500f,
                    colors = SliderDefaults.colors(
                        thumbColor = CinemaAmber,
                        activeTrackColor = CinemaAmber,
                        inactiveTrackColor = ActiveRail
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("slider_text_motion_duration")
                )
                Text(
                    text = String.format("%.1fs", durationMs / 1000f),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkWhite,
                    modifier = Modifier.width(44.dp)
                )
            }
        }
    }
}

@Composable
private fun TextMotionPresetCard(
    preset: TextMotionPreset,
    isSelected: Boolean,
    previewProgress: Float,
    onClick: () -> Unit
) {
    val sampleWord = if (preset == TextMotionPreset.TYPEWRITER) "TYPE" else "Aa"
    val frame = TextMotionEvaluator.evaluate(
        preset = preset,
        fullText = sampleWord,
        rawProgress = if (preset == TextMotionPreset.NONE) 1f else previewProgress
    )
    val renderedSample = if (preset == TextMotionPreset.TYPEWRITER) {
        TextMotionEvaluator.sliceVisibleText(sampleWord, frame)
    } else {
        sampleWord
    }

    Column(
        modifier = Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AmberContainer else ActiveRail)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CinemaAmber else BorderHairline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp)
            .testTag("text_motion_${preset.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live animated preview viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ObsidianBg.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = renderedSample,
                color = if (isSelected) CinemaAmber else ChalkWhite,
                fontSize = if (preset == TextMotionPreset.TYPEWRITER) 13.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (frame.letterSpacingSp * 0.35f).sp,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    alpha = frame.alpha.coerceIn(0f, 1f)
                    translationX = frame.offsetXFraction * 72f
                    translationY = frame.offsetYFraction * 44f
                    scaleX = frame.scale
                    scaleY = frame.scale
                    rotationZ = frame.rotationDegrees
                }
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) CinemaAmber else ChalkWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = preset.subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = SilverMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
