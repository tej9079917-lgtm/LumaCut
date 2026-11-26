package com.example.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Real-time PCM 16-bit AudioProcessor applying per-clip volume gain (0..2x),
 * mute, and smooth fade-in / fade-out ramps during Media3 Transformer export.
 */
@UnstableApi
class FadeAndGainAudioProcessor(
    private val clipDurationUs: Long,
    private val volume: Float,
    private val isMuted: Boolean,
    private val fadeInUs: Long,
    private val fadeOutUs: Long
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val bytesPerFrame = channelCount * 2
        val frameCount = remaining / bytesPerFrame

        for (frame in 0 until frameCount) {
            val timeUs = (processedFrames * 1_000_000L) / sampleRate
            val gain = computeGainAt(timeUs)
            for (ch in 0 until channelCount) {
                val sample = inputBuffer.short.toInt()
                val scaled = (sample * gain).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outputBuffer.putShort(scaled.toShort())
            }
            processedFrames++
        }

        // Copy any trailing partial bytes safely
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }

    private fun computeGainAt(timeUs: Long): Float {
        if (isMuted || volume <= 0.001f) return 0f
        var envelope = 1f
        if (fadeInUs > 0L && timeUs < fadeInUs) {
            envelope = (timeUs.toFloat() / fadeInUs.toFloat()).coerceIn(0f, 1f)
        }
        if (fadeOutUs > 0L && clipDurationUs > 0L) {
            val remainingUs = (clipDurationUs - timeUs).coerceAtLeast(0L)
            if (remainingUs < fadeOutUs) {
                val fadeOutFactor = (remainingUs.toFloat() / fadeOutUs.toFloat()).coerceIn(0f, 1f)
                envelope = minOf(envelope, fadeOutFactor)
            }
        }
        return (volume * envelope).coerceIn(0f, 2f)
    }
}
