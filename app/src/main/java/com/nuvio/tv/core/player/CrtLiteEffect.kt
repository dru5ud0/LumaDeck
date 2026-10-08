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

/**
 * A deliberately inexpensive CRT treatment for SDR ExoPlayer playback.
 *
 * This is one fragment-shader pass: fixed scanlines plus a restrained vignette. It intentionally
 * avoids blur, phosphor persistence, moving noise, and geometric warping so it remains suitable
 * for lower-power Android TV devices.
 */
@UnstableApi
internal class CrtLiteEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return CrtLiteShaderProgram(context, useHdr)
    }
}

@UnstableApi
private class CrtLiteShaderProgram(
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
        glProgram.setFloatsUniform(
            "uDisplayAspectRatio",
            floatArrayOf(inputWidth.toFloat() / inputHeight.coerceAtLeast(1))
        )
        return Size(inputWidth, inputHeight)
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
        const val VERTEX_SHADER_ASSET = "shaders/crt_lite_vertex.glsl"
        const val FRAGMENT_SHADER_ASSET = "shaders/crt_lite_fragment.glsl"
    }
}
