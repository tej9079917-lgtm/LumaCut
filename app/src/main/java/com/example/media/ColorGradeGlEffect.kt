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
import com.example.model.ColorAdjustments
import com.example.model.ColorMatrixEngine
import com.example.model.CubeLutParser
import java.nio.ByteBuffer

/**
 * Custom Media3 OpenGL ES 2.0 [GlEffect] implementing DaVinci-style color grading:
 * - Lift (shadows), Gamma (midtones power), Gain (highlights)
 * - Highlights & Shadows tonal masks
 * - Tint (green-magenta axis)
 * - 5-point RGB tone curve
 * - Optional `.cube` 3D LUT trilinear sampling packed in a 2D RGBA texture strip
 */
@UnstableApi
data class ColorGradeGlEffect(
    val adjustments: ColorAdjustments
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ColorGradeShaderProgram(useHdr = useHdr, adjustments = adjustments)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return !adjustments.hasAdvancedGrade
    }
}

@UnstableApi
private class ColorGradeShaderProgram(
    useHdr: Boolean,
    private val adjustments: ColorAdjustments
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram
    private val liftRgb: FloatArray
    private val gammaExpRgb: FloatArray
    private val gainRgb: FloatArray
    private val curvePts: FloatArray
    private var lutTexId: Int = 0
    private var lutSize: Float = 0f

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val (lr, lg, lb) = ColorMatrixEngine.wheelToRgbDelta(adjustments.lift, 0.30f)
            val (gr, gg, gb) = ColorMatrixEngine.wheelToRgbDelta(adjustments.gamma, 0.50f)
            val (gnr, gng, gnb) = ColorMatrixEngine.wheelToRgbDelta(adjustments.gain, 0.45f)
            liftRgb = floatArrayOf(lr, lg, lb)
            gammaExpRgb = floatArrayOf(
                1f / (1f + gr).coerceIn(0.45f, 1.85f),
                1f / (1f + gg).coerceIn(0.45f, 1.85f),
                1f / (1f + gb).coerceIn(0.45f, 1.85f)
            )
            gainRgb = floatArrayOf(gnr, gng, gnb)
            val normalizedCurve = adjustments.normalizedCurvePoints
            curvePts = FloatArray(5) { normalizedCurve[it] }

            // Upload optional .cube 3D LUT as a 2D RGBA strip (width = N*N, height = N)
            val parsedLut = CubeLutParser.loadCached(adjustments.cubeLutPath)
            if (parsedLut != null && adjustments.cubeLutIntensity > 0.005f) {
                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                lutTexId = texIds[0]
                lutSize = parsedLut.size.toFloat()
                val bytes = parsedLut.toPackedRgbaBytes()
                val buf = ByteBuffer.allocateDirect(bytes.size).apply {
                    put(bytes)
                    position(0)
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    GLES20.GL_RGBA,
                    parsedLut.size * parsedLut.size,
                    parsedLut.size,
                    0,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    buf
                )
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            }
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to initialize ColorGradeShaderProgram", e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setSamplerTexIdUniform("uLutSampler", if (lutTexId != 0) lutTexId else inputTexId, 1)
            glProgram.setFloatsUniform("uLift", liftRgb)
            glProgram.setFloatsUniform("uGammaExp", gammaExpRgb)
            glProgram.setFloatsUniform("uGain", gainRgb)
            glProgram.setFloatUniform("uShadows", adjustments.shadows.coerceIn(-1f, 1f))
            glProgram.setFloatUniform("uHighlights", adjustments.highlights.coerceIn(-1f, 1f))
            glProgram.setFloatUniform("uHasCurve", if (adjustments.hasNonLinearCurve) 1f else 0f)
            glProgram.setFloatsUniform("uCurvePts", curvePts)
            glProgram.setFloatUniform("uLutSize", lutSize)
            glProgram.setFloatUniform("uLutIntensity", adjustments.cubeLutIntensity.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uFilmGrain", adjustments.filmGrain.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uHalation", adjustments.halation.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uTimeSeed", ((presentationTimeUs / 33_333L) % 997L).toFloat() * 0.017f)
            glProgram.bindAttributesAndUniforms()

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            if (lutTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(lutTexId), 0)
                lutTexId = 0
            }
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
            uniform sampler2D uLutSampler;
            uniform vec3 uLift;
            uniform vec3 uGammaExp;
            uniform vec3 uGain;
            uniform float uShadows;
            uniform float uHighlights;
            uniform float uHasCurve;
            uniform float uCurvePts[5];
            uniform float uLutSize;
            uniform float uLutIntensity;
            uniform float uFilmGrain;
            uniform float uHalation;
            uniform float uTimeSeed;
            varying vec2 vTexSamplingCoord;

            float hashGrain(vec2 co) {
                return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
            }

            float evalCurve(float x) {
                float cx = clamp(x, 0.0, 1.0) * 4.0;
                if (cx < 1.0) return mix(uCurvePts[0], uCurvePts[1], cx);
                if (cx < 2.0) return mix(uCurvePts[1], uCurvePts[2], cx - 1.0);
                if (cx < 3.0) return mix(uCurvePts[2], uCurvePts[3], cx - 2.0);
                return mix(uCurvePts[3], uCurvePts[4], clamp(cx - 3.0, 0.0, 1.0));
            }

            vec3 sampleCubeLut(vec3 rgb, float size) {
                float maxIdx = size - 1.0;
                float blueScaled = clamp(rgb.b, 0.0, 1.0) * maxIdx;
                float slice0 = floor(blueScaled);
                float slice1 = min(slice0 + 1.0, maxIdx);
                float fracB = blueScaled - slice0;

                float width = size * size;
                float u0 = (slice0 * size + clamp(rgb.r, 0.0, 1.0) * maxIdx + 0.5) / width;
                float u1 = (slice1 * size + clamp(rgb.r, 0.0, 1.0) * maxIdx + 0.5) / width;
                float v = (clamp(rgb.g, 0.0, 1.0) * maxIdx + 0.5) / size;

                vec3 c0 = texture2D(uLutSampler, vec2(u0, v)).rgb;
                vec3 c1 = texture2D(uLutSampler, vec2(u1, v)).rgb;
                return mix(c0, c1, fracB);
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 rgb = src.rgb;

                // Highlights & Shadows tonal lift/recovery
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                float shadowMask = (1.0 - luma) * (1.0 - luma);
                float highMask = luma * luma;
                rgb = clamp(rgb + vec3(uShadows * 0.28 * shadowMask + uHighlights * 0.28 * highMask), 0.0, 1.0);

                // DaVinci Lift / Gain / Gamma
                rgb = clamp(rgb * (vec3(1.0) + uGain) + uLift * (vec3(1.0) - rgb), 0.0, 1.0);
                rgb = pow(max(rgb, vec3(0.0001)), uGammaExp);

                // Optional RGB Tone Curve
                if (uHasCurve > 0.5) {
                    rgb = vec3(evalCurve(rgb.r), evalCurve(rgb.g), evalCurve(rgb.b));
                }

                // Optional .cube 3D LUT
                if (uLutSize > 1.5 && uLutIntensity > 0.005) {
                    vec3 lutColor = sampleCubeLut(rgb, uLutSize);
                    rgb = mix(rgb, lutColor, uLutIntensity);
                }

                // Warm analog highlight halation bloom
                if (uHalation > 0.005) {
                    float hiLuma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                    float bloom = smoothstep(0.62, 1.0, hiLuma) * uHalation;
                    rgb += vec3(bloom * 0.34, bloom * 0.14, bloom * 0.03);
                }

                // Procedural 35mm analog film grain
                if (uFilmGrain > 0.005) {
                    float n = hashGrain(vTexSamplingCoord * 512.0 + vec2(uTimeSeed, uTimeSeed * 1.7)) - 0.5;
                    rgb += vec3(n * uFilmGrain * 0.18);
                }

                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), src.a);
            }
        """
    }
}
