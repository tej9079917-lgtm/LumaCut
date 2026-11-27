package com.example.model

import com.squareup.moshi.JsonClass
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

enum class AspectRatio(
    val label: String,
    val subtitle: String,
    val ratio: Float,
    val width4k: Int,
    val height4k: Int,
    val width1080: Int,
    val height1080: Int,
    val width720: Int,
    val height720: Int
) {
    PORTRAIT_9_16("9:16", "Reels & Shorts", 9f / 16f, 2160, 3840, 1080, 1920, 720, 1280),
    LANDSCAPE_16_9("16:9", "YouTube Cinema", 16f / 9f, 3840, 2160, 1920, 1080, 1280, 720),
    SQUARE_1_1("1:1", "Social Square", 1f, 2160, 2160, 1080, 1080, 720, 720);

    fun exportDimensions(resolution: ExportResolution): Pair<Int, Int> = when (resolution) {
        ExportResolution.HD_720P -> width720 to height720
        ExportResolution.FHD_1080P -> width1080 to height1080
        ExportResolution.UHD_4K -> width4k to height4k
    }

    companion object {
        fun fromNameOrDefault(name: String?): AspectRatio =
            entries.firstOrNull { it.name == name } ?: PORTRAIT_9_16
    }
}

enum class FilterPreset(val displayName: String, val swatchHex: Long) {
    ORIGINAL("Original", 0xFF334155),
    NOIR("Noir", 0xFF475569),
    TEAL_ORANGE("Teal & Amber", 0xFF0E7490),
    VINTAGE_WARM("Vintage", 0xFFB45309),
    KODAK_FADE("Film Fade", 0xFF92400E),
    CRISP_VIVID("Vivid", 0xFF2563EB),
    MIDNIGHT_COOL("Midnight", 0xFF1D4ED8),
    GOLDEN_HOUR("Golden", 0xFFD97706),
    MATTE_FILM("Matte", 0xFF6B7280)
}

enum class TransitionCategory(val label: String) {
    ALL("All"),
    FADE("Fade & Dissolve"),
    WIPE("Wipe"),
    MOTION("Motion"),
    FX("Cinema FX")
}

enum class TransitionType(
    val displayName: String,
    val category: TransitionCategory
) {
    NONE("Cut", TransitionCategory.FADE),
    CROSSFADE("Dissolve", TransitionCategory.FADE),
    DIP_TO_BLACK("Fade Black", TransitionCategory.FADE),
    DIP_TO_WHITE("Fade White", TransitionCategory.FADE),
    WIPE_RIGHT("Wipe Right", TransitionCategory.WIPE),
    WIPE_LEFT("Wipe Left", TransitionCategory.WIPE),
    SLIDE_LEFT("Slide Left", TransitionCategory.MOTION),
    SLIDE_RIGHT("Slide Right", TransitionCategory.MOTION),
    ZOOM_IN("Zoom In", TransitionCategory.MOTION),
    DIRECTIONAL_BLUR("Dir. Blur", TransitionCategory.FX),
    WHIP_PAN("Whip Pan", TransitionCategory.FX),
    GLITCH("Glitch RGB", TransitionCategory.FX),
    FILM_BURN("Film Burn", TransitionCategory.FX),
    LIGHT_LEAK("Light Leak", TransitionCategory.FX)
}

enum class SafeZoneOverlay(val label: String) {
    NONE("Off"),
    TIKTOK("TikTok"),
    REELS("Reels"),
    SHORTS("Shorts")
}

enum class CanvasBackdropMode(val displayName: String) {
    SOLID_OBSIDIAN("Obsidian"),
    BLURRED_VIDEO("Blur Fill"),
    GRADIENT("Gradient")
}

