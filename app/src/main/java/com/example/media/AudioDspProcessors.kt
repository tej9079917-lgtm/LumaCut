package com.example.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.model.VoiceEffectPreset
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Media3 AudioProcessor that smoothly ducks background music tracks during active
 * voiceover/dialogue windows on MP4 export, matching live preview ducking.
 */
@UnstableApi
class AutoDuckAudioProcessor(
    private val clipTimelineStartMs: Long,
    private val voiceWindowsGlobalMs: List<LongRange>,
    private val duckFloorGain: Float = AutoDuckEvaluator.DEFAULT_DUCK_FLOOR
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val bytesPerFrame = channels * 2
        val frameCount = remaining / bytesPerFrame
        val out = replaceOutputBuffer(remaining)

        for (f in 0 until frameCount) {
            val localMs = (processedFrames * 1000L) / sampleRate
            val globalMs = clipTimelineStartMs + localMs
            val gain = AutoDuckEvaluator.evaluateGainMultiplier(globalMs, voiceWindowsGlobalMs, duckFloorGain)
            for (c in 0 until channels) {
                val sample = inputBuffer.short.toFloat() * gain
                val clamped = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                out.putShort(clamped)
            }
            processedFrames++
        }
        out.flip()
    }
}

/**
 * Pure JVM-testable DSP sample engine for Audio Denoise and Studio Voice Effects.
 */
object VoiceDspMath {
    fun effectiveNoiseGateDb(noiseGateDb: Float, denoiseStrength: Float): Float {
        val cleanDenoise = denoiseStrength.coerceIn(0f, 1f)
        if (cleanDenoise <= 0.02f) return noiseGateDb
        // Map denoise 0..1 to progressive noise floor threshold -52dB .. -26dB
        val denoiseGate = -52f + cleanDenoise * 26f
        return maxOf(noiseGateDb, denoiseGate)
    }

    fun applyVoicePresetSample(
        preset: VoiceEffectPreset,
        sample: Float,
        sampleIndex: Long,
        sampleRate: Int,
        delaySample: Float,
        hpState: Float,
        lpState: Float
    ): Float {
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        return when (preset) {
            VoiceEffectPreset.NONE -> sample
            VoiceEffectPreset.CHIPMUNK -> (sample * 1.06f).coerceIn(-1f, 1f)
            VoiceEffectPreset.DEEP_BASS -> (sample * 1.10f).coerceIn(-1f, 1f)
            VoiceEffectPreset.ROBOT -> {
                val carrier = sin(2.0 * PI * 58.0 * sampleIndex / sr).toFloat()
                val ring = sample * (0.45f + 0.55f * carrier)
                val comb = ring * 0.78f + delaySample * 0.34f
                tanh((comb * 1.25f).toDouble()).toFloat()
            }
            VoiceEffectPreset.RADIO -> {
                // Narrowband walkie-talkie mid-band emphasis + warm radio grit
                val band = lpState - hpState
                tanh((band * 2.1f).toDouble()).toFloat() * 0.85f
            }
            VoiceEffectPreset.ECHO_HALL -> {
                (sample * 0.82f + delaySample * 0.48f).coerceIn(-1f, 1f)
            }
            VoiceEffectPreset.MONSTER -> {
                val growl = sin(2.0 * PI * 32.0 * sampleIndex / sr).toFloat()
                val mod = sample * (0.72f + 0.28f * growl) + delaySample * 0.22f
                tanh((mod * 1.45f).toDouble()).toFloat()
            }
            VoiceEffectPreset.STUDIO_CLARITY -> {
                // Warm broadcast compression + crisp presence
                val compressed = tanh((sample * 1.35f).toDouble()).toFloat()
                (compressed * 0.92f + (sample - lpState) * 0.18f).coerceIn(-1f, 1f)
            }
        }
    }
}

/**
 * On-device Voice Isolation, Broadband Denoise & Noise Gate AudioProcessor for Media3 export:
 * - 85 Hz..140 Hz 1st-order high-pass rumble removal when voiceIsolation or denoise is active
 * - Adaptive broadband hiss low-pass damping controlled by denoiseStrength (0..1)
 * - Smooth envelope noise gate governed by noiseGateDb + denoiseStrength
 */
