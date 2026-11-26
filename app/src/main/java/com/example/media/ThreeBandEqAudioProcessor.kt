package com.example.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure 2nd-order Audio EQ Cookbook biquad filter (Low-Shelf, Peaking, High-Shelf),
 * used by [ThreeBandEqAudioProcessor] and unit tests.
 */
class BiquadFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun processSample(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        fun lowShelf(sampleRate: Double, freqHz: Double, gainDb: Double, slope: Double = 1.0): BiquadFilter {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            val b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            val a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
            return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        fun peaking(sampleRate: Double, freqHz: Double, gainDb: Double, q: Double = 1.0): BiquadFilter {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cosW0
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha / a
            return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        fun highShelf(sampleRate: Double, freqHz: Double, gainDb: Double, slope: Double = 1.0): BiquadFilter {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            val b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            val a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
            return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }
}

/**
 * Fairlight-style 3-Band EQ (Low 120 Hz shelf, Mid 1 kHz peak, High 6 kHz shelf)
 * implemented as a real Media3 PCM [BaseAudioProcessor].
 */
@UnstableApi
class ThreeBandEqAudioProcessor(
    lowDb: Float,
    midDb: Float,
    highDb: Float
) : BaseAudioProcessor() {

    private val clampedLow = lowDb.coerceIn(-12f, 12f).toDouble()
    private val clampedMid = midDb.coerceIn(-12f, 12f).toDouble()
    private val clampedHigh = highDb.coerceIn(-12f, 12f).toDouble()
    private val bypass = abs(clampedLow) < 0.1 && abs(clampedMid) < 0.1 && abs(clampedHigh) < 0.1

    private var lowFilters: Array<BiquadFilter> = emptyArray()
    private var midFilters: Array<BiquadFilter> = emptyArray()
    private var highFilters: Array<BiquadFilter> = emptyArray()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (bypass) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000).toDouble()
        val ch = inputAudioFormat.channelCount.coerceAtLeast(1)
        lowFilters = Array(ch) { BiquadFilter.lowShelf(sr, 120.0, clampedLow) }
        midFilters = Array(ch) { BiquadFilter.peaking(sr, 1000.0, clampedMid, q = 1.0) }
        highFilters = Array(ch) { BiquadFilter.highShelf(sr, 6000.0, clampedHigh) }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        var chIdx = 0
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short.toDouble() / 32768.0
            var processed = sample
            if (chIdx < lowFilters.size) {
                processed = lowFilters[chIdx].processSample(processed)
                processed = midFilters[chIdx].processSample(processed)
                processed = highFilters[chIdx].processSample(processed)
            }
            val scaled = (processed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
            out.putShort(scaled)
            chIdx = (chIdx + 1) % channels
        }
        out.flip()
    }

    override fun onFlush() {
        lowFilters.forEach { it.reset() }
        midFilters.forEach { it.reset() }
        highFilters.forEach { it.reset() }
    }

    override fun onReset() {
        lowFilters = emptyArray()
        midFilters = emptyArray()
        highFilters = emptyArray()
    }
}