enum class CanvasGradientPreset(
    val displayName: String,
    val startArgb: Long,
    val endArgb: Long
) {
    MIDNIGHT_EMBER("Midnight Ember", 0xFF1F122B, 0xFF0A0C10),
    STUDIO_TEAL("Studio Teal", 0xFF0B3B49, 0xFF09131B),
    SUNSET_GOLD("Sunset Gold", 0xFF5A2A0C, 0xFF160B18),
    VELVET_DUSK("Velvet Dusk", 0xFF2B133D, 0xFF0F172A),
    POLAR_ICE("Polar Ice", 0xFF112A46, 0xFF0A141F),
    CARBON_MESH("Carbon", 0xFF262B36, 0xFF0C0E12)
}

@JsonClass(generateAdapter = true)
data class CanvasBackdrop(
    val mode: CanvasBackdropMode = CanvasBackdropMode.SOLID_OBSIDIAN,
    val gradientPreset: CanvasGradientPreset = CanvasGradientPreset.MIDNIGHT_EMBER,
    val blurStrength: Float = 0.7f
)

enum class CutoutBackgroundMode(val displayName: String) {
    TRANSPARENT("Transparent"),
    SOLID_COLOR("Solid Color"),
    GAUSSIAN_BLUR("Gaussian Blur"),
    CUSTOM_IMAGE("Custom Image")
}

@JsonClass(generateAdapter = true)
data class AiCutoutSettings(
    val enabled: Boolean = false,
    val threshold: Float = 0.50f,         // 0.10 .. 0.90 mask confidence cutoff
    val featherPx: Float = 4f,            // 0 .. 15 px soft edge feather
    val backgroundMode: CutoutBackgroundMode = CutoutBackgroundMode.TRANSPARENT,
    val solidColorArgb: Long = 0xFF0A0C10,
    val customBackgroundUri: String? = null
)

enum class MaskShape(val displayName: String) {
    NONE("None"),
    RECTANGLE("Rectangle"),
    CIRCLE("Circle / Ellipse"),
    LINEAR_SPLIT("Linear Split"),
    FREEHAND_PEN("Pen (Bezier)")
}

@JsonClass(generateAdapter = true)
data class MaskPoint(
    val x: Float, // Normalized 0f .. 1f inside preview stage
    val y: Float  // Normalized 0f .. 1f inside preview stage
)

@JsonClass(generateAdapter = true)
data class MaskKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.56f,
    val height: Float = 0.56f,
    val rotationDegrees: Float = 0f
)

@JsonClass(generateAdapter = true)
data class MaskSettings(
    val shape: MaskShape = MaskShape.NONE,
    val centerX: Float = 0.5f,         // 0f .. 1f
    val centerY: Float = 0.5f,         // 0f .. 1f
    val width: Float = 0.56f,          // 0.05f .. 1.5f
    val height: Float = 0.56f,         // 0.05f .. 1.5f
    val rotationDegrees: Float = 0f,   // -180f .. 180f
    val cornerRadius: Float = 0.08f,   // 0f .. 0.5f for Rectangle
    val invert: Boolean = false,
    val featherPx: Float = 4f,         // 0f .. 50f
    val opacity: Float = 1f,           // 0f .. 1f
    val expansion: Float = 0f,         // -0.25f .. 0.25f
    val penPoints: List<MaskPoint> = emptyList(),
    val penClosed: Boolean = false,
    val keyframes: List<MaskKeyframe> = emptyList()
) {
    val isActive: Boolean
        get() = shape != MaskShape.NONE && (shape != MaskShape.FREEHAND_PEN || penPoints.size >= 3)
}

@JsonClass(generateAdapter = true)
data class TrackedPositionKeyframe(
    val timeMs: Long,
    val normX: Float, // Normalized -0.5 .. 0.5 relative to stage center
    val normY: Float  // Normalized -0.5 .. 0.5 relative to stage center
)

@JsonClass(generateAdapter = true)
data class MotionTrackData(
    val enabled: Boolean = false,
    val targetAnchorX: Float = 0.5f,   // 0f .. 1f initial reticle X on stage
    val targetAnchorY: Float = 0.5f,   // 0f .. 1f initial reticle Y on stage
    val offsetX: Float = 0f,           // Fine-tune offset -0.5 .. 0.5
    val offsetY: Float = 0f,           // Fine-tune offset -0.5 .. 0.5
    val keyframes: List<TrackedPositionKeyframe> = emptyList()
) {
    val hasTrack: Boolean
        get() = enabled && keyframes.isNotEmpty()
}

