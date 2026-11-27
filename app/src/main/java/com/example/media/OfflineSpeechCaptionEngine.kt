package com.example.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * On-device Offline Live Microphone Dictation engine using Android's [SpeechRecognizer].
 * Streams partial recognition text in real time and emits finalized caption phrases with
 * timestamps synced to the active playhead.
 */
class OfflineSpeechCaptionEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var phraseStartPlayheadMs = 0L

    private var getPlayheadMs: () -> Long = { 0L }
    private var onPartialCallback: (String) -> Unit = {}
    private var onFinalPhraseCallback: (text: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> }
    private var onErrorCallback: (String) -> Unit = {}
    private var onStateChangedCallback: (Boolean) -> Unit = {}

    fun start(
        languageTag: String = Locale.getDefault().toLanguageTag(),
        currentPlayheadMs: () -> Long,
        onPartialText: (String) -> Unit,
        onFinalPhrase: (text: String, startMs: Long, endMs: Long) -> Unit,
        onError: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("On-device speech recognition is unavailable on this device.")
                onListeningChanged(false)
                return@post
            }
            stopInternal(notify = false)
            getPlayheadMs = currentPlayheadMs
            onPartialCallback = onPartialText
            onFinalPhraseCallback = onFinalPhrase
            onErrorCallback = onError
            onStateChangedCallback = onListeningChanged

            isListening = true
            phraseStartPlayheadMs = getPlayheadMs()
            onListeningChanged(true)
            launchRecognitionCycle(languageTag)
        }
    }

    fun stop() {
        mainHandler.post {
            stopInternal(notify = true)
        }
    }

    fun release() {
        mainHandler.post {
            stopInternal(notify = false)
        }
    }

    private fun stopInternal(notify: Boolean) {
        isListening = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (notify) {
            onPartialCallback("")
            onStateChangedCallback(false)
        }
    }

    private fun launchRecognitionCycle(languageTag: String) {
        if (!isListening) return
        runCatching { recognizer?.destroy() }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        phraseStartPlayheadMs = getPlayheadMs()

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                phraseStartPlayheadMs = getPlayheadMs()
            }

            override fun onBeginningOfSpeech() {
                phraseStartPlayheadMs = getPlayheadMs()
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    onPartialCallback(partial)
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) {
                    val nowPlayhead = getPlayheadMs()
                    val estimatedDur = (text.split(Regex("\\s+")).size * 420L).coerceIn(1100L, 4500L)
                    val endMs = maxOf(nowPlayhead, phraseStartPlayheadMs + estimatedDur)
                    onFinalPhraseCallback(text, phraseStartPlayheadMs, endMs)
                }
                onPartialCallback("")
                if (isListening) {
                    mainHandler.postDelayed({ launchRecognitionCycle(languageTag) }, 160L)
                }
            }

            override fun onError(error: Int) {
                val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (recoverable && isListening) {
                    mainHandler.postDelayed({ launchRecognitionCycle(languageTag) }, 220L)
                    return
                }
                val readable = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required for live dictation."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognizer needed offline voice pack. Enable offline speech in Android settings."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy—retrying."
                    else -> "Live mic dictation stopped (code $error)."
                }
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && isListening) {
                    mainHandler.postDelayed({ launchRecognitionCycle(languageTag) }, 350L)
                    return
                }
                onErrorCallback(readable)
                stopInternal(notify = true)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching {
            sr.startListening(intent)
        }.onFailure { err ->
            onErrorCallback(err.localizedMessage ?: "Failed to start microphone dictation.")
            stopInternal(notify = true)
        }
    }
}
