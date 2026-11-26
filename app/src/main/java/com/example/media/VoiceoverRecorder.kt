package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Lightweight Fairlight-style Voiceover Recorder using Android [MediaRecorder]
 * to capture AAC `.m4a` voiceovers directly into the project audio directory with
 * real-time input amplitude metering.
 */
class VoiceoverRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(outputFile: File): Result<Unit> = runCatching {
        stopAndDiscard()
        outputFile.parentFile?.mkdirs()
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        activeFile = outputFile
    }

    /** Returns normalized microphone peak level in `0f..1f`. */
    fun pollNormalizedLevel(): Float {
        val mr = recorder ?: return 0f
        return runCatching {
            val raw = mr.maxAmplitude.toFloat()
            (raw / 22_000f).coerceIn(0.02f, 1f)
        }.getOrDefault(0f)
    }

    fun stopAndFinalize(): File? {
        val mr = recorder ?: return null
        val file = activeFile
        recorder = null
        activeFile = null
        return try {
            mr.stop()
            mr.release()
            if (file != null && file.exists() && file.length() > 256L) file else null
        } catch (_: Exception) {
            runCatching { mr.release() }
            runCatching { file?.delete() }
            null
        }
    }

    fun stopAndDiscard() {
        val mr = recorder
        val file = activeFile
        recorder = null
        activeFile = null
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        runCatching { file?.delete() }
    }
}