enum class CaptionAnimationPreset(val displayName: String, val subtitle: String) {
    NONE("None", "Clean static"),
    POP_IN("Pop-In", "Elastic punch"),
    ACTIVE_WORD_HIGHLIGHT("Word Pop", "Active word highlight"),
    SLIDE_UP("Slide Up", "Smooth rise")
}

@JsonClass(generateAdapter = true)
data class CaptionClip(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val offsetX: Float = 0f,           // -0.45 .. 0.45 from center
    val offsetY: Float = 0.30f,        // Default lower-third subtitle zone
    val fontId: String = "bundled_montserrat",
    val fontSizeSp: Float = 26f,
    val textColorArgb: Long = 0xFFFFFFFF,
    val highlightColorArgb: Long = 0xFFFACC15, // Vibrant yellow active word pop
    val strokeColorArgb: Long = 0xFF000000,
    val strokeWidthDp: Float = 2.5f,
    val hasBackgroundPill: Boolean = true,
    val backgroundPillArgb: Long = 0xCC0A0C10,
    val animationPreset: CaptionAnimationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT,
    val motionTrack: MotionTrackData = MotionTrackData()
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(250L)
}

enum class MainMediaType {
    VIDEO,
    IMAGE
}

enum class OverlayType {
    IMAGE,
    TEXT,
    VIDEO
}

enum class ChromaKeyColor(
    val displayName: String,
    val swatchHex: Long,
    val keyR: Float,
    val keyG: Float,
    val keyB: Float
) {
    GREEN("Green Screen", 0xFF00D26A, 0.0f, 0.85f, 0.25f),
    BLUE("Blue Screen", 0xFF1D6BFF, 0.05f, 0.28f, 0.96f),
    CYAN("Cyan Screen", 0xFF00D4FF, 0.0f, 0.83f, 1.0f),
    MAGENTA("Magenta", 0xFFE60099, 0.90f, 0.0f, 0.60f)
}

enum class OverlayBlendMode(val displayName: String) {
    NORMAL("Normal"),
    SCREEN("Screen"),
    MULTIPLY("Multiply"),
    OVERLAY("Overlay")
}

@JsonClass(generateAdapter = true)
data class ChromaKeySettings(
    val enabled: Boolean = false,
    val keyColor: ChromaKeyColor = ChromaKeyColor.GREEN,
    val customColorArgb: Long? = null,    // Optional eyedropper-sampled ARGB color overriding keyColor
    val similarity: Float = 0.36f,        // 0.05 .. 0.85 chroma radius threshold
    val smoothness: Float = 0.14f,        // 0.01 .. 0.40 edge feather width
    val spillSuppression: Float = 0.50f   // 0.00 .. 1.00 color-spill removal strength
) {
    val effectiveSwatchHex: Long
        get() = customColorArgb ?: keyColor.swatchHex

    fun effectiveKeyRgb(): Triple<Float, Float, Float> {
        val custom = customColorArgb
        return if (custom != null) {
            val r = ((custom shr 16) and 0xFFL).toFloat() / 255f
            val g = ((custom shr 8) and 0xFFL).toFloat() / 255f
            val b = (custom and 0xFFL).toFloat() / 255f
            Triple(r, g, b)
        } else {
            Triple(keyColor.keyR, keyColor.keyG, keyColor.keyB)
        }
    }
}

enum class TextMotionPreset(
    val displayName: String,
    val subtitle: String,
    val defaultDurationMs: Long
) {
    NONE("Static", "No motion", 0L),
    TYPEWRITER("Typewriter", "Letter reveal", 1400L),
    FADE_IN("Fade In", "Soft dissolve", 850L),
    SLIDE_UP("Slide Up", "Cinema rise", 750L),
    SLIDE_LEFT("Slide In", "Lateral glide", 750L),
    POP_SPRING("Bounce Pop", "Spring scale", 800L),
    TRACKING_EXPAND("Cinema Spread", "Wide tracking", 1100L),
    KINETIC_PULSE("Kinetic Pulse", "Snap & swing", 900L)
}

