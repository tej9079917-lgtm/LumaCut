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
import com.example.model.CropRect
import com.example.model.FxLayer
import com.example.model.FxShape
import com.example.model.FxType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pure JVM-testable geometry and timing evaluator for Visual FX layers
 * (Magnifier, Mosaic, Blur) and CropRect aspect presets.
 */
object VisualFxMath {

    fun isLayerActiveAt(layer: FxLayer, globalTimeMs: Long): Boolean =
        globalTimeMs in layer.startTimeMs..layer.endTimeMs

    /**
     * Evaluates signed region inclusion weight [0..1] for a normalized point (nx, ny) in [0..1].
     * Returns 1.0 everywhere when [layer.fullFrame] is true.
     */
    fun evaluateRegionWeight(layer: FxLayer, nx: Float, ny: Float): Float {
        if (layer.fullFrame) return layer.opacity.coerceIn(0f, 1f)
        val halfW = (layer.width * 0.5f).coerceAtLeast(0.03f)
        val halfH = (layer.height * 0.5f).coerceAtLeast(0.03f)
        val dx = nx - layer.centerX
        val dy = ny - layer.centerY
        val feather = layer.feather.coerceIn(0.002f, 0.30f)

        val signedDist = when (layer.shape) {
            FxShape.CIRCLE -> {
                val radial = hypot(dx / halfW, dy / halfH)
                (radial - 1f) * min(halfW, halfH)
            }
            FxShape.ROUNDED_RECT -> {
                val r = 0.14f * min(halfW, halfH) * 2f
                val qx = abs(dx) - (halfW - r)
                val qy = abs(dy) - (halfH - r)
                hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - r
            }
        }
        val smooth = smoothstep(-feather, feather, signedDist)
        return ((1f - smooth) * layer.opacity).coerceIn(0f, 1f)
    }

