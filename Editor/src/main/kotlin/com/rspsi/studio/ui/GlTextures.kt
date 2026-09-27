package com.rspsi.studio.ui

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.nio.ByteBuffer

/**
 * The one way Studio UI uploads a packed-pixel raster to a GL 2D texture (sprites, texture
 * swatches, previews, minimaps). GL thread only.
 *
 * Rasters arrive packed as ints; GL wants RGBA bytes. [rgba] converts, [upload] creates or
 * replaces the texture and sets its filtering.
 */
object GlTextures {
    /** RGBA bytes from 0xAARRGGBB pixels. */
    @JvmStatic
    fun rgba(argb: IntArray): ByteBuffer = rgba(argb) { it ushr 24 }

    /** RGBA bytes from packed pixels, with [alpha] computing each pixel's alpha. */
    inline fun rgba(argb: IntArray, alpha: (Int) -> Int): ByteBuffer {
        val buffer = BufferUtils.createByteBuffer(argb.size * 4)
        for (pixel in argb) {
            buffer.put((pixel shr 16).toByte()).put((pixel shr 8).toByte()).put(pixel.toByte()).put(alpha(pixel).toByte())
        }
        return buffer.flip()
    }

    /**
     * Uploads [pixels] into texture [textureId], or into a new texture when it is 0, and
     * returns the texture id. [wrap] 0 leaves GL's default wrap mode.
     */
    @JvmStatic
    @JvmOverloads
    fun upload(
        textureId: Int,
        width: Int,
        height: Int,
        pixels: ByteBuffer,
        minFilter: Int,
        magFilter: Int,
        wrap: Int = 0,
    ): Int {
        val id = if (textureId != 0) textureId else GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE, pixels)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter)
        if (wrap != 0) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap)
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        return id
    }

    /** Edge clamping for previews; the legacy GL_CLAMP is not valid in a core profile. */
    const val CLAMP_TO_EDGE = GL12.GL_CLAMP_TO_EDGE
}
