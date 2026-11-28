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
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import com.example.model.ColorMatrixEngine
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.FilterPreset
import com.example.model.KeyframeEvaluator
import com.example.model.MainClip
import com.example.model.MainMediaType
import com.example.model.MaskSettings
import com.example.model.MaskShape
import com.example.model.OverlayLayer
import com.example.model.OverlayType
import com.example.model.StudioFont
import com.example.model.TextMotionEvaluator
import com.example.model.TextMotionPreset
import com.example.model.TimelineDocument
import com.example.model.TransitionType
import com.example.model.VoiceEffectPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pure helper for generating standards-compliant 44.1kHz stereo 16-bit PCM silent WAV chunks
 * and planning gap durations so secondary audio tracks & voiceovers align with their timeline
 * startTimeMs in Media3 Transformer 1.5.x compositions.
 */
object SilentAudioGapHelper {
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val BITS_PER_SAMPLE = 16
    const val DEFAULT_SILENCE_FILE_MS = 5000L

    fun planGapChunkDurationsMs(
        totalGapMs: Long,
        maxChunkMs: Long = DEFAULT_SILENCE_FILE_MS
    ): List<Long> {
        if (totalGapMs < 20L) return emptyList()
        val safeMax = maxChunkMs.coerceAtLeast(100L)
        val chunks = mutableListOf<Long>()
        var remaining = totalGapMs
        while (remaining > safeMax) {
            chunks.add(safeMax)
            remaining -= safeMax
        }
        if (remaining >= 20L || chunks.isEmpty()) {
            chunks.add(remaining.coerceAtLeast(20L))
        }
        return chunks
    }

    fun buildSilentWavBytes(durationMs: Long): ByteArray {
        val safeMs = durationMs.coerceIn(20L, 10_000L)
        val bytesPerSample = BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * bytesPerSample
        val byteRate = SAMPLE_RATE * blockAlign
        val frameCount = ((SAMPLE_RATE.toLong() * safeMs) / 1000L).coerceAtLeast(1L)
        val dataSize = (frameCount * blockAlign).toInt()
        val riffChunkSize = 36 + dataSize

        val out = ByteArray(44 + dataSize) // zero-initialized = PCM silence
        val header = ByteBuffer.wrap(out, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        header.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
        header.putInt(riffChunkSize)
        header.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())
        header.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // PCM format
        header.putShort(CHANNELS.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
        header.putInt(dataSize)
        return out
    }
}

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
        val bitDepthTag = if (config.tenBitColor) "10-bit HDR" else "8-bit"
        val settingsSummary = "${config.resolution.label} • ${config.fps}fps • ${config.codec.label} $bitDepthTag • ${config.bitrateMbps} Mbps"

