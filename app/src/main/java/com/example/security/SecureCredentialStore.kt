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

/**
 * Hardware-backed Android Keystore (AES-256-GCM) credential vault for end-user
 * AI provider login keys and YouTube OAuth tokens. Never hardcodes or logs secrets.
 */
class SecureCredentialStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _aiState = MutableStateFlow(readAiState())
    val aiState: StateFlow<AiCredentialState> = _aiState.asStateFlow()

    private val _youtubeState = MutableStateFlow(readYouTubeState())
    val youtubeState: StateFlow<YouTubeAccountState> = _youtubeState.asStateFlow()

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
    }

    fun clearAiApiKey(provider: AiCaptionProvider) {
        val prefKey = prefKeyFor(provider) ?: return
        val editor = prefs.edit().remove(prefKey)
        if (_aiState.value.activeProvider == provider) {
            editor.putString(KEY_ACTIVE_PROVIDER, AiCaptionProvider.FIREBASE_BUILTIN.name)
        }
        editor.apply()
        _aiState.value = readAiState()
    }

    fun getAiApiKey(provider: AiCaptionProvider): String? {
        val prefKey = prefKeyFor(provider) ?: return null
        val cipherBlob = prefs.getString(prefKey, null) ?: return null
        return decrypt(cipherBlob)?.trim()?.takeIf { it.isNotEmpty() }
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
