package com.example.ai

import android.content.Context
import android.util.Base64
import com.example.media.ExtractedAudioPayload
import com.example.media.OfflineLanguageCatalog
import com.example.model.CaptionClip
import com.example.security.AiCaptionProvider
import com.example.security.FirebaseAuthManager
import com.example.security.FirebaseAuthRequiredException
import com.example.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Multi-engine Cloud AI Auto-Caption transcription router:
 * 1. LumaCut Built-in Gemini (Firebase AI Logic `gemini-3.8-flash`, gated by Firebase Auth Google Sign-In)
 * 2. Personal Gemini Account (`gemini-3-flash-preview` via user-supplied Google AI Studio key)
 * 3. Groq AI (`whisper-large-v3-turbo` — fast 99+ language speech recognition)
 * 4. OpenAI (`whisper-1` — multilingual speech recognition)
 */
object MultiProviderTranscriptionEngine {

    private const val PERSONAL_GEMINI_MODEL = "gemini-3-flash-preview"
    private const val GROQ_WHISPER_MODEL = "whisper-large-v3-turbo"
    private const val OPENAI_WHISPER_MODEL = "whisper-1"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun transcribe(
        context: Context,
        payload: ExtractedAudioPayload,
        clipStartOnTimelineMs: Long,
        languageHint: String,
        outputMode: CaptionOutputMode = CaptionOutputMode.ORIGINAL_SCRIPT,
        targetOutputLanguage: String = "English",
        maxWordsPerLine: Int = 5
    ): Result<List<CaptionClip>> {
        val store = SecureCredentialStore.getInstance(context)
        val provider = store.aiState.value.activeProvider
        return when (provider) {
            AiCaptionProvider.FIREBASE_BUILTIN -> {
                if (!FirebaseAuthManager.isSignedIn()) {
                    return Result.failure(
                        FirebaseAuthRequiredException(
                            "Sign in with Google in AI Login to unlock Cloud Gemini Auto Captions, or use Offline AI for 100% local captions."
                        )
                    )
                }
                GeminiColoristService.transcribeAudioToCaptions(
                    context = context,
                    audioBytes = payload.bytes,
                    mimeType = payload.mimeType,
                    clipStartOnTimelineMs = clipStartOnTimelineMs,
                    clipDurationMs = payload.durationMs,
                    languageHint = languageHint,
                    outputMode = outputMode,
                    targetOutputLanguage = targetOutputLanguage,
                    maxWordsPerLine = maxWordsPerLine
                )
            }
            AiCaptionProvider.GEMINI_API_KEY -> {
                val key = store.getAiApiKey(AiCaptionProvider.GEMINI_API_KEY)
                    ?: return Result.failure(
                        IllegalStateException("Connect your Personal Gemini API key in AI Login first.")
                    )
                transcribeWithPersonalGemini(
                    apiKey = key,
                    payload = payload,
                    clipStartOnTimelineMs = clipStartOnTimelineMs,
                    languageHint = languageHint,
                    outputMode = outputMode,
                    targetOutputLanguage = targetOutputLanguage,
                    maxWordsPerLine = maxWordsPerLine
                )
            }
            AiCaptionProvider.GROQ_WHISPER -> {
                val key = store.getAiApiKey(AiCaptionProvider.GROQ_WHISPER)
                    ?: return Result.failure(
                        IllegalStateException("Connect your Groq API key in AI Login first.")
                    )
                val useDirectEnglishTranslate =
                    outputMode == CaptionOutputMode.TRANSLATE && targetOutputLanguage.equals("English", ignoreCase = true)
                val baseResult = transcribeWithWhisperCompatible(
                    endpointUrl = if (useDirectEnglishTranslate) {
                        "https://api.groq.com/openai/v1/audio/translations"
                    } else {
                        "https://api.groq.com/openai/v1/audio/transcriptions"
                    },
                    modelName = GROQ_WHISPER_MODEL,
                    providerLabel = "Groq Whisper",
                    apiKey = key,
                    payload = payload,
                    clipStartOnTimelineMs = clipStartOnTimelineMs,
                    languageHint = if (useDirectEnglishTranslate) "Auto" else languageHint,
                    maxWordsPerLine = maxWordsPerLine
                )
                if (useDirectEnglishTranslate || outputMode == CaptionOutputMode.ORIGINAL_SCRIPT) {
                    baseResult
                } else {
                    baseResult.fold(
                        onSuccess = { clips -> transformExistingCaptions(context, clips, outputMode, targetOutputLanguage) },
                        onFailure = { Result.failure(it) }
                    )
                }
            }
            AiCaptionProvider.OPENAI_WHISPER -> {
                val key = store.getAiApiKey(AiCaptionProvider.OPENAI_WHISPER)
                    ?: return Result.failure(
                        IllegalStateException("Connect your OpenAI API key in AI Login first.")
                    )
                val useDirectEnglishTranslate =
                    outputMode == CaptionOutputMode.TRANSLATE && targetOutputLanguage.equals("English", ignoreCase = true)
                val baseResult = transcribeWithWhisperCompatible(
                    endpointUrl = if (useDirectEnglishTranslate) {
                        "https://api.openai.com/v1/audio/translations"
                    } else {
                        "https://api.openai.com/v1/audio/transcriptions"
                    },
                    modelName = OPENAI_WHISPER_MODEL,
                    providerLabel = "OpenAI Whisper",
                    apiKey = key,
                    payload = payload,
                    clipStartOnTimelineMs = clipStartOnTimelineMs,
                    languageHint = if (useDirectEnglishTranslate) "Auto" else languageHint,
                    maxWordsPerLine = maxWordsPerLine
                )
                if (useDirectEnglishTranslate || outputMode == CaptionOutputMode.ORIGINAL_SCRIPT) {
                    baseResult
                } else {
                    baseResult.fold(
                        onSuccess = { clips -> transformExistingCaptions(context, clips, outputMode, targetOutputLanguage) },
                        onFailure = { Result.failure(it) }
                    )
                }
            }
        }
    }

