package com.example.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.media.ChromaKeyGlEffect
import com.example.media.FontResolver
import com.example.model.AspectRatio
import com.example.model.AudioClip
import com.example.model.ClipTransform
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.CubeLutParser
import com.example.model.FilterPreset
import com.example.model.KeyframeEvaluator
import com.example.model.MainMediaType
import com.example.model.OverlayBlendMode
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.StudioFont
import com.example.model.TextMotionEvaluator
import com.example.model.TextMotionFrame
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import com.example.model.TransitionType
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.StudioSlate
import com.example.viewmodel.SelectedItem
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun PreviewStage(
    aspectRatio: AspectRatio,
    timeline: TimelineDocument,
    playheadMs: Long,
    isPlaying: Boolean,
    selection: SelectedItem,
    availableFonts: List<StudioFont>,
    snapGuideX: Boolean,
    snapGuideY: Boolean,
    textMotionReplayNonce: Int = 0,
    showScopes: Boolean = false,
    scopeMode: VideoScopeMode = VideoScopeMode.WAVEFORM,
    onToggleScopeMode: (VideoScopeMode) -> Unit = {},
    isSamplingChromaColor: Boolean = false,
    onSampledChromaColor: (Long) -> Unit = {},
    onSelectOverlay: (String) -> Unit,
    onClearSelection: () -> Unit,
    onBeginOverlayGesture: () -> Unit,
    onTransformOverlay: (String, Float, Float, Float, Float) -> Unit,
    onEndOverlayGesture: () -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onQuickImportVideo: () -> Unit,
    onQuickImportPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Primary ExoPlayer for main-track video clips
    val videoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Auxiliary audio players mapped by AudioClip ID for live multi-track mixing
    val audioPlayers = remember { mutableStateMapOf<String, ExoPlayer>() }
    val videoOverlayTextures = remember { mutableStateMapOf<String, TextureView>() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoPlayer.release() }
            audioPlayers.values.forEach { runCatching { it.release() } }
            audioPlayers.clear()
            videoOverlayTextures.clear()
        }
    }

    val activeRange = remember(timeline, playheadMs) { timeline.mainClipAt(playheadMs) }
    val activeClip = activeRange?.clip
    val activeTransition = remember(timeline, playheadMs) { timeline.activeTransitionAt(playheadMs) }
    val activeOverlays = remember(timeline, playheadMs) { timeline.activeOverlaysAt(playheadMs) }
    val anyAudioSoloed = remember(timeline.mainClips, timeline.audioClips) {
        timeline.mainClips.any { it.audio.isSolo } || timeline.audioClips.any { it.audio.isSolo }
    }

    var loadedVideoUri by remember { mutableStateOf<String?>(null) }

    // Sync main-track video player with active clip & playhead
    LaunchedEffect(activeClip?.id, activeClip?.uri, activeClip?.mediaType) {
        if (activeClip != null && activeClip.mediaType == MainMediaType.VIDEO) {
            if (loadedVideoUri != activeClip.uri) {
                loadedVideoUri = activeClip.uri
                videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(activeClip.uri)))
                videoPlayer.prepare()
            }
        } else {
            videoPlayer.pause()
        }
    }

    LaunchedEffect(
        playheadMs,
        isPlaying,
        anyAudioSoloed,
        activeRange?.clip?.id,
        activeRange?.clip?.audio,
        activeRange?.clip?.playbackSpeed,
        activeRange?.clip?.pitchShiftSemitones,
        activeRange?.clip?.preservePitch
    ) {
        val range = activeRange
        if (range != null && range.clip.mediaType == MainMediaType.VIDEO) {
            val clip = range.clip
            val localMs = (playheadMs - range.startMs).coerceIn(0L, clip.durationMs)
            val sourcePosMs = (clip.trimStartMs + (localMs * clip.safeSpeed).toLong())
                .coerceIn(0L, clip.sourceDurationMs)

            // Apply live playback rate (0.25x..4x) & audio pitch shifting
            val targetParams = PlaybackParameters(clip.safeSpeed, clip.effectiveAudioPitch)
            if (videoPlayer.playbackParameters != targetParams) {
                videoPlayer.playbackParameters = targetParams
            }

            // Apply live clip volume, mute, solo routing, and fade-in/out envelope
            val effectiveMuted = clip.audio.isMuted || (anyAudioSoloed && !clip.audio.isSolo)
            val gain = computeLiveEnvelopeGain(
                localMs = localMs,
                durationMs = clip.durationMs,
                volume = clip.audio.volume,
                isMuted = effectiveMuted,
                fadeInMs = clip.audio.fadeInMs,
                fadeOutMs = clip.audio.fadeOutMs
            )
            videoPlayer.volume = gain.coerceIn(0f, 1f)

            val driftTolerance = (220L * clip.safeSpeed).toLong().coerceAtLeast(180L)
            val drift = abs(videoPlayer.currentPosition - sourcePosMs)
            if (!isPlaying || drift > driftTolerance) {
                videoPlayer.seekTo(sourcePosMs)
            }
            if (isPlaying && !videoPlayer.isPlaying) {
                videoPlayer.playWhenReady = true
            } else if (!isPlaying && videoPlayer.isPlaying) {
                videoPlayer.playWhenReady = false
            }
        } else {
            videoPlayer.playWhenReady = false
        }
    }

    // Sync multi-track audio players with live fade, solo routing & volume
    LaunchedEffect(timeline.audioClips, playheadMs, isPlaying, anyAudioSoloed) {
        val validIds = timeline.audioClips.map { it.id }.toSet()
        val stale = audioPlayers.keys.filter { it !in validIds }
        stale.forEach { id ->
            runCatching { audioPlayers.remove(id)?.release() }
        }

        for (audioClip in timeline.audioClips) {
            val inWindow = playheadMs in audioClip.startTimeMs until audioClip.endTimeMs
            val effectiveMuted = audioClip.audio.isMuted || (anyAudioSoloed && !audioClip.audio.isSolo)
            if (inWindow && isPlaying && !effectiveMuted) {
                val player = audioPlayers.getOrPut(audioClip.id) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(audioClip.uri)))
                        prepare()
                    }
                }
                val localMs = (playheadMs - audioClip.startTimeMs).coerceIn(0L, audioClip.durationMs)
                val targetSourceMs = (audioClip.trimStartMs + localMs).coerceIn(0L, audioClip.sourceDurationMs)
                val gain = computeLiveEnvelopeGain(
                    localMs = localMs,
                    durationMs = audioClip.durationMs,
                    volume = audioClip.audio.volume,
                    isMuted = false,
                    fadeInMs = audioClip.audio.fadeInMs,
                    fadeOutMs = audioClip.audio.fadeOutMs
                )
                player.volume = gain.coerceIn(0f, 1f)
                if (abs(player.currentPosition - targetSourceMs) > 220L) {
                    player.seekTo(targetSourceMs)
                }
                if (!player.playWhenReady) player.playWhenReady = true
            } else {
                audioPlayers[audioClip.id]?.let { p ->
                    if (p.playWhenReady) p.playWhenReady = false
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClearSelection() })
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = maxHeight
        val containerRatio = if (availH.value > 0f) availW.value / availH.value else 1f
        val stageW = if (containerRatio > aspectRatio.ratio) availH * aspectRatio.ratio else availW
        val stageH = if (containerRatio > aspectRatio.ratio) availH else availW / aspectRatio.ratio

        val density = LocalDensity.current
        val stageWPx = with(density) { stageW.toPx() }.coerceAtLeast(1f)
        val stageHPx = with(density) { stageH.toPx() }.coerceAtLeast(1f)

        // Transition animation properties
        val transitionAlpha = when (activeTransition?.transition?.type) {
            TransitionType.CROSSFADE -> {
                val p = activeTransition.progress
                if (p < 0.5f) 1f - (p * 0.7f) else 0.65f + (p - 0.5f) * 0.7f
            }
            else -> 1f
        }
        val transitionTranslationX = when (activeTransition?.transition?.type) {
            TransitionType.SLIDE_LEFT -> {
                val p = activeTransition.progress
                if (p >= 0.5f) (1f - (p - 0.5f) * 2f) * stageWPx else 0f
            }
            TransitionType.SLIDE_RIGHT -> {
                val p = activeTransition.progress
                if (p >= 0.5f) -(1f - (p - 0.5f) * 2f) * stageWPx else 0f
            }
            else -> 0f
        }
        val transitionScale = when (activeTransition?.transition?.type) {
            TransitionType.ZOOM_IN -> {
                val p = activeTransition.progress
                if (p >= 0.5f) 1.24f - 0.24f * ((p - 0.5f) * 2f) else 1f + p * 0.2f
            }
            else -> 1f
        }
        val dipVeilColor = when (activeTransition?.transition?.type) {
            TransitionType.DIP_TO_BLACK -> {
                val p = activeTransition.progress
                val strength = 1f - abs(p - 0.5f) * 2f
                Color.Black.copy(alpha = strength.coerceIn(0f, 1f))
            }
            TransitionType.DIP_TO_WHITE -> {
                val p = activeTransition.progress
                val strength = 1f - abs(p - 0.5f) * 2f
                Color.White.copy(alpha = strength.coerceIn(0f, 1f))
            }
            else -> Color.Transparent
        }

        val hasNonNormalBlend = activeOverlays.any {
            it.type == OverlayType.IMAGE && it.blendMode != OverlayBlendMode.NORMAL
        }

        Box(
            modifier = Modifier
                .width(stageW)
                .height(stageH)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .then(
                    if (hasNonNormalBlend) {
                        Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    } else {
                        Modifier
                    }
                )
                .testTag("preview_stage_canvas"),
            contentAlignment = Alignment.Center
        ) {
            if (timeline.mainClips.isEmpty() && timeline.overlays.isEmpty()) {
                EmptyStagePrompt(
                    onImportVideo = onQuickImportVideo,
                    onImportPhoto = onQuickImportPhoto
                )
            } else {
                // Main Track Media Layer (Video or Photo)
                if (activeClip != null) {
                    val colorFilter = remember(
                        activeClip.filterPreset,
                        activeClip.filterIntensity,
                        activeClip.colorAdjustments
                    ) {
                        ColorMatrixEngine.toComposeColorFilter(
                            activeClip.filterPreset,
                            activeClip.filterIntensity,
                            activeClip.colorAdjustments
                        )
                    }

                    val clipMotion = remember(activeClip, activeRange, playheadMs) {
                        val range = activeRange
                        if (range != null) {
                            val localMs = (playheadMs - range.startMs).coerceIn(0L, activeClip.durationMs)
                            KeyframeEvaluator.evaluate(activeClip, localMs)
                        } else {
                            ClipTransform()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = (transitionAlpha * clipMotion.opacity).coerceIn(0f, 1f)
                                translationX = transitionTranslationX + (clipMotion.offsetX * stageWPx)
                                translationY = clipMotion.offsetY * stageHPx
                                scaleX = transitionScale * clipMotion.scale
                                scaleY = transitionScale * clipMotion.scale
                                rotationZ = clipMotion.rotationDegrees
                            }
                    ) {
                        if (activeClip.mediaType == MainMediaType.VIDEO) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = videoPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { view ->
                                    if (view.player !== videoPlayer) view.player = videoPlayer
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            // Live non-blocking DaVinci color grading & LUT preview overlay for video
                            VideoGradePreviewOverlay(
                                preset = activeClip.filterPreset,
                                intensity = activeClip.filterIntensity,
                                adjustments = activeClip.colorAdjustments
                            )
                        } else {
                            AsyncImage(
                                model = activeClip.uri,
                                contentDescription = activeClip.displayName,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Wipe Left / Wipe Right Live Transition Curtain
                val wipeType = activeTransition?.transition?.type
                if (wipeType == TransitionType.WIPE_RIGHT || wipeType == TransitionType.WIPE_LEFT) {
                    val p = activeTransition.progress.coerceIn(0f, 1f)
                    // Sweep a crisp dark cinematic wipe bar across the frame through the transition seam
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val edgeX = if (wipeType == TransitionType.WIPE_RIGHT) {
                            p * size.width
                        } else {
                            (1f - p) * size.width
                        }
                        val bandWidth = (size.width * 0.22f) * (1f - abs(p - 0.5f) * 2f)
                        if (bandWidth > 1f) {
                            drawRect(
                                color = Color.Black.copy(alpha = 0.72f),
                                topLeft = Offset((edgeX - bandWidth / 2f).coerceIn(0f, size.width), 0f),
                                size = androidx.compose.ui.geometry.Size(bandWidth, size.height)
                            )
                        }
                        drawLine(
                            color = CinemaAmber.copy(alpha = 0.85f),
                            start = Offset(edgeX, 0f),
                            end = Offset(edgeX, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Dip-to-Black / Dip-to-White Transition Veil
                if (dipVeilColor.alpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(dipVeilColor)
                    )
                }

                // Alignment Snap Guides (visible during overlay dragging)
                if (snapGuideX || snapGuideY) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (snapGuideX) {
                            drawLine(
                                color = CinemaAmber.copy(alpha = 0.85f),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2f
                            )
                        }
                        if (snapGuideY) {
                            drawLine(
                                color = CinemaAmber.copy(alpha = 0.85f),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                // Stacked Interactive Overlay Layers (Z-ordered)
                for (overlay in activeOverlays) {
                    val isSelected = (selection as? SelectedItem.OverlayItem)?.overlayId == overlay.id
                    InteractiveOverlayNode(
                        overlay = overlay,
                        isSelected = isSelected,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        textMotionReplayNonce = if (isSelected) textMotionReplayNonce else 0,
                        stageWidthPx = stageWPx,
                        stageHeightPx = stageHPx,
                        availableFonts = availableFonts,
                        onSelect = { onSelectOverlay(overlay.id) },
                        onBeginGesture = onBeginOverlayGesture,
                        onTransform = { dx, dy, sf, rot ->
                            onTransformOverlay(overlay.id, dx, dy, sf, rot)
                        },
                        onEndGesture = onEndOverlayGesture,
                        onDelete = { onDeleteOverlay(overlay.id) },
                        onRegisterVideoTexture = { tv ->
                            if (tv != null) videoOverlayTextures[overlay.id] = tv
                            else videoOverlayTextures.remove(overlay.id)
                        }
                    )
                }

                // Live DaVinci Video Scopes HUD (Waveform / RGB Parade)
                if (showScopes) {
                    VideoScopesOverlay(
                        clip = activeClip,
                        mode = scopeMode,
                        onToggleMode = onToggleScopeMode,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }

                // Real-time Chroma Key Eyedropper tap-to-sample overlay
                if (isSamplingChromaColor) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.dp, CinemaAmber, RoundedCornerShape(10.dp))
                            .pointerInput(isSamplingChromaColor, selection, activeOverlays, playheadMs, stageWPx, stageHPx) {
                                detectTapGestures { tapOffset ->
                                    val selectedOverlayId = (selection as? SelectedItem.OverlayItem)?.overlayId
                                    val targetOverlay = activeOverlays.firstOrNull {
                                        it.id == selectedOverlayId && it.type == OverlayType.VIDEO
                                    } ?: activeOverlays.firstOrNull { it.type == OverlayType.VIDEO }

                                    val normX = (tapOffset.x / stageWPx).coerceIn(0f, 1f)
                                    val normY = (tapOffset.y / stageHPx).coerceIn(0f, 1f)
                                    val sampled = sampleVideoOverlayPixelArgb(
                                        context = context,
                                        overlay = targetOverlay,
                                        textureView = targetOverlay?.let { videoOverlayTextures[it.id] },
                                        playheadMs = playheadMs,
                                        normX = normX,
                                        normY = normY
                                    )
                                    onSampledChromaColor(sampled)
                                }
                            }
                            .testTag("chroma_eyedropper_stage")
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(ObsidianBg.copy(alpha = 0.88f))
                                .border(1.dp, CinemaAmber, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("chroma_eyedropper_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Colorize,
                                contentDescription = null,
                                tint = CinemaAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap video to pick key color",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChalkWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sampleVideoOverlayPixelArgb(
    context: android.content.Context,
    overlay: OverlayLayer?,
    textureView: TextureView?,
    playheadMs: Long,
    normX: Float,
    normY: Float
): Long {
    // 1. Prefer live GPU TextureView bitmap sample when available
    val texBmp = runCatching { textureView?.getBitmap(64, 64) }.getOrNull()
    if (texBmp != null) {
        val px = (normX * (texBmp.width - 1)).roundToInt().coerceIn(0, texBmp.width - 1)
        val py = (normY * (texBmp.height - 1)).roundToInt().coerceIn(0, texBmp.height - 1)
        val argb = texBmp.getPixel(px, py)
        texBmp.recycle()
        if ((argb ushr 24) > 10) {
            return (0xFF000000L or (argb.toLong() and 0x00FFFFFFL))
        }
    }
    // 2. Fallback: extract exact source frame via MediaMetadataRetriever at playheadMs
    val uriStr = overlay?.videoUri
    if (uriStr != null) {
        val retriever = MediaMetadataRetriever()
        val sampled = runCatching {
            retriever.setDataSource(context, Uri.parse(uriStr))
            val srcDur = overlay.sourceDurationMs.coerceAtLeast(300L)
            val localMs = (playheadMs - overlay.startTimeMs).coerceAtLeast(0L)
            val targetUs = ((overlay.trimStartMs + (localMs % srcDur)) * 1000L).coerceAtLeast(0L)
            val frame = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                val px = (normX * (frame.width - 1)).roundToInt().coerceIn(0, frame.width - 1)
                val py = (normY * (frame.height - 1)).roundToInt().coerceIn(0, frame.height - 1)
                val argb = frame.getPixel(px, py)
                frame.recycle()
                0xFF000000L or (argb.toLong() and 0x00FFFFFFL)
            } else null
        }.getOrNull()
        runCatching { retriever.release() }
        if (sampled != null) return sampled
    }
    return 0xFF00B140L
}

@Composable
private fun VideoGradePreviewOverlay(
    preset: FilterPreset,
    intensity: Float,
    adjustments: ColorAdjustments
) {
    val presetTint = when (preset) {
        FilterPreset.ORIGINAL -> Color.Transparent
        FilterPreset.NOIR -> Color(0xFF1A1D24).copy(alpha = 0.36f * intensity)
        FilterPreset.TEAL_ORANGE -> Color(0xFF0E7490).copy(alpha = 0.22f * intensity)
        FilterPreset.VINTAGE_WARM -> Color(0xFFD97706).copy(alpha = 0.22f * intensity)
        FilterPreset.KODAK_FADE -> Color(0xFFB45309).copy(alpha = 0.18f * intensity)
        FilterPreset.CRISP_VIVID -> Color(0xFF2563EB).copy(alpha = 0.12f * intensity)
        FilterPreset.MIDNIGHT_COOL -> Color(0xFF1E3A8A).copy(alpha = 0.28f * intensity)
        FilterPreset.GOLDEN_HOUR -> Color(0xFFF59E0B).copy(alpha = 0.25f * intensity)
        FilterPreset.MATTE_FILM -> Color(0xFF6B7280).copy(alpha = 0.22f * intensity)
    }
    if (presetTint.alpha > 0.005f) {
        Box(modifier = Modifier.fillMaxSize().background(presetTint))
    }

    // Combined Exposure/Brightness + Lift/Gamma/Gain luma shift
    val effectiveExposure = adjustments.brightness +
        (adjustments.lift.luma * 0.25f) +
        (adjustments.gamma.luma * 0.35f) +
        (adjustments.gain.luma * 0.35f) +
        (adjustments.highlights * 0.18f) +
        (adjustments.shadows * 0.18f)
    if (effectiveExposure > 0.01f) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = (effectiveExposure * 0.55f).coerceIn(0f, 0.55f))))
    } else if (effectiveExposure < -0.01f) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = (-effectiveExposure * 0.65f).coerceIn(0f, 0.65f))))
    }

    if (adjustments.saturation < 0.85f) {
        val desat = ((1f - adjustments.saturation) * 0.35f).coerceIn(0f, 0.4f)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1F2937).copy(alpha = desat)))
    }
    if (adjustments.temperature > 0.02f) {
        val warmAlpha = (adjustments.temperature * 0.28f).coerceIn(0f, 0.28f)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF59E0B).copy(alpha = warmAlpha)))
    } else if (adjustments.temperature < -0.02f) {
        val coolAlpha = (-adjustments.temperature * 0.28f).coerceIn(0f, 0.28f)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF38BDF8).copy(alpha = coolAlpha)))
    }
    if (adjustments.tint > 0.02f) {
        val magAlpha = (adjustments.tint * 0.24f).coerceIn(0f, 0.24f)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEC4899).copy(alpha = magAlpha)))
    } else if (adjustments.tint < -0.02f) {
        val grnAlpha = (-adjustments.tint * 0.24f).coerceIn(0f, 0.24f)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF10B981).copy(alpha = grnAlpha)))
    }

    // Combined Lift / Gamma / Gain Color Wheels chroma cast
    val (lr, lg, lb) = ColorMatrixEngine.wheelToRgbDelta(adjustments.lift, 0.28f)
    val (mr, mg, mb) = ColorMatrixEngine.wheelToRgbDelta(adjustments.gamma, 0.35f)
    val (gr, gg, gb) = ColorMatrixEngine.wheelToRgbDelta(adjustments.gain, 0.32f)
    val wheelR = lr + mr + gr
    val wheelG = lg + mg + gg
    val wheelB = lb + mb + gb
    val wheelStrength = (abs(wheelR) + abs(wheelG) + abs(wheelB))
    if (wheelStrength > 0.02f) {
        val castColor = Color(
            red = (0.5f + wheelR).coerceIn(0f, 1f),
            green = (0.5f + wheelG).coerceIn(0f, 1f),
            blue = (0.5f + wheelB).coerceIn(0f, 1f),
            alpha = (wheelStrength * 0.38f).coerceIn(0f, 0.35f)
        )
        Box(modifier = Modifier.fillMaxSize().background(castColor))
    }

    // Live .cube 3D LUT midtone preview cast
    val lutPath = adjustments.cubeLutPath
    if (!lutPath.isNullOrBlank() && adjustments.cubeLutIntensity > 0.01f) {
        val lutMidColor = remember(lutPath, adjustments.cubeLutIntensity) {
            CubeLutParser.loadCached(lutPath)?.let { lut ->
                val (sr, sg, sb) = lut.sample(0.5f, 0.5f, 0.5f)
                Color(
                    red = sr.coerceIn(0f, 1f),
                    green = sg.coerceIn(0f, 1f),
                    blue = sb.coerceIn(0f, 1f),
                    alpha = (0.24f * adjustments.cubeLutIntensity).coerceIn(0f, 0.30f)
                )
            }
        }
        if (lutMidColor != null) {
            Box(modifier = Modifier.fillMaxSize().background(lutMidColor))
        }
    }

    if (abs(adjustments.contrast - 1f) > 0.05f) {
        val alpha = ((adjustments.contrast - 1f) * 0.15f).coerceIn(-0.12f, 0.18f)
        if (alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha))
                        )
                    )
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InteractiveOverlayNode(
    overlay: OverlayLayer,
    isSelected: Boolean,
    playheadMs: Long,
    isPlaying: Boolean,
    textMotionReplayNonce: Int,
    stageWidthPx: Float,
    stageHeightPx: Float,
    availableFonts: List<StudioFont>,
    onSelect: () -> Unit,
    onBeginGesture: () -> Unit,
    onTransform: (Float, Float, Float, Float) -> Unit,
    onEndGesture: () -> Unit,
    onDelete: () -> Unit,
    onRegisterVideoTexture: (TextureView?) -> Unit = {}
) {
    // Compose Animatable enables instant spring/tween audition when picking or replaying a text motion preset
    val auditionProgress = remember(overlay.id) { Animatable(1f) }
    var isAuditioning by remember(overlay.id) { mutableStateOf(false) }

    LaunchedEffect(overlay.textMotionPreset, textMotionReplayNonce) {
        if (overlay.type == OverlayType.TEXT &&
            overlay.textMotionPreset != TextMotionPreset.NONE &&
            textMotionReplayNonce > 0 &&
            !isPlaying
        ) {
            isAuditioning = true
            auditionProgress.snapTo(0f)
            val durMs = overlay.textMotionDurationMs.toInt().coerceIn(300, 2500)
            if (overlay.textMotionPreset == TextMotionPreset.POP_SPRING) {
                auditionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            } else {
                auditionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = durMs, easing = LinearEasing)
                )
            }
            isAuditioning = false
        }
    }

    val textMotionFrame: TextMotionFrame? = if (overlay.type == OverlayType.TEXT &&
        overlay.textMotionPreset != TextMotionPreset.NONE
    ) {
        if (isAuditioning && !isPlaying) {
            TextMotionEvaluator.evaluate(
                preset = overlay.textMotionPreset,
                fullText = overlay.text.ifBlank { "Title" },
                rawProgress = auditionProgress.value
            )
        } else {
            TextMotionEvaluator.evaluateAtTimelineMs(overlay, playheadMs)
        }
    } else null

    val pxOffsetX = ((overlay.offsetX + (textMotionFrame?.offsetXFraction ?: 0f)) * stageWidthPx).roundToInt()
    val pxOffsetY = ((overlay.offsetY + (textMotionFrame?.offsetYFraction ?: 0f)) * stageHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(pxOffsetX, pxOffsetY) }
            .graphicsLayer {
                val motionScale = textMotionFrame?.scale ?: 1f
                scaleX = overlay.scale * motionScale
                scaleY = overlay.scale * motionScale
                rotationZ = overlay.rotationDegrees + (textMotionFrame?.rotationDegrees ?: 0f)
                alpha = (overlay.opacity * (textMotionFrame?.alpha ?: 1f)).coerceIn(0f, 1f)
            }
            .pointerInput(overlay.id, stageWidthPx, stageHeightPx) {
                detectTapGestures(onTap = { onSelect() })
            }
            .pointerInput(overlay.id, stageWidthPx, stageHeightPx) {
                detectTransformGestures(
                    onGesture = { _, pan, zoom, rotation ->
                        onSelect()
                        onBeginGesture()
                        onTransform(
                            pan.x / stageWidthPx,
                            pan.y / stageHeightPx,
                            zoom,
                            rotation
                        )
                    }
                )
            }
            .then(
                if (isSelected) {
                    Modifier
                        .border(1.5.dp, CinemaAmber, RoundedCornerShape(8.dp))
                        .padding(6.dp)
                } else {
                    Modifier.padding(6.dp)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (overlay.type) {
            OverlayType.TEXT -> {
                val fontFamily = remember(overlay.fontId, availableFonts) {
                    FontResolver.resolveComposeFont(overlay.fontId, availableFonts)
                }
                val displayedText = remember(overlay.text, textMotionFrame) {
                    if (textMotionFrame != null) {
                        TextMotionEvaluator.sliceVisibleText(overlay.text, textMotionFrame)
                    } else {
                        overlay.text.ifBlank { "Title" }
                    }
                }
                val extraLetterSpacing = (textMotionFrame?.letterSpacingSp ?: 0f).sp
                val textShadow = if (overlay.hasDropShadow) {
                    Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 4f),
                        blurRadius = 10f
                    )
                } else null

                val bgMod = if (overlay.hasBackground) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(overlay.backgroundColorArgb))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                } else {
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                }

                Box(modifier = bgMod, contentAlignment = Alignment.Center) {
                    if (overlay.hasStroke) {
                        Text(
                            text = displayedText,
                            style = TextStyle(
                                fontFamily = fontFamily,
                                fontSize = overlay.fontSizeSp.sp,
                                letterSpacing = extraLetterSpacing,
                                color = Color(overlay.strokeColorArgb),
                                drawStyle = Stroke(width = 6f, join = StrokeJoin.Round),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    Text(
                        text = displayedText,
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontSize = overlay.fontSizeSp.sp,
                            letterSpacing = extraLetterSpacing,
                            color = Color(overlay.textColorArgb),
                            shadow = textShadow,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
            OverlayType.IMAGE -> {
                val composeBlend = when (overlay.blendMode) {
                    OverlayBlendMode.NORMAL -> BlendMode.SrcOver
                    OverlayBlendMode.SCREEN -> BlendMode.Screen
                    OverlayBlendMode.MULTIPLY -> BlendMode.Multiply
                    OverlayBlendMode.OVERLAY -> BlendMode.Overlay
                }
                val imgModifier = Modifier
                    .size(132.dp)
                    .then(
                        if (overlay.cornerRadiusDp > 0.5f) {
                            Modifier.clip(RoundedCornerShape(overlay.cornerRadiusDp.dp))
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (composeBlend != BlendMode.SrcOver) {
                            Modifier.drawWithContent {
                                val paint = Paint().apply { blendMode = composeBlend }
                                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                drawContent()
                                drawContext.canvas.restore()
                            }
                        } else {
                            Modifier
                        }
                    )
                AsyncImage(
                    model = overlay.imageUri,
                    contentDescription = "PNG or image overlay layer",
                    contentScale = if (overlay.cornerRadiusDp <= 0.5f) ContentScale.Fit else ContentScale.Crop,
                    modifier = imgModifier
                )
            }
            OverlayType.VIDEO -> {
                ChromaKeyVideoOverlayView(
                    overlay = overlay,
                    playheadMs = playheadMs,
                    isPlaying = isPlaying,
                    stageWidthPx = stageWidthPx,
                    stageHeightPx = stageHeightPx,
                    onRegisterTexture = onRegisterVideoTexture
                )
            }
        }

        // Single-finger accessible corner handles when selected (delete + scale/rotate)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-12).dp, y = (-12).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDC2626))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove overlay",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CinemaAmber)
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { onBeginGesture() },
                            onDragEnd = { onEndGesture() },
                            onDragCancel = { onEndGesture() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val scaleDelta = 1f + (dragAmount.x + dragAmount.y) * 0.004f
                                val rotDelta = dragAmount.x * 0.25f
                                onTransform(0f, 0f, scaleDelta.coerceIn(0.85f, 1.15f), rotDelta)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Scale and rotate handle",
                    tint = OnAmber,
                    modifier = Modifier.size(14.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(StudioSlate)
                    .border(1.dp, CinemaAmber, CircleShape)
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { onBeginGesture() },
                            onDragEnd = { onEndGesture() },
                            onDragCancel = { onEndGesture() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onTransform(0f, 0f, 1f, dragAmount.x * 0.6f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate overlay",
                    tint = CinemaAmber,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Live GPU-accelerated video overlay player backed by Media3's Effect pipeline
 * ([ChromaKeyGlEffect]) rendering onto a transparent [TextureView] so green/blue screen
 * backgrounds disappear in real time over the main video layer.
 */
@OptIn(UnstableApi::class)
@Composable
private fun ChromaKeyVideoOverlayView(
    overlay: OverlayLayer,
    playheadMs: Long,
    isPlaying: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    onRegisterTexture: (TextureView?) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val uriStr = overlay.videoUri ?: return

    val overlayPlayer = remember(overlay.id, uriStr) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            prepare()
        }
    }

    DisposableEffect(overlay.id, uriStr) {
        onDispose {
            onRegisterTexture(null)
            runCatching { overlayPlayer.release() }
        }
    }

    // Attach / update Media3 ChromaKeyGlEffect whenever chroma-key parameters change
    LaunchedEffect(overlay.chromaKey) {
        val effects = if (overlay.chromaKey.enabled) {
            listOf(
                ChromaKeyGlEffect(
                    settings = overlay.chromaKey,
                    premultiplyAlpha = true
                )
            )
        } else {
            emptyList()
        }
        runCatching {
            overlayPlayer.setVideoEffects(effects)
            val pos = overlayPlayer.currentPosition
            overlayPlayer.seekTo(pos)
        }
    }

    // Sync overlay playback & scrub position to global playhead
    LaunchedEffect(playheadMs, isPlaying, overlay.startTimeMs, overlay.trimStartMs, overlay.sourceDurationMs) {
        val srcDur = overlay.sourceDurationMs.coerceAtLeast(300L)
        val localMs = (playheadMs - overlay.startTimeMs).coerceAtLeast(0L)
        val targetPosMs = (overlay.trimStartMs + (localMs % srcDur)).coerceIn(0L, srcDur)
        if (!isPlaying || abs(overlayPlayer.currentPosition - targetPosMs) > 260L) {
            overlayPlayer.seekTo(targetPosMs)
        }
        if (isPlaying && !overlayPlayer.playWhenReady) {
            overlayPlayer.playWhenReady = true
        } else if (!isPlaying && overlayPlayer.playWhenReady) {
            overlayPlayer.playWhenReady = false
        }
    }

    val boxW = with(density) { (stageWidthPx * 0.54f).coerceAtLeast(110f).toDp() }
    val boxH = with(density) { (stageHeightPx * 0.42f).coerceAtLeast(110f).toDp() }

    Box(
        modifier = Modifier
            .width(boxW)
            .height(boxH)
            .clip(RoundedCornerShape(overlay.cornerRadiusDp.dp))
            .testTag("video_overlay_stage_${overlay.id}")
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    overlayPlayer.setVideoTextureView(this)
                    onRegisterTexture(this)
                }
            },
            update = { textureView ->
                textureView.isOpaque = false
                overlayPlayer.setVideoTextureView(textureView)
                onRegisterTexture(textureView)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun EmptyStagePrompt(
    onImportVideo: () -> Unit,
    onImportPhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CinemaAmber.copy(alpha = 0.14f))
                .border(1.dp, CinemaAmber.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MovieFilter,
                contentDescription = null,
                tint = CinemaAmber,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Import Media to Begin",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add videos or photos to your main track",
            style = MaterialTheme.typography.bodySmall,
            color = SilverMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onImportVideo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaAmber,
                    contentColor = OnAmber
                ),
                modifier = Modifier.testTag("empty_import_video_button")
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Video", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onImportPhoto,
                modifier = Modifier.testTag("empty_import_photo_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Photo", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun computeLiveEnvelopeGain(
    localMs: Long,
    durationMs: Long,
    volume: Float,
    isMuted: Boolean,
    fadeInMs: Long,
    fadeOutMs: Long
): Float {
    if (isMuted || volume <= 0.001f) return 0f
    var env = 1f
    if (fadeInMs > 0L && localMs < fadeInMs) {
        env = (localMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
    }
    if (fadeOutMs > 0L && durationMs > 0L) {
        val remaining = (durationMs - localMs).coerceAtLeast(0L)
        if (remaining < fadeOutMs) {
            env = minOf(env, (remaining.toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f))
        }
    }
    return (volume * env).coerceIn(0f, 1.5f)
}
