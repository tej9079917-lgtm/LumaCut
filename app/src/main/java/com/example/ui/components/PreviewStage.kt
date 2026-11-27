package com.example.ui.components

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import coil.compose.AsyncImage
import com.example.media.AutoDuckEvaluator
import com.example.media.ChromaKeyGlEffect
import com.example.media.FontResolver
import com.example.media.MaskGeometryEvaluator
import com.example.media.SelfieCutoutEngine
import com.example.media.StudioCreativeTools
import com.example.model.AspectRatio
import com.example.model.CanvasBackdropMode
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import com.example.model.ClipTransform
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.CropRect
import com.example.model.CubeLutParser
import com.example.model.FilterPreset
import com.example.model.FxLayer
import com.example.model.FxShape
import com.example.model.FxType
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.MaskSettings
import com.example.model.OverlayBlendMode
import com.example.model.OverlayGestureMath
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.SafeZoneOverlay
import com.example.model.StudioFont
import com.example.model.TextMotionEvaluator
import com.example.model.TextMotionFrame
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import com.example.model.TransitionType
import com.example.model.TransitionVisualEvaluator
import com.example.model.TransitionVisualState
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackFxViolet
import com.example.viewmodel.SelectedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
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
    seekNonce: Long = 0L,
    textMotionReplayNonce: Int = 0,
    showScopes: Boolean = false,
    scopeMode: VideoScopeMode = VideoScopeMode.WAVEFORM,
    onToggleScopeMode: (VideoScopeMode) -> Unit = {},
    isSamplingChromaColor: Boolean = false,
    showCropGuide: Boolean = false,
    onSampledChromaColor: (Long) -> Unit = {},
    onSelectOverlay: (String) -> Unit,
    onSelectCaption: (String) -> Unit = {},
    onSelectFx: (String) -> Unit = {},
    onUpdateFxRegion: (String, Float, Float, Float?, Float?) -> Unit = { _, _, _, _, _ -> },
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
    val audioLastSeekMs = remember { mutableStateMapOf<String, Long>() }
    val videoOverlayTextures = remember { mutableStateMapOf<String, TextureView>() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoPlayer.release() }
            audioPlayers.values.forEach { runCatching { it.release() } }
            audioPlayers.clear()
            audioLastSeekMs.clear()
            videoOverlayTextures.clear()
        }
    }

    val activeRange = remember(timeline, playheadMs) { timeline.mainClipAt(playheadMs) }
    val activeClip = activeRange?.clip
    val activeTransition = remember(timeline, playheadMs) { timeline.activeTransitionAt(playheadMs) }
    val activeOverlays = remember(timeline, playheadMs) { timeline.activeOverlaysAt(playheadMs) }
    val activeCaptions = remember(timeline, playheadMs) { timeline.activeCaptionsAt(playheadMs) }
    val activeFxLayers = remember(timeline, playheadMs) { timeline.activeFxLayersAt(playheadMs) }
    val anyAudioSoloed = remember(timeline.mainClips, timeline.audioClips) {
        timeline.mainClips.any { it.audio.isSolo } || timeline.audioClips.any { it.audio.isSolo }
    }
    val voiceDuckWindowsMs = remember(timeline.mainClips, timeline.audioClips) {
        buildList {
            timeline.audioClips
                .filter { !it.audio.isMuted && !it.audio.autoDuckMusic }
                .forEach { add(it.startTimeMs..it.endTimeMs) }
            timeline.mainClipRanges()
                .filter { it.clip.mediaType == MainMediaType.VIDEO && !it.clip.audio.isMuted && !it.clip.audio.autoDuckMusic }
                .forEach { add(it.startMs..it.endMs) }
        }
    }

    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestActiveRange by rememberUpdatedState(activeRange)

    var loadedVideoUri by remember { mutableStateOf<String?>(null) }
    var loadedClipId by remember { mutableStateOf<String?>(null) }
    var lastMainDriftSeekWallMs by remember { mutableLongStateOf(0L) }
    var lastMainScrubSeekWallMs by remember { mutableLongStateOf(0L) }

    fun computeSourcePosMs(range: com.example.model.ClipTimeRange, timeMs: Long): Long {
        val clip = range.clip
        val localMs = (timeMs - range.startMs).coerceIn(0L, clip.durationMs)
        return (clip.trimStartMs + (localMs * clip.safeSpeed).toLong())
            .coerceIn(0L, clip.sourceDurationMs.coerceAtLeast(1L))
    }

    // 1. Load media item & jump to clip entry when active video clip changes
    LaunchedEffect(activeClip?.id, activeClip?.uri, activeClip?.mediaType) {
        val range = latestActiveRange
        if (range != null && range.clip.mediaType == MainMediaType.VIDEO) {
            val clip = range.clip
            val targetPos = computeSourcePosMs(range, latestPlayheadMs)
            if (loadedVideoUri != clip.uri) {
                loadedVideoUri = clip.uri
                loadedClipId = clip.id
                videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
                videoPlayer.prepare()
                videoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                videoPlayer.seekTo(targetPos)
                lastMainDriftSeekWallMs = SystemClock.elapsedRealtime()
            } else if (loadedClipId != clip.id) {
                loadedClipId = clip.id
                videoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                videoPlayer.seekTo(targetPos)
                lastMainDriftSeekWallMs = SystemClock.elapsedRealtime()
            }
        } else {
            loadedClipId = null
            videoPlayer.playWhenReady = false
            videoPlayer.pause()
        }
    }

    // 2. Deterministic Play / Pause transport sync (NEVER gated on videoPlayer.isPlaying!)
    LaunchedEffect(isPlaying, activeClip?.id, activeClip?.mediaType) {
        val range = latestActiveRange
        if (range != null && range.clip.mediaType == MainMediaType.VIDEO) {
            if (isPlaying) {
                val targetPos = computeSourcePosMs(range, latestPlayheadMs)
                videoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                if (abs(videoPlayer.currentPosition - targetPos) > 140L) {
                    videoPlayer.seekTo(targetPos)
                    lastMainDriftSeekWallMs = SystemClock.elapsedRealtime()
                }
                videoPlayer.playWhenReady = true
                videoPlayer.play()
            } else {
                videoPlayer.playWhenReady = false
                videoPlayer.pause()
            }
        } else {
            videoPlayer.playWhenReady = false
            videoPlayer.pause()
        }
    }

    // 3. Explicit user seek / timeline scrubbing (fast sync during rapid drag + exact settle frame)
    LaunchedEffect(seekNonce) {
        if (seekNonce <= 0L) return@LaunchedEffect
        val range = latestActiveRange
        if (range != null && range.clip.mediaType == MainMediaType.VIDEO) {
            val targetPos = computeSourcePosMs(range, latestPlayheadMs)
            if (latestIsPlaying) {
                videoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                videoPlayer.seekTo(targetPos)
                lastMainDriftSeekWallMs = SystemClock.elapsedRealtime()
            } else {
                val now = SystemClock.elapsedRealtime()
                val rapidScrub = (now - lastMainScrubSeekWallMs) < 120L
                lastMainScrubSeekWallMs = now
                videoPlayer.setSeekParameters(
                    if (rapidScrub) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT
                )
                videoPlayer.seekTo(targetPos)
                if (rapidScrub) {
                    delay(95L)
                    if (!latestIsPlaying) {
                        val settledRange = latestActiveRange
                        if (settledRange != null && settledRange.clip.mediaType == MainMediaType.VIDEO) {
                            videoPlayer.setSeekParameters(SeekParameters.EXACT)
                            videoPlayer.seekTo(computeSourcePosMs(settledRange, latestPlayheadMs))
                        }
                    }
                }
            }
        }
    }

    // 4. Live speed, pitch, volume envelope & gentle playback drift correction (no mid-buffer seek storms)
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
            val sourcePosMs = computeSourcePosMs(range, playheadMs)

            val targetParams = PlaybackParameters(clip.safeSpeed, clip.effectiveAudioPitch)
            if (videoPlayer.playbackParameters != targetParams) {
                videoPlayer.playbackParameters = targetParams
            }

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

            // Only correct large drift while ExoPlayer is READY and actively playing, with cooldown
            if (isPlaying &&
                videoPlayer.playbackState == Player.STATE_READY &&
                videoPlayer.isPlaying
            ) {
                val now = SystemClock.elapsedRealtime()
                val driftTolerance = (700L * clip.safeSpeed).toLong().coerceAtLeast(550L)
                val drift = abs(videoPlayer.currentPosition - sourcePosMs)
                if (drift > driftTolerance && (now - lastMainDriftSeekWallMs) > 950L) {
                    lastMainDriftSeekWallMs = now
                    videoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                    videoPlayer.seekTo(sourcePosMs)
                }
            }
        }
    }

    // 5. Multi-track audio player transport & sync (unconditional pause + zero seek storms)
    LaunchedEffect(timeline.audioClips, playheadMs, isPlaying, seekNonce, anyAudioSoloed) {
        val validIds = timeline.audioClips.map { it.id }.toSet()
        val stale = audioPlayers.keys.filter { it !in validIds }
        stale.forEach { id ->
            runCatching { audioPlayers.remove(id)?.release() }
            audioLastSeekMs.remove(id)
        }

        val now = SystemClock.elapsedRealtime()
        for (audioClip in timeline.audioClips) {
            val inWindow = playheadMs in audioClip.startTimeMs until audioClip.endTimeMs
            val effectiveMuted = audioClip.audio.isMuted || (anyAudioSoloed && !audioClip.audio.isSolo)
            if (inWindow && isPlaying && !effectiveMuted) {
                var newlyCreated = false
                val player = audioPlayers.getOrPut(audioClip.id) {
                    newlyCreated = true
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(audioClip.uri)))
                        prepare()
                    }
                }
                val localMs = (playheadMs - audioClip.startTimeMs).coerceIn(0L, audioClip.durationMs)
                val targetSourceMs = (audioClip.trimStartMs + localMs)
                    .coerceIn(0L, audioClip.sourceDurationMs.coerceAtLeast(1L))
                val baseGain = computeLiveEnvelopeGain(
                    localMs = localMs,
                    durationMs = audioClip.durationMs,
                    volume = audioClip.audio.volume,
                    isMuted = false,
                    fadeInMs = audioClip.audio.fadeInMs,
                    fadeOutMs = audioClip.audio.fadeOutMs
                )
                val duckFactor = if (audioClip.audio.autoDuckMusic && voiceDuckWindowsMs.isNotEmpty()) {
                    AutoDuckEvaluator.evaluateGainMultiplier(playheadMs, voiceDuckWindowsMs)
                } else {
                    1f
                }
                player.volume = (baseGain * duckFactor).coerceIn(0f, 1f)
                val targetAudioParams = PlaybackParameters(1f, audioClip.audio.voiceEffect.pitchFactor)
                if (player.playbackParameters != targetAudioParams) {
                    player.playbackParameters = targetAudioParams
                }

                val wasPaused = !player.playWhenReady
                val lastSeek = audioLastSeekMs[audioClip.id] ?: 0L
                val drift = abs(player.currentPosition - targetSourceMs)
                if (newlyCreated || wasPaused) {
                    player.seekTo(targetSourceMs)
                    audioLastSeekMs[audioClip.id] = now
                } else if (player.playbackState == Player.STATE_READY &&
                    player.isPlaying &&
                    drift > 650L &&
                    (now - lastSeek) > 950L
                ) {
                    player.seekTo(targetSourceMs)
                    audioLastSeekMs[audioClip.id] = now
                }
                player.playWhenReady = true
                if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
                    player.play()
                }
            } else {
                audioPlayers[audioClip.id]?.let { p ->
                    p.playWhenReady = false
                    p.pause()
                }
            }
        }
    }

    // Immediate audio resync when user explicitly seeks while playing
    LaunchedEffect(seekNonce) {
        if (seekNonce <= 0L || !latestIsPlaying) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        for (audioClip in timeline.audioClips) {
            val p = audioPlayers[audioClip.id] ?: continue
            val ph = latestPlayheadMs
            if (ph in audioClip.startTimeMs until audioClip.endTimeMs) {
                val localMs = (ph - audioClip.startTimeMs).coerceIn(0L, audioClip.durationMs)
                val targetSourceMs = (audioClip.trimStartMs + localMs)
                    .coerceIn(0L, audioClip.sourceDurationMs.coerceAtLeast(1L))
                p.seekTo(targetSourceMs)
                audioLastSeekMs[audioClip.id] = now
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

        val transitionVisual = remember(activeTransition) {
            if (activeTransition != null) {
                TransitionVisualEvaluator.evaluate(activeTransition.transition.type, activeTransition.progress)
            } else {
                TransitionVisualState()
            }
        }
        val partnerClip: MainClip? = remember(activeTransition) {
            activeTransition?.let { win ->
                if (win.progress < 0.5f) win.toClip else win.fromClip
            }
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
                // 0. Canvas Backdrop Layer (Obsidian, Blurred Video Fill, or Studio Gradient)
                when (timeline.canvasBackdrop.mode) {
                    CanvasBackdropMode.SOLID_OBSIDIAN -> {
                        Box(modifier = Modifier.fillMaxSize().background(ObsidianBg))
                    }
                    CanvasBackdropMode.GRADIENT -> {
                        val preset = timeline.canvasBackdrop.gradientPreset
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(preset.startArgb), Color(preset.endArgb))
                                    )
                                )
                        )
                    }
                    CanvasBackdropMode.BLURRED_VIDEO -> {
                        val bgModel = activeClip?.thumbnailPath
                            ?: if (activeClip?.mediaType == MainMediaType.IMAGE) activeClip.uri else null
                        if (bgModel != null) {
                            AsyncImage(
                                model = bgModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(20.dp)
                                    .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(ObsidianBg))
                        }
                    }
                }

                // 1. Partner Transition Frame Layer (enables genuine two-layer dissolve, wipe, push-slide & zoom cuts)
                if (partnerClip != null && transitionVisual.showPartnerBehind && transitionVisual.partnerAlpha > 0.005f) {
                    val partnerModel = partnerClip.thumbnailPath
                        ?: if (partnerClip.mediaType == MainMediaType.IMAGE) partnerClip.uri else null
                    val partnerFilter = remember(
                        partnerClip.filterPreset,
                        partnerClip.filterIntensity,
                        partnerClip.colorAdjustments
                    ) {
                        ColorMatrixEngine.toComposeColorFilter(
                            partnerClip.filterPreset,
                            partnerClip.filterIntensity,
                            partnerClip.colorAdjustments
                        )
                    }
                    if (partnerModel != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = transitionVisual.partnerAlpha.coerceIn(0f, 1f)
                                    translationX = transitionVisual.partnerTranslationXFrac * stageWPx
                                    scaleX = transitionVisual.partnerScale
                                    scaleY = transitionVisual.partnerScale
                                }
                        ) {
                            AsyncImage(
                                model = partnerModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = partnerFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // 2. Main Track Media Layer (Hardware-composited TextureView for Video, AsyncImage for Photo)
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

                    val needsWipeClip = transitionVisual.wipeClipStartFrac > 0.001f ||
                        transitionVisual.wipeClipEndFrac < 0.999f

                    val clipLocalMs = activeRange?.let { (playheadMs - it.startMs).coerceIn(0L, activeClip.durationMs) } ?: 0L
                    val flipX = if (clipMotion.flipHorizontal) -1f else 1f
                    val flipY = if (clipMotion.flipVertical) -1f else 1f
                    val fullFillBoost = if (clipMotion.fullFrameFill) 1.18f else 1f

                    // Full-frame FX modifiers (Blur & Magnifier zoom applied directly to main stage layer)
                    val fullBlurFx = activeFxLayers.lastOrNull { it.fullFrame && it.type == FxType.BLUR }
                    val fullMagFx = activeFxLayers.lastOrNull { it.fullFrame && it.type == FxType.MAGNIFIER }
                    val fullMagScale = if (fullMagFx != null) {
                        1f + (fullMagFx.magnification - 1f) * fullMagFx.opacity
                    } else 1f

                    // Async AI Cutout preview bitmap when Cutout is enabled on main clip
                    var mainCutoutBitmap by remember(activeClip.id) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(activeClip.id, activeClip.uri, activeClip.aiCutout, activeClip.mediaType) {
                        if (!activeClip.aiCutout.enabled) {
                            mainCutoutBitmap = null
                        } else {
                            val rawBmp = if (activeClip.mediaType == MainMediaType.VIDEO) {
                                StudioCreativeTools.extractFrameBitmap(context, activeClip.uri, activeClip.trimStartMs)
                            } else {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val uri = Uri.parse(activeClip.uri)
                                        val input = if (uri.scheme == "file" && uri.path != null) {
                                            File(uri.path!!).inputStream()
                                        } else {
                                            context.contentResolver.openInputStream(uri)
                                        }
                                        input?.use { BitmapFactory.decodeStream(it) }
                                    }.getOrNull()
                                }
                            }
                            if (rawBmp != null) {
                                val processed = SelfieCutoutEngine.applyCutoutToBitmap(context, rawBmp, activeClip.aiCutout)
                                mainCutoutBitmap = processed.asImageBitmap()
                                if (rawBmp !== processed) rawBmp.recycle()
                            } else {
                                mainCutoutBitmap = null
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (needsWipeClip) {
                                    Modifier.clip(
                                        HorizontalFractionClipShape(
                                            startFrac = transitionVisual.wipeClipStartFrac,
                                            endFrac = transitionVisual.wipeClipEndFrac
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (clipMotion.crop.isActive) {
                                    Modifier.clip(CropRectClipShape(clipMotion.crop))
                                } else {
                                    Modifier
                                }
                            )
                            .applyMaskPreview(activeClip.mask, clipLocalMs)
                            .then(
                                if (fullBlurFx != null) {
                                    Modifier.blur((fullBlurFx.intensity * 18f * fullBlurFx.opacity).coerceAtLeast(1f).dp)
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer {
                                alpha = (transitionVisual.activeAlpha * clipMotion.opacity).coerceIn(0f, 1f)
                                translationX = (transitionVisual.activeTranslationXFrac * stageWPx) +
                                    (clipMotion.offsetX * stageWPx)
                                translationY = clipMotion.offsetY * stageHPx
                                scaleX = transitionVisual.activeScaleX * clipMotion.scale * fullFillBoost * fullMagScale * flipX
                                scaleY = transitionVisual.activeScaleY * clipMotion.scale * fullFillBoost * fullMagScale * flipY
                                rotationZ = clipMotion.rotationDegrees
                            }
                    ) {
                        val cutoutImg = mainCutoutBitmap
                        if (activeClip.aiCutout.enabled && cutoutImg != null) {
                            Image(
                                bitmap = cutoutImg,
                                contentDescription = "${activeClip.displayName} cutout",
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (activeClip.mediaType == MainMediaType.VIDEO) {
                            MainVideoTextureView(
                                player = videoPlayer,
                                modifier = Modifier.fillMaxSize()
                            )
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
                            if (activeClip.colorAdjustments.filmGrain > 0.01f || activeClip.colorAdjustments.halation > 0.01f) {
                                FilmFxPreviewOverlay(
                                    filmGrain = activeClip.colorAdjustments.filmGrain,
                                    halation = activeClip.colorAdjustments.halation
                                )
                            }
                        }
                    }
                }

                // 3. Live Transition FX & Curtain Overlays (Wipe Edge, Dip Black/White, Blur, Whip Pan, Glitch, Film Burn, Light Leak)
                if (activeTransition != null) {
                    TransitionFxOverlayLayer(
                        type = activeTransition.transition.type,
                        progress = activeTransition.progress,
                        visual = transitionVisual
                    )
                }

                // 4. Alignment Snap Guides (visible only while dragging overlays near center)
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

                // 5. Stacked Interactive Overlay Layers (Z-ordered)
                for (overlay in activeOverlays) {
                    val isSelected = (selection as? SelectedItem.OverlayItem)?.overlayId == overlay.id
                    InteractiveOverlayNode(
                        overlay = overlay,
                        isSelected = isSelected,
                        playheadMs = playheadMs,
                        seekNonce = seekNonce,
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

                // 5B. Live Visual FX Layers (Magnifier, Mosaic, Blur — Full-Frame & Interactive Movable Regions)
                val activeThumbModel = activeClip?.thumbnailPath
                    ?: if (activeClip?.mediaType == MainMediaType.IMAGE) activeClip.uri else null
                for (fx in activeFxLayers) {
                    val isFxSelected = (selection as? SelectedItem.FxItem)?.fxId == fx.id
                    if (fx.fullFrame) {
                        FullFrameFxOverlay(fx = fx)
                    } else {
                        InteractiveFxStageNode(
                            fx = fx,
                            isSelected = isFxSelected,
                            stageWidthPx = stageWPx,
                            stageHeightPx = stageHPx,
                            previewModel = activeThumbModel,
                            onSelect = { onSelectFx(fx.id) },
                            onUpdateRegion = { cx, cy, w, h -> onUpdateFxRegion(fx.id, cx, cy, w, h) }
                        )
                    }
                }

                // 5C. Karaoke Captions Live Overlay Layer
                for (caption in activeCaptions) {
                    val isCaptionSelected = (selection as? SelectedItem.CaptionItem)?.captionId == caption.id
                    KaraokeCaptionStageNode(
                        caption = caption,
                        playheadMs = playheadMs,
                        isSelected = isCaptionSelected,
                        stageWidthPx = stageWPx,
                        stageHeightPx = stageHPx,
                        availableFonts = availableFonts,
                        onSelect = { onSelectCaption(caption.id) }
                    )
                }

                // 5D. Live Crop Frame Rule-of-Thirds Guide (when Transform/Crop inspector is open)
                if (showCropGuide) {
                    val activeCrop = (selection as? SelectedItem.OverlayItem)?.let { sel ->
                        timeline.overlays.firstOrNull { it.id == sel.overlayId }?.crop
                    } ?: activeClip?.baseTransform?.crop ?: CropRect()
                    CropGuideHudOverlay(crop = activeCrop)
                }

                // 5E. Social Platform Safe Zone Guides HUD (TikTok / Reels / Shorts)
                if (timeline.safeZoneOverlay != SafeZoneOverlay.NONE) {
                    SafeZoneGuideOverlay(zone = timeline.safeZoneOverlay)
                }

                // 6. Live DaVinci Video Scopes HUD (Waveform / RGB Parade)
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

                // 7. Real-time Chroma Key Eyedropper tap-to-sample overlay
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

/**
 * Hardware-composited [TextureView] backing the main video preview so Compose `graphicsLayer`
 * (transitions, wipes, keyframe position/scale/rotation/opacity) works reliably on all devices.
 */
@OptIn(UnstableApi::class)
@Composable
private fun MainVideoTextureView(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var videoSize by remember { mutableStateOf(player.videoSize) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
                textureViewRef?.let { tv -> applyCenterCropTransform(tv, size) }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            textureViewRef?.let { tv ->
                runCatching { player.clearVideoTextureView(tv) }
            }
            textureViewRef = null
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    (v as? TextureView)?.let { tv ->
                        applyCenterCropTransform(tv, player.videoSize)
                    }
                }
                player.setVideoTextureView(this)
                textureViewRef = this
                applyCenterCropTransform(this, player.videoSize)
            }
        },
        update = { tv ->
            applyCenterCropTransform(tv, videoSize)
        },
        modifier = modifier
    )
}

private fun applyCenterCropTransform(textureView: TextureView, videoSize: VideoSize) {
    val viewW = textureView.width.toFloat()
    val viewH = textureView.height.toFloat()
    val rawW = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio
    val rawH = videoSize.height.toFloat()
    if (viewW <= 1f || viewH <= 1f || rawW <= 1f || rawH <= 1f) return

    val viewRatio = viewW / viewH
    val videoRatio = rawW / rawH
    var scaleX = 1f
    var scaleY = 1f
    if (videoRatio > viewRatio) {
        scaleX = videoRatio / viewRatio
    } else {
        scaleY = viewRatio / videoRatio
    }
    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
    textureView.setTransform(matrix)
}

private class HorizontalFractionClipShape(
    private val startFrac: Float,
    private val endFrac: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = (startFrac.coerceIn(0f, 1f) * size.width)
        val right = (endFrac.coerceIn(startFrac.coerceIn(0f, 1f), 1f) * size.width)
        return Outline.Rectangle(Rect(left = left, top = 0f, right = right, bottom = size.height))
    }
}

private class CropRectClipShape(
    private val crop: CropRect
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val safe = crop.sanitized()
        return Outline.Rectangle(
            Rect(
                left = safe.left * size.width,
                top = safe.top * size.height,
                right = (1f - safe.right) * size.width,
                bottom = (1f - safe.bottom) * size.height
            )
        )
    }
}

@Composable
private fun CropGuideHudOverlay(crop: CropRect) {
    val safe = crop.sanitized()
    Canvas(modifier = Modifier.fillMaxSize().testTag("crop_guide_hud")) {
        val l = safe.left * size.width
        val t = safe.top * size.height
        val r = (1f - safe.right) * size.width
        val b = (1f - safe.bottom) * size.height
        val scrim = Color.Black.copy(alpha = 0.45f)
        if (t > 0f) drawRect(scrim, Offset.Zero, Size(size.width, t))
        if (b < size.height) drawRect(scrim, Offset(0f, b), Size(size.width, size.height - b))
        if (l > 0f) drawRect(scrim, Offset(0f, t), Size(l, b - t))
        if (r < size.width) drawRect(scrim, Offset(r, t), Size(size.width - r, b - t))

        val w = (r - l).coerceAtLeast(4f)
        val h = (b - t).coerceAtLeast(4f)
        drawRect(
            color = CinemaAmber,
            topLeft = Offset(l, t),
            size = Size(w, h),
            style = Stroke(width = 2.dp.toPx())
        )
        // Rule-of-thirds interior guides
        for (i in 1..2) {
            val gx = l + w * (i / 3f)
            val gy = t + h * (i / 3f)
            drawLine(CinemaAmber.copy(alpha = 0.45f), Offset(gx, t), Offset(gx, b), strokeWidth = 1.dp.toPx())
            drawLine(CinemaAmber.copy(alpha = 0.45f), Offset(l, gy), Offset(r, gy), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun FullFrameFxOverlay(fx: FxLayer) {
    when (fx.type) {
        FxType.MOSAIC -> {
            val cols = (34 - (fx.intensity * 24f)).roundToInt().coerceIn(8, 34)
            Canvas(modifier = Modifier.fillMaxSize().testTag("full_fx_mosaic_${fx.id}")) {
                val cell = (size.width / cols).coerceAtLeast(8f)
                val rows = (size.height / cell).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val shade = if ((r + c) % 2 == 0) Color.White else Color.Black
                        drawRect(
                            color = shade.copy(alpha = (0.14f + fx.intensity * 0.20f) * fx.opacity),
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell - 0.5f, cell - 0.5f)
                        )
                    }
                }
            }
        }
        FxType.BLUR -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = (0.06f * fx.intensity * fx.opacity).coerceIn(0f, 0.16f)))
                    .testTag("full_fx_blur_${fx.id}")
            )
        }
        FxType.MAGNIFIER -> {
            if (fx.showBorderRing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, CinemaAmber.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .testTag("full_fx_magnifier_${fx.id}")
                )
            }
        }
    }
}

