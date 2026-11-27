package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip
import com.example.model.ColorAdjustments
import com.example.model.ColorWheelOffset
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiColorGradeResult(
    val adjustments: ColorAdjustments,
    val coloristNote: String
)

data class AiCreativeIdeasResult(
    val hooks: List<String>,
    val titles: List<String>,
    val hashtags: List<String>,
    val editTips: List<String>
)

/**
 * Firebase AI Logic service powering LumaCut's Gemini AI Studio:
 * - AI Colorist (multimodal frame + prompt -> color wheels & primaries)
 * - AI Smart Script Captions (script -> timed karaoke CaptionClip blocks)
 * - AI Audio Auto Captions (multimodal audio track -> synced subtitle segments)
 * - AI Hooks, Viral Titles & Smart Edit Ideas
 * Uses `gemini-3.8-flash` with strict JSON Schema output and App Check attestation.
 */
object GeminiColoristService {

    private const val TAG = "GeminiColoristService"
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

    private val creativeIdeasSchema = Schema.obj(
        properties = mapOf(
            "hooks" to Schema.array(
                items = Schema.string(description = "Punchy 3 to 8 word opening hook suitable as bold on-screen text"),
                description = "Exactly 3 scroll-stopping opening hooks"
            ),
            "titles" to Schema.array(
                items = Schema.string(description = "Compelling short-form video title"),
                description = "3 ready-to-post video titles"
            ),
            "hashtags" to Schema.array(
                items = Schema.string(description = "Relevant hashtag starting with #"),
                description = "6 targeted hashtags"
            ),
            "editTips" to Schema.array(
                items = Schema.string(description = "Actionable editing recommendation mentioning pacing, transitions, SFX, or color"),
                description = "3 specific editing tips tailored to this project"
            )
        )
    )

