package com.rspsi.studio.ui

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * One OpenGL texture holding a world-map raster, uploaded from a packed int array.
 *
 * Rasters arrive as ARGB because that is what
 * [com.rspsi.editor.minimap.MinimapBuilder] produces; the byte order is fixed
 * here so every world-map surface shares one upload path instead of each
 * caller packing its own buffer.
 *
 * Uploads move one int per texel, which GL reads in native byte order, so the
 * fast path assumes a little-endian host. That covers every desktop platform
 * the studio targets; [upload] falls back to an explicit byte buffer elsewhere
 * rather than silently uploading swapped channels.
 */
class WorldMapRasterTexture(private val minMagFilter: Int) {

    var id: Int = 0
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private var allocated = false
    private var packed: IntArray? = null
    private var words: IntBuffer? = null
    private var bytes: ByteBuffer? = null

    fun hasContent(): Boolean = id != 0 && allocated

    /** Uploads a raster, reallocating the texture when its size changes. */
    fun upload(argb: IntArray, rasterWidth: Int, rasterHeight: Int): Boolean {
        if (rasterWidth <= 0 || rasterHeight <= 0) return false
        val count = rasterWidth * rasterHeight
        if (argb.size < count) return false
        if (id == 0) id = GL11.glGenTextures()

        val fresh = !allocated || width != rasterWidth || height != rasterHeight
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        if (LITTLE_ENDIAN_HOST) {
            val buffer = wordBuffer(count)
            buffer.clear()
            buffer.put(packGlOrder(argb, count), 0, count)
            buffer.flip()
            if (fresh) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, rasterWidth, rasterHeight,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, rasterWidth, rasterHeight,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
            }
        } else {
            val buffer = byteBuffer(count)
            buffer.clear()
            for (index in 0 until count) {
                val pixel = argb[index]
                buffer.put(((pixel shr 16) and 0xFF).toByte())
                buffer.put(((pixel shr 8) and 0xFF).toByte())
                buffer.put((pixel and 0xFF).toByte())
                buffer.put(((pixel ushr 24) and 0xFF).toByte())
            }
            buffer.flip()
            if (fresh) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, rasterWidth, rasterHeight,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, rasterWidth, rasterHeight,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
            }
        }
        applyParameters()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)

        width = rasterWidth
        height = rasterHeight
        allocated = true
        return true
    }

    /**
     * Frees the CPU copy and native staging buffer kept for re-uploads. Textures uploaded
     * once (world-map tiles) call this so each tile costs only its GPU storage.
     */
    fun releaseStaging() {
        words?.let { MemoryUtil.memFree(it) }
        words = null
        bytes?.let { MemoryUtil.memFree(it) }
        bytes = null
        packed = null
    }

    fun dispose() {
        if (id != 0) {
            GL11.glDeleteTextures(id)
            id = 0
        }
        words?.let { MemoryUtil.memFree(it) }
        words = null
        bytes?.let { MemoryUtil.memFree(it) }
        bytes = null
        packed = null
        allocated = false
        width = 0
        height = 0
    }

    private fun applyParameters() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minMagFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, minMagFilter)
        // Texels the bake has not reached stay transparent so the availability
        // grid underneath still reads through, and CLAMP is not a core-profile
        // wrap mode, so sampling past the edge must take the border colour.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER)
        GL11.glTexParameterfv(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, TRANSPARENT_BORDER)
    }

    private fun wordBuffer(count: Int): IntBuffer {
        val existing = words
        if (existing != null && existing.capacity() == count) return existing
        existing?.let { MemoryUtil.memFree(it) }
        val created = MemoryUtil.memAllocInt(count)
        words = created
        return created
    }

    private fun byteBuffer(count: Int): ByteBuffer {
        val existing = bytes
        if (existing != null && existing.capacity() == count) return existing
        existing?.let { MemoryUtil.memFree(it) }
        val created = MemoryUtil.memAlloc(count * 4)
        bytes = created
        return created
    }

    /** Converts ARGB (0xAARRGGBB) into the 0xAABBGGRR GL reads from a native int. */
    private fun packGlOrder(argb: IntArray, count: Int): IntArray {
        val target = packed
        if (target == null || target.size < count) {
            val created = IntArray(count)
            packed = created
            fill(created, argb, count)
            return created
        }
        fill(target, argb, count)
        return target
    }

    private fun fill(target: IntArray, argb: IntArray, count: Int) {
        var index = 0
        while (index < count) {
            val pixel = argb[index]
            val a = pixel and 0xFF000000.toInt()
            val r = (pixel shr 16) and 0xFF
            val g = pixel and 0x0000FF00
            val b = (pixel and 0xFF) shl 16
            target[index] = a or b or g or r
            index++
        }
    }

    private companion object {
        val LITTLE_ENDIAN_HOST: Boolean = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        val TRANSPARENT_BORDER = floatArrayOf(0f, 0f, 0f, 0f)
    }
}
