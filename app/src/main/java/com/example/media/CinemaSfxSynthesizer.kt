package com.example.media

import com.example.model.MainClip
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class CinemaSfxPreset(
    val displayName: String,
    val category: String,
    val durationMs: Long,
    val accentHex: Long
) {
    WHOOSH("Air Whoosh", "Transition", 850L, 0xFF38BDF8),
    RISER("Cinema Riser", "Tension", 2200L, 0xFFF59E0B),
    BASS_DROP("Sub Impact", "Impact", 1200L, 0xFFF43F5E),
    SHUTTER("Camera Snap", "Foley", 320L, 0xFFA855F7),
    GLITCH_ZAP("Glitch Zap", "FX", 620L, 0xFF22D3EE),
    UI_POP("Crisp Pop", "UI", 260L, 0xFF10B981),
    TAPE_STOP("Tape Stop", "FX", 1100L, 0xFFFB923C),
    CINEMA_HIT("Trailer Hit", "Impact", 1500L, 0xFFEF4444)
}

/**
 * Procedural 44.1 kHz stereo 16-bit PCM WAV synthesizer for offline built-in Cinema SFX.
 * Zero external assets or network required; output integrates natively with AudioClip & Media3.
 */
object CinemaSfxSynthesizer {
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val BITS_PER_SAMPLE = 16

