package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

enum class LocalVoiceCloneProfile(
    val displayName: String,
    val subtitle: String,
    val defaultPitch: Float,
    val defaultSpeed: Float
) {
    NATURAL("Natural Narrator", "Clean studio speech", 1.0f, 1.0f),
    CINEMA_DEEP("Cinema Trailer", "Deep baritone punch", 0.72f, 0.88f),
    WARM_PODCAST("Warm Podcast", "Relaxed conversational", 0.90f, 0.96f),
    ENERGETIC_SHORTS("Shorts Energy", "Fast & crisp hook", 1.12f, 1.18f),
    BRIGHT_STUDIO("Bright Host", "Airy upbeat tone", 1.24f, 1.05f),
    DOCUMENTARY("Calm Docu", "Measured authoritative", 0.84f, 0.90f)
}

data class SynthesizedVoiceTrack(
    val file: File,
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val waveformPeaks: List<Float>
)

/**
 * 100% On-Device Text-to-Speech & Voice Style Cloning Studio.
 * Uses Android's local TextToSpeech synthesizer (`synthesizeToFile`) to render scripts or timeline
 * captions straight into WAV files stored locally — zero cloud uploads, zero accounts, zero paywalls.
 */
object LocalTtsVoiceEngine {

    suspend fun synthesizeToTimelineWav(
        context: Context,
        text: String,
        languageDisplayName: String,
        profile: LocalVoiceCloneProfile,
        pitch: Float = profile.defaultPitch,
        speed: Float = profile.defaultSpeed
    ): Result<SynthesizedVoiceTrack> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter narration text or pull captions first."))
        }

        val audioDir = File(context.filesDir, "lumacut_media/audio").apply { mkdirs() }
        val outFile = File(audioDir, "tts_${System.currentTimeMillis()}.wav")
        val utteranceId = "lumacut_tts_${UUID.randomUUID()}"

        val locale = resolveLocale(languageDisplayName)
        var ttsInstance: TextToSpeech? = null
        try {
            val initSuccess = withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine { cont ->
                    ttsInstance = TextToSpeech(context.applicationContext) { status ->
                        if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS)
                    }
                }
            } ?: false

            val tts = ttsInstance
            if (!initSuccess || tts == null) {
                return@withContext Result.failure(
                    IllegalStateException("On-device Text-to-Speech engine is unavailable on this device.")
                )
            }

            val langStatus = tts.setLanguage(locale)
            if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }

            // Pick a matching offline local voice when available on device
            runCatching {
                val matchingVoice = tts.voices
                    ?.filter { !it.isNetworkConnectionRequired }
                    ?.firstOrNull { it.locale.language.equals(locale.language, ignoreCase = true) }
                if (matchingVoice != null) {
                    tts.voice = matchingVoice
                }
            }

            tts.setPitch(pitch.coerceIn(0.55f, 1.75f))
            tts.setSpeechRate(speed.coerceIn(0.60f, 1.65f))

            val synthesisOk = withTimeoutOrNull(30_000L) {
                suspendCancellableCoroutine { cont ->
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(true)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) cont.resume(false)
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId && cont.isActive) cont.resume(false)
                        }
                    })

                    val params = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    }
                    val queued = tts.synthesizeToFile(cleanText, params, outFile, utteranceId)
                    if (queued != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resume(false)
                    }
                }
            } ?: false

            if (!synthesisOk || !outFile.exists() || outFile.length() < 256L) {
                outFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("On-device voice synthesis failed. Check that a local TTS voice is installed.")
                )
            }

            val durationMs = measureDurationMs(outFile).coerceAtLeast(600L)
            val fileUri = Uri.fromFile(outFile)
            val uriString = fileUri.toString()
            val peaks = runCatching {
                AudioWaveformExtractor.extract(context, fileUri, durationMs, 64).peaks
            }.getOrDefault(List(48) { 0.45f })

            val labelSnippet = cleanText.take(20).replace('\n', ' ').trim()
            Result.success(
                SynthesizedVoiceTrack(
                    file = outFile,
                    uriString = uriString,
                    displayName = "AI Voice (${profile.displayName}): $labelSnippet",
                    durationMs = durationMs,
                    waveformPeaks = peaks
                )
            )
        } catch (e: Exception) {
            outFile.delete()
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to synthesize offline voice."))
        } finally {
            runCatching {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            }
        }
    }

    private fun resolveLocale(languageDisplayName: String): Locale {
        if (languageDisplayName.equals("Auto", ignoreCase = true) || languageDisplayName.isBlank()) {
            return Locale.getDefault()
        }
        val bcp47 = OfflineLanguageCatalog.bcp47ForName(languageDisplayName)
        return Locale.forLanguageTag(bcp47)
    }

    private fun measureDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
        } catch (_: Exception) {
            // Fallback estimate from WAV PCM size (22050Hz * 2 bytes/sample)
            ((file.length() - 44L).coerceAtLeast(0L) * 1000L / 44100L).coerceAtLeast(800L)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