@JsonClass(generateAdapter = true)
data class ColorWheelOffset(
    val x: Float = 0f,     // -1f .. 1f horizontal color wheel vector (red-cyan / amber-blue axis)
    val y: Float = 0f,     // -1f .. 1f vertical color wheel vector (green-magenta axis)
    val luma: Float = 0f   // -0.5f .. 0.5f master wheel luminance ring offset
) {
    val isNeutral: Boolean
        get() = abs(x) < 0.005f && abs(y) < 0.005f && abs(luma) < 0.005f
}

@JsonClass(generateAdapter = true)
data class ColorAdjustments(
    val brightness: Float = 0f,   // -0.5f .. 0.5f (Exposure / Brightness)
    val contrast: Float = 1f,     // 0.5f .. 1.8f
    val saturation: Float = 1f,   // 0f .. 2.0f
    val temperature: Float = 0f,  // -1.0f (Cool) .. 1.0f (Warm)
    val tint: Float = 0f,         // -1.0f (Green) .. 1.0f (Magenta)
    val highlights: Float = 0f,   // -1.0f .. 1.0f
    val shadows: Float = 0f,      // -1.0f .. 1.0f
    val lift: ColorWheelOffset = ColorWheelOffset(),
    val gamma: ColorWheelOffset = ColorWheelOffset(),
    val gain: ColorWheelOffset = ColorWheelOffset(),
    val curvePoints: List<Float> = DEFAULT_CURVE_POINTS, // 5 control points at x = 0, .25, .5, .75, 1.0
    val cubeLutPath: String? = null,
    val cubeLutName: String? = null,
    val cubeLutIntensity: Float = 1f,
    val filmGrain: Float = 0f,    // 0f .. 1f procedural analog grain
    val halation: Float = 0f      // 0f .. 1f warm film highlight bloom
) {
    val normalizedCurvePoints: List<Float>
        get() = if (curvePoints.size == 5) curvePoints.map { it.coerceIn(0f, 1f) } else DEFAULT_CURVE_POINTS

    val hasNonLinearCurve: Boolean
        get() = normalizedCurvePoints.indices.any { i ->
            abs(normalizedCurvePoints[i] - DEFAULT_CURVE_POINTS[i]) > 0.01f
        }

    val hasAdvancedGrade: Boolean
        get() = !lift.isNeutral ||
            !gamma.isNeutral ||
            !gain.isNeutral ||
            abs(highlights) > 0.005f ||
            abs(shadows) > 0.005f ||
            abs(tint) > 0.005f ||
            hasNonLinearCurve ||
            !cubeLutPath.isNullOrBlank() ||
            filmGrain > 0.005f ||
            halation > 0.005f

    val isDefault: Boolean
        get() = abs(brightness) < 0.005f &&
            abs(contrast - 1f) < 0.005f &&
            abs(saturation - 1f) < 0.005f &&
            abs(temperature) < 0.005f &&
            !hasAdvancedGrade

    companion object {
        val DEFAULT_CURVE_POINTS: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    }
}

@JsonClass(generateAdapter = true)
data class TransitionSpec(
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 600L
)

enum class VoiceEffectPreset(
    val displayName: String,
    val pitchFactor: Float,
    val subtitle: String
) {
    NONE("Normal", 1.0f, "Natural voice"),
    CHIPMUNK("Chipmunk", 1.55f, "High & bright"),
    DEEP_BASS("Deep Bass", 0.72f, "Rich baritone"),
    ROBOT("Robot", 1.0f, "Metallic ring-mod"),
    RADIO("Radio", 1.0f, "Walkie-talkie"),
    ECHO_HALL("Echo Hall", 1.0f, "Studio hall delay"),
    MONSTER("Monster", 0.58f, "Ultra-deep growl"),
    STUDIO_CLARITY("Broadcast", 1.02f, "Warm studio mic")
}

