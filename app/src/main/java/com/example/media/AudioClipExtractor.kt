package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class ExtractedAudioPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val durationMs: Long
)

/**
 * Extracts a trimmed compressed audio track (.m4a) from any video or audio clip URI
 * using Android MediaExtractor + MediaMuxer, capped safely below 18MB for Firebase AI
 * Logic multimodal Gemini audio transcription.
 */
object AudioClipExtractor {

    private const val MAX_INLINE_BYTES = 17 * 1024 * 1024 // Stay safely below 20MB Gemini inline limit

    suspend fun extractForTranscription(
        context: Context,
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long
    ): Result<ExtractedAudioPayload> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "gemini_autocap_${System.currentTimeMillis()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIdx < 0 || audioFormat == null) {
                return@withContext Result.failure(
                    IllegalStateException("This clip does not contain an audio track to transcribe.")
                )
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val startUs = (trimStartMs.coerceAtLeast(0L)) * 1000L
            val endUs = (trimEndMs.coerceAtLeast(trimStartMs + 500L)) * 1000L

            // If already a compact audio-only MP3/WAV/OGG/M4A file without video, try direct read if small enough
            val isDirectAudioFile = extractor.trackCount == 1 && (
                mime.contains("mpeg") || mime.contains("wav") || mime.contains("ogg") || mime.contains("mp4a")
            )
            if (isDirectAudioFile && trimStartMs <= 100L) {
                val directBytes = readDirectBytesIfSmall(context, uri)
                if (directBytes != null && directBytes.size <= MAX_INLINE_BYTES) {
                    val safeMime = when {
                        mime.contains("mpeg") -> "audio/mp3"
                        mime.contains("ogg") -> "audio/ogg"
                        mime.contains("wav") -> "audio/wav"
                        else -> "audio/mp4"
                    }
                    return@withContext Result.success(
                        ExtractedAudioPayload(
                            bytes = directBytes,
                            mimeType = safeMime,
                            durationMs = (trimEndMs - trimStartMs).coerceAtLeast(500L)
                        )
                    )
                }
            }

            extractor.selectTrack(audioTrackIdx)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var writtenBytes = 0L
            var firstSampleUs = -1L
            var lastSampleUs = startUs

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break
                if (sampleTimeUs >= startUs - 50_000L) {
                    if (firstSampleUs < 0L) firstSampleUs = sampleTimeUs
                    val relativeUs = (sampleTimeUs - firstSampleUs).coerceAtLeast(0L)
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = relativeUs
                    @Suppress("DEPRECATION")
                    info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    muxer.writeSampleData(muxTrack, buffer, info)
                    writtenBytes += sampleSize
                    lastSampleUs = sampleTimeUs
                    if (writtenBytes >= MAX_INLINE_BYTES - 512 * 1024) break
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val outBytes = tempFile.readBytes()
            if (outBytes.size < 128) {
                return@withContext Result.failure(
                    IllegalStateException("Could not extract playable audio samples from this clip.")
                )
            }
            val extractedMs = ((lastSampleUs - (if (firstSampleUs >= 0L) firstSampleUs else startUs)) / 1000L)
                .coerceAtLeast(500L)
            Result.success(
                ExtractedAudioPayload(
                    bytes = outBytes,
                    mimeType = "audio/mp4",
                    durationMs = extractedMs
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to extract audio from clip."))
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { tempFile.delete() }
        }
    }

    private fun readDirectBytesIfSmall(context: Context, uri: Uri): ByteArray? = runCatching {
        val stream = if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            if (f.length() > MAX_INLINE_BYTES) return null
            f.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        stream?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size <= MAX_INLINE_BYTES) bytes else null
        }
    }.getOrNull()
}
