package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.security.ClonedVoiceProfile
import com.example.security.OnlineVoiceCloneProvider
import com.example.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Online AI Voice Cloning & Neural Text-to-Speech client powered by user-supplied API keys
 * stored encrypted in [SecureCredentialStore] (Android Keystore AES-256-GCM).
 *
 * Supports:
 * - ElevenLabs: Instant Voice Clone (`POST /v1/voices/add`), list voices (`GET /v1/voices`),
 *   Multilingual v2 TTS (`POST /v1/text-to-speech/{voice_id}`).
 * - OpenAI TTS: Studio Neural TTS (`POST /v1/audio/speech` with `tts-1-hd`).
 * - Cartesia Sonic: Instant Voice Clone (`POST /voices/clone`) & Sonic-2 TTS (`POST /tts/bytes`).
 * - Custom HTTPS OpenAI-compatible `/v1/audio/speech` endpoints (strict HTTPS + public host checks).
 */
object OnlineVoiceCloneTtsEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val openAiBuiltInVoices: List<ClonedVoiceProfile> = listOf(
        ClonedVoiceProfile("openai_alloy", "Alloy (Balanced)", OnlineVoiceCloneProvider.OPENAI_TTS, "alloy", isCloned = false, subtitle = "Neutral crisp studio"),
        ClonedVoiceProfile("openai_nova", "Nova (Warm Female)", OnlineVoiceCloneProvider.OPENAI_TTS, "nova", isCloned = false, subtitle = "Natural storytelling"),
        ClonedVoiceProfile("openai_onyx", "Onyx (Deep Male)", OnlineVoiceCloneProvider.OPENAI_TTS, "onyx", isCloned = false, subtitle = "Trailer & documentary"),
        ClonedVoiceProfile("openai_echo", "Echo (Smooth Male)", OnlineVoiceCloneProvider.OPENAI_TTS, "echo", isCloned = false, subtitle = "Podcast narration"),
        ClonedVoiceProfile("openai_fable", "Fable (Expressive)", OnlineVoiceCloneProvider.OPENAI_TTS, "fable", isCloned = false, subtitle = "Dynamic creator voice"),
        ClonedVoiceProfile("openai_shimmer", "Shimmer (Bright)", OnlineVoiceCloneProvider.OPENAI_TTS, "shimmer", isCloned = false, subtitle = "Energetic Shorts host")
    )

    val elevenLabsDefaultVoices: List<ClonedVoiceProfile> = listOf(
        ClonedVoiceProfile("el_rachel", "Rachel (Studio)", OnlineVoiceCloneProvider.ELEVENLABS, "21m00Tcm4TlvDq8ikWAM", isCloned = false, subtitle = "Calm narrative"),
        ClonedVoiceProfile("el_adam", "Adam (Deep)", OnlineVoiceCloneProvider.ELEVENLABS, "pNInz6obpgDQGcFmaJgB", isCloned = false, subtitle = "Cinema narrator"),
        ClonedVoiceProfile("el_bella", "Bella (Warm)", OnlineVoiceCloneProvider.ELEVENLABS, "EXAVITQu4vr4xnSDxMaL", isCloned = false, subtitle = "Conversational")
    )

    /**
     * Validates a user-supplied custom HTTPS endpoint URL to prevent cleartext transport
     * and block loopback / private / link-local hosts.
     */
    fun validateCustomHttpsEndpoint(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid HTTPS endpoint URL."))
        if (!parsed.isHttps) {
            return Result.failure(IllegalArgumentException("Custom TTS endpoint must use HTTPS."))
        }
        val host = parsed.host.lowercase(Locale.US)
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
            return Result.failure(IllegalArgumentException("Private or local hostnames are not allowed."))
        }
        if (isPrivateOrLoopbackHost(host)) {
            return Result.failure(IllegalArgumentException("Private, loopback, or metadata IP addresses are blocked."))
        }
        return Result.success(parsed.toString())
    }

    private fun isPrivateOrLoopbackHost(host: String): Boolean {
        val ipv4Match = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").matchEntire(host)
        if (ipv4Match != null) {
            val a = ipv4Match.groupValues[1].toIntOrNull() ?: return true
            val b = ipv4Match.groupValues[2].toIntOrNull() ?: return true
            if (a == 127 || a == 10 || a == 0) return true
            if (a == 169 && b == 254) return true
            if (a == 172 && b in 16..31) return true
            if (a == 192 && b == 168) return true
            return false
        }
        if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) {
            return true
        }
        return runCatching {
            val addrs = InetAddress.getAllByName(host)
            addrs.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
        }.getOrDefault(false)
    }

    suspend fun verifyApiKey(
        provider: OnlineVoiceCloneProvider,
        rawApiKey: String,
        customEndpointUrl: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = rawApiKey.trim()
        if (key.length < 8) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid API key first."))
        }
        if (provider == OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS) {
            val validUrl = validateCustomHttpsEndpoint(customEndpointUrl).getOrElse {
                return@withContext Result.failure(it)
            }
            return@withContext Result.success("HTTPS endpoint saved ($validUrl)")
        }

        try {
            val req = when (provider) {
                OnlineVoiceCloneProvider.ELEVENLABS -> Request.Builder()
                    .url("https://api.elevenlabs.io/v1/voices")
                    .addHeader("xi-api-key", key)
                    .get()
                    .build()

                OnlineVoiceCloneProvider.OPENAI_TTS -> Request.Builder()
                    .url("https://api.openai.com/v1/models")
                    .addHeader("Authorization", "Bearer $key")
                    .get()
                    .build()

                OnlineVoiceCloneProvider.CARTESIA -> Request.Builder()
                    .url("https://api.cartesia.ai/voices")
                    .addHeader("X-API-Key", key)
                    .addHeader("Cartesia-Version", "2024-06-10")
                    .get()
                    .build()

                OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS -> return@withContext Result.success("Saved")
            }

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Connected to ${provider.displayName}")
                } else {
                    val hint = when (resp.code) {
                        401, 403 -> "Invalid or unauthorized API key (HTTP ${resp.code})."
                        429 -> "Key valid, but rate-limited (HTTP 429)."
                        else -> "${provider.shortBadge} returned HTTP ${resp.code}."
                    }
                    Result.failure(IllegalStateException(hint))
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Network verification failed: ${e.localizedMessage ?: "check internet"}"))
        }
    }

    suspend fun fetchAvailableVoices(
        context: Context,
        provider: OnlineVoiceCloneProvider
    ): Result<List<ClonedVoiceProfile>> = withContext(Dispatchers.IO) {
        val store = SecureCredentialStore.getInstance(context)
        val savedLocal = store.voiceCloneState.value.savedClonedVoices.filter { it.provider == provider }
        val apiKey = store.getVoiceCloneApiKey(provider)

        when (provider) {
            OnlineVoiceCloneProvider.OPENAI_TTS -> Result.success(savedLocal + openAiBuiltInVoices)
            OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS -> {
                val defaults = listOf(
                    ClonedVoiceProfile("custom_default", "Default Server Voice", provider, "alloy", isCloned = false, subtitle = "Server voice ID")
                )
                Result.success((savedLocal + defaults).distinctBy { it.remoteVoiceId })
            }
            OnlineVoiceCloneProvider.ELEVENLABS -> {
                if (apiKey.isNullOrBlank()) {
                    return@withContext Result.success((savedLocal + elevenLabsDefaultVoices).distinctBy { it.remoteVoiceId })
                }
                try {
                    val req = Request.Builder()
                        .url("https://api.elevenlabs.io/v1/voices")
                        .addHeader("xi-api-key", apiKey)
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            return@withContext Result.failure(IllegalStateException("ElevenLabs voice list failed (HTTP ${resp.code})."))
                        }
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val arr = root.optJSONArray("voices")
                        val remote = mutableListOf<ClonedVoiceProfile>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val vid = obj.optString("voice_id", "").trim()
                                val name = obj.optString("name", "").trim()
                                if (vid.isEmpty() || name.isEmpty()) continue
                                val category = obj.optString("category", "premade")
                                remote.add(
                                    ClonedVoiceProfile(
                                        id = "el_$vid",
                                        name = name,
                                        provider = provider,
                                        remoteVoiceId = vid,
                                        isCloned = category.equals("cloned", ignoreCase = true) || category.equals("generated", ignoreCase = true),
                                        subtitle = category.replaceFirstChar { it.uppercase() }
                                    )
                                )
                            }
                        }
                        val merged = (savedLocal + remote.sortedByDescending { it.isCloned })
                            .distinctBy { it.remoteVoiceId }
                            .take(30)
                        Result.success(merged.ifEmpty { elevenLabsDefaultVoices })
                    }
                } catch (e: Exception) {
                    Result.failure(IllegalStateException("Could not load ElevenLabs voices: ${e.localizedMessage}"))
                }
            }
            OnlineVoiceCloneProvider.CARTESIA -> {
                if (apiKey.isNullOrBlank()) return@withContext Result.success(savedLocal)
                try {
                    val req = Request.Builder()
                        .url("https://api.cartesia.ai/voices")
                        .addHeader("X-API-Key", apiKey)
                        .addHeader("Cartesia-Version", "2024-06-10")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            return@withContext Result.failure(IllegalStateException("Cartesia voice list failed (HTTP ${resp.code})."))
                        }
                        val arr = org.json.JSONArray(resp.body?.string().orEmpty())
                        val remote = mutableListOf<ClonedVoiceProfile>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val vid = obj.optString("id", "").trim()
                            val name = obj.optString("name", "").trim()
                            if (vid.isEmpty() || name.isEmpty()) continue
                            remote.add(
                                ClonedVoiceProfile(
                                    id = "car_$vid",
                                    name = name,
                                    provider = provider,
                                    remoteVoiceId = vid,
                                    isCloned = !obj.optBoolean("is_public", true),
                                    subtitle = obj.optString("language", "multilingual")
                                )
                            )
                        }
                        Result.success((savedLocal + remote).distinctBy { it.remoteVoiceId }.take(30))
                    }
                } catch (e: Exception) {
                    Result.failure(IllegalStateException("Could not load Cartesia voices: ${e.localizedMessage}"))
                }
            }
        }
    }

    /**
     * Uploads a clean audio voice sample (from a selected audio file OR extracted from a timeline clip)
     * to ElevenLabs or Cartesia Instant Voice Cloning and stores the resulting cloned voice profile.
     */
    suspend fun cloneVoiceFromAudioBytes(
        context: Context,
        provider: OnlineVoiceCloneProvider,
        voiceName: String,
        audioBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<ClonedVoiceProfile> = withContext(Dispatchers.IO) {
        val cleanName = voiceName.trim().ifBlank { "My Cloned Voice" }
        if (audioBytes.size < 1024) {
            return@withContext Result.failure(IllegalArgumentException("Voice sample is too short. Provide at least a few seconds of clear speech."))
        }
        val store = SecureCredentialStore.getInstance(context)
        val apiKey = store.getVoiceCloneApiKey(provider)
            ?: return@withContext Result.failure(IllegalStateException("Save your ${provider.shortBadge} API key first."))

        try {
            when (provider) {
                OnlineVoiceCloneProvider.ELEVENLABS -> {
                    val body = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("name", cleanName)
                        .addFormDataPart("description", "Cloned via LumaCut Studio")
                        .addFormDataPart(
                            "files",
                            fileName,
                            audioBytes.toRequestBody(mimeType.toMediaType())
                        )
                        .build()
                    val req = Request.Builder()
                        .url("https://api.elevenlabs.io/v1/voices/add")
                        .addHeader("xi-api-key", apiKey)
                        .post(body)
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            val detail = extractErrorMessage(raw)
                            return@withContext Result.failure(
                                IllegalStateException("ElevenLabs clone failed (HTTP ${resp.code}): ${detail.ifBlank { "check plan/key" }}")
                            )
                        }
                        val vid = JSONObject(raw).optString("voice_id", "").trim()
                        if (vid.isEmpty()) {
                            return@withContext Result.failure(IllegalStateException("ElevenLabs did not return a voice_id."))
                        }
                        val profile = ClonedVoiceProfile(
                            id = "el_$vid",
                            name = cleanName,
                            provider = provider,
                            remoteVoiceId = vid,
                            isCloned = true,
                            subtitle = "Instant Clone"
                        )
                        store.saveClonedVoiceProfile(profile)
                        Result.success(profile)
                    }
                }

                OnlineVoiceCloneProvider.CARTESIA -> {
                    val body = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("name", cleanName)
                        .addFormDataPart("description", "Cloned via LumaCut Studio")
                        .addFormDataPart("mode", "similarity")
                        .addFormDataPart(
                            "clip",
                            fileName,
                            audioBytes.toRequestBody(mimeType.toMediaType())
                        )
                        .build()
                    val req = Request.Builder()
                        .url("https://api.cartesia.ai/voices/clone")
                        .addHeader("X-API-Key", apiKey)
                        .addHeader("Cartesia-Version", "2024-06-10")
                        .post(body)
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            val detail = extractErrorMessage(raw)
                            return@withContext Result.failure(
                                IllegalStateException("Cartesia clone failed (HTTP ${resp.code}): ${detail.ifBlank { "check key" }}")
                            )
                        }
                        val vid = JSONObject(raw).optString("id", "").trim()
                        if (vid.isEmpty()) {
                            return@withContext Result.failure(IllegalStateException("Cartesia did not return a voice id."))
                        }
                        val profile = ClonedVoiceProfile(
                            id = "car_$vid",
                            name = cleanName,
                            provider = provider,
                            remoteVoiceId = vid,
                            isCloned = true,
                            subtitle = "Sonic Clone"
                        )
                        store.saveClonedVoiceProfile(profile)
                        Result.success(profile)
                    }
                }

                else -> Result.failure(
                    IllegalStateException("${provider.displayName} does not support sample uploads. Switch to ElevenLabs or Cartesia for instant voice cloning.")
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Voice cloning error: ${e.localizedMessage ?: "network error"}"))
        }
    }

    suspend fun readAudioSampleBytes(context: Context, uri: Uri): Result<Triple<ByteArray, String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val maxBytes = 10 * 1024 * 1024 // 10MB cap for fast upload
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size > maxBytes) data.copyOf(maxBytes) else data
                } ?: return@withContext Result.failure(IllegalStateException("Could not read selected audio sample."))
                val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
                val ext = when {
                    mime.contains("wav") -> "wav"
                    mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
                    mime.contains("ogg") -> "ogg"
                    else -> "mp3"
                }
                Result.success(Triple(bytes, "voice_sample.$ext", mime))
            } catch (e: Exception) {
                Result.failure(IllegalStateException("Failed to read audio sample: ${e.localizedMessage}"))
            }
        }

    /**
     * Synthesizes speech from [text] using the chosen online AI voice clone / TTS provider
     * and writes the resulting audio file directly to LumaCut's private media directory for the timeline.
     */
    suspend fun synthesizeSpeechToTimeline(
        context: Context,
        provider: OnlineVoiceCloneProvider,
        voiceId: String,
        voiceLabel: String,
        text: String,
        languageDisplayName: String = "Auto",
        stability: Float = 0.50f,
        similarityBoost: Float = 0.78f,
        speed: Float = 1.0f
    ): Result<SynthesizedVoiceTrack> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter narration text or pull captions first."))
        }
        val store = SecureCredentialStore.getInstance(context)
        val apiKey = store.getVoiceCloneApiKey(provider)
            ?: return@withContext Result.failure(IllegalStateException("Enter and save your ${provider.shortBadge} API key first."))

        val audioDir = File(context.filesDir, "lumacut_media/audio").apply { mkdirs() }
        val ext = if (provider == OnlineVoiceCloneProvider.CARTESIA) "wav" else "mp3"
        val outFile = File(audioDir, "ai_clone_${System.currentTimeMillis()}.$ext")

        try {
            val request = when (provider) {
                OnlineVoiceCloneProvider.ELEVENLABS -> {
                    val safeVoiceId = voiceId.trim().ifBlank { elevenLabsDefaultVoices.first().remoteVoiceId }
                    val payload = JSONObject()
                        .put("text", cleanText)
                        .put("model_id", "eleven_multilingual_v2")
                        .put(
                            "voice_settings",
                            JSONObject()
                                .put("stability", stability.coerceIn(0.1f, 1.0f).toDouble())
                                .put("similarity_boost", similarityBoost.coerceIn(0.1f, 1.0f).toDouble())
                                .put("speed", speed.coerceIn(0.7f, 1.2f).toDouble())
                        )
                    Request.Builder()
                        .url("https://api.elevenlabs.io/v1/text-to-speech/$safeVoiceId")
                        .addHeader("xi-api-key", apiKey)
                        .addHeader("Accept", "audio/mpeg")
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                }

                OnlineVoiceCloneProvider.OPENAI_TTS -> {
                    val safeVoice = voiceId.trim().ifBlank { "nova" }
                    val payload = JSONObject()
                        .put("model", "tts-1-hd")
                        .put("voice", safeVoice)
                        .put("input", cleanText)
                        .put("speed", speed.coerceIn(0.5f, 2.0f).toDouble())
                        .put("response_format", "mp3")
                    Request.Builder()
                        .url("https://api.openai.com/v1/audio/speech")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                }

                OnlineVoiceCloneProvider.CARTESIA -> {
                    val safeVoice = voiceId.trim()
                    if (safeVoice.isEmpty()) {
                        return@withContext Result.failure(IllegalArgumentException("Select or clone a Cartesia voice first."))
                    }
                    val isoLang = OfflineLanguageCatalog.isoCodeForName(languageDisplayName) ?: "en"
                    val payload = JSONObject()
                        .put("model_id", "sonic-2")
                        .put("transcript", cleanText)
                        .put("language", isoLang)
                        .put("voice", JSONObject().put("mode", "id").put("id", safeVoice))
                        .put(
                            "output_format",
                            JSONObject()
                                .put("container", "wav")
                                .put("encoding", "pcm_s16le")
                                .put("sample_rate", 44100)
                        )
                    Request.Builder()
                        .url("https://api.cartesia.ai/tts/bytes")
                        .addHeader("X-API-Key", apiKey)
                        .addHeader("Cartesia-Version", "2024-06-10")
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                }

                OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS -> {
                    val validUrl = validateCustomHttpsEndpoint(store.getCustomTtsEndpointUrl()).getOrElse {
                        return@withContext Result.failure(it)
                    }
                    val payload = JSONObject()
                        .put("model", "tts-1")
                        .put("voice", voiceId.trim().ifBlank { "alloy" })
                        .put("input", cleanText)
                        .put("speed", speed.coerceIn(0.5f, 2.0f).toDouble())
                        .put("response_format", "mp3")
                    Request.Builder()
                        .url(validUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                }
            }

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errRaw = resp.body?.string().orEmpty()
                    val detail = extractErrorMessage(errRaw)
                    return@withContext Result.failure(
                        IllegalStateException("${provider.shortBadge} TTS failed (HTTP ${resp.code}): ${detail.ifBlank { "check key & voice ID" }}")
                    )
                }
                val bytes = resp.body?.bytes()
                if (bytes == null || bytes.size < 256) {
                    return@withContext Result.failure(IllegalStateException("${provider.shortBadge} returned empty audio."))
                }
                outFile.writeBytes(bytes)
            }

            val durationMs = measureDurationMs(outFile).coerceAtLeast(600L)
            val uri = Uri.fromFile(outFile)
            val peaks = runCatching {
                AudioWaveformExtractor.extract(context, uri, durationMs, 64).peaks
            }.getOrDefault(List(48) { 0.5f })

            val snippet = cleanText.take(20).replace('\n', ' ').trim()
            Result.success(
                SynthesizedVoiceTrack(
                    file = outFile,
                    uriString = uri.toString(),
                    displayName = "${provider.shortBadge} ($voiceLabel): $snippet",
                    durationMs = durationMs,
                    waveformPeaks = peaks
                )
            )
        } catch (e: Exception) {
            outFile.delete()
            Result.failure(IllegalStateException("Online AI voice error: ${e.localizedMessage ?: "network error"}"))
        }
    }

    private fun extractErrorMessage(raw: String): String = runCatching {
        val obj = JSONObject(raw)
        obj.optJSONObject("detail")?.optString("message")
            ?: obj.optString("detail").takeIf { it.isNotBlank() && !it.startsWith("{") }
            ?: obj.optJSONObject("error")?.optString("message")
            ?: obj.optString("message")
            ?: ""
    }.getOrDefault("").trim()

    private fun measureDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2500L
        } catch (_: Exception) {
            2500L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