enum class FxType(val displayName: String, val badge: String) {
    MAGNIFIER("Magnifier", "Zoom Lens"),
    MOSAIC("Mosaic", "Pixelate"),
    BLUR("Blur", "Gaussian")
}

enum class FxShape(val displayName: String) {
    CIRCLE("Circle"),
    ROUNDED_RECT("Rectangle")
}

@JsonClass(generateAdapter = true)
data class FxLayer(
    val id: String = UUID.randomUUID().toString(),
    val type: FxType = FxType.BLUR,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 4000L,
    val fullFrame: Boolean = false,
    val shape: FxShape = FxShape.CIRCLE,
    val centerX: Float = 0.5f,          // Normalized 0.05..0.95
    val centerY: Float = 0.5f,          // Normalized 0.05..0.95
    val width: Float = 0.38f,           // Normalized 0.12..0.95
    val height: Float = 0.38f,          // Normalized 0.12..0.95
    val intensity: Float = 0.55f,       // 0.05..1.0 (blur strength or mosaic block size)
    val magnification: Float = 2.0f,    // 1.2..4.0 (used by MAGNIFIER)
    val feather: Float = 0.06f,         // 0.0..0.30 soft edge blend
    val opacity: Float = 1f,            // 0.1..1.0
    val showBorderRing: Boolean = true  // Crisp lens rim on MAGNIFIER
) {
    val endTimeMs: Long
        get() = startTimeMs + durationMs.coerceAtLeast(300L)
}

@JsonClass(generateAdapter = true)
data class CropRect(
    val left: Float = 0f,               // 0f..0.45f
    val top: Float = 0f,                // 0f..0.45f
    val right: Float = 0f,              // 0f..0.45f
    val bottom: Float = 0f,             // 0f..0.45f
    val aspectPreset: String = "Free"
) {
    val isActive: Boolean
        get() = left > 0.002f || top > 0.002f || right > 0.002f || bottom > 0.002f

    val visibleWidthFraction: Float
        get() = (1f - left.coerceIn(0f, 0.45f) - right.coerceIn(0f, 0.45f)).coerceAtLeast(0.1f)

    val visibleHeightFraction: Float
        get() = (1f - top.coerceIn(0f, 0.45f) - bottom.coerceIn(0f, 0.45f)).coerceAtLeast(0.1f)

    fun sanitized(): CropRect {
        val l = left.coerceIn(0f, 0.45f)
        val r = right.coerceIn(0f, (0.90f - l).coerceAtLeast(0f))
        val t = top.coerceIn(0f, 0.45f)
        val b = bottom.coerceIn(0f, (0.90f - t).coerceAtLeast(0f))
        return copy(left = l, top = t, right = r, bottom = b)
    }
}

@JsonClass(generateAdapter = true)
data class PanZoomSpec(
    val enabled: Boolean = false,
    val startScale: Float = 1f,         // 0.5..3.5
    val startOffsetX: Float = 0f,       // -0.6..0.6
    val startOffsetY: Float = 0f,       // -0.6..0.6
    val endScale: Float = 1.25f,        // 0.5..3.5
    val endOffsetX: Float = 0f,         // -0.6..0.6
    val endOffsetY: Float = 0f,         // -0.6..0.6
    val linked: Boolean = false         // When true, start and end framing stay locked together
)

