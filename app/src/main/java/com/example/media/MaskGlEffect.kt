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
import com.example.model.MaskSettings
import com.example.model.MaskShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware OpenGL ES 2.0 Media3 [GlEffect] rendering animated Shape Masks
 * (Rectangle with rounded corners, Circle/Ellipse, Linear Split) with smooth feathering,
 * rotation, expansion, and invert onto main clips or video overlays during MP4 export.
 */
@UnstableApi
data class MaskGlEffect(
    val mask: MaskSettings,
    val premultiplyOnBlack: Boolean = true
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return MaskShaderProgram(useHdr = useHdr, mask = mask, premultiplyOnBlack = premultiplyOnBlack)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = !mask.isActive
}

@UnstableApi
private class MaskShaderProgram(
    useHdr: Boolean,
    private val mask: MaskSettings,
    private val premultiplyOnBlack: Boolean
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram
    private var frameW = 1080f
    private var frameH = 1920f

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to initialize MaskShaderProgram", e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        frameW = inputWidth.coerceAtLeast(1).toFloat()
        frameH = inputHeight.coerceAtLeast(1).toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val localMs = (presentationTimeUs / 1000L).coerceAtLeast(0L)
            val geom = MaskGeometryEvaluator.evaluateGeometry(mask, localMs)
            val shapeCode = when (mask.shape) {
                MaskShape.RECTANGLE -> 1f
                MaskShape.CIRCLE -> 2f
                MaskShape.LINEAR_SPLIT -> 3f
                else -> 0f
            }
            // OpenGL texture v=0 is bottom, while Compose/Model centerY=0 is top
            val glCenterY = 1f - geom.centerY
            val rad = (geom.rotationDegrees * PI / 180.0)
            val featherNorm = (mask.featherPx / 360f).coerceIn(0.002f, 0.25f)

            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatUniform("uShapeType", shapeCode)
            glProgram.setFloatsUniform("uCenter", floatArrayOf(geom.centerX, glCenterY))
            glProgram.setFloatsUniform("uHalfSize", floatArrayOf((geom.width * 0.5f).coerceAtLeast(0.02f), (geom.height * 0.5f).coerceAtLeast(0.02f)))
            glProgram.setFloatsUniform("uRot", floatArrayOf(cos(rad).toFloat(), sin(rad).toFloat()))
            glProgram.setFloatUniform("uCornerRadius", mask.cornerRadius.coerceIn(0f, 0.48f))
            glProgram.setFloatUniform("uFeather", featherNorm)
            glProgram.setFloatUniform("uInvert", if (mask.invert) 1f else 0f)
            glProgram.setFloatUniform("uOpacity", mask.opacity.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uPremultBlack", if (premultiplyOnBlack) 1f else 0f)
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
            uniform float uShapeType;
            uniform vec2 uCenter;
            uniform vec2 uHalfSize;
            uniform vec2 uRot;
            uniform float uCornerRadius;
            uniform float uFeather;
            uniform float uInvert;
            uniform float uOpacity;
            uniform float uPremultBlack;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                if (uShapeType < 0.5) {
                    gl_FragColor = src;
                    return;
                }
                vec2 d = vTexSamplingCoord - uCenter;
                // Rotate into local mask space (accounting for GL Y-up)
                vec2 p = vec2(d.x * uRot.x + d.y * uRot.y, -d.x * uRot.y + d.y * uRot.x);
                float signedDist = 0.0;

                if (uShapeType < 1.5) {
                    // Rounded Rectangle SDF
                    float r = uCornerRadius * min(uHalfSize.x, uHalfSize.y) * 2.0;
                    vec2 q = abs(p) - (uHalfSize - vec2(r));
                    signedDist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
                } else if (uShapeType < 2.5) {
                    // Ellipse SDF approximation
                    vec2 n = p / max(uHalfSize, vec2(0.01));
                    float k0 = length(n);
                    signedDist = (k0 - 1.0) * min(uHalfSize.x, uHalfSize.y);
                } else {
                    // Linear Split: left half inside (p.x <= 0)
                    signedDist = p.x;
                }

                float maskAlpha = 1.0 - smoothstep(-uFeather, uFeather, signedDist);
                if (uInvert > 0.5) {
                    maskAlpha = 1.0 - maskAlpha;
                }
                maskAlpha = clamp(maskAlpha * uOpacity, 0.0, 1.0);

                if (uPremultBlack > 0.5) {
                    gl_FragColor = vec4(src.rgb * maskAlpha, src.a);
                } else {
                    gl_FragColor = vec4(src.rgb, src.a * maskAlpha);
                }
            }
        """
    }
}
