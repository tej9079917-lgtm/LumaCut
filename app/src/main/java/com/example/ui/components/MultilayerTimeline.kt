package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.AudioClip
import com.example.model.FilterPreset
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.OverlayBlendMode
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import com.example.model.TransitionType
import com.example.model.formatShortDuration
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SlateDim
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackAudioBg
import com.example.ui.theme.TrackAudioRose
import com.example.ui.theme.TrackOverlayBg
import com.example.ui.theme.TrackOverlayEmerald
import com.example.ui.theme.TrackTextAmber
import com.example.ui.theme.TrackTextBg
import com.example.ui.theme.TrackVideoBg
import com.example.ui.theme.TrackVideoBlue
import com.example.ui.theme.TransitionGold
import com.example.viewmodel.SelectedItem
import kotlin.math.roundToInt

@Composable
fun MultilayerTimeline(
    timeline: TimelineDocument,
    playheadMs: Long,
    isPlaying: Boolean,
    zoom: Float,
    selection: SelectedItem,
    onSeek: (Long) -> Unit,
    onSelectMainClip: (String) -> Unit,
    onSelectOverlay: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectTransition: (String) -> Unit,
    onTrimMainClip: (String, Long, Long) -> Unit,
    onTrimOverlay: (String, Long, Long) -> Unit,
    onTrimAudio: (String, Long, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSecDp = (64f * zoom).coerceIn(28f, 192f)
    val density = LocalDensity.current
    val pxPerMs = with(density) { (pxPerSecDp.dp.toPx()) / 1000f }

    val totalDurationMs = maxOf(timeline.totalDurationMs, 10_000L)
    val totalTrackWidthDp = ((totalDurationMs / 1000f) * pxPerSecDp).dp
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSlate)
            .testTag("multilayer_timeline")
    ) {
        val halfViewportDp = maxWidth / 2f
        val halfViewportPx = with(density) { halfViewportDp.toPx() }

        // Keep playhead centered during active playback
        LaunchedEffect(playheadMs, isPlaying, pxPerMs) {
            if (isPlaying) {
                val targetScroll = (playheadMs * pxPerMs).roundToInt()
                hScrollState.scrollTo(targetScroll)
            }
        }

        // Scrollable multi-track surface
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScrollState)
                .verticalScroll(vScrollState)
                .pointerInput(pxPerMs, halfViewportPx) {
                    detectTapGestures { offset ->
                        val ms = ((offset.x - halfViewportPx) / pxPerMs).toLong().coerceAtLeast(0L)
                        onSeek(ms)
                    }
                }
        ) {
            val allTimelineBeatsMs = remember(timeline.audioClips) {
                timeline.audioClips
                    .flatMap { it.timelineBeatPositionsMs() }
                    .distinct()
                    .sorted()
            }

            // 1. Time Ruler Row (with amber audio beat markers)
            TimeRulerRow(
                totalDurationMs = totalDurationMs,
                beatPositionsMs = allTimelineBeatsMs,
                pxPerSecDp = pxPerSecDp,
                leadingPadding = halfViewportDp,
                trailingPadding = halfViewportDp,
                onScrubDeltaPx = { deltaPx ->
                    val deltaMs = (deltaPx / pxPerMs).toLong()
                    onSeek((playheadMs + deltaMs).coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L)))
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Stacked Overlay Layers (V2, V3... highest zIndex on top)
            val overlaysDesc = remember(timeline.overlays) {
                timeline.overlays.sortedByDescending { it.zIndex }
            }
            if (overlaysDesc.isNotEmpty()) {
                for (layer in overlaysDesc) {
                    val trackCode = "V${(layer.zIndex + 1).coerceAtLeast(2)}"
                    OverlayTrackRow(
                        trackCode = trackCode,
                        layer = layer,
                        isSelected = (selection as? SelectedItem.OverlayItem)?.overlayId == layer.id,
                        pxPerSecDp = pxPerSecDp,
                        pxPerMs = pxPerMs,
                        leadingPadding = halfViewportDp,
                        trailingPadding = halfViewportDp,
                        totalWidthDp = totalTrackWidthDp,
                        onSelect = { onSelectOverlay(layer.id) },
                        onTrim = { newStart, newDur -> onTrimOverlay(layer.id, newStart, newDur) }
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }
            } else {
                TrackHintStrip(
                    trackCode = "V2",
                    icon = Icons.Default.Layers,
                    label = "Overlay Layers (PNG, Chroma Video & Titles)",
                    accent = TrackOverlayEmerald,
                    leadingPadding = halfViewportDp
                )
                Spacer(modifier = Modifier.height(5.dp))
            }

            // 3. Main Video / Photo Track (V1) + Diamond Transition Nodes + Beat Sync Guides
            MainTrackRow(
                timeline = timeline,
                selection = selection,
                beatPositionsMs = allTimelineBeatsMs,
                pxPerSecDp = pxPerSecDp,
                pxPerMs = pxPerMs,
                leadingPadding = halfViewportDp,
                trailingPadding = halfViewportDp,
                onSelectClip = onSelectMainClip,
                onSelectTransition = onSelectTransition,
                onTrimClip = onTrimMainClip
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Multi-Lane Audio Tracks (A1, A2...) with Visual Waveforms, Beat Markers, SOLO/EQ & Fade Ramps
            val audioLanes = remember(timeline.audioClips) {
                timeline.audioClips.groupBy { it.laneIndex }.toSortedMap()
            }
            if (audioLanes.isNotEmpty()) {
                audioLanes.forEach { (laneIdx, clips) ->
                    AudioTrackLaneRow(
                        trackCode = "A${laneIdx + 1}",
                        clips = clips,
                        selectedAudioId = (selection as? SelectedItem.AudioItem)?.audioId,
                        pxPerSecDp = pxPerSecDp,
                        pxPerMs = pxPerMs,
                        leadingPadding = halfViewportDp,
                        trailingPadding = halfViewportDp,
                        totalWidthDp = totalTrackWidthDp,
                        onSelectAudio = onSelectAudio,
                        onTrimAudio = onTrimAudio
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }
            } else {
                TrackHintStrip(
                    trackCode = "A1",
                    icon = Icons.Default.MusicNote,
                    label = "Audio Tracks (Music, Voiceover & 3-Band EQ)",
                    accent = TrackAudioRose,
                    leadingPadding = halfViewportDp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Playhead Needle Indicator (positioned at playheadMs relative to horizontal scroll)
        val playheadX = (halfViewportPx + playheadMs * pxPerMs - hScrollState.value).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(playheadX - 8, 0) }
                .fillMaxHeight()
                .width(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Glowing Amber Cap
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .rotate(45f)
                    .background(CinemaAmber, RoundedCornerShape(2.dp))
            )
            // Vertical Playhead Line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(CinemaAmber)
            )
        }
    }
}

@Composable
private fun TimeRulerRow(
    totalDurationMs: Long,
    beatPositionsMs: List<Long>,
    pxPerSecDp: Float,
    leadingPadding: Dp,
    trailingPadding: Dp,
    onScrubDeltaPx: (Float) -> Unit
) {
    val totalSecs = ((totalDurationMs / 1000L) + 4L).toInt()
    val stepSecs = when {
        pxPerSecDp < 40f -> 5
        pxPerSecDp < 75f -> 2
        else -> 1
    }

    Row(
        modifier = Modifier
            .height(26.dp)
            .background(InspectorCharcoal)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onScrubDeltaPx(dragAmount)
                }
            }
            .padding(start = leadingPadding, end = trailingPadding),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width((totalSecs * pxPerSecDp).dp)
                .fillMaxHeight()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pxPerSec = pxPerSecDp.dp.toPx()
                for (s in 0..totalSecs) {
                    val x = s * pxPerSec
                    val isMajor = s % stepSecs == 0
                    val tickH = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                    drawLine(
                        color = if (isMajor) SilverMuted else BorderHairline,
                        start = Offset(x, size.height - tickH),
                        end = Offset(x, size.height),
                        strokeWidth = if (isMajor) 1.5f else 1f
                    )
                }
                // Amber beat marker dots along bottom of ruler for quick beat-sync alignment
                val pxPerMsLocal = pxPerSec / 1000f
                val dotRadius = 2.2.dp.toPx()
                for (beatMs in beatPositionsMs) {
                    val bx = beatMs * pxPerMsLocal
                    if (bx in 0f..size.width) {
                        drawCircle(
                            color = CinemaAmber.copy(alpha = 0.85f),
                            radius = dotRadius,
                            center = Offset(bx, size.height - dotRadius - 1.dp.toPx())
                        )
                    }
                }
            }
            for (s in 0..totalSecs step stepSecs) {
                Text(
                    text = formatShortDuration(s * 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateDim,
                    modifier = Modifier
                        .offset(x = (s * pxPerSecDp + 3f).dp, y = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayTrackRow(
    trackCode: String,
    layer: OverlayLayer,
    isSelected: Boolean,
    pxPerSecDp: Float,
    pxPerMs: Float,
    leadingPadding: Dp,
    trailingPadding: Dp,
    totalWidthDp: Dp,
    onSelect: () -> Unit,
    onTrim: (Long, Long) -> Unit
) {
    val startDp = ((layer.startTimeMs / 1000f) * pxPerSecDp).dp
    val widthDp = ((layer.durationMs / 1000f) * pxPerSecDp).dp.coerceAtLeast(48.dp)
    val accent = if (layer.type == OverlayType.TEXT) TrackTextAmber else TrackOverlayEmerald
    val bg = if (layer.type == OverlayType.TEXT) TrackTextBg else TrackOverlayBg

    Box(
        modifier = Modifier
            .padding(start = leadingPadding, end = trailingPadding)
            .width(maxOf(totalWidthDp + 120.dp, startDp + widthDp + 64.dp))
            .height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .offset(x = startDp)
                .width(widthDp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(bg)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) CinemaAmber else accent.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(7.dp)
                )
                .clickable { onSelect() }
                .pointerInput(layer.id, pxPerMs) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = (dragAmount / pxPerMs).toLong()
                        onTrim((layer.startTimeMs + deltaMs).coerceAtLeast(0L), layer.durationMs)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Trim Handle
            TrimEdgeHandle(
                visible = isSelected,
                color = CinemaAmber,
                onDragPx = { dx ->
                    val deltaMs = (dx / pxPerMs).toLong()
                    val newStart = (layer.startTimeMs + deltaMs).coerceIn(0L, layer.endTimeMs - 300L)
                    val newDur = (layer.endTimeMs - newStart).coerceAtLeast(300L)
                    onTrim(newStart, newDur)
                }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TrackCodePill(code = trackCode, accent = accent)
                Icon(
                    imageVector = when (layer.type) {
                        OverlayType.TEXT -> Icons.Default.TextFields
                        OverlayType.IMAGE -> Icons.Default.Image
                        OverlayType.VIDEO -> Icons.Default.Videocam
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = when (layer.type) {
                        OverlayType.TEXT -> layer.text
                        OverlayType.IMAGE -> "PNG #${layer.zIndex}"
                        OverlayType.VIDEO -> layer.displayName.ifBlank { "Video #${layer.zIndex}" }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (layer.type == OverlayType.VIDEO && layer.chromaKey.enabled) {
                    TrackMicroTag("KEY", TrackOverlayEmerald)
                } else if (layer.type == OverlayType.TEXT && layer.textMotionPreset != TextMotionPreset.NONE) {
                    TrackMicroTag("FX", CinemaAmber)
                } else if (layer.type == OverlayType.IMAGE && layer.blendMode != OverlayBlendMode.NORMAL) {
                    TrackMicroTag(layer.blendMode.displayName.take(3).uppercase(), CinemaAmber)
                }
            }

            // Right Trim Handle
            TrimEdgeHandle(
                visible = isSelected,
                color = CinemaAmber,
                onDragPx = { dx ->
                    val deltaMs = (dx / pxPerMs).toLong()
                    onTrim(layer.startTimeMs, (layer.durationMs + deltaMs).coerceAtLeast(300L))
                }
            )
        }
    }
}

@Composable
private fun MainTrackRow(
    timeline: TimelineDocument,
    selection: SelectedItem,
    beatPositionsMs: List<Long>,
    pxPerSecDp: Float,
    pxPerMs: Float,
    leadingPadding: Dp,
    trailingPadding: Dp,
    onSelectClip: (String) -> Unit,
    onSelectTransition: (String) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit
) {
    val ranges = remember(timeline) { timeline.mainClipRanges() }

    Row(
        modifier = Modifier
            .padding(start = leadingPadding, end = trailingPadding)
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ranges.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(InspectorCharcoal)
                    .border(1.dp, BorderHairline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = TrackVideoBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Main Video / Photo Track",
                        style = MaterialTheme.typography.labelMedium,
                        color = SilverMuted
                    )
                }
            }
        } else {
            ranges.forEachIndexed { idx, range ->
                val clip = range.clip
                val isSelected = (selection as? SelectedItem.MainClipItem)?.clipId == clip.id
                val clipWidthDp = ((clip.durationMs / 1000f) * pxPerSecDp).dp.coerceAtLeast(56.dp)
                val clipBeats = remember(beatPositionsMs, range.startMs, range.endMs) {
                    beatPositionsMs.filter { it in range.startMs..range.endMs }
                }

                Box(
                    modifier = Modifier
                        .width(clipWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TrackVideoBg)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) CinemaAmber else TrackVideoBlue.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectClip(clip.id) }
                        .testTag("main_clip_${idx}")
                ) {
                    // Thumbnail backdrop
                    val thumb = clip.thumbnailPath ?: if (clip.mediaType == MainMediaType.IMAGE) clip.uri else null
                    if (thumb != null) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = clip.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianBg)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.42f))
                        )
                    }

                    // Faint amber beat-sync vertical guides so cuts align with audio beats
                    if (clipBeats.isNotEmpty() && clip.durationMs > 0L) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (beatMs in clipBeats) {
                                val frac = ((beatMs - range.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
                                val bx = frac * size.width
                                drawLine(
                                    color = CinemaAmber.copy(alpha = 0.35f),
                                    start = Offset(bx, 0f),
                                    end = Offset(bx, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }

                    // Keyframe Diamond Markers anchored at each keyframe's local timestamp
                    if (clip.keyframes.isNotEmpty() && clip.durationMs > 0L) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = 3.5.dp.toPx()
                            val cy = size.height - r - 3.dp.toPx()
                            for (kf in clip.keyframes) {
                                val frac = (kf.timeOffsetMs.toFloat() / clip.durationMs.toFloat()).coerceIn(0.02f, 0.98f)
                                val cx = frac * size.width
                                val diamond = Path().apply {
                                    moveTo(cx, cy - r)
                                    lineTo(cx + r, cy)
                                    lineTo(cx, cy + r)
                                    lineTo(cx - r, cy)
                                    close()
                                }
                                drawPath(diamond, color = CinemaAmber)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrimEdgeHandle(
                            visible = isSelected,
                            color = CinemaAmber,
                            onDragPx = { dx ->
                                onTrimClip(clip.id, (dx / pxPerMs).toLong(), 0L)
                            }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TrackCodePill(code = "V1", accent = TrackVideoBlue)
                                Text(
                                    text = clip.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ChalkWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!clip.colorAdjustments.cubeLutPath.isNullOrBlank()) {
                                    TrackMicroTag("LUT", CinemaAmber)
                                } else if (clip.filterPreset != FilterPreset.ORIGINAL || !clip.colorAdjustments.isDefault) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Color graded",
                                        tint = CinemaAmber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = String.format("%.1fs", clip.durationMs / 1000f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChalkWhite.copy(alpha = 0.85f)
                                )
                                if (kotlin.math.abs(clip.safeSpeed - 1f) > 0.01f) {
                                    val spdLbl = if (clip.safeSpeed % 1f == 0f) {
                                        "${clip.safeSpeed.toInt()}x"
                                    } else {
                                        String.format("%.2gx", clip.safeSpeed)
                                    }
                                    TrackMicroTag(spdLbl, CinemaAmber)
                                }
                                if (clip.mediaType == MainMediaType.VIDEO && clip.audio.isSolo) {
                                    TrackMicroTag("S", CinemaAmber)
                                }
                                if (clip.mediaType == MainMediaType.VIDEO && clip.audio.hasActiveEq) {
                                    TrackMicroTag("EQ", TrackOverlayEmerald)
                                }
                                if (clip.mediaType == MainMediaType.VIDEO && clip.audio.isMuted) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = "Muted",
                                        tint = TrackAudioRose,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        TrimEdgeHandle(
                            visible = isSelected,
                            color = CinemaAmber,
                            onDragPx = { dx ->
                                onTrimClip(clip.id, 0L, (dx / pxPerMs).toLong())
                            }
                        )
                    }
                }

                // Tappable Diamond Transition Node between adjacent clips
                if (idx < ranges.lastIndex) {
                    val hasTransition = clip.transitionToNext.type != TransitionType.NONE
                    val isTransSelected = (selection as? SelectedItem.TransitionItem)?.afterClipId == clip.id
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(28.dp)
                            .clickable { onSelectTransition(clip.id) }
                            .testTag("transition_node_${idx}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (hasTransition || isTransSelected) 18.dp else 14.dp)
                                .rotate(45f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        isTransSelected -> CinemaAmber
                                        hasTransition -> TransitionGold
                                        else -> InspectorCharcoal
                                    }
                                )
                                .border(
                                    1.2.dp,
                                    if (hasTransition || isTransSelected) CinemaAmber else SilverMuted,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTrackLaneRow(
    trackCode: String,
    clips: List<AudioClip>,
    selectedAudioId: String?,
    pxPerSecDp: Float,
    pxPerMs: Float,
    leadingPadding: Dp,
    trailingPadding: Dp,
    totalWidthDp: Dp,
    onSelectAudio: (String) -> Unit,
    onTrimAudio: (String, Long, Long, Long) -> Unit
) {
    val maxEndDp = clips.maxOfOrNull { ((it.endTimeMs / 1000f) * pxPerSecDp).dp } ?: 0.dp
    Box(
        modifier = Modifier
            .padding(start = leadingPadding, end = trailingPadding)
            .width(maxOf(totalWidthDp + 120.dp, maxEndDp + 64.dp))
            .height(58.dp)
            .testTag("audio_waveform_track")
    ) {
        for (clip in clips) {
            val isSelected = selectedAudioId == clip.id
            val startDp = ((clip.startTimeMs / 1000f) * pxPerSecDp).dp
            val widthDp = ((clip.durationMs / 1000f) * pxPerSecDp).dp.coerceAtLeast(56.dp)

            // Slice waveform peaks strictly to the active [trimStartMs..trimEndMs] window
            val visiblePeaks = remember(clip.waveformPeaks, clip.trimStartMs, clip.trimEndMs, clip.sourceDurationMs) {
                sliceWaveformPeaks(
                    peaks = clip.waveformPeaks,
                    sourceDurationMs = clip.sourceDurationMs,
                    trimStartMs = clip.trimStartMs,
                    trimEndMs = clip.trimEndMs
                )
            }
            val visibleBeats = remember(clip.beatPositionsMs, clip.trimStartMs, clip.trimEndMs) {
                clip.beatPositionsMs.filter { it in clip.trimStartMs..clip.trimEndMs }
            }

            Box(
                modifier = Modifier
                    .offset(x = startDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrackAudioBg)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) CinemaAmber else TrackAudioRose.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectAudio(clip.id) }
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / pxPerMs).toLong()
                            onTrimAudio(clip.id, deltaMs, 0L, 0L)
                        }
                    }
                    .testTag("audio_clip_${clip.id}")
            ) {
                // High-Resolution Studio Waveform + Beat Markers + Fade Envelope Visualization
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    val midY = size.height * 0.56f
                    val maxHalfBar = size.height * 0.40f
                    val clipDurFloat = clip.durationMs.coerceAtLeast(1L).toFloat()

                    // Subtle horizontal studio baseline
                    drawLine(
                        color = TrackAudioRose.copy(alpha = 0.22f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Resample visible peaks to match available pixel width cleanly (~3.5dp per bar)
                    val targetBarPitchPx = 3.2.dp.toPx().coerceAtLeast(2.5f)
                    val barCount = (size.width / targetBarPitchPx).toInt().coerceIn(12, 240)
                    val step = size.width / barCount.toFloat()
                    val strokeW = (step * 0.58f).coerceIn(1.5f, 3.8f)
                    val volumeScale = if (clip.audio.isMuted) 0.35f else clip.audio.volume.coerceIn(0.15f, 1.5f)

                    for (i in 0 until barCount) {
                        val frac = (i + 0.5f) / barCount.toFloat()
                        val srcIndex = ((frac * (visiblePeaks.size - 1)).roundToInt())
                            .coerceIn(0, visiblePeaks.lastIndex)
                        val rawAmp = visiblePeaks[srcIndex].coerceIn(0.06f, 1f)

                        // Attenuate bar amplitude smoothly inside fade-in / fade-out zones
                        val localMs = frac * clipDurFloat
                        var envGain = 1f
                        if (clip.audio.fadeInMs > 0L && localMs < clip.audio.fadeInMs) {
                            envGain *= (localMs / clip.audio.fadeInMs.toFloat()).coerceIn(0.08f, 1f)
                        }
                        if (clip.audio.fadeOutMs > 0L && localMs > clipDurFloat - clip.audio.fadeOutMs) {
                            val remain = (clipDurFloat - localMs).coerceAtLeast(0f)
                            envGain *= (remain / clip.audio.fadeOutMs.toFloat()).coerceIn(0.08f, 1f)
                        }

                        val shaped = (rawAmp * volumeScale * envGain).coerceIn(0.06f, 1f)
                        val halfH = (shaped * maxHalfBar).coerceAtLeast(1.8.dp.toPx())
                        val x = i * step + step * 0.5f

                        val barColor = if (clip.audio.isMuted) {
                            SlateDim.copy(alpha = 0.42f)
                        } else if (rawAmp > 0.72f) {
                            lerp(TrackAudioRose, CinemaAmber, ((rawAmp - 0.72f) / 0.28f).coerceIn(0f, 1f))
                                .copy(alpha = 0.92f)
                        } else {
                            TrackAudioRose.copy(alpha = 0.55f + rawAmp * 0.35f)
                        }

                        drawLine(
                            color = barColor,
                            start = Offset(x, midY - halfH),
                            end = Offset(x, midY + halfH),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }

                    // Beat onset vertical markers & top amber dots for accurate cut/transition sync
                    if (!clip.audio.isMuted && visibleBeats.isNotEmpty()) {
                        val dotR = 2.dp.toPx()
                        for (beatSrcMs in visibleBeats) {
                            val frac = ((beatSrcMs - clip.trimStartMs).toFloat() / clipDurFloat).coerceIn(0f, 1f)
                            val bx = frac * size.width
                            drawLine(
                                color = CinemaAmber.copy(alpha = 0.52f),
                                start = Offset(bx, dotR * 2f),
                                end = Offset(bx, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(
                                color = CinemaAmber,
                                radius = dotR,
                                center = Offset(bx, dotR + 0.5.dp.toPx())
                            )
                        }
                    }

                    // Fade-In ramp overlay & envelope stroke
                    if (clip.audio.fadeInMs > 0L && clip.durationMs > 0L) {
                        val fadeW = (clip.audio.fadeInMs.toFloat() / clipDurFloat).coerceIn(0f, 0.5f) * size.width
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(fadeW, 0f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, color = CinemaAmber.copy(alpha = 0.20f))
                        drawLine(
                            color = CinemaAmber.copy(alpha = 0.8f),
                            start = Offset(0f, size.height),
                            end = Offset(fadeW, 0f),
                            strokeWidth = 1.4.dp.toPx()
                        )
                    }
                    // Fade-Out ramp overlay & envelope stroke
                    if (clip.audio.fadeOutMs > 0L && clip.durationMs > 0L) {
                        val fadeW = (clip.audio.fadeOutMs.toFloat() / clipDurFloat).coerceIn(0f, 0.5f) * size.width
                        val path = Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(size.width - fadeW, 0f)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(path, color = CinemaAmber.copy(alpha = 0.20f))
                        drawLine(
                            color = CinemaAmber.copy(alpha = 0.8f),
                            start = Offset(size.width - fadeW, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.4.dp.toPx()
                        )
                    }
                }

                // Trim handles + compact top-left badge so the waveform stays unobstructed
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top
                ) {
                    TrimEdgeHandle(
                        visible = isSelected,
                        color = CinemaAmber,
                        onDragPx = { dx ->
                            val dMs = (dx / pxPerMs).toLong()
                            onTrimAudio(clip.id, dMs, dMs, 0L)
                        }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 5.dp, top = 3.dp, end = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ObsidianBg.copy(alpha = 0.72f))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TrackCodePill(code = trackCode, accent = TrackAudioRose)
                            Icon(
                                imageVector = if (clip.audio.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (clip.audio.isMuted) "Audio muted" else "Audio active",
                                tint = if (clip.audio.isMuted) SilverMuted else TrackAudioRose,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = clip.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = ChalkWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (clip.audio.isSolo) {
                                TrackMicroTag("SOLO", CinemaAmber)
                            }
                            if (clip.audio.hasActiveEq) {
                                TrackMicroTag("EQ", TrackOverlayEmerald)
                            }
                            if (visibleBeats.isNotEmpty() && !clip.audio.isMuted) {
                                Text(
                                    text = "• ${visibleBeats.size}b",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaAmber,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    TrimEdgeHandle(
                        visible = isSelected,
                        color = CinemaAmber,
                        onDragPx = { dx ->
                            onTrimAudio(clip.id, 0L, 0L, (dx / pxPerMs).toLong())
                        }
                    )
                }
            }
        }
    }
}

private fun sliceWaveformPeaks(
    peaks: List<Float>,
    sourceDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long
): List<Float> {
    if (peaks.isEmpty()) return List(32) { 0.35f }
    if (sourceDurationMs <= 0L) return peaks
    val startFrac = (trimStartMs.toFloat() / sourceDurationMs.toFloat()).coerceIn(0f, 1f)
    val endFrac = (trimEndMs.toFloat() / sourceDurationMs.toFloat()).coerceIn(startFrac, 1f)
    val startIdx = (startFrac * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
    val endIdx = (endFrac * peaks.size).roundToInt().coerceIn(startIdx + 1, peaks.size)
    return peaks.subList(startIdx, endIdx).ifEmpty { listOf(peaks[startIdx]) }
}

@Composable
private fun TrimEdgeHandle(
    visible: Boolean,
    color: Color,
    onDragPx: (Float) -> Unit
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(14.dp)
            .background(color)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragPx(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .clip(CircleShape)
                .background(OnAmber)
        )
    }
}

@Composable
private fun TrackCodePill(
    code: String,
    accent: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(accent.copy(alpha = 0.22f))
            .border(0.75.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 0.5.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun TrackMicroTag(
    text: String,
    accent: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.22f))
            .border(0.8.dp, accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 0.5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun TrackHintStrip(
    trackCode: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    leadingPadding: Dp
) {
    Row(
        modifier = Modifier
            .padding(start = leadingPadding)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(InspectorCharcoal.copy(alpha = 0.6f))
            .border(1.dp, BorderHairline.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TrackCodePill(code = trackCode, accent = accent)
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SlateDim)
    }
}