    /**
     * Computes a centered [CropRect] matching a named KineMaster-style aspect preset
     * relative to the current canvas aspect ratio.
     */
    fun cropPresetForRatio(preset: String, stageAspect: Float): CropRect {
        val targetAspect = when (preset) {
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "1:1" -> 1f
            "4:5" -> 4f / 5f
            "4:3" -> 4f / 3f
            else -> return CropRect(0f, 0f, 0f, 0f, "Free")
        }
        val safeStage = stageAspect.coerceIn(0.25f, 4f)
        return if (targetAspect > safeStage) {
            // Wider target -> trim top & bottom
            val desiredH = (safeStage / targetAspect).coerceIn(0.2f, 1f)
            val marginV = ((1f - desiredH) * 0.5f).coerceIn(0f, 0.42f)
            CropRect(left = 0f, top = marginV, right = 0f, bottom = marginV, aspectPreset = preset)
        } else {
            // Taller target -> trim left & right
            val desiredW = (targetAspect / safeStage).coerceIn(0.2f, 1f)
            val marginH = ((1f - desiredW) * 0.5f).coerceIn(0f, 0.42f)
            CropRect(left = marginH, top = 0f, right = marginH, bottom = 0f, aspectPreset = preset)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * Hardware OpenGL ES 2.0 Media3 [GlEffect] rendering time-windowed Magnifier, Mosaic,
 * and Gaussian Blur effects in either Movable Spot Region or Full-Frame mode during MP4 export.
 */
@UnstableApi
data class VisualFxGlEffect(
    val fxLayer: FxLayer,
    val clipGlobalStartMs: Long
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return VisualFxShaderProgram(
            useHdr = useHdr,
            fxLayer = fxLayer,
            clipGlobalStartMs = clipGlobalStartMs
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = fxLayer.opacity <= 0.01f
}

@UnstableApi
private class VisualFxShaderProgram(
    useHdr: Boolean,
    private val fxLayer: FxLayer,
    private val clipGlobalStartMs: Long
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
            throw VideoFrameProcessingException("Failed to initialize VisualFxShaderProgram", e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        frameW = inputWidth.coerceAtLeast(1).toFloat()
        frameH = inputHeight.coerceAtLeast(1).toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val globalMs = clipGlobalStartMs + (presentationTimeUs / 1000L).coerceAtLeast(0L)
            val active = VisualFxMath.isLayerActiveAt(fxLayer, globalMs)

            val fxCode = when {
                !active -> 0f
                fxLayer.type == FxType.MAGNIFIER -> 1f
                fxLayer.type == FxType.MOSAIC -> 2f
                fxLayer.type == FxType.BLUR -> 3f
                else -> 0f
            }
            val shapeCode = if (fxLayer.shape == FxShape.CIRCLE) 1f else 2f
            val glCenterY = 1f - fxLayer.centerY.coerceIn(0.02f, 0.98f)

            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatUniform("uFxType", fxCode)
            glProgram.setFloatUniform("uFullFrame", if (fxLayer.fullFrame) 1f else 0f)
            glProgram.setFloatUniform("uShapeType", shapeCode)
            glProgram.setFloatsUniform("uCenter", floatArrayOf(fxLayer.centerX.coerceIn(0.02f, 0.98f), glCenterY))
            glProgram.setFloatsUniform(
                "uHalfSize",
                floatArrayOf(
                    (fxLayer.width * 0.5f).coerceIn(0.04f, 0.5f),
                    (fxLayer.height * 0.5f).coerceIn(0.04f, 0.5f)
                )
            )
            glProgram.setFloatsUniform("uTexSize", floatArrayOf(frameW, frameH))
            glProgram.setFloatUniform("uIntensity", fxLayer.intensity.coerceIn(0.05f, 1f))
            glProgram.setFloatUniform("uMagnification", fxLayer.magnification.coerceIn(1.2f, 4.0f))
            glProgram.setFloatUniform("uFeather", fxLayer.feather.coerceIn(0.003f, 0.28f))
            glProgram.setFloatUniform("uOpacity", fxLayer.opacity.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uBorderRing", if (fxLayer.showBorderRing && !fxLayer.fullFrame) 1f else 0f)
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
            uniform float uFxType;
            uniform float uFullFrame;
            uniform float uShapeType;
            uniform vec2 uCenter;
            uniform vec2 uHalfSize;
            uniform vec2 uTexSize;
            uniform float uIntensity;
            uniform float uMagnification;
            uniform float uFeather;
            uniform float uOpacity;
            uniform float uBorderRing;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 baseCol = texture2D(uTexSampler, uv);
                if (uFxType < 0.5) {
                    gl_FragColor = baseCol;
                    return;
                }

                float signedDist = -1.0;
                if (uFullFrame < 0.5) {
                    vec2 d = uv - uCenter;
                    if (uShapeType < 1.5) {
                        vec2 n = d / max(uHalfSize, vec2(0.01));
                        signedDist = (length(n) - 1.0) * min(uHalfSize.x, uHalfSize.y);
                    } else {
                        float r = 0.14 * min(uHalfSize.x, uHalfSize.y) * 2.0;
                        vec2 q = abs(d) - (uHalfSize - vec2(r));
                        signedDist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
                    }
                }

                float weight = (uFullFrame > 0.5) ? 1.0 : (1.0 - smoothstep(-uFeather, uFeather, signedDist));
                weight = clamp(weight * uOpacity, 0.0, 1.0);

                if (weight <= 0.001) {
                    gl_FragColor = baseCol;
                    return;
                }

                vec4 fxCol = baseCol;
                if (uFxType < 1.5) {
                    // MAGNIFIER: radial zoom around lens center with subtle glass specular lift
                    vec2 centerPt = (uFullFrame > 0.5) ? vec2(0.5, 0.5) : uCenter;
                    vec2 zoomedUv = centerPt + (uv - centerPt) / max(uMagnification, 1.05);
                    zoomedUv = clamp(zoomedUv, vec2(0.001), vec2(0.999));
                    fxCol = texture2D(uTexSampler, zoomedUv);
                    fxCol.rgb = clamp(fxCol.rgb * 1.03, 0.0, 1.0);
                } else if (uFxType < 2.5) {
                    // MOSAIC: crisp block pixelation
                    float blockPx = mix(6.0, 56.0, uIntensity);
                    vec2 grid = max(uTexSize / blockPx, vec2(4.0));
                    vec2 mosaicUv = (floor(uv * grid) + vec2(0.5)) / grid;
                    fxCol = texture2D(uTexSampler, clamp(mosaicUv, vec2(0.001), vec2(0.999)));
                } else {
                    // GAUSSIAN BLUR: 13-tap dual-ring circular blur kernel
                    vec2 texel = (mix(1.5, 16.0, uIntensity)) / max(uTexSize, vec2(64.0));
                    vec4 acc = baseCol * 0.20;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( texel.x,  0.0), 0.001, 0.999)) * 0.09;
                    acc += texture2D(uTexSampler, clamp(uv + vec2(-texel.x,  0.0), 0.001, 0.999)) * 0.09;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( 0.0,  texel.y), 0.001, 0.999)) * 0.09;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( 0.0, -texel.y), 0.001, 0.999)) * 0.09;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( texel.x,  texel.y) * 1.4, 0.001, 0.999)) * 0.06;
                    acc += texture2D(uTexSampler, clamp(uv + vec2(-texel.x,  texel.y) * 1.4, 0.001, 0.999)) * 0.06;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( texel.x, -texel.y) * 1.4, 0.001, 0.999)) * 0.06;
                    acc += texture2D(uTexSampler, clamp(uv + vec2(-texel.x, -texel.y) * 1.4, 0.001, 0.999)) * 0.06;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( texel.x * 2.2, 0.0), 0.001, 0.999)) * 0.05;
                    acc += texture2D(uTexSampler, clamp(uv + vec2(-texel.x * 2.2, 0.0), 0.001, 0.999)) * 0.05;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( 0.0,  texel.y * 2.2), 0.001, 0.999)) * 0.05;
                    acc += texture2D(uTexSampler, clamp(uv + vec2( 0.0, -texel.y * 2.2), 0.001, 0.999)) * 0.05;
                    fxCol = acc;
                }

                vec4 blended = mix(baseCol, fxCol, weight);

                // Optional crisp glass rim on Magnifier lens
                if (uFxType < 1.5 && uBorderRing > 0.5 && abs(signedDist) < 0.006) {
                    blended.rgb = mix(blended.rgb, vec3(0.96, 0.68, 0.12), 0.85);
                }
                gl_FragColor = blended;
            }
        """
    }
}