    /**
     * Translates existing timeline captions into any target language OR converts them into
     * Phonetic English (e.g., Telugu "ఎలా ఉన్నారు" -> "Ela unnaru").
     * Automatically routes through the user's connected AI provider, falling back to
     * pure on-device [com.example.media.PhoneticTransliterator] for Phonetic English when offline.
     */
    suspend fun transformExistingCaptions(
        context: Context,
        captions: List<CaptionClip>,
        outputMode: CaptionOutputMode,
        targetOutputLanguage: String
    ): Result<List<CaptionClip>> {
        if (captions.isEmpty()) {
            return Result.failure(IllegalArgumentException("Generate or add captions first before converting."))
        }
        if (outputMode == CaptionOutputMode.ORIGINAL_SCRIPT) {
            return Result.success(captions)
        }

        val store = SecureCredentialStore.getInstance(context)
        val provider = store.aiState.value.activeProvider

        val cloudResult: Result<List<CaptionClip>> = when (provider) {
            AiCaptionProvider.FIREBASE_BUILTIN -> {
                if (FirebaseAuthManager.isSignedIn()) {
                    GeminiColoristService.transformCaptions(context, captions, outputMode, targetOutputLanguage)
                } else {
                    Result.failure(
                        FirebaseAuthRequiredException(
                            "Sign in with Google in AI Login to translate captions into $targetOutputLanguage, or use Phonetic English offline."
                        )
                    )
                }
            }
            AiCaptionProvider.GEMINI_API_KEY -> {
                val key = store.getAiApiKey(AiCaptionProvider.GEMINI_API_KEY)
                    ?: return Result.failure(IllegalStateException("Connect your Personal Gemini key in AI Login first."))
                transformWithPersonalGemini(key, captions, outputMode, targetOutputLanguage)
            }
            AiCaptionProvider.GROQ_WHISPER -> {
                val key = store.getAiApiKey(AiCaptionProvider.GROQ_WHISPER)
                    ?: return Result.failure(IllegalStateException("Connect your Groq key in AI Login first."))
                transformWithOpenAiCompatibleChat(
                    endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
                    modelName = "llama-3.3-70b-versatile",
                    apiKey = key,
                    captions = captions,
                    outputMode = outputMode,
                    targetOutputLanguage = targetOutputLanguage
                )
            }
            AiCaptionProvider.OPENAI_WHISPER -> {
                val key = store.getAiApiKey(AiCaptionProvider.OPENAI_WHISPER)
                    ?: return Result.failure(IllegalStateException("Connect your OpenAI key in AI Login first."))
                transformWithOpenAiCompatibleChat(
                    endpointUrl = "https://api.openai.com/v1/chat/completions",
                    modelName = "gpt-4o-mini",
                    apiKey = key,
                    captions = captions,
                    outputMode = outputMode,
                    targetOutputLanguage = targetOutputLanguage
                )
            }
        }

        if (cloudResult.isSuccess) return cloudResult

        // For Phonetic English mode, always provide a seamless 100% offline Indic transliteration fallback!
        if (outputMode == CaptionOutputMode.PHONETIC_ENGLISH) {
            val offlineConverted = captions.map { cap ->
                cap.copy(text = com.example.media.PhoneticTransliterator.toPhoneticEnglish(cap.text))
            }
            return Result.success(offlineConverted)
        }

        return cloudResult
    }

