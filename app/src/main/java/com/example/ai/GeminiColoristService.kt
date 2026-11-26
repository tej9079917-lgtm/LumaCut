package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import com.example.model.ColorAdjustments
import com.example.model.ColorWheelOffset
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiColoristService"
private const val MODEL_ID = "gemini-3.8-flash"

data class AiColoristResult(
    val adjustments: ColorAdjustments,
    val coloristNote: String
)

/**
 * Cloud AI services powered by Firebase AI Logic (`gemini-3.8-flash`):
 * 1. Gemini AI Colorist: analyzes an active video/photo frame + natural language look prompt
 *    and returns exact DaVinci Lift/Gamma/Gain, primary sliders, film grain & halation values.
 * 2. Gemini Smart Auto-Captions: converts a spoken/pasted script into timed Reels/Shorts caption
 *    blocks with punchy 3–6 word segments for Track T1.
 */
object GeminiColoristService {

    private val coloristSchema: Schema by lazy {
        Schema.obj(
            mapOf(
                "brightness" to Schema.double("Exposure/brightness offset between -0.35 and 0.35"),
                "contrast" to Schema.double("Contrast multiplier between 0.65 and 1.65 (1.0 is neutral)"),
                "saturation" to Schema.double("Saturation multiplier between 0.0 and 1.85 (1.0 is neutral)"),
                "temperature" to Schema.double("Color temperature between -1.0 (cool blue) and 1.0 (warm amber)"),
                "tint" to Schema.double("Green-magenta tint between -0.8 (green) and 0.8 (magenta)"),
                "highlights" to Schema.double("Highlights recovery/boost between -0.8 and 0.8"),
                "shadows" to Schema.double("Shadows lift/crush between -0.8 and 0.8"),
                "liftX" to Schema.double("Shadow color wheel X (-0.75 cool cyan to 0.75 warm red)"),
                "liftY" to Schema.double("Shadow color wheel Y (-0.75 green to 0.75 magenta)"),
                "liftLuma" to Schema.double("Shadow luminance lift between -0.25 and 0.25"),
                "gammaX" to Schema.double("Midtone color wheel X (-0.75 cool to 0.75 warm)"),
                "gammaY" to Schema.double("Midtone color wheel Y (-0.75 green to 0.75 magenta)"),
                "gammaLuma" to Schema.double("Midtone luminance offset between -0.25 and 0.25"),
                "gainX" to Schema.double("Highlight color wheel X (-0.75 cool to 0.75 warm)"),
                "gainY" to Schema.double("Highlight color wheel Y (-0.75 green to 0.75 magenta)"),
                "gainLuma" to Schema.double("Highlight luminance offset between -0.25 and 0.25"),
                "filmGrain" to Schema.double("Analog film grain strength between 0.0 and 0.65"),
                "halation" to Schema.double("Warm cinema highlight halation between 0.0 and 0.70"),
                "coloristNote" to Schema.string("Short 1-sentence colorist explanation of the look applied")
            )
        )
    }