@JsonClass(generateAdapter = true)
data class AudioSettings(
    val volume: Float = 1f,       // 0f .. 2f
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val fadeInMs: Long = 0L,      // 0 .. 5000ms
    val fadeOutMs: Long = 0L,     // 0 .. 5000ms
    val eqLowDb: Float = 0f,      // -12f .. +12f dB (120 Hz shelf)
    val eqMidDb: Float = 0f,      // -12f .. +12f dB (1 kHz peak)
    val eqHighDb: Float = 0f,     // -12f .. +12f dB (6 kHz shelf)
    val voiceIsolation: Boolean = false,  // 85 Hz HPF + vocal presence + compressor
    val noiseGateDb: Float = -60f,        // -60 dB (Off) .. -12 dB
    val autoDuckMusic: Boolean = false,   // Duck background music when voice tracks peak
    val denoiseStrength: Float = 0f,      // 0f .. 1f broadband spectral & rumble denoise
    val voiceEffect: VoiceEffectPreset = VoiceEffectPreset.NONE
) {
    val hasActiveEq: Boolean
        get() = abs(eqLowDb) > 0.1f || abs(eqMidDb) > 0.1f || abs(eqHighDb) > 0.1f

    val hasActiveDsp: Boolean
        get() = hasActiveEq ||
            voiceIsolation ||
            noiseGateDb > -58f ||
            denoiseStrength > 0.02f ||
            voiceEffect != VoiceEffectPreset.NONE
}

enum class KeyframeEasing(val displayName: String) {
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In-Out")
}

@JsonClass(generateAdapter = true)
data class ClipTransform(
    val offsetX: Float = 0f,          // Normalized -1.0 .. 1.0 of stage width
    val offsetY: Float = 0f,          // Normalized -1.0 .. 1.0 of stage height
    val scale: Float = 1f,            // 0.25 .. 4.0
    val rotationDegrees: Float = 0f,  // -180 .. 180
    val opacity: Float = 1f,          // 0.0 .. 1.0
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val fullFrameFill: Boolean = false,
    val crop: CropRect = CropRect()
) {
    val isIdentity: Boolean
        get() = abs(offsetX) < 0.002f &&
            abs(offsetY) < 0.002f &&
            abs(scale - 1f) < 0.005f &&
            abs(rotationDegrees) < 0.2f &&
            abs(opacity - 1f) < 0.005f &&
            !flipHorizontal &&
            !flipVertical &&
            !fullFrameFill &&
            !crop.isActive
}

@JsonClass(generateAdapter = true)
data class TransformKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,          // Clip-local timeline time (0 .. clip.durationMs)
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT
) {
    fun toTransform(): ClipTransform = ClipTransform(
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        rotationDegrees = rotationDegrees,
        opacity = opacity
    )
}

@JsonClass(generateAdapter = true)
data class MainClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val mediaType: MainMediaType,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val audio: AudioSettings = AudioSettings(),
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL,
    val filterIntensity: Float = 1f,
    val colorAdjustments: ColorAdjustments = ColorAdjustments(),
    val transitionToNext: TransitionSpec = TransitionSpec(),
    val thumbnailPath: String? = null,
    val playbackSpeed: Float = 1.0f,        // 0.25f .. 4.0f
    val pitchShiftSemitones: Float = 0f,    // -12f .. +12f
    val preservePitch: Boolean = true,
    val opticalFlowSlowMo: Boolean = false,
    val baseTransform: ClipTransform = ClipTransform(),
    val keyframes: List<TransformKeyframe> = emptyList(),
    val panZoom: PanZoomSpec = PanZoomSpec(),
    val aiCutout: AiCutoutSettings = AiCutoutSettings(),
    val mask: MaskSettings = MaskSettings()
) {
    val rawTrimDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(250L)

    val safeSpeed: Float
        get() = if (mediaType == MainMediaType.VIDEO) playbackSpeed.coerceIn(0.25f, 4.0f) else 1.0f

    val durationMs: Long
        get() = (rawTrimDurationMs.toDouble() / safeSpeed.toDouble()).toLong().coerceAtLeast(250L)

    val effectiveAudioPitch: Float
        get() {
            val semitoneFactor = 2.0.pow(pitchShiftSemitones.coerceIn(-12f, 12f) / 12.0).toFloat()
            val basePitch = if (preservePitch) 1.0f else safeSpeed
            val voicePitch = audio.voiceEffect.pitchFactor
            return (basePitch * semitoneFactor * voicePitch).coerceIn(0.25f, 4.0f)
        }
}