@UnstableApi
class VoiceDspAudioProcessor(
    private val voiceIsolation: Boolean,
    private val noiseGateDb: Float,
    private val denoiseStrength: Float = 0f
) : BaseAudioProcessor() {

    private var hpPrevIn = FloatArray(2)
    private var hpPrevOut = FloatArray(2)
    private var lpPrevOut = FloatArray(2)
    private var hpAlpha = 0.985f
    private var lpAlpha = 0.65f
    private var envelope = 0f
    private var gateGain = 1f
    private val clampedDenoise = denoiseStrength.coerceIn(0f, 1f)
    private val effectiveGateDb = VoiceDspMath.effectiveNoiseGateDb(noiseGateDb, clampedDenoise)
    private val gateThresholdLinear: Float =
        if (effectiveGateDb <= -58f) 0f else 10.0.pow(effectiveGateDb.coerceIn(-58f, -10f) / 20.0).toFloat()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val ch = inputAudioFormat.channelCount.coerceAtLeast(1)
        hpPrevIn = FloatArray(ch)
        hpPrevOut = FloatArray(ch)
        lpPrevOut = FloatArray(ch)
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000).toDouble()
        val hpCutoff = if (clampedDenoise > 0.02f) 85.0 + (clampedDenoise * 60.0) else 85.0
        val rcHp = 1.0 / (2.0 * PI * hpCutoff)
        val dt = 1.0 / sr
        hpAlpha = (rcHp / (rcHp + dt)).toFloat().coerceIn(0.8f, 0.999f)

        // Adaptive hiss-damping low-pass cutoff (9kHz down to 4.2kHz at full denoise)
        val lpCutoff = 9000.0 - (clampedDenoise * 4800.0)
        val rcLp = 1.0 / (2.0 * PI * lpCutoff)
        lpAlpha = (dt / (rcLp + dt)).toFloat().coerceIn(0.15f, 0.95f)
        return inputAudioFormat
    }

    override fun onFlush() {
        hpPrevIn.fill(0f)
        hpPrevOut.fill(0f)
        lpPrevOut.fill(0f)
        envelope = 0f
        gateGain = 1f
    }

    override fun onReset() {
        onFlush()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bytesPerFrame = channels * 2
        val frameCount = remaining / bytesPerFrame
        val out = replaceOutputBuffer(remaining)

        for (f in 0 until frameCount) {
            var framePeak = 0f
            val temp = FloatArray(channels)
            for (c in 0 until channels) {
                var x = inputBuffer.short.toFloat() / 32768f
                if (voiceIsolation || clampedDenoise > 0.02f) {
                    val y = hpAlpha * (hpPrevOut[c] + x - hpPrevIn[c])
                    hpPrevIn[c] = x
                    hpPrevOut[c] = y
                    x = y
                }
                if (clampedDenoise > 0.02f) {
                    val lp = lpPrevOut[c] + lpAlpha * (x - lpPrevOut[c])
                    lpPrevOut[c] = lp
                    // Blend denoised low-pass signal more aggressively during quiet/hiss frames
                    val hissBlend = (clampedDenoise * (1f - (envelope * 3.5f).coerceIn(0f, 0.7f))).coerceIn(0f, 0.9f)
                    x = x * (1f - hissBlend) + lp * hissBlend
                }
                if (voiceIsolation) {
                    x = (x * 1.15f).coerceIn(-1f, 1f)
                }
                temp[c] = x
                val ax = abs(x)
                if (ax > framePeak) framePeak = ax
            }

            if (gateThresholdLinear > 0f) {
                envelope = if (framePeak > envelope) {
                    envelope * 0.92f + framePeak * 0.08f
                } else {
                    envelope * 0.9985f + framePeak * 0.0015f
                }
                val floorGain = (0.14f - clampedDenoise * 0.10f).coerceIn(0.03f, 0.18f)
                val targetGate = if (envelope >= gateThresholdLinear) 1f else floorGain
                gateGain += (targetGate - gateGain) * 0.025f
            } else {
                gateGain = 1f
            }

            for (c in 0 until channels) {
                val sample = (temp[c] * gateGain * 32767f).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                out.putShort(sample)
            }
        }
        out.flip()
    }
}

/**
 * Creative Voice Effects DSP AudioProcessor for Media3 export (Robot, Radio, Echo Hall,
 * Monster, Studio Broadcast; Chipmunk/Deep Bass pitch scaling pairs with SonicAudioProcessor).
 */
@UnstableApi
class VoiceEffectAudioProcessor(
    private val preset: VoiceEffectPreset
) : BaseAudioProcessor() {

    private var delayBuffer = FloatArray(1)
    private var delayWritePos = 0
    private var delayFrames = 1
    private var hpState = FloatArray(2)
    private var lpState = FloatArray(2)
    private var processedFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val ch = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        hpState = FloatArray(ch)
        lpState = FloatArray(ch)
        delayFrames = when (preset) {
            VoiceEffectPreset.ECHO_HALL -> (sr * 0.24f).toInt().coerceAtLeast(64)
            VoiceEffectPreset.ROBOT -> (sr * 0.014f).toInt().coerceAtLeast(32)
            VoiceEffectPreset.MONSTER -> (sr * 0.028f).toInt().coerceAtLeast(32)
            else -> 64
        }
        delayBuffer = FloatArray(delayFrames * ch)
        delayWritePos = 0
        processedFrames = 0L
        return inputAudioFormat
    }

    override fun onFlush() {
        delayBuffer.fill(0f)
        hpState.fill(0f)
        lpState.fill(0f)
        delayWritePos = 0
        processedFrames = 0L
    }

    override fun onReset() {
        onFlush()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val bytesPerFrame = channels * 2
        val frameCount = remaining / bytesPerFrame
        val out = replaceOutputBuffer(remaining)

        for (f in 0 until frameCount) {
            for (c in 0 until channels) {
                val x = inputBuffer.short.toFloat() / 32768f
                val bufIdx = (delayWritePos * channels + c).coerceIn(0, delayBuffer.lastIndex)
                val delayed = delayBuffer[bufIdx]

                // Simple 1-pole filter states used by Radio / Broadcast presets
                hpState[c] += 0.05f * (x - hpState[c])
                lpState[c] += 0.35f * (x - lpState[c])

                val processed = VoiceDspMath.applyVoicePresetSample(
                    preset = preset,
                    sample = x,
                    sampleIndex = processedFrames,
                    sampleRate = sr,
                    delaySample = delayed,
                    hpState = hpState[c],
                    lpState = lpState[c]
                )

                delayBuffer[bufIdx] = if (preset == VoiceEffectPreset.ECHO_HALL) {
                    (x + delayed * 0.38f).coerceIn(-1f, 1f)
                } else {
                    x
                }

                val outShort = (processed * 32767f).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                out.putShort(outShort)
            }
            delayWritePos = (delayWritePos + 1) % delayFrames.coerceAtLeast(1)
            processedFrames++
        }
        out.flip()
    }
}
