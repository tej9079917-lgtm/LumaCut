package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiCaptionProvider(
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val keyHint: String,
    val signupUrl: String,
    val requiresUserKey: Boolean
) {
    FIREBASE_BUILTIN(
        displayName = "LumaCut Built-in Gemini",
        shortBadge = "Built-in Gemini",
        description = "Uses bundled Firebase AI Logic (no key required when signed & attested).",
        keyHint = "",
        signupUrl = "",
        requiresUserKey = false
    ),
    GEMINI_API_KEY(
        displayName = "Personal Gemini Account",
        shortBadge = "Personal Gemini",
        description = "Sign in with your Google AI Studio Gemini key — works on any phone & sideloaded APK.",
        keyHint = "AIzaSy...",
        signupUrl = "https://aistudio.google.com/apikey",
        requiresUserKey = true
    ),
    GROQ_WHISPER(
        displayName = "Groq Whisper v3 Turbo",
        shortBadge = "Groq Whisper",
        description = "Ultra-fast Whisper Large v3 Turbo supporting 99+ languages (Telugu, Hindi, Tamil, etc.).",
        keyHint = "gsk_...",
        signupUrl = "https://console.groq.com/keys",
        requiresUserKey = true
    ),
    OPENAI_WHISPER(
        displayName = "OpenAI Whisper",
        shortBadge = "OpenAI Whisper",
        description = "Studio-grade multilingual Whisper speech transcription via your OpenAI account.",
        keyHint = "sk-...",
        signupUrl = "https://platform.openai.com/api-keys",
        requiresUserKey = true
    );

    companion object {
        fun fromStored(name: String?): AiCaptionProvider =
            entries.firstOrNull { it.name == name } ?: FIREBASE_BUILTIN
    }
}

data class AiCredentialState(
    val activeProvider: AiCaptionProvider = AiCaptionProvider.FIREBASE_BUILTIN,
    val hasGeminiKey: Boolean = false,
    val hasGroqKey: Boolean = false,
    val hasOpenAiKey: Boolean = false,
    val maskedActiveKey: String = ""
) {
    fun hasKeyFor(provider: AiCaptionProvider): Boolean = when (provider) {
        AiCaptionProvider.FIREBASE_BUILTIN -> true
        AiCaptionProvider.GEMINI_API_KEY -> hasGeminiKey
        AiCaptionProvider.GROQ_WHISPER -> hasGroqKey
        AiCaptionProvider.OPENAI_WHISPER -> hasOpenAiKey
    }
}

data class YouTubeAccountState(
    val isSignedIn: Boolean = false,
    val channelId: String = "",
    val channelTitle: String = "",
    val channelHandle: String = "",
    val avatarUrl: String = "",
    val subscriberCount: String = "",
    val hasOAuthToken: Boolean = false,
    val hasDataApiKey: Boolean = false
)

enum class OnlineVoiceCloneProvider(
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val keyHint: String,
    val signupUrl: String,
    val supportsInstantClone: Boolean
) {
    ELEVENLABS(
        displayName = "ElevenLabs Voice Clone",
        shortBadge = "ElevenLabs",
        description = "Instant voice cloning from a short audio sample + multilingual neural TTS.",
        keyHint = "sk_...",
        signupUrl = "https://elevenlabs.io/app/settings/api-keys",
        supportsInstantClone = true
    ),
    OPENAI_TTS(
        displayName = "OpenAI Neural TTS",
        shortBadge = "OpenAI TTS",
        description = "Expressive studio TTS (`tts-1-hd`) using your OpenAI API key.",
        keyHint = "sk-...",
        signupUrl = "https://platform.openai.com/api-keys",
        supportsInstantClone = false
    ),
    CARTESIA(
        displayName = "Cartesia Sonic Clone",
        shortBadge = "Cartesia",
        description = "Ultra-fast Sonic-2 voice cloning & multilingual text-to-speech.",
        keyHint = "sk_car_...",
        signupUrl = "https://play.cartesia.ai/keys",
        supportsInstantClone = true
    ),
    CUSTOM_HTTPS_TTS(
        displayName = "Custom HTTPS TTS API",
        shortBadge = "Custom TTS",
        description = "Any HTTPS OpenAI-compatible `/v1/audio/speech` voice clone server.",
        keyHint = "Bearer token / API key",
        signupUrl = "",
        supportsInstantClone = false
    );

    companion object {
        fun fromStored(name: String?): OnlineVoiceCloneProvider =
            entries.firstOrNull { it.name == name } ?: ELEVENLABS
    }
}