    /**
     * Verifies user-provided API credentials live against the selected AI provider before saving.
     */
    suspend fun verifyCredentials(
        provider: AiCaptionProvider,
        rawApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = rawApiKey.trim()
        if (provider == AiCaptionProvider.FIREBASE_BUILTIN) {
            return@withContext Result.success("Using LumaCut Built-in Gemini")
        }
        if (key.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid API key."))
        }
        try {
            val request = when (provider) {
                AiCaptionProvider.GEMINI_API_KEY -> {
                    Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$PERSONAL_GEMINI_MODEL")
                        .addHeader("x-goog-api-key", key)
                        .get()
                        .build()
                }
                AiCaptionProvider.GROQ_WHISPER -> {
                    Request.Builder()
                        .url("https://api.groq.com/openai/v1/models")
                        .addHeader("Authorization", "Bearer $key")
                        .get()
                        .build()
                }
                AiCaptionProvider.OPENAI_WHISPER -> {
                    Request.Builder()
                        .url("https://api.openai.com/v1/models")
                        .addHeader("Authorization", "Bearer $key")
                        .get()
                        .build()
                }
                AiCaptionProvider.FIREBASE_BUILTIN -> return@withContext Result.success("Ready")
            }

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Connected to ${provider.displayName}")
                } else {
                    val msg = when (resp.code) {
                        400, 401, 403 -> "Invalid or unauthorized API key (HTTP ${resp.code}). Please check your key."
                        429 -> "Key recognized, but rate limit exceeded (HTTP 429)."
                        else -> "${provider.displayName} returned HTTP ${resp.code}."
                    }
                    Result.failure(IllegalStateException(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Network check failed: ${e.localizedMessage ?: "check internet"}"))
        }
    }

    private suspend fun transcribeWithPersonalGemini(
        apiKey: String,
        payload: ExtractedAudioPayload,
        clipStartOnTimelineMs: Long,
        languageHint: String,
        outputMode: CaptionOutputMode,
        targetOutputLanguage: String,
        maxWordsPerLine: Int
    ): Result<List<CaptionClip>> = withContext(Dispatchers.IO) {
        try {
            val b64Audio = Base64.encodeToString(payload.bytes, Base64.NO_WRAP)
            val sourceDesc = if (languageHint.equals("Auto", ignoreCase = true) || languageHint.isBlank()) {
                "automatically detect the spoken language in the audio"
            } else {
                "the spoken audio is in $languageHint"
            }
            val outputInstruction = GeminiColoristService.buildCaptionOutputPromptInstruction(
                sourceLangDesc = sourceDesc,
                outputMode = outputMode,
                targetOutputLanguage = targetOutputLanguage
            )
            val prompt = buildString {
                append("Create timed video subtitle segments for all spoken words in the attached audio. ")
                append("$outputInstruction ")
                append("Keep each segment 2 to ${maxWordsPerLine.coerceIn(3, 8)} words long with startMs and endMs integers ")
                append("between 0 and ${payload.durationMs.coerceAtLeast(1000L)}. ")
                append("Return ONLY JSON matching {\"segments\":[{\"startMs\":0,\"endMs\":1800,\"text\":\"...\"}]}.")
            }

            val inlinePart = JSONObject().put(
                "inline_data",
                JSONObject()
                    .put("mime_type", payload.mimeType)
                    .put("data", b64Audio)
            )
            val textPart = JSONObject().put("text", prompt)
            val contentObj = JSONObject().put("parts", JSONArray().put(inlinePart).put(textPart))

            val bodyJson = JSONObject()
                .put("contents", JSONArray().put(contentObj))
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", buildSegmentsJsonSchema())
                )

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$PERSONAL_GEMINI_MODEL:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errDetail = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Personal Gemini failed (HTTP ${resp.code}): ${errDetail.ifBlank { "check your API key" }}"
                        )
                    )
                }