    private val audioTranscriptionSchema = Schema.obj(
        properties = mapOf(
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Segment start time in milliseconds relative to start of audio"),
                        "endMs" to Schema.integer(description = "Segment end time in milliseconds relative to start of audio"),
                        "text" to Schema.string(description = "Accurate spoken subtitle phrase (2 to 7 words)")
                    )
                ),
                description = "Chronological spoken subtitle segments transcribed directly from the audio track"
            )
        )
    )

    private fun requireFirebaseApp(context: Context): FirebaseApp =
        ensureFirebaseReady(context)
            ?: throw IllegalStateException(
                "Firebase AI is not configured in this build. Use Offline Mic dictation or rebuild with google-services.json."
            )

    private fun coloristModel(app: FirebaseApp): GenerativeModel =
        Firebase.ai(app = app).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = colorGradeSchema
                temperature = 0.55f
            }
        )

    private fun captionModel(app: FirebaseApp): GenerativeModel =
        Firebase.ai(app = app).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = timedCaptionsSchema
                temperature = 0.3f
            }
        )

    private fun ideasModel(app: FirebaseApp): GenerativeModel =
        Firebase.ai(app = app).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = creativeIdeasSchema
                temperature = 0.7f
            }
        )

    private fun audioTranscriptionModel(app: FirebaseApp): GenerativeModel =
        Firebase.ai(app = app).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = audioTranscriptionSchema
                temperature = 0.2f
            }
        )

    suspend fun generateColorGrade(
        context: Context,
        frameBitmap: Bitmap?,
        prompt: String,
        currentAdjustments: ColorAdjustments
    ): Result<AiColorGradeResult> {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a cinematic look prompt first."))
        }

        return try {
            val app = requireFirebaseApp(context)
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
                coloristModel(app).generateContent(inputContent)
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
        } catch (te: TimeoutCancellationException) {
            Log.e(TAG, "AI Colorist timed out", te)
            Result.failure(IllegalStateException("AI Colorist timed out after 60s. Please retry."))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "AI Colorist failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateTimedCaptionsFromScript(
        context: Context,
        script: String,
        startAtMs: Long,
        targetDurationMs: Long
    ): Result<List<CaptionClip>> {
        val cleaned = script.trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter or paste a voiceover script first."))
        }

        return try {
            val app = requireFirebaseApp(context)
            val prompt = buildString {
                append("Split the following video voiceover script into punchy short-form Reels/Shorts/YouTube subtitle blocks ")
                append("of 3 to 6 words each with realistic speaking durations in milliseconds. ")
                append("Target total duration is roughly ${targetDurationMs.coerceAtLeast(3000L)} ms.\n\n")
                append("Script:\n$cleaned")
            }

            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                captionModel(app).generateContent(prompt)
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
        } catch (te: TimeoutCancellationException) {
            Log.e(TAG, "Script captions timed out", te)
            Result.failure(IllegalStateException("AI script captioning timed out after 60s. Please retry."))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Script captions failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateHooksAndEditIdeas(
        context: Context,
        frameBitmap: Bitmap?,
        topic: String,
        clipCount: Int,
        durationMs: Long
    ): Result<AiCreativeIdeasResult> {
        val trimmed = topic.trim()
        if (trimmed.isEmpty() && frameBitmap == null) {
            return Result.failure(IllegalArgumentException("Describe your video topic or add a clip first."))
        }

        return try {
            val app = requireFirebaseApp(context)
            val scaledFrame = frameBitmap?.let { scaleForVision(it, maxDim = 640) }
            val seconds = (durationMs / 1000L).coerceAtLeast(1L)
            val instruction = buildString {
                append("You are an expert short-form video director and editor for Reels, TikTok, and YouTube. ")
                append("The creator's current timeline has $clipCount clip(s) totaling ~${seconds}s. ")
                if (trimmed.isNotEmpty()) {
                    append("Video topic / vibe: \"$trimmed\". ")
                } else {
                    append("Analyze the attached video frame to infer the subject and mood. ")
                }
                append("Generate 3 punchy on-screen opening hooks (3-8 words each), 3 titles, 6 hashtags, ")
                append("and 3 concrete editing tips (e.g., where to use Whip Pan transitions, Speed Ramps, Whoosh SFX, or Karaoke Captions).")
            }

            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                val inputContent = content {
                    if (scaledFrame != null) {
                        image(scaledFrame)
                    }
                    text(instruction)
                }
                ideasModel(app).generateContent(inputContent)
            }

            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val root = JSONObject(rawJson)
            fun readList(key: String): List<String> {
                val arr = root.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
                }
            }

            val result = AiCreativeIdeasResult(
                hooks = readList("hooks"),
                titles = readList("titles"),
                hashtags = readList("hashtags"),
                editTips = readList("editTips")
            )
            if (result.hooks.isEmpty() && result.titles.isEmpty() && result.editTips.isEmpty()) {
                Result.failure(IllegalStateException("Gemini did not return usable creative ideas."))
            } else {
                Result.success(result)
            }
        } catch (te: TimeoutCancellationException) {
            Log.e(TAG, "Creative ideas timed out", te)
            Result.failure(IllegalStateException("AI Creative Studio timed out after 60s. Please retry."))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Creative ideas failed", e)
            Result.failure(e)
        }
    }

    suspend fun transcribeAudioToCaptions(
        context: Context,
        audioBytes: ByteArray,
        mimeType: String,
        clipStartOnTimelineMs: Long,
        clipDurationMs: Long,
        languageHint: String = "Auto",
        maxWordsPerLine: Int = 5
    ): Result<List<CaptionClip>> {
        if (audioBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("No audio data available for transcription."))
        }

        return try {
            val app = requireFirebaseApp(context)
            val langInstruction = if (languageHint.equals("Auto", ignoreCase = true) || languageHint.isBlank()) {
                "Detect the spoken language automatically and transcribe naturally in that language."
            } else {
                "Transcribe the spoken audio in $languageHint."
            }
            val prompt = buildString {
                append("Transcribe all spoken words from the attached audio accurately into short, punchy video subtitles. ")
                append("$langInstruction ")
                append("Keep each segment concise (around 2 to ${maxWordsPerLine.coerceIn(3, 8)} words) with accurate startMs and endMs timestamps ")
                append("within 0 to ${clipDurationMs.coerceAtLeast(1000L)} milliseconds. ")
                append("If there is no speech, return an empty segments array.")
            }

            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                val inputContent = content {
                    inlineData(audioBytes, mimeType)
                    text(prompt)
                }
                audioTranscriptionModel(app).generateContent(inputContent)
            }

            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini returned an empty transcription response."))

            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("segments")
                ?: return Result.failure(IllegalStateException("No speech segments found in audio."))

            val clips = mutableListOf<CaptionClip>()
            val baseOffset = clipStartOnTimelineMs.coerceAtLeast(0L)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text", "").trim()
                if (text.isEmpty()) continue
                val relStart = obj.optLong("startMs", 0L).coerceIn(0L, clipDurationMs.coerceAtLeast(500L))
                val relEnd = obj.optLong("endMs", relStart + 1500L)
                    .coerceAtLeast(relStart + 450L)
                    .coerceAtMost(clipDurationMs.coerceAtLeast(relStart + 450L))
                clips.add(
                    CaptionClip(
                        text = text,
                        startTimeMs = baseOffset + relStart,
                        endTimeMs = baseOffset + relEnd,
                        animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                    )
                )
            }

            if (clips.isEmpty()) {
                Result.failure(IllegalStateException("No clear speech was detected in this audio clip."))
            } else {
                Result.success(clips.sortedBy { it.startTimeMs })
            }
        } catch (te: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", te)
            Result.failure(IllegalStateException("AI transcription timed out after 60s. Try a shorter clip or retry."))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
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
