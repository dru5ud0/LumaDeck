package com.nuvio.tv.core.player

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
import java.io.IOException
import kotlin.math.roundToInt

/**
 * CRT Full performs a single curved-screen shader pass and outputs no more than 480 vertical
 * pixels. The player scales that texture to the display, giving a deliberately low-resolution CRT
 * appearance without decoding the source a second time.
 */
@UnstableApi
internal class CrtFullEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return CrtFullShaderProgram(context, useHdr)
    }
}

@UnstableApi
private class CrtFullShaderProgram(
    context: Context,
    useHdr: Boolean
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ useHdr,
    /* texturePoolCapacity = */ 1
) {
    private val glProgram: GlProgram

    init {
        try {
            glProgram = GlProgram(context, VERTEX_SHADER_ASSET, FRAGMENT_SHADER_ASSET)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        } catch (error: IOException) {
            throw VideoFrameProcessingException(error)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val height = inputHeight.coerceAtMost(CRT_OUTPUT_HEIGHT)
        val scale = height.toFloat() / inputHeight.coerceAtLeast(1)
        val width = (inputWidth * scale).roundToInt().coerceAtLeast(2).let { value ->
            if (value % 2 == 0) value else value - 1
        }
        glProgram.setFloatsUniform(
            "uSourceAspectRatio",
            floatArrayOf(inputWidth.toFloat() / inputHeight.coerceAtLeast(1))
        )
        return Size(width, height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    private companion object {
        const val CRT_OUTPUT_HEIGHT = 480
        const val VERTEX_SHADER_ASSET = "shaders/crt_full_vertex.glsl"
        const val FRAGMENT_SHADER_ASSET = "shaders/crt_full_fragment.glsl"
    }
}
