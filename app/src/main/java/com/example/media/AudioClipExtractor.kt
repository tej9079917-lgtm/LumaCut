package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class ExtractedAudioPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val durationMs: Long,
    val fileExtension: String = "m4a"
)

data class PcmSpeechSegment(
    val startMs: Long,
    val endMs: Long,
    val pcm16kMonoBytes: ByteArray
)

/**
 * Resilient audio extractor for both Cloud AI (Gemini / Whisper) and Offline On-Device Speech models.
 * - Enforces strictly monotonic presentation timestamps during M4A muxing.
 * - Falls back automatically to MediaCodec 16kHz Mono WAV decoding if the track uses Opus/Vorbis/AMR/PCM
 *   or if MediaMuxer rejects the container codec.
 * - Provides 16kHz Mono PCM Voice-Activity Segmentation for offline on-device video transcription.
 */
object AudioClipExtractor {

    private const val MAX_INLINE_BYTES = 17 * 1024 * 1024 // Stay safely below 20MB Gemini/Whisper inline limit
    private const val TARGET_PCM_SAMPLE_RATE = 16_000

    suspend fun extractForTranscription(
        context: Context,
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long
    ): Result<ExtractedAudioPayload> = withContext(Dispatchers.IO) {
        val directOrMuxed = tryDirectOrMuxedM4a(context, uriString, trimStartMs, trimEndMs)
        if (directOrMuxed.isSuccess) return@withContext directOrMuxed

        // Fallback: decode any codec (Opus, Vorbis, AMR, PCM, etc.) via MediaCodec into clean 16kHz mono WAV
        val wavFallback = decodeToMono16kWav(context, uriString, trimStartMs, trimEndMs)
        if (wavFallback.isSuccess) return@withContext wavFallback

        Result.failure(
            wavFallback.exceptionOrNull()
                ?: directOrMuxed.exceptionOrNull()
                ?: IllegalStateException("Could not extract audio from this clip.")
        )
    }

