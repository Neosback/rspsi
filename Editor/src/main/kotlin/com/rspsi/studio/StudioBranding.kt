package com.rspsi.studio

import imgui.ImGui
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

/**
 * The OpenRune wordmark texture drawn by the launcher and loading gate. Loaded lazily from
 * the classpath on first use and released by [close]. GL thread only.
 */
object StudioBranding {
    private const val WORDMARK_RESOURCE = "/images/openrune-wordmark.png"

    private var wordmarkTexture = 0
    private var wordmarkWidth = 0
    private var wordmarkHeight = 0

    /** Draws the wordmark [displayWidth] wide, keeping its aspect ratio. */
    @JvmStatic
    fun drawWordmark(displayWidth: Float) {
        ensureLoaded()
        val width = maxOf(1.0f, displayWidth)
        ImGui.image(wordmarkTexture.toLong(), width, width * wordmarkHeight / wordmarkWidth.toFloat())
    }

    /** The wordmark's height when drawn [displayWidth] wide. */
    @JvmStatic
    fun wordmarkHeight(displayWidth: Float): Float {
        ensureLoaded()
        return maxOf(1.0f, displayWidth) * wordmarkHeight / wordmarkWidth.toFloat()
    }

    @JvmStatic
    fun close() {
        if (wordmarkTexture == 0) return
        GL11.glDeleteTextures(wordmarkTexture)
        wordmarkTexture = 0
        wordmarkWidth = 0
        wordmarkHeight = 0
    }

    private fun ensureLoaded() {
        if (wordmarkTexture != 0) return
        val bytes = StudioBranding::class.java.getResourceAsStream(WORDMARK_RESOURCE)?.use { it.readAllBytes() }
            ?: throw IllegalStateException("Missing OpenRune wordmark: $WORDMARK_RESOURCE")
        val encoded = MemoryUtil.memAlloc(bytes.size)
        try {
            encoded.put(bytes).flip()
            MemoryStack.stackPush().use { stack ->
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                val channels = stack.mallocInt(1)
                val pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4)
                    ?: throw IllegalStateException("Unable to decode OpenRune wordmark: ${STBImage.stbi_failure_reason()}")
                try {
                    wordmarkWidth = width.get(0)
                    wordmarkHeight = height.get(0)
                    // Restore the caller's texture binding and unpack alignment afterwards.
                    val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                    val previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
                    wordmarkTexture = GL11.glGenTextures()
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, wordmarkTexture)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, wordmarkWidth, wordmarkHeight, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment)
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                } finally {
                    STBImage.stbi_image_free(pixels)
                }
            }
        } finally {
            MemoryUtil.memFree(encoded)
        }
    }
}
