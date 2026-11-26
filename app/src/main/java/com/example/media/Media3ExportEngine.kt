package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.AspectRatio
import com.example.model.ColorMatrixEngine
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.FilterPreset
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.StudioFont
import com.example.model.TextMotionEvaluator
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import com.example.model.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@UnstableApi
class Media3ExportEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    fun startExport(
        scope: CoroutineScope,
        projectTitle: String,
        aspectRatio: AspectRatio,
        timeline: TimelineDocument,
        availableFonts: List<StudioFont>,
        config: ExportConfig,
        onStateChange: (ExportState) -> Unit
    ) {
        if (timeline.mainClips.isEmpty()) {
            onStateChange(ExportState.Error("Add at least one video or photo clip before exporting."))
            return
        }

        cancelExport()
        onStateChange(ExportState.Exporting(2, "Preparing multilayer composition…"))

        val (targetW, targetH) = aspectRatio.exportDimensions(config.resolution)
        val clampedBitrateBps = (config.bitrateMbps.coerceIn(4, 60) * 1_000_000)
        val settingsSummary = "${config.resolution.label} • ${config.fps}fps • ${config.bitrateMbps} Mbps"

        scope.launch {
            val prepResult = withContext(Dispatchers.IO) {
                runCatching {
                    val overlayBitmaps = timeline.overlays
                        .filter { it.type != OverlayType.VIDEO }
                        .sortedBy { it.zIndex }
                        .associate { layer ->
                            layer.id to OverlayBitmapRenderer.renderOverlayBitmap(
                                context = context,
                                overlay = layer,
                                exportWidth = targetW,
                                exportHeight = targetH,
                                availableFonts = availableFonts
                            )
                        }

                    val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val tempFile = File(cacheDir, "lumacut_tmp_${System.currentTimeMillis()}.mp4")
                    if (tempFile.exists()) tempFile.delete()
                    Pair(overlayBitmaps, tempFile)
                }
            }

            prepResult.onFailure { err ->
                onStateChange(ExportState.Error(err.message ?: "Failed to prepare overlays for export."))
                return@launch
            }

            val (overlayBitmaps, tempFile) = prepResult.getOrThrow()

            mainHandler.post {
                try {
                    val composition = buildComposition(
                        timeline = timeline,
                        targetW = targetW,
                        targetH = targetH,
                        fps = config.fps,
                        availableFonts = availableFonts,
                        overlayBitmaps = overlayBitmaps
                    )

                    val videoEncoderSettings = VideoEncoderSettings.Builder()
                        .setBitrate(clampedBitrateBps)
                        .build()
                    val encoderFactory = DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(videoEncoderSettings)
                        .setEnableFallback(true)
                        .build()

                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactory)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                stopProgressPolling()
                                activeTransformer = null
                                recycleBitmaps(overlayBitmaps)
                                onStateChange(ExportState.Exporting(96, "Saving MP4 to device gallery…"))
                                scope.launch {
                                    val saved = saveExportedFileToGallery(projectTitle, tempFile)
                                    saved.onSuccess { info ->
                                        onStateChange(
                                            ExportState.Success(
                                                contentUri = info.uriString,
                                                displayName = info.displayName,
                                                sizeMb = info.sizeMb,
                                                savedToGallery = info.savedToGallery
                                            )
                                        )
                                    }.onFailure { err ->
                                        onStateChange(ExportState.Error(err.message ?: "Failed to save MP4."))
                                    }
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                stopProgressPolling()
                                activeTransformer = null
                                recycleBitmaps(overlayBitmaps)
                                tempFile.delete()
                                onStateChange(
                                    ExportState.Error(
                                        exportException.localizedMessage
                                            ?: "Export failed on this device codec."
                                    )
                                )
                            }
                        })
                        .build()

                    activeTransformer = transformer
                    onStateChange(ExportState.Exporting(5, "Rendering $settingsSummary…"))
                    transformer.start(composition, tempFile.absolutePath)
                    startProgressPolling(transformer, settingsSummary, onStateChange)
                } catch (e: Exception) {
                    recycleBitmaps(overlayBitmaps)
                    tempFile.delete()
                    onStateChange(ExportState.Error(e.localizedMessage ?: "Unable to start export."))
                }
            }
        }
    }

    fun cancelExport() {
        mainHandler.post {
            stopProgressPolling()
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
        }
    }

    private fun startProgressPolling(
        transformer: Transformer,
        settingsSummary: String,
        onStateChange: (ExportState) -> Unit
    ) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                when (state) {
                    Transformer.PROGRESS_STATE_AVAILABLE -> {
                        val pct = holder.progress.coerceIn(5, 95)
                        onStateChange(ExportState.Exporting(pct, "Encoding $settingsSummary ($pct%)…"))
                    }
                    Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY -> {
                        onStateChange(ExportState.Exporting(10, "Initializing encoder ($settingsSummary)…"))
                    }
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED && activeTransformer != null) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 250L)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun buildComposition(
        timeline: TimelineDocument,
        targetW: Int,
        targetH: Int,
        fps: Int,
        availableFonts: List<StudioFont>,
        overlayBitmaps: Map<String, Bitmap>
    ): Composition {
        val ranges = timeline.mainClipRanges()
        val sortedBitmapOverlays = timeline.overlays
            .filter { it.type != OverlayType.VIDEO }
            .sortedBy { it.zIndex }
        val videoOverlaysDesc = timeline.overlays
            .filter { it.type == OverlayType.VIDEO && !it.videoUri.isNullOrBlank() }
            .sortedByDescending { it.zIndex }
        val anyAudioSoloed = timeline.mainClips.any { it.mediaType == MainMediaType.VIDEO && it.audio.isSolo && !it.audio.isMuted } ||
            timeline.audioClips.any { it.audio.isSolo && !it.audio.isMuted }
        val hasAnyVideoWithAudio = timeline.mainClips.any {
            it.mediaType == MainMediaType.VIDEO && !it.audio.isMuted && (!anyAudioSoloed || it.audio.isSolo)
        }

        val mainEditedItems = ranges.mapIndexed { idx, range ->
            val clip = range.clip
            val incomingTransition = if (idx > 0) ranges[idx - 1].clip.transitionToNext else null
            val outgoingTransition = if (idx < ranges.lastIndex) clip.transitionToNext else null

            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(clip.uri))
            if (clip.mediaType == MainMediaType.VIDEO) {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build()
                )
            }

            val videoEffects = mutableListOf<Effect>()
            // 1. Scale/crop to chosen project aspect ratio & resolution (720p / 1080p / 4K)
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    targetW,
                    targetH,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )
            // Enforce target frame rate on video clips when configured
            if (clip.mediaType == MainMediaType.VIDEO && fps > 0) {
                videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat()))
            }

            // 1b. Playback speed adjustment on video clips (0.25x .. 4.0x)
            if (clip.mediaType == MainMediaType.VIDEO && abs(clip.safeSpeed - 1f) > 0.01f) {
                videoEffects.add(SpeedChangeEffect(clip.safeSpeed))
            }

            // 1c. Keyframe Animation (Position, Scale, Rotation with Easing)
            if (clip.keyframes.isNotEmpty() || !clip.baseTransform.isIdentity) {
                videoEffects.add(createKeyframeMotionEffect(clip))
            }

            // 2. Apply preset filter + primary color grading + fade transitions + keyframe opacity via RgbMatrix
            val hasOpacityAnim = clip.baseTransform.opacity < 0.995f || clip.keyframes.any { it.opacity < 0.995f }
            val needsColorOrFade = clip.filterPreset != FilterPreset.ORIGINAL ||
                !clip.colorAdjustments.isDefault ||
                hasOpacityAnim ||
                incomingTransition?.type in listOf(TransitionType.CROSSFADE, TransitionType.DIP_TO_BLACK, TransitionType.DIP_TO_WHITE) ||
                outgoingTransition?.type in listOf(TransitionType.CROSSFADE, TransitionType.DIP_TO_BLACK, TransitionType.DIP_TO_WHITE)

            if (needsColorOrFade) {
                val primaryOnly = clip.colorAdjustments.copy(
                    highlights = 0f,
                    shadows = 0f,
                    lift = com.example.model.ColorWheelOffset(),
                    gamma = com.example.model.ColorWheelOffset(),
                    gain = com.example.model.ColorWheelOffset(),
                    curvePoints = com.example.model.ColorAdjustments.DEFAULT_CURVE_POINTS,
                    cubeLutPath = null,
                    cubeLutName = null
                )
                val base4x5 = ColorMatrixEngine.buildCombinedMatrix(
                    clip.filterPreset,
                    clip.filterIntensity,
                    primaryOnly
                )
                val baseGl4x4 = ColorMatrixEngine.toGlColumnMajor4x4(base4x5)
                videoEffects.add(
                    TransitionAndGradeRgbMatrix(
                        clip = clip,
                        baseGl4x4 = baseGl4x4,
                        clipDurationUs = clip.durationMs * 1000L,
                        incomingType = incomingTransition?.type ?: TransitionType.NONE,
                        incomingDurUs = ((incomingTransition?.durationMs ?: 0L) / 2L) * 1000L,
                        outgoingType = outgoingTransition?.type ?: TransitionType.NONE,
                        outgoingDurUs = ((outgoingTransition?.durationMs ?: 0L) / 2L) * 1000L
                    )
                )
            }

            // 2b. DaVinci GPU Color Wheels (Lift/Gamma/Gain), Highlights/Shadows, RGB Curve & .cube 3D LUT
            if (clip.colorAdjustments.hasAdvancedGrade) {
                videoEffects.add(ColorGradeGlEffect(clip.colorAdjustments))
            }

            // 3. Geometric transition motion (Slide / Zoom / Wipe)
            val motionType = incomingTransition?.type ?: TransitionType.NONE
            if (motionType == TransitionType.SLIDE_LEFT ||
                motionType == TransitionType.SLIDE_RIGHT ||
                motionType == TransitionType.ZOOM_IN ||
                motionType == TransitionType.WIPE_RIGHT ||
                motionType == TransitionType.WIPE_LEFT
            ) {
                val halfUs = ((incomingTransition?.durationMs ?: 600L) / 2L).coerceAtLeast(150L) * 1000L
                videoEffects.add(createMotionTransitionEffect(motionType, halfUs))
            }

            // 4. Time-synced Text & Image Overlays intersecting this clip
            val intersectingOverlays = sortedBitmapOverlays.filter { overlay ->
                overlay.endTimeMs > range.startMs && overlay.startTimeMs < range.endMs
            }
            if (intersectingOverlays.isNotEmpty()) {
                val textureOverlays = mutableListOf<TextureOverlay>()
                for (overlay in intersectingOverlays) {
                    val bmp = overlayBitmaps[overlay.id] ?: continue
                    textureOverlays.add(
                        TimedBitmapOverlay(
                            context = context,
                            baseBitmap = bmp,
                            overlay = overlay,
                            clipGlobalStartMs = range.startMs,
                            exportWidth = targetW,
                            availableFonts = availableFonts
                        )
                    )
                }
                if (textureOverlays.isNotEmpty()) {
                    videoEffects.add(OverlayEffect(textureOverlays))
                }
            }

            // 5. Clip audio speed + pitch-shifting, Fairlight 3-Band EQ & gain/fade processors
            val clipAudioActive = clip.mediaType == MainMediaType.VIDEO &&
                !clip.audio.isMuted &&
                (!anyAudioSoloed || clip.audio.isSolo)
            val audioProcessors = mutableListOf<AudioProcessor>()
            if (clipAudioActive) {
                if (abs(clip.safeSpeed - 1f) > 0.01f || abs(clip.effectiveAudioPitch - 1f) > 0.01f) {
                    val sonic = SonicAudioProcessor().apply {
                        setSpeed(clip.safeSpeed)
                        setPitch(clip.effectiveAudioPitch)
                    }
                    audioProcessors.add(sonic)
                }
                if (clip.audio.hasActiveEq) {
                    audioProcessors.add(
                        ThreeBandEqAudioProcessor(
                            lowDb = clip.audio.eqLowDb,
                            midDb = clip.audio.eqMidDb,
                            highDb = clip.audio.eqHighDb
                        )
                    )
                }
                audioProcessors.add(
                    FadeAndGainAudioProcessor(
                        clipDurationUs = clip.durationMs * 1000L,
                        volume = clip.audio.volume,
                        isMuted = false,
                        fadeInUs = clip.audio.fadeInMs * 1000L,
                        fadeOutUs = clip.audio.fadeOutMs * 1000L
                    )
                )
            }

            val editedBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(Effects(audioProcessors, videoEffects))

            if (clip.mediaType == MainMediaType.IMAGE) {
                editedBuilder
                    .setDurationUs(clip.durationMs * 1000L)
                    .setFrameRate(fps)
                    .setRemoveAudio(true)
            } else if (!clipAudioActive || !hasAnyVideoWithAudio) {
                editedBuilder.setRemoveAudio(true)
            }

            editedBuilder.build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()

        // Video Overlay Sequences (Chroma Key Background Removal via Media3 Effect pipeline)
        for (vOverlay in videoOverlaysDesc) {
            val uriStr = vOverlay.videoUri ?: continue
            val clipEndMs = (vOverlay.trimStartMs + vOverlay.durationMs)
                .coerceAtMost(vOverlay.sourceDurationMs.coerceAtLeast(300L))
                .coerceAtLeast(vOverlay.trimStartMs + 250L)

            val overlayMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uriStr))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(vOverlay.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clipEndMs)
                        .build()
                )
                .build()

            val overlayVideoEffects = mutableListOf<Effect>()
            if (fps > 0) {
                overlayVideoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(fps.toFloat()))
            }
            if (vOverlay.chromaKey.enabled) {
                overlayVideoEffects.add(
                    ChromaKeyGlEffect(
                        settings = vOverlay.chromaKey,
                        premultiplyAlpha = false
                    )
                )
            }

            val editedOverlay = EditedMediaItem.Builder(overlayMediaItem)
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), overlayVideoEffects))
                .build()

            sequences.add(
                EditedMediaItemSequence.Builder(listOf(editedOverlay))
                    .setIsLooping(true)
                    .build()
            )
        }

        val mainSequence = EditedMediaItemSequence.Builder(mainEditedItems).build()
        sequences.add(mainSequence)

        // Additional Audio Tracks (respecting Solo & Mute + Fairlight 3-Band EQ)
        val activeAudioClips = timeline.audioClips
            .filter { !it.audio.isMuted && it.audio.volume > 0.01f && (!anyAudioSoloed || it.audio.isSolo) }
            .sortedBy { it.startTimeMs }

        for (audioClip in activeAudioClips) {
            val audioItem = MediaItem.Builder()
                .setUri(Uri.parse(audioClip.uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audioClip.trimStartMs)
                        .setEndPositionMs(audioClip.trimEndMs)
                        .build()
                )
                .build()

            val processors = mutableListOf<AudioProcessor>()
            if (audioClip.audio.hasActiveEq) {
                processors.add(
                    ThreeBandEqAudioProcessor(
                        lowDb = audioClip.audio.eqLowDb,
                        midDb = audioClip.audio.eqMidDb,
                        highDb = audioClip.audio.eqHighDb
                    )
                )
            }
            processors.add(
                FadeAndGainAudioProcessor(
                    clipDurationUs = audioClip.durationMs * 1000L,
                    volume = audioClip.audio.volume,
                    isMuted = false,
                    fadeInUs = audioClip.audio.fadeInMs * 1000L,
                    fadeOutUs = audioClip.audio.fadeOutMs * 1000L
                )
            )

            val editedAudio = EditedMediaItem.Builder(audioItem)
                .setRemoveVideo(true)
                .setEffects(Effects(processors, emptyList()))
                .build()

            sequences.add(EditedMediaItemSequence.Builder(listOf(editedAudio)).build())
        }

        val compBuilder = Composition.Builder(sequences)
        if (videoOverlaysDesc.isNotEmpty()) {
            compBuilder.setVideoCompositorSettings(
                object : VideoCompositorSettings {
                    override fun getOutputSize(inputSizes: List<Size>): Size = Size(targetW, targetH)

                    override fun getOverlaySettings(
                        inputId: Int,
                        presentationTimeUs: Long
                    ): OverlaySettings {
                        if (inputId in videoOverlaysDesc.indices) {
                            val layer = videoOverlaysDesc[inputId]
                            val globalMs = presentationTimeUs / 1000L
                            val visible = globalMs in layer.startTimeMs..layer.endTimeMs
                            val alpha = if (visible) layer.opacity.coerceIn(0f, 1f) else 0f
                            val ndcX = (layer.offsetX * 2f).coerceIn(-1.5f, 1.5f)
                            val ndcY = (-layer.offsetY * 2f).coerceIn(-1.5f, 1.5f)
                            // Default overlay footprint matches preview (~0.52 of stage) scaled by user scale
                            val s = (layer.scale * 0.52f).coerceIn(0.15f, 2.5f)
                            return OverlaySettings.Builder()
                                .setAlphaScale(alpha)
                                .setOverlayFrameAnchor(0f, 0f)
                                .setBackgroundFrameAnchor(ndcX, ndcY)
                                .setScale(s, s)
                                .setRotationDegrees(-layer.rotationDegrees)
                                .build()
                        }
                        return OverlaySettings.Builder().build()
                    }
                }
            )
        }
        return compBuilder.build()
    }

    private fun createMotionTransitionEffect(
        type: TransitionType,
        durationUs: Long
    ): MatrixTransformation = MatrixTransformation { presentationTimeUs ->
        val matrix = Matrix()
        if (presentationTimeUs < durationUs && durationUs > 0L) {
            val t = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
            // Ease-out cubic
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            when (type) {
                TransitionType.SLIDE_LEFT -> matrix.postTranslate((1f - eased) * 2f, 0f)
                TransitionType.SLIDE_RIGHT -> matrix.postTranslate(-(1f - eased) * 2f, 0f)
                TransitionType.ZOOM_IN -> {
                    val s = 1.25f - 0.25f * eased
                    matrix.postScale(s, s)
                }
                TransitionType.WIPE_RIGHT -> {
                    val sx = 0.05f + 0.95f * eased
                    matrix.postScale(sx, 1f)
                    matrix.postTranslate(-(1f - sx), 0f)
                }
                TransitionType.WIPE_LEFT -> {
                    val sx = 0.05f + 0.95f * eased
                    matrix.postScale(sx, 1f)
                    matrix.postTranslate(1f - sx, 0f)
                }
                else -> Unit
            }
        }
        matrix
    }

    private fun createKeyframeMotionEffect(clip: MainClip): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val localMs = (presentationTimeUs / 1000L).coerceIn(0L, clip.durationMs)
            val transform = KeyframeEvaluator.evaluate(clip, localMs)
            val matrix = Matrix()
            if (!transform.isIdentity) {
                matrix.postScale(transform.scale, transform.scale)
                matrix.postRotate(-transform.rotationDegrees)
                // OpenGL NDC space is -1..+1 (width 2.0, Y-up)
                matrix.postTranslate(transform.offsetX * 2f, -transform.offsetY * 2f)
            }
            matrix
        }

    private class TransitionAndGradeRgbMatrix(
        private val clip: MainClip,
        private val baseGl4x4: FloatArray,
        private val clipDurationUs: Long,
        private val incomingType: TransitionType,
        private val incomingDurUs: Long,
        private val outgoingType: TransitionType,
        private val outgoingDurUs: Long
    ) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val localMs = (presentationTimeUs / 1000L).coerceIn(0L, clip.durationMs)
            var dimFactor = KeyframeEvaluator.evaluate(clip, localMs).opacity.coerceIn(0f, 1f)
            var whiteLift = 0f

            if (incomingDurUs > 0L && presentationTimeUs < incomingDurUs) {
                val t = (presentationTimeUs.toFloat() / incomingDurUs.toFloat()).coerceIn(0f, 1f)
                when (incomingType) {
                    TransitionType.CROSSFADE, TransitionType.DIP_TO_BLACK -> dimFactor *= t
                    TransitionType.DIP_TO_WHITE -> {
                        dimFactor *= t
                        whiteLift = 1f - t
                    }
                    else -> Unit
                }
            }

            if (outgoingDurUs > 0L && clipDurationUs > 0L) {
                val remaining = (clipDurationUs - presentationTimeUs).coerceAtLeast(0L)
                if (remaining < outgoingDurUs) {
                    val t = (remaining.toFloat() / outgoingDurUs.toFloat()).coerceIn(0f, 1f)
                    when (outgoingType) {
                        TransitionType.CROSSFADE, TransitionType.DIP_TO_BLACK -> dimFactor = minOf(dimFactor, t)
                        TransitionType.DIP_TO_WHITE -> {
                            dimFactor = minOf(dimFactor, t)
                            whiteLift = maxOf(whiteLift, 1f - t)
                        }
                        else -> Unit
                    }
                }
            }

            if (dimFactor >= 0.999f && whiteLift <= 0.001f) return baseGl4x4

            val out = baseGl4x4.copyOf()
            // Scale RGB columns (0..2, 4..6, 8..10) and translation (12..14)
            for (i in listOf(0, 1, 2, 4, 5, 6, 8, 9, 10)) {
                out[i] *= dimFactor
            }
            out[12] = out[12] * dimFactor + whiteLift
            out[13] = out[13] * dimFactor + whiteLift
            out[14] = out[14] * dimFactor + whiteLift
            return out
        }
    }

    private class TimedBitmapOverlay(
        private val context: Context,
        private val baseBitmap: Bitmap,
        private val overlay: OverlayLayer,
        private val clipGlobalStartMs: Long,
        private val exportWidth: Int,
        private val availableFonts: List<StudioFont>
    ) : BitmapOverlay() {

        private var lastRenderedCharCount: Int = -1
        private var cachedTypewriterBitmap: Bitmap? = null

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            if (overlay.type == OverlayType.TEXT && overlay.textMotionPreset == TextMotionPreset.TYPEWRITER) {
                val globalTimeMs = clipGlobalStartMs + (presentationTimeUs / 1000L)
                val frame = TextMotionEvaluator.evaluateAtTimelineMs(overlay, globalTimeMs)
                val full = overlay.text.ifBlank { "Title" }
                val count = frame.visibleCharCount.coerceIn(0, full.length)
                if (count >= full.length) return baseBitmap
                if (count != lastRenderedCharCount || cachedTypewriterBitmap == null) {
                    val sliced = full.substring(0, count).ifEmpty { " " }
                    cachedTypewriterBitmap?.let { if (!it.isRecycled && it !== baseBitmap) it.recycle() }
                    cachedTypewriterBitmap = OverlayBitmapRenderer.renderTextBitmap(
                        context = context,
                        overlay = overlay,
                        exportWidth = exportWidth,
                        availableFonts = availableFonts,
                        textOverride = sliced
                    )
                    lastRenderedCharCount = count
                }
                return cachedTypewriterBitmap ?: baseBitmap
            }
            return baseBitmap
        }

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
            val globalTimeMs = clipGlobalStartMs + (presentationTimeUs / 1000L)
            val isVisible = globalTimeMs in overlay.startTimeMs..overlay.endTimeMs
            if (!isVisible) {
                return OverlaySettings.Builder().setAlphaScale(0f).build()
            }

            val motionFrame = if (overlay.type == OverlayType.TEXT && overlay.textMotionPreset != TextMotionPreset.NONE) {
                TextMotionEvaluator.evaluateAtTimelineMs(overlay, globalTimeMs)
            } else null

            val alpha = (overlay.opacity * (motionFrame?.alpha ?: 1f)).coerceIn(0f, 1f)
            val ndcX = ((overlay.offsetX + (motionFrame?.offsetXFraction ?: 0f)) * 2f).coerceIn(-2.0f, 2.0f)
            val ndcY = (-(overlay.offsetY + (motionFrame?.offsetYFraction ?: 0f)) * 2f).coerceIn(-2.0f, 2.0f)
            val s = (overlay.scale * (motionFrame?.scale ?: 1f)).coerceIn(0.15f, 4.5f)
            val rot = -(overlay.rotationDegrees + (motionFrame?.rotationDegrees ?: 0f))

            return OverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(ndcX, ndcY)
                .setScale(s, s)
                .setRotationDegrees(rot)
                .build()
        }

        override fun release() {
            super.release()
            cachedTypewriterBitmap?.let { if (!it.isRecycled && it !== baseBitmap) it.recycle() }
            cachedTypewriterBitmap = null
        }
    }

    private data class SavedExportInfo(
        val uriString: String,
        val displayName: String,
        val sizeMb: Float,
        val savedToGallery: Boolean
    )

    private suspend fun saveExportedFileToGallery(
        projectTitle: String,
        tempFile: File
    ): Result<SavedExportInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = projectTitle
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .replace(' ', '_')
                .ifBlank { "LumaCut" }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${safeName}_${stamp}.mp4"
            val sizeMb = (tempFile.length().toFloat() / (1024f * 1024f)).coerceAtLeast(0.01f)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/LumaCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore entry.")

                resolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(tempFile).use { inStream ->
                        inStream.copyTo(outStream, bufferSize = 64 * 1024)
                    }
                } ?: error("Could not write to gallery output stream.")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tempFile.delete()

                SavedExportInfo(
                    uriString = uri.toString(),
                    displayName = fileName,
                    sizeMb = sizeMb,
                    savedToGallery = true
                )
            } else {
                val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "LumaCut")
                    .apply { mkdirs() }
                val destFile = File(moviesDir, fileName)
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                tempFile.delete()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
                SavedExportInfo(
                    uriString = uri.toString(),
                    displayName = fileName,
                    sizeMb = sizeMb,
                    savedToGallery = false
                )
            }
        }
    }

    private fun recycleBitmaps(map: Map<String, Bitmap>) {
        map.values.forEach { bmp ->
            runCatching { if (!bmp.isRecycled) bmp.recycle() }
        }
    }
}
