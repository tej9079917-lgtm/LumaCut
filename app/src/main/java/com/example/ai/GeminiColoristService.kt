package com.example.ai

import android.graphics.Bitmap
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
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiColorGradeResult(
    val adjustments: ColorAdjustments,
    val coloristNote: String
)

/**
 * Firebase AI Logic service powering LumaCut's Gemini AI Colorist and AI Smart Script Captions.
 * Uses `gemini-3.8-flash` with strict JSON Schema output and App Check attestation.
 */
object GeminiColoristService {

    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val REQUEST_TIMEOUT_MS = 60_000L

    private val colorGradeSchema = Schema.obj(
        properties = mapOf(
            "brightness" to Schema.float(description = "Exposure / brightness offset from -0.35 to 0.35"),
            "contrast" to Schema.float(description = "Contrast multiplier from 0.65 to 1.65 (1.0 neutral)"),
            "saturation" to Schema.float(description = "Saturation multiplier from 0.0 to 1.85 (1.0 neutral)"),
            "temperature" to Schema.float(description = "Color temperature from -0.85 (cool blue) to 0.85 (warm amber)"),
            "tint" to Schema.float(description = "Green-Magenta tint from -0.6 to 0.6"),
            "highlights" to Schema.float(description = "Highlights recovery/boost from -0.8 to 0.8"),
            "shadows" to Schema.float(description = "Shadows lift/crush from -0.8 to 0.8"),
            "liftX" to Schema.float(description = "Shadow color wheel X from -0.75 (cyan/teal) to 0.75 (amber/red)"),
            "liftY" to Schema.float(description = "Shadow color wheel Y from -0.75 (green) to 0.75 (magenta)"),
            "liftLuma" to Schema.float(description = "Shadow master lift from -0.25 to 0.25"),
            "gammaX" to Schema.float(description = "Midtone color wheel X from -0.75 to 0.75"),
            "gammaY" to Schema.float(description = "Midtone color wheel Y from -0.75 to 0.75"),
            "gammaLuma" to Schema.float(description = "Midtone master gamma from -0.25 to 0.25"),
            "gainX" to Schema.float(description = "Highlight color wheel X from -0.75 to 0.75"),
            "gainY" to Schema.float(description = "Highlight color wheel Y from -0.75 to 0.75"),
            "gainLuma" to Schema.float(description = "Highlight master gain from -0.25 to 0.25"),
            "filmGrain" to Schema.float(description = "Analog film grain amount from 0.0 to 0.65"),
            "halation" to Schema.float(description = "Warm highlight film halation from 0.0 to 0.65"),
            "coloristNote" to Schema.string(description = "One concise sentence explaining the cinematic look created")
        )
    )