@Composable
private fun InteractiveFxStageNode(
    fx: FxLayer,
    isSelected: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    previewModel: Any?,
    onSelect: () -> Unit,
    onUpdateRegion: (Float, Float, Float?, Float?) -> Unit
) {
    val density = LocalDensity.current
    val latestFx by rememberUpdatedState(fx)
    val latestStageW by rememberUpdatedState(stageWidthPx)
    val latestStageH by rememberUpdatedState(stageHeightPx)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnUpdate by rememberUpdatedState(onUpdateRegion)

    val boxWPx = (fx.width * stageWidthPx).coerceIn(56f, stageWidthPx)
    val boxHPx = (fx.height * stageHeightPx).coerceIn(56f, stageHeightPx)
    val boxWDp = with(density) { boxWPx.toDp() }
    val boxHDp = with(density) { boxHPx.toDp() }
    val offsetX = ((fx.centerX - 0.5f) * stageWidthPx).roundToInt()
    val offsetY = ((fx.centerY - 0.5f) * stageHeightPx).roundToInt()
    val shape = if (fx.shape == FxShape.CIRCLE) CircleShape else RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .width(boxWDp)
            .height(boxHDp)
            .testTag("stage_fx_node_${fx.id}"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .then(
                    when (fx.type) {
                        FxType.MAGNIFIER -> Modifier.background(Color.Black.copy(alpha = 0.35f))
                        FxType.MOSAIC -> Modifier.background(Color(0xFF1E1B4B).copy(alpha = 0.25f * fx.opacity))
                        FxType.BLUR -> Modifier.background(Color(0xFF0F172A).copy(alpha = (0.30f + fx.intensity * 0.28f) * fx.opacity))
                    }
                )
                .border(
                    width = if (isSelected) 2.2.dp else if (fx.type == FxType.MAGNIFIER && fx.showBorderRing) 2.dp else 1.2.dp,
                    color = when {
                        isSelected -> CinemaAmber
                        fx.type == FxType.MAGNIFIER && fx.showBorderRing -> ChalkWhite.copy(alpha = 0.9f)
                        else -> TrackFxViolet.copy(alpha = 0.85f)
                    },
                    shape = shape
                )
                .pointerInput(fx.id) {
                    detectTapGestures(onTap = { latestOnSelect() })
                }
                .pointerInput(fx.id) {
                    detectDragGestures(
                        onDragStart = { latestOnSelect() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val cur = latestFx
                            val nextCx = (cur.centerX + dragAmount.x / latestStageW.coerceAtLeast(1f)).coerceIn(0.06f, 0.94f)
                            val nextCy = (cur.centerY + dragAmount.y / latestStageH.coerceAtLeast(1f)).coerceIn(0.06f, 0.94f)
                            latestOnUpdate(nextCx, nextCy, null, null)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            when (fx.type) {
                FxType.MAGNIFIER -> {
                    if (previewModel != null) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Magnifier preview lens",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = fx.magnification
                                    scaleY = fx.magnification
                                    translationX = (0.5f - fx.centerX) * stageWidthPx * 0.45f
                                    translationY = (0.5f - fx.centerY) * stageHeightPx * 0.45f
                                    alpha = fx.opacity
                                }
                        )
                    }
                    // Subtle optical glass sheen + magnification badge
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)
                                )
                            )
                    )
                    Text(
                        text = String.format("%.1fx", fx.magnification),
                        style = MaterialTheme.typography.labelSmall,
                        color = CinemaAmber,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ObsidianBg.copy(alpha = 0.72f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                FxType.MOSAIC -> {
                    val cells = (14 - (fx.intensity * 9f)).roundToInt().coerceIn(4, 14)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cw = size.width / cells
                        val ch = size.height / cells
                        for (r in 0 until cells) {
                            for (c in 0 until cells) {
                                val tone = ((r * 31 + c * 17) % 5) / 5f
                                drawRect(
                                    color = Color(
                                        red = 0.20f + tone * 0.45f,
                                        green = 0.22f + tone * 0.40f,
                                        blue = 0.30f + tone * 0.45f,
                                        alpha = (0.58f + fx.intensity * 0.30f) * fx.opacity
                                    ),
                                    topLeft = Offset(c * cw, r * ch),
                                    size = Size(cw - 1f, ch - 1f)
                                )
                            }
                        }
                    }
                    Text(
                        text = "MOSAIC",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChalkWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ObsidianBg.copy(alpha = 0.65f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                FxType.BLUR -> {
                    if (previewModel != null) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Gaussian blur region",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur((fx.intensity * 18f).coerceAtLeast(2f).dp)
                                .graphicsLayer { alpha = (0.72f * fx.opacity).coerceIn(0.2f, 0.92f) }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = (0.14f + fx.intensity * 0.16f) * fx.opacity))
                    )
                    Text(
                        text = "BLUR ${(fx.intensity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChalkWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ObsidianBg.copy(alpha = 0.65f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Bottom-right Resize Handle when FX region is selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CinemaAmber)
                    .border(1.5.dp, ObsidianBg, CircleShape)
                    .pointerInput(fx.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cur = latestFx
                            val dw = (dragAmount.x * 2f) / latestStageW.coerceAtLeast(1f)
                            val dh = (dragAmount.y * 2f) / latestStageH.coerceAtLeast(1f)
                            val nextW = (cur.width + dw).coerceIn(0.12f, 0.96f)
                            val nextH = if (cur.shape == FxShape.CIRCLE) nextW else (cur.height + dh).coerceIn(0.12f, 0.96f)
                            latestOnUpdate(cur.centerX, cur.centerY, nextW, nextH)
                        }
                    }
                    .testTag("stage_fx_resize_${fx.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Resize FX region",
                    tint = OnAmber,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun TransitionFxOverlayLayer(
    type: TransitionType,
    progress: Float,
    visual: TransitionVisualState
) {
    // 1. Wipe edge highlight line
    if (type == TransitionType.WIPE_RIGHT || type == TransitionType.WIPE_LEFT) {
        val p = progress.coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgeX = if (type == TransitionType.WIPE_RIGHT) p * size.width else (1f - p) * size.width
            drawLine(
                color = CinemaAmber.copy(alpha = 0.85f),
                start = Offset(edgeX, 0f),
                end = Offset(edgeX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }

    // 2. Dip-to-Black / Dip-to-White veil
    if (visual.dipBlackAlpha > 0.01f) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = visual.dipBlackAlpha)))
    }
    if (visual.dipWhiteAlpha > 0.01f) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = visual.dipWhiteAlpha)))
    }

    // 3. Directional Blur & Whip Pan motion streak sheen
    val streakStrength = maxOf(visual.blurStreakStrength, visual.whipStreakStrength)
    if (streakStrength > 0.02f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bandCount = if (visual.whipStreakStrength > 0.02f) 14 else 9
            for (i in 0 until bandCount) {
                val y = (i + 0.5f) / bandCount * size.height
                val bandH = size.height / (bandCount * 1.4f)
                val alpha = (0.14f + (i % 3) * 0.06f) * streakStrength
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = alpha),
                            Color.Transparent
                        )
                    ),
                    topLeft = Offset(0f, y - bandH / 2f),
                    size = Size(size.width, bandH)
                )
            }
        }
    }

    // 4. Glitch RGB chromatic slices & scanlines
    if (visual.glitchStrength > 0.02f) {
        val g = visual.glitchStrength
        val phase = visual.glitchPhase
        Canvas(modifier = Modifier.fillMaxSize()) {
            val slices = 7
            for (i in 0 until slices) {
                val seed = ((phase * 23f + i * 3.7f).toInt() % 10) / 10f
                val sliceTop = (i.toFloat() / slices) * size.height
                val sliceH = (size.height / slices) * (0.45f + seed * 0.45f)
                val shift = (seed - 0.5f) * size.width * 0.25f * g
                val color = when (i % 3) {
                    0 -> Color(0xFF00E5FF).copy(alpha = 0.34f * g)
                    1 -> Color(0xFFFF2D55).copy(alpha = 0.34f * g)
                    else -> Color(0xFFF59E0B).copy(alpha = 0.24f * g)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(shift.coerceIn(-size.width * 0.2f, size.width * 0.2f), sliceTop),
                    size = Size(size.width, sliceH)
                )
            }
            // Crisp horizontal scanlines
            var y = 0f
            val step = 6.dp.toPx()
            while (y < size.height) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.25f * g),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }
        }
    }

    // 5. Film Burn organic warm overexposure flare
    if (visual.filmBurnStrength > 0.02f) {
        val fb = visual.filmBurnStrength
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF7AE).copy(alpha = (0.85f * fb).coerceIn(0f, 0.9f)),
                            Color(0xFFF97316).copy(alpha = (0.68f * fb).coerceIn(0f, 0.78f)),
                            Color(0xFF9A3412).copy(alpha = (0.42f * fb).coerceIn(0f, 0.55f)),
                            Color.Transparent
                        )
                    )
                )
        )
    }

    // 6. Anamorphic Light Leak diagonal golden-coral-violet wash
    if (visual.lightLeakStrength > 0.02f) {
        val ll = visual.lightLeakStrength
        val sweep = visual.glitchPhase.coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = size.width * (sweep * 0.8f - 0.3f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFF59E0B).copy(alpha = (0.58f * ll).coerceIn(0f, 0.65f)),
                        Color(0xFFFF6B35).copy(alpha = (0.64f * ll).coerceIn(0f, 0.72f)),
                        Color(0xFFA855F7).copy(alpha = (0.36f * ll).coerceIn(0f, 0.45f)),
                        Color.Transparent
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(startX + size.width * 0.9f, size.height)
                )
            )
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

    if (adjustments.filmGrain > 0.01f || adjustments.halation > 0.01f) {
        FilmFxPreviewOverlay(
            filmGrain = adjustments.filmGrain,
            halation = adjustments.halation
        )
    }
}