    private fun tryDirectOrMuxedM4a(
        context: Context,
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long
    ): Result<ExtractedAudioPayload> {
        val tempFile = File(context.cacheDir, "lumacut_autocap_${System.currentTimeMillis()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            val uri = Uri.parse(uriString)
            setExtractorSource(context, extractor, uri)

            val (audioTrackIdx, audioFormat) = findAudioTrack(extractor)
                ?: return Result.failure(
                    IllegalStateException("This clip does not contain an audio track to transcribe.")
                )

            val mime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val startUs = (trimStartMs.coerceAtLeast(0L)) * 1000L
            val endUs = (trimEndMs.coerceAtLeast(trimStartMs + 500L)) * 1000L

            // Fast path for small audio-only files
            val isDirectAudioFile = extractor.trackCount == 1 && (
                mime.contains("mpeg") || mime.contains("wav") || mime.contains("ogg") || mime.contains("mp4a")
            )
            if (isDirectAudioFile && trimStartMs <= 100L) {
                val directBytes = readDirectBytesIfSmall(context, uri)
                if (directBytes != null && directBytes.size <= MAX_INLINE_BYTES) {
                    val (safeMime, ext) = when {
                        mime.contains("mpeg") -> "audio/mp3" to "mp3"
                        mime.contains("ogg") -> "audio/ogg" to "ogg"
                        mime.contains("wav") -> "audio/wav" to "wav"
                        else -> "audio/mp4" to "m4a"
                    }
                    return Result.success(
                        ExtractedAudioPayload(
                            bytes = directBytes,
                            mimeType = safeMime,
                            durationMs = (trimEndMs - trimStartMs).coerceAtLeast(500L),
                            fileExtension = ext
                        )
                    )
                }
            }

            // Only attempt MP4 muxing for MP4-compatible audio codecs (AAC / MP4A)
            if (!mime.contains("mp4a") && !mime.contains("aac")) {
                return Result.failure(IllegalStateException("Non-AAC codec ($mime), switching to WAV decoder."))
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
            var lastWrittenUs = -1L
            var lastSourceSampleUs = startUs

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) {
                    extractor.advance()
                    continue
                }
                if (sampleTimeUs > endUs) break
                if (sampleTimeUs >= startUs - 50_000L) {
                    if (firstSampleUs < 0L) firstSampleUs = sampleTimeUs
                    val rawRelUs = (sampleTimeUs - firstSampleUs).coerceAtLeast(0L)
                    // Strictly monotonic PTS prevents MediaMuxer native abort on jittery phone videos
                    val safePtsUs = if (lastWrittenUs >= 0L && rawRelUs <= lastWrittenUs) {
                        lastWrittenUs + 1_000L
                    } else {
                        rawRelUs
                    }
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = safePtsUs
                    @Suppress("DEPRECATION")
                    info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    muxer.writeSampleData(muxTrack, buffer, info)
                    writtenBytes += sampleSize
                    lastWrittenUs = safePtsUs
                    lastSourceSampleUs = sampleTimeUs
                    if (writtenBytes >= MAX_INLINE_BYTES - 512 * 1024) break
                }
                extractor.advance()
            }

            if (writtenBytes == 0L) {
                return Result.failure(IllegalStateException("No AAC samples extracted in trimmed range."))
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val outBytes = tempFile.readBytes()
            if (outBytes.size < 128) {
                return Result.failure(
                    IllegalStateException("Could not extract playable audio samples from this clip.")
                )
            }
            val extractedMs = ((lastSourceSampleUs - (if (firstSampleUs >= 0L) firstSampleUs else startUs)) / 1000L)
                .coerceAtLeast(500L)
            return Result.success(
                ExtractedAudioPayload(
                    bytes = outBytes,
                    mimeType = "audio/mp4",
                    durationMs = extractedMs,
                    fileExtension = "m4a"
                )
            )
        } catch (e: Exception) {
            return Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to mux audio track."))
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Decodes any Android-supported audio codec (AAC, Opus, Vorbis, MP3, AMR, FLAC, PCM)
     * into a clean 16kHz 16-bit mono WAV payload ready for Gemini or Whisper APIs.
     */
    private fun decodeToMono16kWav(
        context: Context,
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long
    ): Result<ExtractedAudioPayload> {
        val pcmResult = decodeToMono16kPcm(context, uriString, trimStartMs, trimEndMs, maxDurationMs = 240_000L)
        return pcmResult.map { pcmBytes ->
            val wav = wrapPcmAsWav(pcmBytes, sampleRate = TARGET_PCM_SAMPLE_RATE, channels = 1)
            val durationMs = ((pcmBytes.size / 2L) * 1000L / TARGET_PCM_SAMPLE_RATE).coerceAtLeast(500L)
            ExtractedAudioPayload(
                bytes = wav,
                mimeType = "audio/wav",
                durationMs = durationMs,
                fileExtension = "wav"
            )
        }
    }

    /**
     * Decodes clip audio to 16kHz 16-bit mono PCM and slices it into speech segments (2–8s each)
     * using RMS voice-activity boundaries so Android's offline on-device SpeechRecognizer
     * can transcribe full video clips without needing a microphone.
     */
    suspend fun decodeToMono16kPcmSegments(
        context: Context,
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long
    ): Result<List<PcmSpeechSegment>> = withContext(Dispatchers.IO) {
        val pcmRes = decodeToMono16kPcm(context, uriString, trimStartMs, trimEndMs, maxDurationMs = 180_000L)
        pcmRes.map { pcm ->
            slicePcmIntoSpeechSegments(pcm, sampleRate = TARGET_PCM_SAMPLE_RATE)
        }
    }

    private fun decodeToMono16kPcm(
        context: Context,
        uriString: String,
        trimStartMs: Long,
        trimEndMs: Long,
        maxDurationMs: Long
    ): Result<ByteArray> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val uri = Uri.parse(uriString)
            setExtractorSource(context, extractor, uri)
            val (trackIdx, format) = findAudioTrack(extractor)
                ?: return Result.failure(IllegalStateException("This clip does not contain an audio track."))
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: return Result.failure(IllegalStateException("Unsupported audio format."))

            var srcSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44_100
            var srcChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1

            extractor.selectTrack(trackIdx)
            val startUs = trimStartMs.coerceAtLeast(0L) * 1000L
            val cappedEndMs = minOf(
                trimEndMs.coerceAtLeast(trimStartMs + 500L),
                trimStartMs + maxDurationMs
            )
            val endUs = cappedEndMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val outStream = ByteArrayOutputStream(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val maxPcmBytes = MAX_INLINE_BYTES - 128 * 1024

            while (!outputDone && outStream.size() < maxPcmBytes) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            val sampleTime = extractor.sampleTime
                            if (sampleSize < 0 || sampleTime > endUs) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startUs - 50_000L) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(chunk)
                            val mono16k = downsamplePcmToMono16k(chunk, srcSampleRate, srcChannels)
                            outStream.write(mono16k)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val bytes = outStream.toByteArray()
            if (bytes.size < 640) {
                return Result.failure(IllegalStateException("Audio track was silent or empty."))
            }
            return Result.success(bytes)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException(e.localizedMessage ?: "Audio decoding failed."))
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    internal fun downsamplePcmToMono16k(
        pcm16Le: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int
    ): ByteArray {
        val ch = srcChannels.coerceAtLeast(1)
        val totalFrames = pcm16Le.size / (2 * ch)
        if (totalFrames <= 0) return ByteArray(0)
        val inBuf = ByteBuffer.wrap(pcm16Le).order(ByteOrder.LITTLE_ENDIAN)

        // Downmix to mono shorts
        val mono = ShortArray(totalFrames)
        for (f in 0 until totalFrames) {
            var acc = 0
            for (c in 0 until ch) {
                acc += inBuf.short.toInt()
            }
            mono[f] = (acc / ch).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        if (srcSampleRate == TARGET_PCM_SAMPLE_RATE || srcSampleRate <= 0) {
            val out = ByteBuffer.allocate(mono.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in mono) out.putShort(s)
            return out.array()
        }

        val ratio = srcSampleRate.toDouble() / TARGET_PCM_SAMPLE_RATE.toDouble()
        val outFrames = (totalFrames / ratio).toInt().coerceAtLeast(1)
        val out = ByteBuffer.allocate(outFrames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outFrames) {
            val pos = i * ratio
            val idx0 = pos.toInt().coerceIn(0, totalFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, totalFrames - 1)
            val frac = pos - idx0
            val sample = (mono[idx0] * (1.0 - frac) + mono[idx1] * frac).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            out.putShort(sample)
        }
        return out.array()
    }

    internal fun wrapPcmAsWav(pcmBytes: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val dataSize = pcmBytes.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        val result = ByteArray(44 + dataSize)
        System.arraycopy(header.array(), 0, result, 0, 44)
        System.arraycopy(pcmBytes, 0, result, 44, dataSize)
        return result
    }

    internal fun slicePcmIntoSpeechSegments(
        pcm16kMono: ByteArray,
        sampleRate: Int = TARGET_PCM_SAMPLE_RATE
    ): List<PcmSpeechSegment> {
        val bytesPerMs = (sampleRate * 2) / 1000
        val totalMs = (pcm16kMono.size / bytesPerMs).toLong()
        if (totalMs <= 0L) return emptyList()
        if (totalMs <= 6_500L) {
            return listOf(PcmSpeechSegment(0L, totalMs, pcm16kMono))
        }

        // Compute 100ms frame RMS energy to split cleanly at pauses every ~4-7 seconds
        val frameMs = 100
        val frameBytes = frameMs * bytesPerMs
        val frameCount = pcm16kMono.size / frameBytes
        val rmsValues = DoubleArray(frameCount)
        val bb = ByteBuffer.wrap(pcm16kMono).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frameCount) {
            var sumSq = 0.0
            val samples = frameBytes / 2
            val startByte = f * frameBytes
            for (s in 0 until samples) {
                val sample = bb.getShort(startByte + s * 2).toDouble()
                sumSq += sample * sample
            }
            rmsValues[f] = sqrt(sumSq / samples.coerceAtLeast(1))
        }
        val avgRms = rmsValues.average().coerceAtLeast(120.0)
        val quietThreshold = avgRms * 0.42

        val segments = mutableListOf<PcmSpeechSegment>()
        var segStartFrame = 0
        val minFrames = 32 // 3.2s minimum chunk
        val idealMaxFrames = 65 // 6.5s target chunk
        val hardMaxFrames = 85 // 8.5s max chunk

        var f = 0
        while (f < frameCount) {
            val len = f - segStartFrame
            val shouldCut = (len >= minFrames && rmsValues[f] < quietThreshold && len >= 38) ||
                (len >= idealMaxFrames && rmsValues[f] < avgRms * 0.7) ||
                (len >= hardMaxFrames)
            if (shouldCut) {
                val startByte = segStartFrame * frameBytes
                val endByte = (f * frameBytes).coerceAtMost(pcm16kMono.size)
                if (endByte - startByte >= bytesPerMs * 400) {
                    segments.add(
                        PcmSpeechSegment(
                            startMs = segStartFrame.toLong() * frameMs,
                            endMs = f.toLong() * frameMs,
                            pcm16kMonoBytes = pcm16kMono.copyOfRange(startByte, endByte)
                        )
                    )
                }
                segStartFrame = f
            }
            f++
        }

        if (segStartFrame < frameCount) {
            val startByte = segStartFrame * frameBytes
            val endByte = pcm16kMono.size
            if (endByte - startByte >= bytesPerMs * 350) {
                segments.add(
                    PcmSpeechSegment(
                        startMs = segStartFrame.toLong() * frameMs,
                        endMs = totalMs,
                        pcm16kMonoBytes = pcm16kMono.copyOfRange(startByte, endByte)
                    )
                )
            }
        }
        return segments
    }

    private fun setExtractorSource(context: Context, extractor: MediaExtractor, uri: Uri) {
        if (uri.scheme == "file" && uri.path != null) {
            extractor.setDataSource(uri.path!!)
        } else {
            extractor.setDataSource(context, uri, null)
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                return i to fmt
            }
        }
        return null
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
