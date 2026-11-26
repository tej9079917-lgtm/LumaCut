package com.example.media

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.model.ChromaKeyColor
import com.example.model.ChromaKeySettings
import kotlin.math.hypot

/**
 * Pure YCbCr chroma-key math mirroring [ChromaKeyShaderProgram]'s GLSL fragment shader,
 * supporting preset green/blue/cyan/magenta screens and custom eyedropper-sampled RGB colors.
 */
object ChromaKeyMath {

    fun rgbToCbCr(r: Float, g: Float, b: Float): Pair<Float, Float> {
        val cb = -0.168736f * r - 0.331264f * g + 0.500000f * b
        val cr = 0.500000f * r - 0.418688f * g - 0.081312f * b
        return cb to cr
    }

    /**
     * Determines spill suppression mode:
     * 0 = Green dominant, 1 = Blue dominant, 2 = Cyan, 3 = Magenta, 4 = Red dominant.
     */
    fun resolveSpillMode(settings: ChromaKeySettings): Int {
        if (settings.customColorArgb == null) return settings.keyColor.ordinal
        val (kr, kg, kb) = settings.effectiveKeyRgb()
        return when {
            kg >= kr && kg >= kb -> 0
            kb >= kr && kb >= kg -> 1
            else -> 4
        }
    }