@Composable
private fun FilmFxPreviewOverlay(
    filmGrain: Float,
    halation: Float
) {
    if (halation > 0.01f) {
        val h = halation.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF7A3D).copy(alpha = 0.20f * h),
                            Color(0xFFE11D48).copy(alpha = 0.10f * h),
                            Color.Transparent
                        )
                    )
                )
        )
    }
    if (filmGrain > 0.01f) {
        val g = filmGrain.coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 22
            val rows = 28
            val cellW = size.width / cols
            val cellH = size.height / rows
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val hash = ((r * 73856093) xor (c * 19349663)) and 0xFF
                    if (hash % 3 == 0) {
                        val bright = (hash and 1) == 0
                        drawCircle(
                            color = (if (bright) Color.White else Color.Black).copy(alpha = 0.14f * g),
                            radius = 1.1f,
                            center = Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH)
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.applyMaskPreview(mask: MaskSettings, localMs: Long): Modifier {
    if (!mask.isActive) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val androidPath = MaskGeometryEvaluator.buildAndroidPath(mask, size.width, size.height, localMs)
            drawPath(
                path = androidPath.asComposePath(),
                color = Color.White,
                blendMode = if (mask.invert) BlendMode.DstOut else BlendMode.DstIn
            )
        }
}

@Composable
private fun KaraokeCaptionStageNode(
    caption: CaptionClip,
    playheadMs: Long,
    isSelected: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    availableFonts: List<StudioFont>,
    onSelect: () -> Unit
) {
    val fontFamily = remember(caption.fontId, availableFonts) {
        FontResolver.resolveComposeFont(caption.fontId, availableFonts)
    }
    val localMs = (playheadMs - caption.startTimeMs).coerceIn(0L, caption.durationMs)
    val progress = (localMs.toFloat() / caption.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    val words = remember(caption.text) {
        caption.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    val activeWordIdx = if (words.isNotEmpty()) {
        (progress * words.size).toInt().coerceIn(0, words.lastIndex)
    } else -1

    val animScale = when (caption.animationPreset) {
        CaptionAnimationPreset.POP_IN -> {
            val t = (localMs / 240f).coerceIn(0f, 1f)
            0.72f + 0.28f * t
        }
        else -> 1f
    }
    val animTranslateY = when (caption.animationPreset) {
        CaptionAnimationPreset.SLIDE_UP -> {
            val t = (localMs / 260f).coerceIn(0f, 1f)
            (1f - t) * 28f
        }
        else -> 0f
    }

    val pxX = (caption.offsetX * stageWidthPx).roundToInt()
    val pxY = (caption.offsetY * stageHeightPx).roundToInt()

    val annotated = buildAnnotatedString {
        if (caption.animationPreset == CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT && words.isNotEmpty()) {
            words.forEachIndexed { idx, word ->
                val isCurrent = idx == activeWordIdx
                withStyle(
                    SpanStyle(
                        color = if (isCurrent) Color(caption.highlightColorArgb) else Color(caption.textColorArgb),
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                ) {
                    append(word)
                }
                if (idx < words.lastIndex) append(" ")
            }
        } else {
            withStyle(SpanStyle(color = Color(caption.textColorArgb), fontWeight = FontWeight.Bold)) {
                append(caption.text)
            }
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(pxX, pxY) }
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
                translationY = animTranslateY
            }
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (caption.hasBackgroundPill) {
                    Modifier.background(Color(caption.backgroundPillArgb))
                } else Modifier
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, CinemaAmber, RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("stage_caption_${caption.id}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = annotated,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = caption.fontSizeSp.sp,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color(caption.strokeColorArgb).copy(alpha = 0.9f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f
                )
            )
        )
    }
}

@Composable
private fun SafeZoneGuideOverlay(zone: SafeZoneOverlay) {
    val topFrac = when (zone) {
        SafeZoneOverlay.TIKTOK -> 0.13f
        SafeZoneOverlay.REELS -> 0.12f
        SafeZoneOverlay.SHORTS -> 0.11f
        SafeZoneOverlay.YOUTUBE -> 0.10f
        SafeZoneOverlay.NONE -> 0f
    }
    val bottomFrac = when (zone) {
        SafeZoneOverlay.TIKTOK -> 0.24f
        SafeZoneOverlay.REELS -> 0.22f
        SafeZoneOverlay.SHORTS -> 0.21f
        SafeZoneOverlay.YOUTUBE -> 0.17f
        SafeZoneOverlay.NONE -> 0f
    }
    val leftRailFrac = when (zone) {
        SafeZoneOverlay.YOUTUBE -> 0.05f
        else -> 0f
    }
    val rightRailFrac = when (zone) {
        SafeZoneOverlay.TIKTOK -> 0.17f
        SafeZoneOverlay.REELS -> 0.15f
        SafeZoneOverlay.SHORTS -> 0.16f
        SafeZoneOverlay.YOUTUBE -> 0.06f
        SafeZoneOverlay.NONE -> 0f
    }
    Box(modifier = Modifier.fillMaxSize().testTag("safe_zone_hud_${zone.name.lowercase()}")) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topY = size.height * topFrac
            val bottomY = size.height * (1f - bottomFrac)
            val leftX = size.width * leftRailFrac
            val rightX = size.width * (1f - rightRailFrac)
            val tint = Color(0xFFEF4444).copy(alpha = 0.16f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

            drawRect(tint, Offset.Zero, Size(size.width, topY))
            drawRect(tint, Offset(0f, bottomY), Size(size.width, size.height - bottomY))
            if (leftX > 0f) {
                drawRect(tint, Offset(0f, topY), Size(leftX, (bottomY - topY).coerceAtLeast(0f)))
            }
            drawRect(tint, Offset(rightX, topY), Size(size.width - rightX, (bottomY - topY).coerceAtLeast(0f)))

            // YouTube bottom-right channel watermark / end-card corner reserve
            if (zone == SafeZoneOverlay.YOUTUBE) {
                val wmW = size.width * 0.10f
                val wmH = size.height * 0.11f
                drawRect(
                    color = Color(0xFFEF4444).copy(alpha = 0.24f),
                    topLeft = Offset((rightX - wmW).coerceAtLeast(leftX), (bottomY - wmH).coerceAtLeast(topY)),
                    size = Size(wmW, wmH)
                )
            }

            val boxLeft = (leftX + if (leftX > 0f) 0f else 8f)
            val boxWidth = (rightX - boxLeft - if (leftX > 0f) 0f else 4f).coerceAtLeast(10f)
            drawRect(
                color = CinemaAmber.copy(alpha = 0.75f),
                topLeft = Offset(boxLeft, topY),
                size = Size(boxWidth, (bottomY - topY).coerceAtLeast(10f)),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
            )
        }
        Text(
            text = "${zone.label} Safe Zone",
            style = MaterialTheme.typography.labelSmall,
            color = CinemaAmber,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(ObsidianBg.copy(alpha = 0.78f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InteractiveOverlayNode(
    overlay: OverlayLayer,
    isSelected: Boolean,
    playheadMs: Long,
    seekNonce: Long,
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
    val latestOverlay by rememberUpdatedState(overlay)
    val latestStageW by rememberUpdatedState(stageWidthPx)
    val latestStageH by rememberUpdatedState(stageHeightPx)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnBeginGesture by rememberUpdatedState(onBeginGesture)
    val latestOnTransform by rememberUpdatedState(onTransform)
    val latestOnEndGesture by rememberUpdatedState(onEndGesture)

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

    val effectiveScale = overlay.scale * (textMotionFrame?.scale ?: 1f)
    val effectiveRotation = overlay.rotationDegrees + (textMotionFrame?.rotationDegrees ?: 0f)
    val latestScale by rememberUpdatedState(effectiveScale)
    val latestRotation by rememberUpdatedState(effectiveRotation)

    val pxOffsetX = ((overlay.offsetX + (textMotionFrame?.offsetXFraction ?: 0f)) * stageWidthPx).roundToInt()
    val pxOffsetY = ((overlay.offsetY + (textMotionFrame?.offsetYFraction ?: 0f)) * stageHeightPx).roundToInt()

    val overlayLocalMs = (playheadMs - overlay.startTimeMs).coerceIn(0L, overlay.durationMs)
    val flipX = if (overlay.flipHorizontal) -1f else 1f
    val flipY = if (overlay.flipVertical) -1f else 1f
    val fullFillBoost = if (overlay.fullFrameFill) 1.22f else 1f

    Box(
        modifier = Modifier
            .offset { IntOffset(pxOffsetX, pxOffsetY) }
            .then(
                if (overlay.crop.isActive) Modifier.clip(CropRectClipShape(overlay.crop)) else Modifier
            )
            .applyMaskPreview(overlay.mask, overlayLocalMs)
            .graphicsLayer {
                scaleX = effectiveScale * fullFillBoost * flipX
                scaleY = effectiveScale * fullFillBoost * flipY
                rotationZ = effectiveRotation
                alpha = (overlay.opacity * (textMotionFrame?.alpha ?: 1f)).coerceIn(0f, 1f)
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
                val ctx = LocalContext.current
                var cutoutImg by remember(overlay.id) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(overlay.id, overlay.imageUri, overlay.aiCutout) {
                    if (!overlay.aiCutout.enabled || overlay.imageUri.isNullOrBlank()) {
                        cutoutImg = null
                    } else {
                        val raw = withContext(Dispatchers.IO) {
                            runCatching {
                                val uri = Uri.parse(overlay.imageUri)
                                val input = if (uri.scheme == "file" && uri.path != null) {
                                    File(uri.path!!).inputStream()
                                } else {
                                    ctx.contentResolver.openInputStream(uri)
                                }
                                input?.use { BitmapFactory.decodeStream(it) }
                            }.getOrNull()
                        }
                        if (raw != null) {
                            val processed = SelfieCutoutEngine.applyCutoutToBitmap(ctx, raw, overlay.aiCutout)
                            cutoutImg = processed.asImageBitmap()
                            if (raw !== processed) raw.recycle()
                        } else {
                            cutoutImg = null
                        }
                    }
                }

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
                val readyCutout = cutoutImg
                if (overlay.aiCutout.enabled && readyCutout != null) {
                    Image(
                        bitmap = readyCutout,
                        contentDescription = "AI Cutout image overlay",
                        contentScale = if (overlay.cornerRadiusDp <= 0.5f) ContentScale.Fit else ContentScale.Crop,
                        modifier = imgModifier
                    )
                } else {
                    AsyncImage(
                        model = overlay.imageUri,
                        contentDescription = "PNG or image overlay layer",
                        contentScale = if (overlay.cornerRadiusDp <= 0.5f) ContentScale.Fit else ContentScale.Crop,
                        modifier = imgModifier
                    )
                }
            }
            OverlayType.VIDEO -> {
                ChromaKeyVideoOverlayView(
                    overlay = overlay,
                    playheadMs = playheadMs,
                    seekNonce = seekNonce,
                    isPlaying = isPlaying,
                    stageWidthPx = stageWidthPx,
                    stageHeightPx = stageHeightPx,
                    onRegisterTexture = onRegisterVideoTexture
                )
            }
        }

        // Dedicated transparent gesture capture layer ABOVE TextureView/Image/Text (and BELOW corner handles)
        // Guarantees instant 1-finger drag + 2-finger pinch/rotate + clean onEndGesture() on every overlay type!
        Box(
            modifier = Modifier
                .matchParentSize()
                .testTag("overlay_drag_surface_${overlay.id}")
                .pointerInput(overlay.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var gestureStarted = false
                        var zoomAccum = 1f
                        var rotationAccum = 0f
                        var panAccum = Offset.Zero
                        val touchSlop = (viewConfiguration.touchSlop * 0.45f).coerceAtLeast(3f)

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val rotationChange = event.calculateRotation()
                                    val panChange = event.calculatePan()

                                    if (!gestureStarted) {
                                        zoomAccum *= zoomChange
                                        rotationAccum += rotationChange
                                        panAccum += panChange

                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1f - zoomAccum) * centroidSize
                                        val rotationMotion = abs(rotationAccum * Math.PI.toFloat() * centroidSize / 180f)
                                        val panMotion = hypot(panAccum.x.toDouble(), panAccum.y.toDouble()).toFloat()

                                        if (zoomMotion > touchSlop ||
                                            rotationMotion > touchSlop ||
                                            panMotion > touchSlop
                                        ) {
                                            gestureStarted = true
                                            latestOnSelect()
                                            latestOnBeginGesture()
                                        }
                                    }

                                    if (gestureStarted) {
                                        val hasChange = rotationChange != 0f || zoomChange != 1f || panChange != Offset.Zero
                                        if (hasChange) {
                                            val (stageDx, stageDy) = OverlayGestureMath.localPanToStageDelta(
                                                localDx = panChange.x,
                                                localDy = panChange.y,
                                                scale = latestScale,
                                                rotationDegrees = latestRotation
                                            )
                                            latestOnTransform(
                                                stageDx / latestStageW.coerceAtLeast(1f),
                                                stageDy / latestStageH.coerceAtLeast(1f),
                                                zoomChange,
                                                rotationChange
                                            )
                                        }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }
                                }
                            } while (!canceled && event.changes.any { it.pressed })

                            if (!gestureStarted) {
                                latestOnSelect()
                            }
                        } finally {
                            if (gestureStarted) {
                                latestOnEndGesture()
                            }
                        }
                    }
                }
        )

        // Single-finger accessible corner handles when selected (delete + scale/rotate)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-12).dp, y = (-12).dp)
                    .size(26.dp)
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
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(CinemaAmber)
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { latestOnBeginGesture() },
                            onDragEnd = { latestOnEndGesture() },
                            onDragCancel = { latestOnEndGesture() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val scaleDelta = 1f + (dragAmount.x + dragAmount.y) * 0.004f
                                val rotDelta = dragAmount.x * 0.25f
                                latestOnTransform(0f, 0f, scaleDelta.coerceIn(0.85f, 1.15f), rotDelta)
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
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(StudioSlate)
                    .border(1.dp, CinemaAmber, CircleShape)
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { latestOnBeginGesture() },
                            onDragEnd = { latestOnEndGesture() },
                            onDragCancel = { latestOnEndGesture() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                latestOnTransform(0f, 0f, 1f, dragAmount.x * 0.6f)
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
 * ([ChromaKeyGlEffect]) rendering onto a transparent, touch-passthrough [TextureView].
 */
@OptIn(UnstableApi::class)
@Composable
private fun ChromaKeyVideoOverlayView(
    overlay: OverlayLayer,
    playheadMs: Long,
    seekNonce: Long,
    isPlaying: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    onRegisterTexture: (TextureView?) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val uriStr = overlay.videoUri ?: return

    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestOverlay by rememberUpdatedState(overlay)

    val overlayPlayer = remember(overlay.id, uriStr) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            prepare()
        }
    }

    var textureRef by remember(overlay.id, uriStr) { mutableStateOf<TextureView?>(null) }
    var overlayVideoSize by remember(overlay.id, uriStr) { mutableStateOf(overlayPlayer.videoSize) }
    var lastOverlayDriftSeekMs by remember(overlay.id) { mutableLongStateOf(0L) }

    fun targetOverlayPosMs(ov: OverlayLayer, phMs: Long): Long {
        val srcDur = ov.sourceDurationMs.coerceAtLeast(300L)
        val localMs = (phMs - ov.startTimeMs).coerceAtLeast(0L)
        return (ov.trimStartMs + (localMs % srcDur)).coerceIn(0L, srcDur)
    }

    DisposableEffect(overlay.id, uriStr) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                overlayVideoSize = size
                textureRef?.let { tv -> applyCenterCropTransform(tv, size) }
            }
        }
        overlayPlayer.addListener(listener)
        onDispose {
            overlayPlayer.removeListener(listener)
            textureRef?.let { tv -> runCatching { overlayPlayer.clearVideoTextureView(tv) } }
            textureRef = null
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
            val pos = targetOverlayPosMs(latestOverlay, latestPlayheadMs)
            overlayPlayer.seekTo(pos)
        }
    }

    // Deterministic Play / Pause sync for video overlay (never seek-stormed)
    LaunchedEffect(isPlaying, overlay.id) {
        val targetPos = targetOverlayPosMs(latestOverlay, latestPlayheadMs)
        if (isPlaying) {
            if (abs(overlayPlayer.currentPosition - targetPos) > 160L) {
                overlayPlayer.setSeekParameters(SeekParameters.DEFAULT)
                overlayPlayer.seekTo(targetPos)
                lastOverlayDriftSeekMs = SystemClock.elapsedRealtime()
            }
            overlayPlayer.playWhenReady = true
            overlayPlayer.play()
        } else {
            overlayPlayer.playWhenReady = false
            overlayPlayer.pause()
        }
    }

    // Explicit seek / timeline scrub sync
    LaunchedEffect(seekNonce, overlay.startTimeMs, overlay.trimStartMs) {
        val targetPos = targetOverlayPosMs(latestOverlay, latestPlayheadMs)
        overlayPlayer.setSeekParameters(
            if (latestIsPlaying) SeekParameters.DEFAULT else SeekParameters.CLOSEST_SYNC
        )
        overlayPlayer.seekTo(targetPos)
        lastOverlayDriftSeekMs = SystemClock.elapsedRealtime()
    }

    // Gentle drift check during active playback only
    LaunchedEffect(playheadMs, isPlaying) {
        if (isPlaying &&
            overlayPlayer.playbackState == Player.STATE_READY &&
            overlayPlayer.isPlaying
        ) {
            val now = SystemClock.elapsedRealtime()
            val targetPos = targetOverlayPosMs(latestOverlay, playheadMs)
            if (abs(overlayPlayer.currentPosition - targetPos) > 700L &&
                (now - lastOverlayDriftSeekMs) > 950L
            ) {
                lastOverlayDriftSeekMs = now
                overlayPlayer.setSeekParameters(SeekParameters.DEFAULT)
                overlayPlayer.seekTo(targetPos)
            }
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
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnTouchListener { _, _ -> false }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        (v as? TextureView)?.let { tv ->
                            applyCenterCropTransform(tv, overlayPlayer.videoSize)
                        }
                    }
                    overlayPlayer.setVideoTextureView(this)
                    textureRef = this
                    onRegisterTexture(this)
                    applyCenterCropTransform(this, overlayPlayer.videoSize)
                }
            },
            update = { textureView ->
                textureView.isOpaque = false
                applyCenterCropTransform(textureView, overlayVideoSize)
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