    fun generateWavBytes(preset: CinemaSfxPreset): ByteArray {
        val numFrames = ((SAMPLE_RATE.toLong() * preset.durationMs) / 1000L).toInt().coerceAtLeast(1000)
        val samplesL = FloatArray(numFrames)
        val samplesR = FloatArray(numFrames)

        // Deterministic xorshift PRNG for reproducible noise textures
        var seed = (preset.ordinal + 1) * 0x9E3779B9L
        fun nextNoise(): Float {
            seed = seed xor (seed shl 13)
            seed = seed xor (seed ushr 17)
            seed = seed xor (seed shl 5)
            return ((seed and 0xFFFFL).toFloat() / 32768f) - 1f
        }

        var lpL = 0f
        var lpR = 0f
        var phase = 0.0
        val durSec = numFrames.toDouble() / SAMPLE_RATE.toDouble()

        for (i in 0 until numFrames) {
            val t = i.toDouble() / SAMPLE_RATE.toDouble()
            val norm = (i.toFloat() / numFrames.toFloat()).coerceIn(0f, 1f)
            var outL = 0f
            var outR = 0f

            when (preset) {
                CinemaSfxPreset.WHOOSH -> {
                    val env = sin(norm * PI).toFloat() * sin(norm * PI).toFloat()
                    val cutoff = 0.04f + 0.36f * env
                    lpL += cutoff * (nextNoise() - lpL)
                    lpR += cutoff * (nextNoise() - lpR)
                    val pan = 0.5f + 0.35f * sin((norm - 0.5) * PI).toFloat()
                    val tone = sin(2.0 * PI * (110.0 + 260.0 * env) * t).toFloat() * 0.16f
                    outL = (lpL * 0.95f + tone) * env * (1.2f - pan * 0.4f)
                    outR = (lpR * 0.95f + tone) * env * (0.8f + pan * 0.4f)
                }
                CinemaSfxPreset.RISER -> {
                    val env = (norm * norm) * (1f - if (norm > 0.94f) ((norm - 0.94f) / 0.06f) else 0f)
                    val freq = 75.0 + 780.0 * norm * norm * norm
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    val shimmer = sin(phase).toFloat() * 0.5f + sin(phase * 1.503).toFloat() * 0.25f
                    lpL += (0.03f + 0.25f * norm) * (nextNoise() - lpL)
                    val sig = (shimmer + lpL * 0.45f) * env * 0.85f
                    outL = sig
                    outR = sig * 0.96f + sin(phase * 1.005).toFloat() * env * 0.15f
                }
                CinemaSfxPreset.BASS_DROP -> {
                    val env = exp(-3.4 * t).toFloat()
                    val freq = 135.0 * exp(-4.8 * t) + 34.0
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    val punch = if (t < 0.025) nextNoise() * (1f - (t / 0.025).toFloat()) * 0.55f else 0f
                    val sig = tanh((sin(phase).toFloat() * 1.35f + punch).toDouble()).toFloat() * env * 0.92f
                    outL = sig
                    outR = sig
                }
                CinemaSfxPreset.SHUTTER -> {
                    // Crisp two-click mechanical shutter transient
                    val click1 = if (t < 0.045) exp(-110.0 * t).toFloat() * (nextNoise() * 0.7f + sin(2.0 * PI * 1950.0 * t).toFloat() * 0.35f) else 0f
                    val t2 = t - 0.085
                    val click2 = if (t2 in 0.0..0.06) exp(-95.0 * t2).toFloat() * (nextNoise() * 0.65f + sin(2.0 * PI * 1450.0 * t2).toFloat() * 0.3f) else 0f
                    val sig = (click1 + click2) * 0.9f
                    outL = sig
                    outR = sig * 0.95f
                }
                CinemaSfxPreset.GLITCH_ZAP -> {
                    val burst = if (((norm * 16f).toInt() % 3) != 2) 1f else 0.18f
                    val env = (1f - norm * 0.75f) * burst
                    val freq = 220.0 + ((norm * 24f).toInt() % 5) * 340.0
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    val sq = if (sin(phase) >= 0.0) 0.65f else -0.65f
                    val sig = (sq * 0.55f + nextNoise() * 0.45f) * env * 0.78f
                    outL = sig
                    outR = sig * (if ((norm * 20f).toInt() % 2 == 0) 0.6f else 1f)
                }
                CinemaSfxPreset.UI_POP -> {
                    val env = exp(-24.0 * t).toFloat() * sin((norm.coerceAtMost(0.15f) / 0.15f) * (PI / 2.0)).toFloat()
                    val freq = 340.0 + 520.0 * exp(-32.0 * t)
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    val sig = sin(phase).toFloat() * env * 0.9f
                    outL = sig
                    outR = sig
                }
                CinemaSfxPreset.TAPE_STOP -> {
                    val speed = (1.0 - norm.toDouble() * norm.toDouble()).coerceAtLeast(0.03)
                    val env = (1f - norm * 0.85f)
                    val freq = 440.0 * speed
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    val chord = sin(phase).toFloat() * 0.55f + sin(phase * 1.25).toFloat() * 0.25f + sin(phase * 1.5).toFloat() * 0.2f
                    val sig = chord * env * 0.85f
                    outL = sig
                    outR = sig
                }
                CinemaSfxPreset.CINEMA_HIT -> {
                    val env = exp(-2.6 * t / durSec * 1.8).toFloat()
                    val subFreq = 95.0 * exp(-2.8 * t) + 38.0
                    phase += 2.0 * PI * subFreq / SAMPLE_RATE
                    val sub = sin(phase).toFloat() * 0.75f
                    val metallic = sin(2.0 * PI * 612.0 * t).toFloat() * exp(-8.0 * t).toFloat() * 0.35f +
                        sin(2.0 * PI * 917.0 * t).toFloat() * exp(-10.0 * t).toFloat() * 0.25f
                    lpL += 0.12f * (nextNoise() - lpL)
                    val tail = lpL * exp(-3.0 * t).toFloat() * 0.5f
                    val sig = tanh((sub + metallic + tail).toDouble() * 1.2).toFloat() * env * 0.92f
                    outL = sig
                    outR = sig * 0.97f
                }
            }

            // Smooth 5ms fade-in/out edge guard against clicks
            val edgeSamples = (SAMPLE_RATE * 0.005).toInt()
            val edgeGain = when {
                i < edgeSamples -> i.toFloat() / edgeSamples
                i > numFrames - edgeSamples -> (numFrames - i).toFloat() / edgeSamples
                else -> 1f
            }.coerceIn(0f, 1f)

            samplesL[i] = (outL * edgeGain).coerceIn(-0.98f, 0.98f)
            samplesR[i] = (outR * edgeGain).coerceIn(-0.98f, 0.98f)
        }

        return encodePcm16StereoWav(samplesL, samplesR)
    }