data class ClonedVoiceProfile(
    val id: String,
    val name: String,
    val provider: OnlineVoiceCloneProvider,
    val remoteVoiceId: String,
    val isCloned: Boolean = true,
    val subtitle: String = ""
)

data class VoiceCloneCredentialState(
    val activeProvider: OnlineVoiceCloneProvider = OnlineVoiceCloneProvider.ELEVENLABS,
    val hasElevenLabsKey: Boolean = false,
    val hasOpenAiTtsKey: Boolean = false,
    val hasCartesiaKey: Boolean = false,
    val hasCustomTtsKey: Boolean = false,
    val customEndpointUrl: String = "",
    val maskedActiveKey: String = "",
    val savedClonedVoices: List<ClonedVoiceProfile> = emptyList()
) {
    fun hasKeyFor(provider: OnlineVoiceCloneProvider): Boolean = when (provider) {
        OnlineVoiceCloneProvider.ELEVENLABS -> hasElevenLabsKey
        OnlineVoiceCloneProvider.OPENAI_TTS -> hasOpenAiTtsKey
        OnlineVoiceCloneProvider.CARTESIA -> hasCartesiaKey
        OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS -> hasCustomTtsKey && customEndpointUrl.startsWith("https://")
    }
}

/**
 * Hardware-backed Android Keystore (AES-256-GCM) credential vault for end-user
 * AI provider login keys, Online AI Voice Clone API keys, and YouTube OAuth tokens.
 * Never hardcodes or logs secrets.
 */
class SecureCredentialStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _aiState = MutableStateFlow(readAiState())
    val aiState: StateFlow<AiCredentialState> = _aiState.asStateFlow()

    private val _youtubeState = MutableStateFlow(readYouTubeState())
    val youtubeState: StateFlow<YouTubeAccountState> = _youtubeState.asStateFlow()

    private val _voiceCloneState = MutableStateFlow(readVoiceCloneState())
    val voiceCloneState: StateFlow<VoiceCloneCredentialState> = _voiceCloneState.asStateFlow()

    fun setActiveAiProvider(provider: AiCaptionProvider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
        _aiState.value = readAiState()
    }

    fun saveAiApiKey(provider: AiCaptionProvider, rawKey: String) {
        val clean = rawKey.trim()
        val prefKey = prefKeyFor(provider) ?: return
        if (clean.isEmpty()) {
            prefs.edit().remove(prefKey).apply()
        } else {
            val encrypted = encrypt(clean)
            prefs.edit()
                .putString(prefKey, encrypted)
                .putString(KEY_ACTIVE_PROVIDER, provider.name)
                .apply()
        }
        _aiState.value = readAiState()
        _voiceCloneState.value = readVoiceCloneState()
    }

    fun clearAiApiKey(provider: AiCaptionProvider) {
        val prefKey = prefKeyFor(provider) ?: return
        val editor = prefs.edit().remove(prefKey)
        if (_aiState.value.activeProvider == provider) {
            editor.putString(KEY_ACTIVE_PROVIDER, AiCaptionProvider.FIREBASE_BUILTIN.name)
        }
        editor.apply()
        _aiState.value = readAiState()
        _voiceCloneState.value = readVoiceCloneState()
    }

    fun getAiApiKey(provider: AiCaptionProvider): String? {
        val prefKey = prefKeyFor(provider) ?: return null
        val cipherBlob = prefs.getString(prefKey, null) ?: return null
        return decrypt(cipherBlob)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ---- Online AI Voice Clone & TTS Credential Management ----

    fun setActiveVoiceCloneProvider(provider: OnlineVoiceCloneProvider) {
        prefs.edit().putString(KEY_ACTIVE_VOICE_PROVIDER, provider.name).apply()
        _voiceCloneState.value = readVoiceCloneState()
    }

    fun saveVoiceCloneApiKey(
        provider: OnlineVoiceCloneProvider,
        rawKey: String,
        customEndpointUrl: String? = null
    ) {
        val clean = rawKey.trim()
        val prefKey = voicePrefKeyFor(provider)
        val editor = prefs.edit()
        if (clean.isEmpty()) {
            editor.remove(prefKey)
        } else {
            editor.putString(prefKey, encrypt(clean))
            editor.putString(KEY_ACTIVE_VOICE_PROVIDER, provider.name)
        }
        if (provider == OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS && customEndpointUrl != null) {
            editor.putString(KEY_CUSTOM_TTS_URL, customEndpointUrl.trim())
        }
        editor.apply()
        _voiceCloneState.value = readVoiceCloneState()
    }

    fun clearVoiceCloneApiKey(provider: OnlineVoiceCloneProvider) {
        val editor = prefs.edit().remove(voicePrefKeyFor(provider))
        if (provider == OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS) {
            editor.remove(KEY_CUSTOM_TTS_URL)
        }
        editor.apply()
        _voiceCloneState.value = readVoiceCloneState()
    }

    fun getVoiceCloneApiKey(provider: OnlineVoiceCloneProvider): String? {
        val direct = prefs.getString(voicePrefKeyFor(provider), null)
            ?.let { decrypt(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (direct != null) return direct
        // Allow OpenAI TTS to seamlessly reuse the user's already-saved OpenAI Whisper key
        if (provider == OnlineVoiceCloneProvider.OPENAI_TTS) {
            return getAiApiKey(AiCaptionProvider.OPENAI_WHISPER)
        }
        return null
    }

    fun getCustomTtsEndpointUrl(): String =
        prefs.getString(KEY_CUSTOM_TTS_URL, "").orEmpty().trim()

    fun saveClonedVoiceProfile(profile: ClonedVoiceProfile) {
        val existing = readSavedClonedVoices().filterNot {
            it.provider == profile.provider && it.remoteVoiceId == profile.remoteVoiceId
        }
        val updated = (listOf(profile) + existing).take(24)
        persistClonedVoices(updated)
        _voiceCloneState.value = readVoiceCloneState()
    }

    fun removeClonedVoiceProfile(provider: OnlineVoiceCloneProvider, remoteVoiceId: String) {
        val updated = readSavedClonedVoices().filterNot {
            it.provider == provider && it.remoteVoiceId == remoteVoiceId
        }
        persistClonedVoices(updated)
        _voiceCloneState.value = readVoiceCloneState()
    }

    private fun readSavedClonedVoices(): List<ClonedVoiceProfile> {
        val raw = prefs.getString(KEY_CLONED_VOICES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val vid = obj.optString("remoteVoiceId", "").trim()
                val name = obj.optString("name", "").trim()
                if (vid.isEmpty() || name.isEmpty()) return@mapNotNull null
                ClonedVoiceProfile(
                    id = obj.optString("id", vid),
                    name = name,
                    provider = OnlineVoiceCloneProvider.fromStored(obj.optString("provider")),
                    remoteVoiceId = vid,
                    isCloned = obj.optBoolean("isCloned", true),
                    subtitle = obj.optString("subtitle", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistClonedVoices(voices: List<ClonedVoiceProfile>) {
        val arr = org.json.JSONArray()
        voices.forEach { v ->
            arr.put(
                org.json.JSONObject()
                    .put("id", v.id)
                    .put("name", v.name)
                    .put("provider", v.provider.name)
                    .put("remoteVoiceId", v.remoteVoiceId)
                    .put("isCloned", v.isCloned)
                    .put("subtitle", v.subtitle)
            )
        }
        prefs.edit().putString(KEY_CLONED_VOICES_JSON, arr.toString()).apply()
    }

    private fun readVoiceCloneState(): VoiceCloneCredentialState {
        val active = OnlineVoiceCloneProvider.fromStored(prefs.getString(KEY_ACTIVE_VOICE_PROVIDER, null))
        val elKey = getVoiceCloneApiKey(OnlineVoiceCloneProvider.ELEVENLABS)
        val oaKey = getVoiceCloneApiKey(OnlineVoiceCloneProvider.OPENAI_TTS)
        val cartKey = getVoiceCloneApiKey(OnlineVoiceCloneProvider.CARTESIA)
        val customKey = getVoiceCloneApiKey(OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS)
        val customUrl = getCustomTtsEndpointUrl()
        val activeRaw = when (active) {
            OnlineVoiceCloneProvider.ELEVENLABS -> elKey.orEmpty()
            OnlineVoiceCloneProvider.OPENAI_TTS -> oaKey.orEmpty()
            OnlineVoiceCloneProvider.CARTESIA -> cartKey.orEmpty()
            OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS -> customKey.orEmpty()
        }
        return VoiceCloneCredentialState(
            activeProvider = active,
            hasElevenLabsKey = !elKey.isNullOrEmpty(),
            hasOpenAiTtsKey = !oaKey.isNullOrEmpty(),
            hasCartesiaKey = !cartKey.isNullOrEmpty(),
            hasCustomTtsKey = !customKey.isNullOrEmpty(),
            customEndpointUrl = customUrl,
            maskedActiveKey = maskSecret(activeRaw),
            savedClonedVoices = readSavedClonedVoices()
        )
    }

    private fun voicePrefKeyFor(provider: OnlineVoiceCloneProvider): String = when (provider) {
        OnlineVoiceCloneProvider.ELEVENLABS -> KEY_ELEVENLABS_API_KEY
        OnlineVoiceCloneProvider.OPENAI_TTS -> KEY_OPENAI_TTS_API_KEY
        OnlineVoiceCloneProvider.CARTESIA -> KEY_CARTESIA_API_KEY
        OnlineVoiceCloneProvider.CUSTOM_HTTPS_TTS -> KEY_CUSTOM_TTS_API_KEY
    }

    fun saveYouTubeSession(
        accessToken: String,
        channelId: String,
        channelTitle: String,
        channelHandle: String,
        avatarUrl: String,
        subscriberCount: String
    ) {
        val cleanToken = accessToken.trim()
        if (cleanToken.isEmpty()) return
        prefs.edit()
            .putString(KEY_YT_OAUTH_TOKEN, encrypt(cleanToken))
            .putString(KEY_YT_CHANNEL_ID, channelId)
            .putString(KEY_YT_CHANNEL_TITLE, channelTitle)
            .putString(KEY_YT_CHANNEL_HANDLE, channelHandle)
            .putString(KEY_YT_AVATAR_URL, avatarUrl)
            .putString(KEY_YT_SUBSCRIBER_COUNT, subscriberCount)
            .apply()
        _youtubeState.value = readYouTubeState()
    }

    fun saveYouTubeDataApiKey(rawKey: String) {
        val clean = rawKey.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_YT_DATA_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_YT_DATA_API_KEY, encrypt(clean)).apply()
        }
        _youtubeState.value = readYouTubeState()
    }

    fun getYouTubeOAuthToken(): String? {
        val blob = prefs.getString(KEY_YT_OAUTH_TOKEN, null) ?: return null
        return decrypt(blob)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getYouTubeDataApiKey(): String? {
        val blob = prefs.getString(KEY_YT_DATA_API_KEY, null) ?: return null
        return decrypt(blob)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun signOutYouTube() {
        prefs.edit()
            .remove(KEY_YT_OAUTH_TOKEN)
            .remove(KEY_YT_CHANNEL_ID)
            .remove(KEY_YT_CHANNEL_TITLE)
            .remove(KEY_YT_CHANNEL_HANDLE)
            .remove(KEY_YT_AVATAR_URL)
            .remove(KEY_YT_SUBSCRIBER_COUNT)
            .apply()
        _youtubeState.value = readYouTubeState()
    }

    private fun readAiState(): AiCredentialState {
        val active = AiCaptionProvider.fromStored(prefs.getString(KEY_ACTIVE_PROVIDER, null))
        val gemKey = getAiApiKey(AiCaptionProvider.GEMINI_API_KEY)
        val groqKey = getAiApiKey(AiCaptionProvider.GROQ_WHISPER)
        val openAiKey = getAiApiKey(AiCaptionProvider.OPENAI_WHISPER)
        val activeRaw = when (active) {
            AiCaptionProvider.FIREBASE_BUILTIN -> ""
            AiCaptionProvider.GEMINI_API_KEY -> gemKey.orEmpty()
            AiCaptionProvider.GROQ_WHISPER -> groqKey.orEmpty()
            AiCaptionProvider.OPENAI_WHISPER -> openAiKey.orEmpty()
        }
        return AiCredentialState(
            activeProvider = active,
            hasGeminiKey = !gemKey.isNullOrEmpty(),
            hasGroqKey = !groqKey.isNullOrEmpty(),
            hasOpenAiKey = !openAiKey.isNullOrEmpty(),
            maskedActiveKey = maskSecret(activeRaw)
        )
    }

    private fun readYouTubeState(): YouTubeAccountState {
        val token = getYouTubeOAuthToken()
        val dataKey = getYouTubeDataApiKey()
        val title = prefs.getString(KEY_YT_CHANNEL_TITLE, "").orEmpty()
        return YouTubeAccountState(
            isSignedIn = !token.isNullOrEmpty(),
            channelId = prefs.getString(KEY_YT_CHANNEL_ID, "").orEmpty(),
            channelTitle = title.ifBlank { if (!token.isNullOrEmpty()) "YouTube Creator" else "" },
            channelHandle = prefs.getString(KEY_YT_CHANNEL_HANDLE, "").orEmpty(),
            avatarUrl = prefs.getString(KEY_YT_AVATAR_URL, "").orEmpty(),
            subscriberCount = prefs.getString(KEY_YT_SUBSCRIBER_COUNT, "").orEmpty(),
            hasOAuthToken = !token.isNullOrEmpty(),
            hasDataApiKey = !dataKey.isNullOrEmpty()
        )
    }

    private fun prefKeyFor(provider: AiCaptionProvider): String? = when (provider) {
        AiCaptionProvider.FIREBASE_BUILTIN -> null
        AiCaptionProvider.GEMINI_API_KEY -> KEY_GEMINI_API_KEY
        AiCaptionProvider.GROQ_WHISPER -> KEY_GROQ_API_KEY
        AiCaptionProvider.OPENAI_WHISPER -> KEY_OPENAI_API_KEY
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    private fun decrypt(payload: String): String? = runCatching {
        val parts = payload.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "lumacut_secure_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "lumacut_vault_aes256_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_ACTIVE_PROVIDER = "active_ai_provider"
        private const val KEY_GEMINI_API_KEY = "enc_gemini_api_key"
        private const val KEY_GROQ_API_KEY = "enc_groq_api_key"
        private const val KEY_OPENAI_API_KEY = "enc_openai_api_key"

        private const val KEY_ACTIVE_VOICE_PROVIDER = "active_voice_clone_provider"
        private const val KEY_ELEVENLABS_API_KEY = "enc_elevenlabs_api_key"
        private const val KEY_OPENAI_TTS_API_KEY = "enc_openai_tts_api_key"
        private const val KEY_CARTESIA_API_KEY = "enc_cartesia_api_key"
        private const val KEY_CUSTOM_TTS_API_KEY = "enc_custom_tts_api_key"
        private const val KEY_CUSTOM_TTS_URL = "custom_tts_endpoint_url"
        private const val KEY_CLONED_VOICES_JSON = "cloned_voices_json"

        private const val KEY_YT_OAUTH_TOKEN = "enc_yt_oauth_token"
        private const val KEY_YT_DATA_API_KEY = "enc_yt_data_api_key"
        private const val KEY_YT_CHANNEL_ID = "yt_channel_id"
        private const val KEY_YT_CHANNEL_TITLE = "yt_channel_title"
        private const val KEY_YT_CHANNEL_HANDLE = "yt_channel_handle"
        private const val KEY_YT_AVATAR_URL = "yt_avatar_url"
        private const val KEY_YT_SUBSCRIBER_COUNT = "yt_subscriber_count"

        @Volatile
        private var INSTANCE: SecureCredentialStore? = null

        fun getInstance(context: Context): SecureCredentialStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureCredentialStore(context.applicationContext).also { INSTANCE = it }
            }

        fun maskSecret(secret: String): String {
            val s = secret.trim()
            if (s.isEmpty()) return ""
            if (s.length <= 8) return "••••${s.takeLast(2)}"
            return "${s.take(4)}••••••${s.takeLast(4)}"
        }
    }
}