    private val captionSchema: Schema by lazy {
        Schema.obj(
            mapOf(
                "blocks" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "text" to Schema.string("Short punchy subtitle phrase (2 to 6 words)"),
                            "startMs" to Schema.integer("Start timestamp in milliseconds"),
                            "endMs" to Schema.integer("End timestamp in milliseconds")
                        )
                    )
                )
            )
        )
    }

    private val coloristModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_ID,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = coloristSchema
            }
        )
    }

    private val captionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_ID,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = captionSchema
            }
        )
    }

    suspend fun analyzeAndGrade(
        frameBitmap: Bitmap?,
        lookPrompt: String,
        currentGrade: ColorAdjustments
    ): Result<AiColoristResult> {
        val cleanPrompt = lookPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a color look prompt first."))
        }
        return withContext(Dispatchers.IO) {
            try {
                val scaledFrame = frameBitmap?.let { downscaleForAi(it, 512) }
                val instruction = buildString {
                    appendLine("You are a senior Hollywood DaVinci Resolve DI colorist.")
                    appendLine("Translate the user's grading direction into precise numeric Lift/Gamma/Gain color wheel coordinates and primary grading sliders.")
                    appendLine("Keep skin tones natural unless the prompt asks for stylized neon/cyberpunk/monochrome.")
                    appendLine("User request: \"$cleanPrompt\"")
                }
                val response = withTimeout(60_000L) {
                    val promptContent = content {
                        if (scaledFrame != null) {
                            image(scaledFrame)
                        }
                        text(instruction)
                    }
                    coloristModel.generateContent(promptContent)
                }
                if (scaledFrame != null && scaledFrame !== frameBitmap && !scaledFrame.isRecycled) {
                    scaledFrame.recycle()
                }
                val rawJson = response.text?.trim()
                    ?: return@withContext Result.failure(IllegalStateException("Empty response from AI Colorist."))

                val json = JSONObject(rawJson)
                val updated = currentGrade.copy(
                    brightness = json.optDouble("brightness", 0.0).toFloat().coerceIn(-0.45f, 0.45f),
                    contrast = json.optDouble("contrast", 1.0).toFloat().coerceIn(0.55f, 1.75f),
                    saturation = json.optDouble("saturation", 1.0).toFloat().coerceIn(0f, 1.95f),
                    temperature = json.optDouble("temperature", 0.0).toFloat().coerceIn(-1f, 1f),
                    tint = json.optDouble("tint", 0.0).toFloat().coerceIn(-1f, 1f),
                    highlights = json.optDouble("highlights", 0.0).toFloat().coerceIn(-1f, 1f),
                    shadows = json.optDouble("shadows", 0.0).toFloat().coerceIn(-1f, 1f),
                    lift = ColorWheelOffset(
                        x = json.optDouble("liftX", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                        y = json.optDouble("liftY", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                        luma = json.optDouble("liftLuma", 0.0).toFloat().coerceIn(-0.35f, 0.35f)
                    ),
                    gamma = ColorWheelOffset(
                        x = json.optDouble("gammaX", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                        y = json.optDouble("gammaY", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                        luma = json.optDouble("gammaLuma", 0.0).toFloat().coerceIn(-0.35f, 0.35f)
                    ),
                    gain = ColorWheelOffset(
                        x = json.optDouble("gainX", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                        y = json.optDouble("gainY", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                        luma = json.optDouble("gainLuma", 0.0).toFloat().coerceIn(-0.35f, 0.35f)
                    ),
                    filmGrain = json.optDouble("filmGrain", 0.0).toFloat().coerceIn(0f, 0.85f),
                    halation = json.optDouble("halation", 0.0).toFloat().coerceIn(0f, 0.85f)
                )
                val note = json.optString("coloristNote", "Applied AI grade for \"$cleanPrompt\".")
                Result.success(AiColoristResult(updated, note))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Colorist request failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun generateSmartCaptionsFromScript(
        script: String,
        startTimeMs: Long,
        totalDurationMs: Long
    ): Result<List<CaptionClip>> {
        val clean = script.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter or paste dialogue text first."))
        }
        val windowDuration = totalDurationMs.coerceAtLeast(3000L)
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildString {
                    appendLine("Split the following video dialogue into kinetic Reels/Shorts subtitle captions.")
                    appendLine("Each caption block should be 2 to 6 punchy words with realistic spoken pacing.")
                    appendLine("Start at ${startTimeMs}ms and fit within ${startTimeMs + windowDuration}ms without overlapping.")
                    appendLine("Dialogue:")
                    appendLine(clean)
                }
                val response = withTimeout(60_000L) {
                    captionModel.generateContent(prompt)
                }
                val rawJson = response.text?.trim()
                    ?: return@withContext Result.failure(IllegalStateException("Empty response from AI Auto-Captions."))
                val root = JSONObject(rawJson)
                val arr = root.optJSONArray("blocks")
                    ?: return@withContext Result.failure(IllegalStateException("No caption blocks returned."))
                val captions = mutableListOf<CaptionClip>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val txt = obj.optString("text", "").trim()
                    if (txt.isEmpty()) continue
                    val st = obj.optLong("startMs", startTimeMs + i * 1600L).coerceAtLeast(0L)
                    val en = obj.optLong("endMs", st + 1500L).coerceAtLeast(st + 350L)
                    captions.add(
                        CaptionClip(
                            text = txt,
                            startTimeMs = st,
                            endTimeMs = en,
                            animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                        )
                    )
                }
                if (captions.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Could not extract any captions."))
                }
                Result.success(captions.sortedBy { it.startTimeMs })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Smart Captions request failed", e)
                Result.failure(e)
            }
        }
    }

    private fun downscaleForAi(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxEdge && h <= maxEdge) return src
        val ratio = w.toFloat() / h.coerceAtLeast(1).toFloat()
        val dstW: Int
        val dstH: Int
        if (ratio >= 1f) {
            dstW = maxEdge
            dstH = (maxEdge / ratio).toInt().coerceAtLeast(32)
        } else {
            dstH = maxEdge
            dstW = (maxEdge * ratio).toInt().coerceAtLeast(32)
        }
        return Bitmap.createScaledBitmap(src, dstW, dstH, true)
    }
}