    private fun encodePcm16StereoWav(left: FloatArray, right: FloatArray): ByteArray {
        val frames = left.size
        val dataSize = frames * CHANNELS * 2
        val out = ByteArray(44 + dataSize)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
        buf.putInt(36 + dataSize)
        buf.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())
        buf.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(CHANNELS.toShort())
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * CHANNELS * 2)
        buf.putShort((CHANNELS * 2).toShort())
        buf.putShort(BITS_PER_SAMPLE.toShort())
        buf.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
        buf.putInt(dataSize)
        for (i in 0 until frames) {
            buf.putShort((left[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            buf.putShort((right[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        return out
    }
}

/**
 * Velocity Ramp Presets applied as atomic multi-segment speed curves on a video clip.
 */
enum class SpeedRampPreset(
    val displayName: String,
    val subtitle: String,
    val segments: List<Pair<Float, Float>> // (fractionOfSourceSpan, playbackSpeed)
) {
    HERO_MOMENT(
        "Hero Moment",
        "Fast → Slow-Mo → Fast",
        listOf(0.28f to 2.25f, 0.44f to 0.45f, 0.28f to 2.25f)
    ),
    MONTAGE(
        "Montage",
        "Punchy hype rhythm",
        listOf(0.25f to 2.5f, 0.25f to 0.75f, 0.25f to 2.5f, 0.25f to 0.75f)
    ),
    BULLET_TIME(
        "Bullet Time",
        "Dramatic mid dip",
        listOf(0.20f to 1.8f, 0.60f to 0.35f, 0.20f to 1.8f)
    ),
    JUMP_CUT(
        "Jump Cut",
        "Rapid burst beat",
        listOf(0.35f to 3.0f, 0.30f to 1.0f, 0.35f to 3.0f)
    ),
    FLASH_IN(
        "Flash In",
        "Whip entry settle",
        listOf(0.30f to 2.8f, 0.70f to 0.85f)
    ),
    FLASH_OUT(
        "Flash Out",
        "Build to fast exit",
        listOf(0.70f to 0.85f, 0.30f to 2.8f)
    );

    fun splitClip(source: MainClip): List<MainClip> {
        val totalSpan = source.rawTrimDurationMs
        if (totalSpan < 1000L) {
            return listOf(source.copy(playbackSpeed = segments.first().second))
        }
        val out = mutableListOf<MainClip>()
        var cursor = source.trimStartMs
        segments.forEachIndexed { idx, (frac, spd) ->
            val isLast = idx == segments.lastIndex
            val segEnd = if (isLast) {
                source.trimEndMs
            } else {
                (cursor + (totalSpan * frac).toLong())
                    .coerceIn(cursor + 250L, source.trimEndMs - 250L * (segments.lastIndex - idx))
            }
            if (segEnd - cursor >= 250L) {
                out.add(
                    source.copy(
                        id = if (idx == 0) source.id else UUID.randomUUID().toString(),
                        displayName = "${source.displayName} • ${displayName.take(6)} ${idx + 1}",
                        trimStartMs = cursor,
                        trimEndMs = segEnd,
                        playbackSpeed = spd,
                        transitionToNext = if (isLast) source.transitionToNext else com.example.model.TransitionSpec()
                    )
                )
                cursor = segEnd
            }
        }
        return if (out.isEmpty()) listOf(source) else out
    }
}

/**
 * Pure JVM-testable Auto-Ducking envelope evaluator.
 * Smoothly lowers background music gain down to [duckFloorGain] whenever [timeMs] overlaps
 * any active dialogue/voiceover window, with 250ms attack and release ramps.
 */
object AutoDuckEvaluator {
    const val DEFAULT_DUCK_FLOOR = 0.30f
    const val RAMP_MS = 250L

    fun evaluateGainMultiplier(
        timeMs: Long,
        voiceWindowsMs: List<LongRange>,
        duckFloorGain: Float = DEFAULT_DUCK_FLOOR
    ): Float {
        if (voiceWindowsMs.isEmpty()) return 1f
        val floor = duckFloorGain.coerceIn(0.10f, 0.90f)
        var minGain = 1f
        for (window in voiceWindowsMs) {
            val start = window.first
            val end = window.last
            if (end <= start) continue
            val g = when {
                timeMs in start..end -> floor
                timeMs in (start - RAMP_MS) until start -> {
                    val frac = (timeMs - (start - RAMP_MS)).toFloat() / RAMP_MS.toFloat()
                    1f - (1f - floor) * frac.coerceIn(0f, 1f)
                }
                timeMs in (end + 1)..(end + RAMP_MS) -> {
                    val frac = (timeMs - end).toFloat() / RAMP_MS.toFloat()
                    floor + (1f - floor) * frac.coerceIn(0f, 1f)
                }
                else -> 1f
            }
            if (g < minGain) minGain = g
        }
        return minGain.coerceIn(floor, 1f)
    }
}
