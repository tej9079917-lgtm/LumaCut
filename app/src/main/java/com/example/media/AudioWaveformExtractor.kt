package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class DecodedAudioWaveform(
    val peaks: List<Float>,
    val beatPositionsMs: List<Long>
)

/**
 * Decodes real PCM samples via MediaExtractor + MediaCodec to produce
 * high-resolution amplitude peaks and onset beat timestamps for accurate
 * timeline beat-syncing of cuts and transitions.
 */
object AudioWaveformExtractor {

    fun extract(
        context: Context,
        uri: Uri,
        durationMs: Long,
        bucketCount: Int = 140
    ): DecodedAudioWaveform {
        val safeBuckets = bucketCount.coerceIn(32, 240)
        val decoded = runCatching {
            decodeViaMediaCodec(context, uri, durationMs.coerceAtLeast(500L), safeBuckets)
        }.getOrNull()

        val peaks = if (decoded != null && decoded.any { it > 0.02f }) {
            normalizePeaks(decoded)
        } else {
            fallbackBytePeaks(context, uri, safeBuckets)
        }

        val beats = detectBeats(peaks, durationMs.coerceAtLeast(500L))
        return DecodedAudioWaveform(peaks = peaks, beatPositionsMs = beats)
    }

    private fun decodeViaMediaCodec(
        context: Context,
        uri: Uri,
        durationMs: Long,
        bucketCount: Int
    ): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrackIdx < 0 || format == null) return null

            extractor.selectTrack(audioTrackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val trackDurUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(500_000L)
            } else {
                durationMs * 1000L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bucketEnergySum = DoubleArray(bucketCount)
            val bucketSampleCount = IntArray(bucketCount)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            // Stride sampling for very long audio files so import remains fast (< 1.2s)
            val isLongTrack = trackDurUs > 60_000_000L
            var decodedFrames = 0
            val maxFrames = if (isLongTrack) 900 else 4000

            while (!outputDone && decodedFrames < maxFrames) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                                if (isLongTrack && decodedFrames % 8 == 0) {
                                    val nextTargetUs = ((decodedFrames.toDouble() / maxFrames) * trackDurUs).toLong()
                                    if (nextTargetUs > ptsUs + 150_000L) {
                                        extractor.seekTo(nextTargetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                    }
                                }
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val bucketIdx = (((bufferInfo.presentationTimeUs.toDouble() / trackDurUs.toDouble()) * bucketCount)
                            .toInt())
                            .coerceIn(0, bucketCount - 1)

                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val count = shortBuf.remaining()
                        // Step by 4 samples for fast RMS estimation
                        var sumSq = 0.0
                        var sampled = 0
                        var idx = 0
                        while (idx < count) {
                            val norm = shortBuf.get(idx) / 32768.0
                            sumSq += norm * norm
                            sampled++
                            idx += 4
                        }
                        if (sampled > 0) {
                            bucketEnergySum[bucketIdx] += sumSq
                            bucketSampleCount[bucketIdx] += sampled
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    decodedFrames++
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }

            val raw = FloatArray(bucketCount)
            for (i in 0 until bucketCount) {
                raw[i] = if (bucketSampleCount[i] > 0) {
                    sqrt(bucketEnergySum[i] / bucketSampleCount[i]).toFloat()
                } else {
                    0f
                }
            }
            // Interpolate any sparse gaps from fast seeking on long tracks
            fillSparseBuckets(raw)
            return raw
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun fillSparseBuckets(values: FloatArray) {
        for (i in values.indices) {
            if (values[i] <= 0.0001f) {
                val prev = (i - 1 downTo 0).firstOrNull { values[it] > 0.0001f }?.let { values[it] } ?: 0f
                val next = (i + 1 until values.size).firstOrNull { values[it] > 0.0001f }?.let { values[it] } ?: prev
                values[i] = (prev + next) * 0.5f
            }
        }
    }

    private fun normalizePeaks(raw: FloatArray): List<Float> {
        val maxVal = raw.maxOrNull()?.coerceAtLeast(0.02f) ?: 1f
        return raw.map { v ->
            val scaled = (v / maxVal).coerceIn(0f, 1f)
            // Slight perceptual curve so quieter details stay readable while beats pop clearly
            (0.08f + sqrt(scaled) * 0.92f).coerceIn(0.08f, 1f)
        }
    }

    /**
     * Detects rhythmic audio beat / transient onset timestamps by identifying
     * sharp local amplitude spikes above a sliding window baseline.
     */
    fun detectBeats(peaks: List<Float>, durationMs: Long): List<Long> {
        if (peaks.size < 8 || durationMs <= 0L) return emptyList()
        val beats = mutableListOf<Long>()
        val msPerBucket = durationMs.toDouble() / peaks.size.toDouble()
        val minSpacingMs = 320L // Cap beat markers around ~185 BPM max so timeline stays crisp
        var lastBeatMs = -minSpacingMs

        val window = 4
        for (i in 1 until peaks.size - 1) {
            val current = peaks[i]
            val isLocalMax = current >= peaks[i - 1] && current >= peaks[i + 1]
            if (!isLocalMax || current < 0.52f) continue

            val start = (i - window).coerceAtLeast(0)
            val end = (i + window).coerceAtMost(peaks.lastIndex)
            var sum = 0f
            for (j in start..end) sum += peaks[j]
            val localAvg = sum / (end - start + 1)

            if (current > localAvg * 1.18f) {
                val timeMs = (i * msPerBucket).toLong()
                if (timeMs - lastBeatMs >= minSpacingMs) {
                    beats.add(timeMs)
                    lastBeatMs = timeMs
                }
            }
        }
        return beats
    }

    private fun fallbackBytePeaks(context: Context, uri: Uri, bars: Int): List<Float> {
        val result = MutableList(bars) { 0.35f }
        runCatching {
            val stream = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val sampleBuf = ByteArray(32_768)
                val read = input.read(sampleBuf)
                if (read > bars) {
                    val stride = read / bars
                    for (i in 0 until bars) {
                        var sum = 0f
                        val start = i * stride
                        val end = (start + stride).coerceAtMost(read)
                        for (j in start until end) {
                            sum += abs(sampleBuf[j].toInt()) / 128f
                        }
                        val avg = sum / (end - start).coerceAtLeast(1)
                        result[i] = (0.14f + avg * 0.86f).coerceIn(0.12f, 1.0f)
                    }
                }
            }
        }
        return result
    }
}