    fun computeChromaAlpha(
        r: Float,
        g: Float,
        b: Float,
        settings: ChromaKeySettings
    ): Float {
        if (!settings.enabled) return 1f
        val (cb, cr) = rgbToCbCr(r, g, b)
        val (kr, kg, kb) = settings.effectiveKeyRgb()
        val (keyCb, keyCr) = rgbToCbCr(kr, kg, kb)
        val dist = hypot((cb - keyCb).toDouble(), (cr - keyCr).toDouble()).toFloat()
        val edge0 = settings.similarity.coerceIn(0.05f, 0.85f)
        val edge1 = edge0 + settings.smoothness.coerceIn(0.005f, 0.40f)
        val t = ((dist - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun suppressSpill(
        r: Float,
        g: Float,
        b: Float,
        settings: ChromaKeySettings
    ): Triple<Float, Float, Float> {
        if (!settings.enabled || settings.spillSuppression <= 0.001f) return Triple(r, g, b)
        val strength = settings.spillSuppression.coerceIn(0f, 1f)
        return when (resolveSpillMode(settings)) {
            0 -> { // Green dominant
                val limit = (r + b) * 0.5f
                val excess = (g - limit).coerceAtLeast(0f)
                Triple(r, (g - excess * strength).coerceIn(0f, 1f), b)
            }
            1 -> { // Blue dominant
                val limit = (r + g) * 0.5f
                val excess = (b - limit).coerceAtLeast(0f)
                Triple(r, g, (b - excess * strength).coerceIn(0f, 1f))
            }
            2 -> { // Cyan
                val excessG = (g - r).coerceAtLeast(0f)
                val excessB = (b - r).coerceAtLeast(0f)
                Triple(
                    r,
                    (g - excessG * strength * 0.65f).coerceIn(0f, 1f),
                    (b - excessB * strength * 0.65f).coerceIn(0f, 1f)
                )
            }
            3 -> { // Magenta
                val excessR = (r - g).coerceAtLeast(0f)
                val excessB = (b - g).coerceAtLeast(0f)
                Triple(
                    (r - excessR * strength * 0.65f).coerceIn(0f, 1f),
                    g,
                    (b - excessB * strength * 0.65f).coerceIn(0f, 1f)
                )
            }
            else -> { // Red dominant (custom warm key)
                val limit = (g + b) * 0.5f
                val excess = (r - limit).coerceAtLeast(0f)
                Triple((r - excess * strength).coerceIn(0f, 1f), g, b)
            }
        }
    }
}

/**
 * Custom Media3 [GlEffect] implementing real-time GPU chroma key background removal
 * (YCbCr chroma keying + smoothstep edge feathering + spill suppression) with both
 * preset screen colors and custom eyedropper-sampled key colors.
 */
@UnstableApi
data class ChromaKeyGlEffect(
    val settings: ChromaKeySettings,
    val premultiplyAlpha: Boolean = false
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ChromaKeyShaderProgram(
            useHdr = useHdr,
            settings = settings,
            premultiplyAlpha = premultiplyAlpha
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return !settings.enabled
    }
}

@UnstableApi
private class ChromaKeyShaderProgram(
    useHdr: Boolean,
    private val settings: ChromaKeySettings,
    private val premultiplyAlpha: Boolean
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram
    private val keyCb: Float
    private val keyCr: Float
    private val spillMode: Int

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val (kr, kg, kb) = settings.effectiveKeyRgb()
            val (cb, cr) = ChromaKeyMath.rgbToCbCr(kr, kg, kb)
            keyCb = cb
            keyCr = cr
            spillMode = ChromaKeyMath.resolveSpillMode(settings)
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to initialize ChromaKeyShaderProgram", e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uKeyCbCr", floatArrayOf(keyCb, keyCr))
            glProgram.setFloatUniform("uSimilarity", settings.similarity.coerceIn(0.05f, 0.85f))
            glProgram.setFloatUniform("uSmoothness", settings.smoothness.coerceIn(0.005f, 0.40f))
            glProgram.setFloatUniform("uSpillSuppression", settings.spillSuppression.coerceIn(0f, 1f))
            glProgram.setIntUniform("uKeyMode", spillMode)
            glProgram.setIntUniform("uPremultiply", if (premultiplyAlpha) 1 else 0)
            glProgram.bindAttributesAndUniforms()

            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 100
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uKeyCbCr;
            uniform float uSimilarity;
            uniform float uSmoothness;
            uniform float uSpillSuppression;
            uniform int uKeyMode;
            uniform int uPremultiply;
            varying vec2 vTexSamplingCoord;

            vec2 toCbCr(vec3 rgb) {
                float cb = -0.168736 * rgb.r - 0.331264 * rgb.g + 0.500000 * rgb.b;
                float cr =  0.500000 * rgb.r - 0.418688 * rgb.g - 0.081312 * rgb.b;
                return vec2(cb, cr);
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec2 chroma = toCbCr(src.rgb);
                float dist = distance(chroma, uKeyCbCr);
                float alphaMask = smoothstep(uSimilarity, uSimilarity + max(uSmoothness, 0.005), dist);

                vec3 rgb = src.rgb;
                if (uSpillSuppression > 0.001) {
                    if (uKeyMode == 0) {
                        float limit = (rgb.r + rgb.b) * 0.5;
                        float excess = max(rgb.g - limit, 0.0);
                        rgb.g = clamp(rgb.g - excess * uSpillSuppression, 0.0, 1.0);
                    } else if (uKeyMode == 1) {
                        float limit = (rgb.r + rgb.g) * 0.5;
                        float excess = max(rgb.b - limit, 0.0);
                        rgb.b = clamp(rgb.b - excess * uSpillSuppression, 0.0, 1.0);
                    } else if (uKeyMode == 2) {
                        float excessG = max(rgb.g - rgb.r, 0.0);
                        float excessB = max(rgb.b - rgb.r, 0.0);
                        rgb.g = clamp(rgb.g - excessG * uSpillSuppression * 0.65, 0.0, 1.0);
                        rgb.b = clamp(rgb.b - excessB * uSpillSuppression * 0.65, 0.0, 1.0);
                    } else if (uKeyMode == 3) {
                        float excessR = max(rgb.r - rgb.g, 0.0);
                        float excessB = max(rgb.b - rgb.g, 0.0);
                        rgb.r = clamp(rgb.r - excessR * uSpillSuppression * 0.65, 0.0, 1.0);
                        rgb.b = clamp(rgb.b - excessB * uSpillSuppression * 0.65, 0.0, 1.0);
                    } else {
                        float limit = (rgb.g + rgb.b) * 0.5;
                        float excess = max(rgb.r - limit, 0.0);
                        rgb.r = clamp(rgb.r - excess * uSpillSuppression, 0.0, 1.0);
                    }
                }

                float finalAlpha = src.a * alphaMask;
                if (uPremultiply == 1) {
                    gl_FragColor = vec4(rgb * finalAlpha, finalAlpha);
                } else {
                    gl_FragColor = vec4(rgb, finalAlpha);
                }
            }
        """
    }
}