@JsonClass(generateAdapter = true)
data class OverlayLayer(
    val id: String = UUID.randomUUID().toString(),
    val type: OverlayType,
    val zIndex: Int = 1,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 4000L,
    val offsetX: Float = 0f,         // Normalized -0.5..0.5 from canvas center
    val offsetY: Float = 0f,         // Normalized -0.5..0.5 from canvas center
    val scale: Float = 1f,           // 0.25..4.0
    val rotationDegrees: Float = 0f, // -180..180
    val opacity: Float = 1f,         // 0.05..1.0
    val blendMode: OverlayBlendMode = OverlayBlendMode.NORMAL,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val displayName: String = "",
    val sourceDurationMs: Long = 4000L,
    val trimStartMs: Long = 0L,
    val chromaKey: ChromaKeySettings = ChromaKeySettings(),
    val cornerRadiusDp: Float = 12f,
    val text: String = "LumaCut Title",
    val fontId: String = "bundled_bebas_neue",
    val fontSizeSp: Float = 36f,
    val textColorArgb: Long = 0xFFFFFFFF,
    val strokeColorArgb: Long = 0xFF000000,
    val hasStroke: Boolean = true,
    val hasDropShadow: Boolean = false,
    val backgroundColorArgb: Long = 0xAA0B0C0E,
    val hasBackground: Boolean = false,
    val textMotionPreset: TextMotionPreset = TextMotionPreset.NONE,
    val textMotionDurationMs: Long = 900L,
    val aiCutout: AiCutoutSettings = AiCutoutSettings(),
    val mask: MaskSettings = MaskSettings(),
    val motionTrack: MotionTrackData = MotionTrackData(),
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val fullFrameFill: Boolean = false,
    val crop: CropRect = CropRect()
) {
    val endTimeMs: Long
        get() = startTimeMs + durationMs.coerceAtLeast(300L)
}

@JsonClass(generateAdapter = true)
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val laneIndex: Int = 0,
    val displayName: String,
    val uri: String,
    val startTimeMs: Long = 0L,
    val sourceDurationMs: Long = 10_000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val audio: AudioSettings = AudioSettings(),
    val waveformPeaks: List<Float> = emptyList(),
    val beatPositionsMs: List<Long> = emptyList()
) {
    val durationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(300L)

    val endTimeMs: Long
        get() = startTimeMs + durationMs

    /**
     * Returns only the detected beat timestamps (in global timeline milliseconds)
     * that fall inside this clip's trimmed window.
     */
    fun timelineBeatPositionsMs(): List<Long> =
        beatPositionsMs
            .filter { it in trimStartMs..trimEndMs }
            .map { startTimeMs + (it - trimStartMs) }
}

data class ClipTimeRange(
    val index: Int,
    val clip: MainClip,
    val startMs: Long,
    val endMs: Long
)

data class ActiveTransitionWindow(
    val fromClip: MainClip,
    val toClip: MainClip,
    val transition: TransitionSpec,
    val progress: Float // 0f..1f across the transition seam
)

