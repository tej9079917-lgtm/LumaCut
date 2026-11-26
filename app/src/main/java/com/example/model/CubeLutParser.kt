package com.example.model

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Parsed Adobe / Iridas standard `.cube` 3D LUT lattice with trilinear RGB interpolation
 * and OpenGL texture packing support.
 */
data class CubeLut3D(
    val title: String,
    val size: Int,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    /** Flat RGB float array of length `size * size * size * 3`, ordered R-fastest (standard .cube order). */
    val table: FloatArray
) {
    /**
     * Evaluates the 3D LUT at normalized input RGB `(0..1)` using trilinear interpolation.
     */
    fun sample(rIn: Float, gIn: Float, bIn: Float): Triple<Float, Float, Float> {
        val nr = normalize(rIn, domainMin[0], domainMax[0]) * (size - 1)
        val ng = normalize(gIn, domainMin[1], domainMax[1]) * (size - 1)
        val nb = normalize(bIn, domainMin[2], domainMax[2]) * (size - 1)

        val r0 = floor(nr).toInt().coerceIn(0, size - 1)
        val g0 = floor(ng).toInt().coerceIn(0, size - 1)
        val b0 = floor(nb).toInt().coerceIn(0, size - 1)
        val r1 = (r0 + 1).coerceAtMost(size - 1)
        val g1 = (g0 + 1).coerceAtMost(size - 1)
        val b1 = (b0 + 1).coerceAtMost(size - 1)

        val fr = (nr - r0).coerceIn(0f, 1f)
        val fg = (ng - g0).coerceIn(0f, 1f)
        val fb = (nb - b0).coerceIn(0f, 1f)

        val c000 = idx(r0, g0, b0)
        val c100 = idx(r1, g0, b0)
        val c010 = idx(r0, g1, b0)
        val c110 = idx(r1, g1, b0)
        val c001 = idx(r0, g0, b1)
        val c101 = idx(r1, g0, b1)
        val c011 = idx(r0, g1, b1)
        val c111 = idx(r1, g1, b1)

        val outR = trilerp(c000, c100, c010, c110, c001, c101, c011, c111, 0, fr, fg, fb)
        val outG = trilerp(c000, c100, c010, c110, c001, c101, c011, c111, 1, fr, fg, fb)
        val outB = trilerp(c000, c100, c010, c110, c001, c101, c011, c111, 2, fr, fg, fb)
        return Triple(outR.coerceIn(0f, 1f), outG.coerceIn(0f, 1f), outB.coerceIn(0f, 1f))
    }

    /**
     * Packs the 3D LUT into a 2D RGBA8888 horizontal strip `(width = size * size, height = size)`
     * where X encodes `bSlice * size + rIndex` and Y encodes `gIndex`, ideal for OpenGL ES 2.0 2D samplers.
     */
    fun toPackedRgbaBytes(): ByteArray {
        val width = size * size
        val height = size
        val out = ByteArray(width * height * 4)
        for (g in 0 until size) {
            for (b in 0 until size) {
                for (r in 0 until size) {
                    val src = idx(r, g, b)
                    val x = b * size + r
                    val dst = (g * width + x) * 4
                    out[dst] = (table[src].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
                    out[dst + 1] = (table[src + 1].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
                    out[dst + 2] = (table[src + 2].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
                    out[dst + 3] = 255.toByte()
                }
            }
        }
        return out
    }

    /**
     * Approximates a 4x5 Compose ColorMatrix from 4 basis probes of the 3D LUT so live
     * non-GL previews reflect the imported `.cube` look immediately.
     */
    fun toApproximateColorMatrix(): FloatArray {
        val (r0, g0, b0) = sample(0.05f, 0.05f, 0.05f)
        val (rr, gr, br) = sample(0.95f, 0.05f, 0.05f)
        val (rg, gg, bg) = sample(0.05f, 0.95f, 0.05f)
        val (rb, gb, bb) = sample(0.05f, 0.05f, 0.95f)
        val span = 0.90f
        return floatArrayOf(
            (rr - r0) / span, (rg - r0) / span, (rb - r0) / span, 0f, (r0 - 0.05f * ((rr - r0) / span)) * 255f,
            (gr - g0) / span, (gg - g0) / span, (gb - g0) / span, 0f, (g0 - 0.05f * ((gg - g0) / span)) * 255f,
            (br - b0) / span, (bg - b0) / span, (bb - b0) / span, 0f, (b0 - 0.05f * ((bb - b0) / span)) * 255f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    private fun idx(r: Int, g: Int, b: Int): Int = ((b * size + g) * size + r) * 3

    private fun normalize(v: Float, minV: Float, maxV: Float): Float {
        val denom = (maxV - minV).takeIf { it > 1e-5f } ?: 1f
        return ((v - minV) / denom).coerceIn(0f, 1f)
    }

    private fun trilerp(
        c000: Int, c100: Int, c010: Int, c110: Int,
        c001: Int, c101: Int, c011: Int, c111: Int,
        ch: Int, fr: Float, fg: Float, fb: Float
    ): Float {
        val x00 = table[c000 + ch] * (1f - fr) + table[c100 + ch] * fr
        val x10 = table[c010 + ch] * (1f - fr) + table[c110 + ch] * fr
        val x01 = table[c001 + ch] * (1f - fr) + table[c101 + ch] * fr
        val x11 = table[c011 + ch] * (1f - fr) + table[c111 + ch] * fr
        val y0 = x00 * (1f - fg) + x10 * fg
        val y1 = x01 * (1f - fg) + x11 * fg
        return y0 * (1f - fb) + y1 * fb
    }
}

object CubeLutParser {
    private val cache = ConcurrentHashMap<String, CubeLut3D>()

    fun loadCached(filePath: String?): CubeLut3D? {
        if (filePath.isNullOrBlank()) return null
        cache[filePath]?.let { return it }
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null
        val parsed = runCatching { parse(file.readText(), fallbackTitle = file.nameWithoutExtension) }.getOrNull()
        if (parsed != null) {
            cache[filePath] = parsed
        }
        return parsed
    }

    fun clearCache() {
        cache.clear()
    }

    fun parse(content: String, fallbackTitle: String = "Custom 3D LUT"): CubeLut3D {
        var title = fallbackTitle
        var lutSize = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>(17 * 17 * 17 * 3)

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val upper = line.uppercase()
            when {
                upper.startsWith("TITLE") -> {
                    val extracted = line.substringAfter(' ', "").trim().trim('"')
                    if (extracted.isNotEmpty()) title = extracted
                }
                upper.startsWith("LUT_3D_SIZE") -> {
                    lutSize = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
                }
                upper.startsWith("DOMAIN_MIN") -> {
                    val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toFloatOrNull() }
                    if (parts.size >= 3) domainMin = floatArrayOf(parts[0], parts[1], parts[2])
                }
                upper.startsWith("DOMAIN_MAX") -> {
                    val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toFloatOrNull() }
                    if (parts.size >= 3) domainMax = floatArrayOf(parts[0], parts[1], parts[2])
                }
                upper.startsWith("LUT_1D_SIZE") -> {
                    // 1D LUTs are unsupported directly; require 3D LUT
                }
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val r = parts[0].toFloatOrNull()
                        val g = parts[1].toFloatOrNull()
                        val b = parts[2].toFloatOrNull()
                        if (r != null && g != null && b != null) {
                            values.add(r.coerceIn(0f, 1f))
                            values.add(g.coerceIn(0f, 1f))
                            values.add(b.coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }

        require(lutSize in 2..65) { "Invalid or missing LUT_3D_SIZE in .cube file (expected 2..65)." }
        val expectedFloats = lutSize * lutSize * lutSize * 3
        require(values.size >= expectedFloats) {
            "Incomplete .cube 3D LUT table: expected ${lutSize * lutSize * lutSize} RGB triplets, found ${values.size / 3}."
        }
        val table = FloatArray(expectedFloats) { values[it] }
        return CubeLut3D(
            title = title,
            size = lutSize,
            domainMin = domainMin,
            domainMax = domainMax,
            table = table
        )
    }
}