                val root = JSONObject(rawBody)
                val candidates = root.optJSONArray("candidates")
                val firstText = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.let { CaptionSegmentNormalizer.stripJsonMarkdownFences(it) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@withContext Result.failure(IllegalStateException("Gemini returned empty transcription."))

                val parsed = JSONObject(firstText)
                val arr = parsed.optJSONArray("segments")
                    ?: return@withContext Result.failure(IllegalStateException("No speech segments returned."))

                val rawSegments = mutableListOf<RawCaptionSegment>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val txt = obj.optString("text", "").trim()
                    if (txt.isEmpty()) continue
                    val s = obj.optLong("startMs", 0L)
                    val e = obj.optLong("endMs", s + 1500L)
                    rawSegments.add(RawCaptionSegment(s, e, txt))
                }

                val normalized = CaptionSegmentNormalizer.normalize(
                    rawSegments = rawSegments,
                    timelineOffsetMs = clipStartOnTimelineMs,
                    clipDurationMs = payload.durationMs,
                    maxWordsPerLine = maxWordsPerLine
                )
                if (normalized.isEmpty()) {
                    Result.failure(IllegalStateException("No clear speech detected in this clip."))
                } else {
                    Result.success(normalized)
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Personal Gemini error: ${e.localizedMessage ?: "network error"}"))
        }
    }

    private suspend fun transformWithPersonalGemini(
        apiKey: String,
        captions: List<CaptionClip>,
        outputMode: CaptionOutputMode,
        targetOutputLanguage: String
    ): Result<List<CaptionClip>> = withContext(Dispatchers.IO) {
        try {
            val indexed = captions.mapIndexed { idx, c -> "${idx + 1}. ${c.text}" }.joinToString("\n")
            val prompt = GeminiColoristService.buildTransformCaptionsPrompt(indexed, captions.size, outputMode, targetOutputLanguage)
            val bodyJson = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", buildSegmentsJsonSchema())
                )
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$PERSONAL_GEMINI_MODEL:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Personal Gemini caption convert failed (HTTP ${resp.code})."))
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                val txt = root.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                    ?.let { CaptionSegmentNormalizer.stripJsonMarkdownFences(it) }
                    ?: return@withContext Result.failure(IllegalStateException("Empty conversion response."))
                val arr = JSONObject(txt).optJSONArray("segments")
                    ?: return@withContext Result.failure(IllegalStateException("No converted segments returned."))
                val updated = captions.mapIndexed { idx, cap ->
                    val newText = arr.optJSONObject(idx)?.optString("text", "")?.trim().orEmpty()
                    if (newText.isNotEmpty()) cap.copy(text = newText) else cap
                }
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Caption conversion failed: ${e.localizedMessage}"))
        }
    }

    private suspend fun transformWithOpenAiCompatibleChat(
        endpointUrl: String,
        modelName: String,
        apiKey: String,
        captions: List<CaptionClip>,
        outputMode: CaptionOutputMode,
        targetOutputLanguage: String
    ): Result<List<CaptionClip>> = withContext(Dispatchers.IO) {
        try {
            val indexed = captions.mapIndexed { idx, c -> "${idx + 1}. ${c.text}" }.joinToString("\n")
            val prompt = GeminiColoristService.buildTransformCaptionsPrompt(indexed, captions.size, outputMode, targetOutputLanguage)
            val bodyJson = JSONObject()
                .put("model", modelName)
                .put("temperature", 0.2)
                .put("response_format", JSONObject().put("type", "json_object"))
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                )
            val req = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Translation failed (HTTP ${resp.code})."))
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                val content = root.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")
                    ?.let { CaptionSegmentNormalizer.stripJsonMarkdownFences(it) }
                    ?: return@withContext Result.failure(IllegalStateException("Empty translation response."))
                val arr = JSONObject(content).optJSONArray("segments")
                    ?: return@withContext Result.failure(IllegalStateException("No translated segments returned."))
                val updated = captions.mapIndexed { idx, cap ->
                    val newText = arr.optJSONObject(idx)?.optString("text", "")?.trim().orEmpty()
                    if (newText.isNotEmpty()) cap.copy(text = newText) else cap
                }
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Caption conversion error: ${e.localizedMessage}"))
        }
    }

    private fun buildSegmentsJsonSchema(): JSONObject = JSONObject()
        .put("type", "OBJECT")
        .put(
            "properties",
            JSONObject().put(
                "segments",
                JSONObject()
                    .put("type", "ARRAY")
                    .put(
                        "items",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put(
                                "properties",
                                JSONObject()
                                    .put("startMs", JSONObject().put("type", "INTEGER"))
                                    .put("endMs", JSONObject().put("type", "INTEGER"))
                                    .put("text", JSONObject().put("type", "STRING"))
                            )
                            .put("required", JSONArray().put("startMs").put("endMs").put("text"))
                    )
            )
        )
        .put("required", JSONArray().put("segments"))

    private suspend fun transcribeWithWhisperCompatible(
        endpointUrl: String,
        modelName: String,
        providerLabel: String,
        apiKey: String,
        payload: ExtractedAudioPayload,
        clipStartOnTimelineMs: Long,
        languageHint: String,
        maxWordsPerLine: Int
    ): Result<List<CaptionClip>> = withContext(Dispatchers.IO) {
        try {
            val fileName = "lumacut_audio.${payload.fileExtension}"
            val mediaType = payload.mimeType.toMediaType()
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, payload.bytes.toRequestBody(mediaType))
                .addFormDataPart("model", modelName)
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("temperature", "0")

            val isoCode = OfflineLanguageCatalog.isoCodeForName(languageHint)
            if (!isoCode.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("language", isoCode)
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(multipartBuilder.build())
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errDetail = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    return@withContext Result.failure(
                        IllegalStateException(
                            "$providerLabel failed (HTTP ${resp.code}): ${errDetail.ifBlank { "check your API key" }}"
                        )
                    )
                }

                val root = JSONObject(rawBody)
                val rawSegments = mutableListOf<RawCaptionSegment>()
                val segmentsArr = root.optJSONArray("segments")
                if (segmentsArr != null && segmentsArr.length() > 0) {
                    for (i in 0 until segmentsArr.length()) {
                        val seg = segmentsArr.optJSONObject(i) ?: continue
                        val txt = seg.optString("text", "").trim()
                        if (txt.isEmpty()) continue
                        val startMs = (seg.optDouble("start", 0.0) * 1000.0).toLong()
                        val endMs = (seg.optDouble("end", (startMs + 1500L) / 1000.0) * 1000.0).toLong()
                        rawSegments.add(RawCaptionSegment(startMs, endMs, txt))
                    }
                } else {
                    val fullText = root.optString("text", "").trim()
                    if (fullText.isNotEmpty()) {
                        rawSegments.add(RawCaptionSegment(0L, payload.durationMs, fullText))
                    }
                }

                val normalized = CaptionSegmentNormalizer.normalize(
                    rawSegments = rawSegments,
                    timelineOffsetMs = clipStartOnTimelineMs,
                    clipDurationMs = payload.durationMs,
                    maxWordsPerLine = maxWordsPerLine
                )
                if (normalized.isEmpty()) {
                    Result.failure(IllegalStateException("No speech detected by $providerLabel."))
                } else {
                    Result.success(normalized)
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("$providerLabel error: ${e.localizedMessage ?: "network error"}"))
        }
    }
}
