package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Helper utilities for on-device frame extraction (Freeze Frame & Gemini Vision AI),
 * procedural Cinema SFX WAV caching, and instant one-shot SFX audition playback.
 */
object StudioCreativeTools {

    @Volatile
    private var activeSfxPlayer: MediaPlayer? = null

    suspend fun extractFrameBitmap(
        context: Context,
        uriString: String,
        sourceTimeMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    retriever.setDataSource(uri.path!!)
                } else {
                    retriever.setDataSource(context, uri)
                }
                val us = (sourceTimeMs.coerceAtLeast(0L)) * 1000L
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                    ?: retriever.frameAtTime
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }

    suspend fun saveFreezeFramePng(
        context: Context,
        bitmap: Bitmap
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "freeze_frames").apply { mkdirs() }
        val file = File(dir, "freeze_${UUID.randomUUID().toString().take(10)}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        file
    }

    suspend fun ensureCinemaSfxFile(
        context: Context,
        preset: CinemaSfxPreset
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "cinema_sfx").apply { mkdirs() }
        val file = File(dir, "sfx_${preset.name.lowercase()}.wav")
        if (!file.exists() || file.length() < 128L) {
            file.writeBytes(CinemaSfxSynthesizer.generateWavBytes(preset))
        }
        file
    }

    fun auditionWavFile(file: File) {
        runCatching {
            activeSfxPlayer?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { player ->
                    runCatching { player.release() }
                    if (activeSfxPlayer === player) activeSfxPlayer = null
                }
                setOnErrorListener { player, _, _ ->
                    runCatching { player.release() }
                    if (activeSfxPlayer === player) activeSfxPlayer = null
                    true
                }
                prepare()
                start()
            }
            activeSfxPlayer = mp
        }
    }

    fun stopAudition() {
        runCatching {
            activeSfxPlayer?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            activeSfxPlayer = null
        }
    }
}