        scope.launch {
            val prepResult = withContext(Dispatchers.IO) {
                runCatching {
                    val overlayBitmaps = timeline.overlays
                        .filter { it.type != OverlayType.VIDEO }
                        .sortedBy { it.zIndex }
                        .associate { layer ->
                            layer.id to OverlayBitmapRenderer.renderOverlayBitmapWithCutout(
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

                    val needsAudioGap = timeline.audioClips.any { !it.audio.isMuted && it.startTimeMs >= 20L }
                    val silenceWavFile = if (needsAudioGap) {
                        val file = File(cacheDir, "lumacut_silence_5s.wav")
                        val expectedSize = 44 + (SilentAudioGapHelper.SAMPLE_RATE * SilentAudioGapHelper.CHANNELS * 2 * (SilentAudioGapHelper.DEFAULT_SILENCE_FILE_MS / 1000L)).toInt()
                        if (!file.exists() || file.length().toInt() != expectedSize) {
                            file.writeBytes(SilentAudioGapHelper.buildSilentWavBytes(SilentAudioGapHelper.DEFAULT_SILENCE_FILE_MS))
                        }
                        file
                    } else null

                    Triple(overlayBitmaps, tempFile, silenceWavFile)
                }
            }

            prepResult.onFailure { err ->
                onStateChange(ExportState.Error(err.message ?: "Failed to prepare overlays for export."))
                return@launch
            }

            val (overlayBitmaps, tempFile, silenceWavFile) = prepResult.getOrThrow()

            mainHandler.post {
                try {
                    val composition = buildComposition(
                        timeline = timeline,
                        targetW = targetW,
                        targetH = targetH,
                        fps = config.fps,
                        tenBitColor = config.tenBitColor,
                        availableFonts = availableFonts,
                        overlayBitmaps = overlayBitmaps,
                        silenceWavFile = silenceWavFile
                    )

                    val videoEncoderSettings = VideoEncoderSettings.Builder()
                        .setBitrate(clampedBitrateBps)
                        .build()
                    val encoderFactory = DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(videoEncoderSettings)
                        .setEnableFallback(true)
                        .build()

                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(config.codec.mimeType)
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
        tenBitColor: Boolean,
        availableFonts: List<StudioFont>,
        overlayBitmaps: Map<String, Bitmap>,
        silenceWavFile: File? = null
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

            // 1c. Keyframe Animation, Pan & Zoom, Mirror/Flip Full & Transform Matrix
            if (clip.keyframes.isNotEmpty() || !clip.baseTransform.isIdentity || clip.panZoom.enabled) {
                videoEffects.add(createKeyframeMotionEffect(clip))
            }

            // 2. Apply preset filter + primary color grading + fade/flare/glitch transitions + keyframe opacity via RgbMatrix
            val colorTransitionTypes = setOf(
                TransitionType.CROSSFADE,
                TransitionType.DIP_TO_BLACK,
                TransitionType.DIP_TO_WHITE,
                TransitionType.FILM_BURN,
                TransitionType.LIGHT_LEAK,
                TransitionType.GLITCH,
                TransitionType.DIRECTIONAL_BLUR,
                TransitionType.WHIP_PAN
            )
            val hasOpacityAnim = clip.baseTransform.opacity < 0.995f || clip.keyframes.any { it.opacity < 0.995f }
            val needsColorOrFade = clip.filterPreset != FilterPreset.ORIGINAL ||
                !clip.colorAdjustments.isDefault ||
                hasOpacityAnim ||
                incomingTransition?.type in colorTransitionTypes ||
                outgoingTransition?.type in colorTransitionTypes

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

            // 3. Geometric & Cinema FX transition motion (incoming & outgoing seams)
            val motionTransitionTypes = setOf(
                TransitionType.SLIDE_LEFT,
                TransitionType.SLIDE_RIGHT,
                TransitionType.ZOOM_IN,
                TransitionType.WIPE_RIGHT,
                TransitionType.WIPE_LEFT,
                TransitionType.DIRECTIONAL_BLUR,
                TransitionType.WHIP_PAN,
                TransitionType.GLITCH,
                TransitionType.FILM_BURN,
                TransitionType.LIGHT_LEAK
            )
            val inMotionType = incomingTransition?.type ?: TransitionType.NONE
            val outMotionType = outgoingTransition?.type ?: TransitionType.NONE
            if (inMotionType in motionTransitionTypes || outMotionType in motionTransitionTypes) {
                val inHalfUs = if (inMotionType in motionTransitionTypes) {
                    ((incomingTransition?.durationMs ?: 600L) / 2L).coerceAtLeast(150L) * 1000L
                } else 0L
                val outHalfUs = if (outMotionType in motionTransitionTypes) {
                    ((outgoingTransition?.durationMs ?: 600L) / 2L).coerceAtLeast(150L) * 1000L
                } else 0L
                videoEffects.add(
                    createMotionTransitionEffect(
                        clipDurationUs = clip.durationMs * 1000L,
                        incomingType = inMotionType,
                        incomingDurUs = inHalfUs,
                        outgoingType = outMotionType,
                        outgoingDurUs = outHalfUs
                    )
                )
            }

            // 3b. Interactive Crop framing on main clip (razor-sharp rectangular crop mask)
            val crop = clip.baseTransform.crop
            if (crop.isActive) {
                val cropMask = MaskSettings(
                    shape = MaskShape.RECTANGLE,
                    centerX = crop.left + crop.visibleWidthFraction * 0.5f,
                    centerY = crop.top + crop.visibleHeightFraction * 0.5f,
                    width = crop.visibleWidthFraction,
                    height = crop.visibleHeightFraction,
                    cornerRadius = 0f,
                    featherPx = 0f
                )
                videoEffects.add(MaskGlEffect(mask = cropMask, premultiplyOnBlack = true))
            }

            // 3c. Shape Mask on main clip (Rectangle, Circle, Linear Split with feather & invert)
            if (clip.mask.isActive) {
                videoEffects.add(MaskGlEffect(mask = clip.mask, premultiplyOnBlack = true))
            }

            // 3d. Time-windowed Visual FX Layers (Magnifier, Mosaic, Gaussian Blur - Spot or Full Frame)
            val intersectingFx = timeline.fxLayers.filter { fx ->
                fx.endTimeMs > range.startMs && fx.startTimeMs < range.endMs
            }
            for (fx in intersectingFx) {
                videoEffects.add(
                    VisualFxGlEffect(
                        fxLayer = fx,
                        clipGlobalStartMs = range.startMs
                    )
                )
            }

            // 4. Time-synced Text/Image Overlays & Karaoke Captions intersecting this clip
            val intersectingOverlays = sortedBitmapOverlays.filter { overlay ->
                overlay.endTimeMs > range.startMs && overlay.startTimeMs < range.endMs
            }
            val intersectingCaptions = timeline.captions.filter { cap ->
                cap.endTimeMs > range.startMs && cap.startTimeMs < range.endMs
            }
            if (intersectingOverlays.isNotEmpty() || intersectingCaptions.isNotEmpty()) {
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
                for (caption in intersectingCaptions) {
                    textureOverlays.add(
                        TimedCaptionBitmapOverlay(
                            context = context,
                            caption = caption,
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

            // 5. Clip audio speed + pitch-shifting, Denoise, Voice Effects, Voice Isolation/Noise Gate, 3-Band EQ & gain/fade
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
                if (clip.audio.voiceIsolation || clip.audio.noiseGateDb > -58f || clip.audio.denoiseStrength > 0.02f) {
                    audioProcessors.add(
                        VoiceDspAudioProcessor(
                            voiceIsolation = clip.audio.voiceIsolation,
                            noiseGateDb = clip.audio.noiseGateDb,
                            denoiseStrength = clip.audio.denoiseStrength
                        )
                    )
                }
                if (clip.audio.voiceEffect != VoiceEffectPreset.NONE) {
                    audioProcessors.add(VoiceEffectAudioProcessor(clip.audio.voiceEffect))
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
            if (vOverlay.mask.isActive) {
                overlayVideoEffects.add(
                    MaskGlEffect(
                        mask = vOverlay.mask,
                        premultiplyOnBlack = false
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

        // Additional Audio Tracks (respecting Solo & Mute + Fairlight 3-Band EQ + Voice DSP + Auto-Ducking)
        val activeAudioClips = timeline.audioClips
            .filter { !it.audio.isMuted && it.audio.volume > 0.01f && (!anyAudioSoloed || it.audio.isSolo) }
            .sortedBy { it.startTimeMs }

        // Voice windows for Auto-Ducking: voiceover/speech tracks (non-ducked clips or explicit voice tracks)
        val explicitVoiceWindows = activeAudioClips
            .filter {
                !it.audio.autoDuckMusic && (
                    it.audio.voiceIsolation ||
                        it.displayName.contains("voice", ignoreCase = true) ||
                        it.displayName.contains("vo ", ignoreCase = true)
                    )
            }
            .map { it.startTimeMs..it.endTimeMs }

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
            val voicePitch = audioClip.audio.voiceEffect.pitchFactor
            if (abs(voicePitch - 1f) > 0.01f) {
                val sonic = SonicAudioProcessor().apply {
                    setSpeed(1f)
                    setPitch(voicePitch)
                }
                processors.add(sonic)
            }
            if (audioClip.audio.voiceIsolation || audioClip.audio.noiseGateDb > -58f || audioClip.audio.denoiseStrength > 0.02f) {
                processors.add(
                    VoiceDspAudioProcessor(
                        voiceIsolation = audioClip.audio.voiceIsolation,
                        noiseGateDb = audioClip.audio.noiseGateDb,
                        denoiseStrength = audioClip.audio.denoiseStrength
                    )
                )
            }
            if (audioClip.audio.voiceEffect != VoiceEffectPreset.NONE) {
                processors.add(VoiceEffectAudioProcessor(audioClip.audio.voiceEffect))
            }
            if (audioClip.audio.hasActiveEq) {
                processors.add(
                    ThreeBandEqAudioProcessor(
                        lowDb = audioClip.audio.eqLowDb,
                        midDb = audioClip.audio.eqMidDb,
                        highDb = audioClip.audio.eqHighDb
                    )
                )
            }
            if (audioClip.audio.autoDuckMusic) {
                val duckWindows = explicitVoiceWindows.ifEmpty {
                    activeAudioClips
                        .filter { it.id != audioClip.id && !it.audio.autoDuckMusic }
                        .map { it.startTimeMs..it.endTimeMs }
                }
                if (duckWindows.isNotEmpty()) {
                    processors.add(
                        AutoDuckAudioProcessor(
                            clipTimelineStartMs = audioClip.startTimeMs,
                            voiceWindowsGlobalMs = duckWindows
                        )
                    )
                }
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

            val sequenceItems = mutableListOf<EditedMediaItem>()
            if (audioClip.startTimeMs >= 20L && silenceWavFile != null && silenceWavFile.exists()) {
                val silenceUri = Uri.fromFile(silenceWavFile)
                val gapChunks = SilentAudioGapHelper.planGapChunkDurationsMs(audioClip.startTimeMs)
                for (chunkMs in gapChunks) {
                    val gapBuilder = MediaItem.Builder().setUri(silenceUri)
                    if (chunkMs < SilentAudioGapHelper.DEFAULT_SILENCE_FILE_MS) {
                        gapBuilder.setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0L)
                                .setEndPositionMs(chunkMs)
                                .build()
                        )
                    }
                    sequenceItems.add(
                        EditedMediaItem.Builder(gapBuilder.build())
                            .setRemoveVideo(true)
                            .build()
                    )
                }
            }
            sequenceItems.add(editedAudio)
            sequences.add(EditedMediaItemSequence.Builder(sequenceItems).build())
        }

        val compBuilder = Composition.Builder(sequences)
            .setHdrMode(
                if (tenBitColor) Composition.HDR_MODE_KEEP_HDR
                else Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            )
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
                            val ndcX = (layer.offsetX * 2f).coerceIn(-2.0f, 2.0f)
                            val ndcY = (-layer.offsetY * 2f).coerceIn(-2.0f, 2.0f)
                            // Default overlay footprint matches preview (~0.52 of stage) scaled by user scale, or full frame when Flip Full active
                            val baseScale = if (layer.fullFrameFill) 1.0f else 0.52f
                            val s = (layer.scale * baseScale).coerceIn(0.15f, 3.0f)
                            val sx = if (layer.flipHorizontal) -s else s
                            val sy = if (layer.flipVertical) -s else s
                            return OverlaySettings.Builder()
                                .setAlphaScale(alpha)
                                .setOverlayFrameAnchor(0f, 0f)
                                .setBackgroundFrameAnchor(ndcX, ndcY)
                                .setScale(sx, sy)
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
        clipDurationUs: Long,
        incomingType: TransitionType,
        incomingDurUs: Long,
        outgoingType: TransitionType,
        outgoingDurUs: Long
    ): MatrixTransformation = MatrixTransformation { presentationTimeUs ->
        val matrix = Matrix()
        if (incomingDurUs > 0L && presentationTimeUs < incomingDurUs) {
            val t = (presentationTimeUs.toFloat() / incomingDurUs.toFloat()).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            val inv = 1f - eased
            when (incomingType) {
                TransitionType.SLIDE_LEFT -> matrix.postTranslate(inv * 2f, 0f)
                TransitionType.SLIDE_RIGHT -> matrix.postTranslate(-inv * 2f, 0f)
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
                TransitionType.DIRECTIONAL_BLUR -> {
                    val sx = 1f + 0.28f * inv
                    matrix.postScale(sx, 1f)
                    matrix.postTranslate(inv * 0.35f, 0f)
                }
                TransitionType.WHIP_PAN -> {
                    val sx = 1f + 0.32f * inv
                    matrix.postScale(sx, 1f)
                    matrix.postTranslate(inv * 1.65f, 0f)
                }
                TransitionType.GLITCH -> {
                    val jitter = sin(t * 36.0).toFloat() * 0.14f * inv
                    val s = 1f + 0.08f * inv
                    matrix.postScale(s, s)
                    matrix.postTranslate(jitter, -jitter * 0.35f)
                }
                TransitionType.FILM_BURN, TransitionType.LIGHT_LEAK -> {
                    val s = 1f + 0.07f * inv
                    matrix.postScale(s, s)
                }
                else -> Unit
            }
        } else if (outgoingDurUs > 0L && clipDurationUs > 0L) {
            val remaining = (clipDurationUs - presentationTimeUs).coerceAtLeast(0L)
            if (remaining < outgoingDurUs) {
                // p goes 0 -> 1 as we approach the cut seam
                val p = (1f - remaining.toFloat() / outgoingDurUs.toFloat()).coerceIn(0f, 1f)
                val eased = p * p * p
                when (outgoingType) {
                    TransitionType.SLIDE_LEFT -> matrix.postTranslate(-eased * 1.35f, 0f)
                    TransitionType.SLIDE_RIGHT -> matrix.postTranslate(eased * 1.35f, 0f)
                    TransitionType.ZOOM_IN -> {
                        val s = 1f + 0.25f * eased
                        matrix.postScale(s, s)
                    }
                    TransitionType.DIRECTIONAL_BLUR -> {
                        val sx = 1f + 0.28f * eased
                        matrix.postScale(sx, 1f)
                        matrix.postTranslate(-eased * 0.35f, 0f)
                    }
                    TransitionType.WHIP_PAN -> {
                        val sx = 1f + 0.32f * eased
                        matrix.postScale(sx, 1f)
                        matrix.postTranslate(-eased * 1.65f, 0f)
                    }
                    TransitionType.GLITCH -> {
                        val jitter = sin(p * 36.0).toFloat() * 0.14f * p
                        val s = 1f + 0.08f * p
                        matrix.postScale(s, s)
                        matrix.postTranslate(jitter, jitter * 0.35f)
                    }
                    TransitionType.FILM_BURN, TransitionType.LIGHT_LEAK -> {
                        val s = 1f + 0.07f * eased
                        matrix.postScale(s, s)
                    }
                    else -> Unit
                }
            }
        }
        matrix
    }

    private fun createKeyframeMotionEffect(clip: MainClip): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val localMs = (presentationTimeUs / 1000L).coerceIn(0L, clip.durationMs)
            val transform = KeyframeEvaluator.evaluate(clip, localMs)
            val flipX = if (transform.flipHorizontal) -1f else 1f
            val flipY = if (transform.flipVertical) -1f else 1f
            val fillBoost = if (transform.fullFrameFill) 1.18f else 1f
            val effectiveScale = transform.scale * fillBoost
            val matrix = Matrix()
            if (!transform.isIdentity || flipX < 0f || flipY < 0f || clip.panZoom.enabled) {
                matrix.postScale(effectiveScale * flipX, effectiveScale * flipY)
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
            var liftR = 0f
            var liftG = 0f
            var liftB = 0f
            var gainR = 1f
            var gainG = 1f
            var gainB = 1f

            fun applySeamColor(type: TransitionType, seamStrength: Float, phase: Float) {
                val s = seamStrength.coerceIn(0f, 1f)
                when (type) {
                    TransitionType.CROSSFADE, TransitionType.DIP_TO_BLACK -> {
                        dimFactor = minOf(dimFactor, 1f - s)
                    }
                    TransitionType.DIP_TO_WHITE -> {
                        dimFactor = minOf(dimFactor, 1f - s)
                        liftR = maxOf(liftR, s)
                        liftG = maxOf(liftG, s)
                        liftB = maxOf(liftB, s)
                    }
                    TransitionType.FILM_BURN -> {
                        // Fiery amber-orange celluloid overexposure peaking at the seam
                        dimFactor = minOf(dimFactor, 1f - 0.25f * s)
                        liftR = maxOf(liftR, 0.92f * s)
                        liftG = maxOf(liftG, 0.42f * s)
                        liftB = maxOf(liftB, 0.08f * s)
                        gainR = maxOf(gainR, 1f + 0.35f * s)
                    }
                    TransitionType.LIGHT_LEAK -> {
                        // Dreamy golden-peach lens flare bloom
                        liftR = maxOf(liftR, 0.85f * s)
                        liftG = maxOf(liftG, 0.66f * s)
                        liftB = maxOf(liftB, 0.28f * s)
                    }
                    TransitionType.GLITCH -> {
                        // Chromatic RGB pulse + stutter flash near the cut seam
                        val osc = sin(phase * 42.0).toFloat()
                        gainR *= (1f + 0.45f * s * osc).coerceIn(0.4f, 1.8f)
                        gainB *= (1f - 0.45f * s * osc).coerceIn(0.4f, 1.8f)
                        val flash = if (osc > 0.65f) 0.28f * s else 0f
                        liftR = maxOf(liftR, flash)
                        liftG = maxOf(liftG, flash * 0.5f)
                        liftB = maxOf(liftB, flash * 1.1f)
                    }
                    TransitionType.DIRECTIONAL_BLUR, TransitionType.WHIP_PAN -> {
                        // Subtle motion-blur exposure blend across the whip seam
                        dimFactor = minOf(dimFactor, 1f - 0.35f * s)
                    }
                    else -> Unit
                }
            }

            if (incomingDurUs > 0L && presentationTimeUs < incomingDurUs) {
                val t = (presentationTimeUs.toFloat() / incomingDurUs.toFloat()).coerceIn(0f, 1f)
                applySeamColor(incomingType, seamStrength = 1f - t, phase = t)
            }

            if (outgoingDurUs > 0L && clipDurationUs > 0L) {
                val remaining = (clipDurationUs - presentationTimeUs).coerceAtLeast(0L)
                if (remaining < outgoingDurUs) {
                    val t = (remaining.toFloat() / outgoingDurUs.toFloat()).coerceIn(0f, 1f)
                    applySeamColor(outgoingType, seamStrength = 1f - t, phase = 1f - t)
                }
            }

            if (dimFactor >= 0.999f &&
                liftR <= 0.001f && liftG <= 0.001f && liftB <= 0.001f &&
                abs(gainR - 1f) <= 0.001f && abs(gainG - 1f) <= 0.001f && abs(gainB - 1f) <= 0.001f
            ) {
                return baseGl4x4
            }

            val out = baseGl4x4.copyOf()
            // Column-major 4x4: row 0 = R (0,4,8,12), row 1 = G (1,5,9,13), row 2 = B (2,6,10,14)
            for (col in listOf(0, 4, 8)) {
                out[col] *= dimFactor * gainR
                out[col + 1] *= dimFactor * gainG
                out[col + 2] *= dimFactor * gainB
            }
            out[12] = (out[12] * dimFactor * gainR + liftR).coerceIn(-1f, 1.5f)
            out[13] = (out[13] * dimFactor * gainG + liftG).coerceIn(-1f, 1.5f)
            out[14] = (out[14] * dimFactor * gainB + liftB).coerceIn(-1f, 1.5f)
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

    /**
     * Burns timed karaoke [CaptionClip] subtitles into exported MP4 frames with active-word
     * highlighting, Pop-In scale, and Slide-Up motion.
     */
    private class TimedCaptionBitmapOverlay(
        private val context: Context,
        private val caption: CaptionClip,
        private val clipGlobalStartMs: Long,
        private val exportWidth: Int,
        private val availableFonts: List<StudioFont>
    ) : BitmapOverlay() {

        private val words: List<String> = caption.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        private var lastWordIdx: Int = Int.MIN_VALUE
        private var cachedBitmap: Bitmap? = null

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val globalTimeMs = clipGlobalStartMs + (presentationTimeUs / 1000L)
            val targetWordIdx = if (caption.animationPreset == CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT && words.isNotEmpty()) {
                val frac = ((globalTimeMs - caption.startTimeMs).toFloat() / caption.durationMs.toFloat()).coerceIn(0f, 0.999f)
                (frac * words.size).toInt().coerceIn(0, words.lastIndex)
            } else {
                -1
            }

            if (targetWordIdx != lastWordIdx || cachedBitmap == null || cachedBitmap?.isRecycled == true) {
                cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
                cachedBitmap = OverlayBitmapRenderer.renderCaptionBitmap(
                    context = context,
                    caption = caption,
                    exportWidth = exportWidth,
                    availableFonts = availableFonts,
                    activeWordIndex = targetWordIdx
                )
                lastWordIdx = targetWordIdx
            }
            return cachedBitmap ?: OverlayBitmapRenderer.createTransparentPlaceholder()
        }

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
            val globalTimeMs = clipGlobalStartMs + (presentationTimeUs / 1000L)
            if (globalTimeMs !in caption.startTimeMs..caption.endTimeMs) {
                return OverlaySettings.Builder().setAlphaScale(0f).build()
            }
            val elapsedMs = (globalTimeMs - caption.startTimeMs).coerceAtLeast(0L)
            val enterFrac = (elapsedMs / 220f).coerceIn(0f, 1f)
            var alpha = 1f
            var scale = 1f
            var extraY = 0f

            when (caption.animationPreset) {
                CaptionAnimationPreset.POP_IN -> {
                    val ease = 1f - (1f - enterFrac) * (1f - enterFrac)
                    scale = 0.72f + 0.28f * ease
                    alpha = enterFrac
                }
                CaptionAnimationPreset.SLIDE_UP -> {
                    val ease = 1f - (1f - enterFrac) * (1f - enterFrac)
                    extraY = (1f - ease) * 0.06f
                    alpha = enterFrac
                }
                else -> Unit
            }

            val ndcX = (caption.offsetX * 2f).coerceIn(-1.6f, 1.6f)
            val ndcY = (-(caption.offsetY + extraY) * 2f).coerceIn(-1.8f, 1.8f)

            return OverlaySettings.Builder()
                .setAlphaScale(alpha.coerceIn(0f, 1f))
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(ndcX, ndcY)
                .setScale(scale, scale)
                .build()
        }

        override fun release() {
            super.release()
            cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
            cachedBitmap = null
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