    private val timedCaptionsSchema = Schema.obj(
        properties = mapOf(
            "blocks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "text" to Schema.string(description = "Short punchy subtitle phrase (3 to 7 words max)"),
                        "durationMs" to Schema.integer(description = "Estimated speaking duration in milliseconds between 900 and 4000")
                    )
                ),
                description = "Ordered list of short kinetic subtitle blocks"
            )
        )
    )

    private val coloristModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = colorGradeSchema
                temperature = 0.55f
            }
        )
    }

    private val captionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = timedCaptionsSchema
                temperature = 0.3f
            }
        )
    }

    suspend fun generateColorGrade(
        frameBitmap: Bitmap?,
        prompt: String,
        currentAdjustments: ColorAdjustments
    ): Result<AiColorGradeResult> {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a cinematic look prompt first."))
        }

        return try {
            val scaledFrame = frameBitmap?.let { scaleForVision(it, maxDim = 640) }
            val instruction = buildString {
                append("You are a senior DaVinci Resolve feature-film colorist. ")
                append("Translate the creator's requested look into exact color grading wheel and primary coordinates. ")
                append("Keep skin tones natural unless a stylized look is explicitly requested. ")
                append("Requested look: \"$trimmed\".")
            }

            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                val inputContent = content {
                    if (scaledFrame != null) {
                        image(scaledFrame)
                    }
                    text(instruction)
                }
                coloristModel.generateContent(inputContent)
            }

            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini returned an empty grading response."))

            val json = JSONObject(rawJson)
            val newAdjustments = currentAdjustments.copy(
                brightness = json.optDouble("brightness", 0.0).toFloat().coerceIn(-0.45f, 0.45f),
                contrast = json.optDouble("contrast", 1.0).toFloat().coerceIn(0.55f, 1.75f),
                saturation = json.optDouble("saturation", 1.0).toFloat().coerceIn(0f, 1.95f),
                temperature = json.optDouble("temperature", 0.0).toFloat().coerceIn(-0.95f, 0.95f),
                tint = json.optDouble("tint", 0.0).toFloat().coerceIn(-0.85f, 0.85f),
                highlights = json.optDouble("highlights", 0.0).toFloat().coerceIn(-0.95f, 0.95f),
                shadows = json.optDouble("shadows", 0.0).toFloat().coerceIn(-0.95f, 0.95f),
                lift = ColorWheelOffset(
                    x = json.optDouble("liftX", 0.0).toFloat().coerceIn(-0.9f, 0.9f),
                    y = json.optDouble("liftY", 0.0).toFloat().coerceIn(-0.9f, 0.9f),
                    luma = json.optDouble("liftLuma", 0.0).toFloat().coerceIn(-0.35f, 0.35f)
                ),
                gamma = ColorWheelOffset(
                    x = json.optDouble("gammaX", 0.0).toFloat().coerceIn(-0.9f, 0.9f),
                    y = json.optDouble("gammaY", 0.0).toFloat().coerceIn(-0.9f, 0.9f),
                    luma = json.optDouble("gammaLuma", 0.0).toFloat().coerceIn(-0.35f, 0.35f)
                ),
                gain = ColorWheelOffset(
                    x = json.optDouble("gainX", 0.0).toFloat().coerceIn(-0.9f, 0.9f),
                    y = json.optDouble("gainY", 0.0).toFloat().coerceIn(-0.9f, 0.9f),
                    luma = json.optDouble("gainLuma", 0.0).toFloat().coerceIn(-0.35f, 0.35f)
                ),
                filmGrain = json.optDouble("filmGrain", 0.0).toFloat().coerceIn(0f, 0.85f),
                halation = json.optDouble("halation", 0.0).toFloat().coerceIn(0f, 0.85f)
            )
            val note = json.optString("coloristNote", "Applied custom AI color grade.")
            Result.success(AiColorGradeResult(newAdjustments, note))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateTimedCaptionsFromScript(
        script: String,
        startAtMs: Long,
        targetDurationMs: Long
    ): Result<List<CaptionClip>> {
        val cleaned = script.trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter or paste a voiceover script first."))
        }

        return try {
            val prompt = buildString {
                append("Split the following video voiceover script into punchy short-form Reels/Shorts subtitle blocks ")
                append("of 3 to 6 words each with realistic speaking durations in milliseconds. ")
                append("Target total duration is roughly ${targetDurationMs.coerceAtLeast(3000L)} ms.\n\n")
                append("Script:\n$cleaned")
            }

            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                captionModel.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini returned an empty captions response."))

            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("blocks")
                ?: return Result.failure(IllegalStateException("No caption blocks returned."))

            var cursor = startAtMs.coerceAtLeast(0L)
            val clips = mutableListOf<CaptionClip>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text", "").trim()
                if (text.isEmpty()) continue
                val dur = obj.optLong("durationMs", 1800L).coerceIn(700L, 6000L)
                clips.add(
                    CaptionClip(
                        text = text,
                        startTimeMs = cursor,
                        endTimeMs = cursor + dur,
                        animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                    )
                )
                cursor += dur + 80L
            }
            if (clips.isEmpty()) {
                Result.failure(IllegalStateException("Could not extract subtitle blocks from script."))
            } else {
                Result.success(clips)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaleForVision(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        if (w <= maxDim && h <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val outW = (w * ratio).toInt().coerceAtLeast(64)
        val outH = (h * ratio).toInt().coerceAtLeast(64)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }
}