@JsonClass(generateAdapter = true)
data class TimelineDocument(
    val mainClips: List<MainClip> = emptyList(),
    val overlays: List<OverlayLayer> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val captions: List<CaptionClip> = emptyList(),
    val fxLayers: List<FxLayer> = emptyList(),
    val canvasBackdrop: CanvasBackdrop = CanvasBackdrop(),
    val safeZoneOverlay: SafeZoneOverlay = SafeZoneOverlay.NONE
) {
    fun mainClipRanges(): List<ClipTimeRange> {
        var cursor = 0L
        return mainClips.mapIndexed { idx, clip ->
            val start = cursor
            val end = start + clip.durationMs
            cursor = end
            ClipTimeRange(idx, clip, start, end)
        }
    }

    val mainTrackDurationMs: Long
        get() = mainClips.sumOf { it.durationMs }

    val totalDurationMs: Long
        get() {
            val mainEnd = mainTrackDurationMs
            val overlayEnd = overlays.maxOfOrNull { it.endTimeMs } ?: 0L
            val audioEnd = audioClips.maxOfOrNull { it.endTimeMs } ?: 0L
            val captionEnd = captions.maxOfOrNull { it.endTimeMs } ?: 0L
            val fxEnd = fxLayers.maxOfOrNull { it.endTimeMs } ?: 0L
            return maxOf(mainEnd, overlayEnd, audioEnd, captionEnd, fxEnd)
        }

    fun mainClipAt(timeMs: Long): ClipTimeRange? {
        val ranges = mainClipRanges()
        if (ranges.isEmpty()) return null
        return ranges.firstOrNull { timeMs in it.startMs until it.endMs }
            ?: if (timeMs >= ranges.last().endMs) ranges.last() else ranges.first()
    }

    fun activeTransitionAt(timeMs: Long): ActiveTransitionWindow? {
        val ranges = mainClipRanges()
        for (i in 0 until ranges.size - 1) {
            val current = ranges[i]
            val next = ranges[i + 1]
            val spec = current.clip.transitionToNext
            if (spec.type == TransitionType.NONE || spec.durationMs <= 0L) continue
            val half = (spec.durationMs / 2L).coerceAtMost(minOf(current.clip.durationMs, next.clip.durationMs) / 2L)
            if (half <= 0L) continue
            val seam = current.endMs
            val windowStart = seam - half
            val windowEnd = seam + half
            if (timeMs in windowStart..windowEnd) {
                val progress = ((timeMs - windowStart).toFloat() / (windowEnd - windowStart).toFloat()).coerceIn(0f, 1f)
                return ActiveTransitionWindow(current.clip, next.clip, spec, progress)
            }
        }
        return null
    }

    fun activeOverlaysAt(timeMs: Long): List<OverlayLayer> =
        overlays
            .filter { timeMs in it.startTimeMs..it.endTimeMs }
            .sortedBy { it.zIndex }

    fun activeCaptionsAt(timeMs: Long): List<CaptionClip> =
        captions
            .filter { timeMs in it.startTimeMs..it.endTimeMs }
            .sortedBy { it.startTimeMs }

    fun activeFxLayersAt(timeMs: Long): List<FxLayer> =
        fxLayers
            .filter { timeMs in it.startTimeMs..it.endTimeMs }
            .sortedBy { it.startTimeMs }
}

data class StudioFont(
    val id: String,
    val displayName: String,
    val isCustom: Boolean,
    val resId: Int? = null,
    val filePath: String? = null
)

enum class ExportResolution(
    val label: String,
    val badge: String,
    val recommendedBitrateMbps: Int
) {
    HD_720P("720p", "Compact", 8),
    FHD_1080P("1080p", "Full HD", 16),
    UHD_4K("4K", "Ultra HD", 35)
}

enum class ExportBitrateTier(val label: String, val mbps: Int) {
    STANDARD("Standard", 8),
    HIGH("High", 16),
    CINEMA("Cinema", 35),
    MASTER("Master", 50)
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val fps: Int = 30,
    val bitrateMbps: Int = 16
) {
    fun estimateFileSizeMb(durationMs: Long): Float {
        val seconds = (durationMs.coerceAtLeast(1000L) / 1000f)
        // Video bitrate + 192 kbps AAC audio overhead
        val totalBitsPerSec = (bitrateMbps * 1_000_000f) + 192_000f
        return ((totalBitsPerSec / 8f) * seconds / (1024f * 1024f)).coerceAtLeast(0.1f)
    }
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progressPercent: Int, val stageLabel: String) : ExportState
    data class Success(
        val contentUri: String,
        val displayName: String,
        val sizeMb: Float,
        val savedToGallery: Boolean
    ) : ExportState
    data class Error(val message: String) : ExportState
}

fun formatTimecode(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val centiseconds = (clamped % 1000L) / 10L
    return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds)
}

fun formatShortDuration(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%d:%02d", minutes, seconds)
}
