package com.rspsi.studio.ui

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.stb.STBImage
import org.slf4j.LoggerFactory

/**
 * HUD and branding textures (compass, minimap frame, world-map button) decoded from the
 * classpath with STB and loaded on first use. A missing or undecodable image yields an
 * invalid [Texture] (id 0) so the HUD falls back instead of failing. GL thread only.
 */
object BrandingAssets {
    private val LOGGER = LoggerFactory.getLogger(BrandingAssets::class.java)

    /** A GL texture and its size; id 0 means the asset could not be loaded. */
    class Texture(@JvmField val id: Int, @JvmField val width: Int, @JvmField val height: Int) {
        fun isValid(): Boolean = id > 0
    }

    private val loaded = HashMap<String, Texture>()

    @JvmStatic
    fun compass(): Texture = texture("/branding/hud/compass.png")

    @JvmStatic
    fun minimapFrame(): Texture = texture("/branding/hud/minimap-frame.png")

    @JvmStatic
    fun worldmapIcon(): Texture = texture("/branding/hud/worldmap-icon.png")

    @JvmStatic
    fun worldmapIconHover(): Texture = texture("/branding/hud/worldmap-icon-hover.png")

    @JvmStatic
    fun dispose() {
        loaded.values.filter { it.isValid() }.forEach { GL11.glDeleteTextures(it.id) }
        loaded.clear()
    }

    private fun texture(path: String): Texture = loaded.getOrPut(path) { load(path) }

    private fun load(path: String): Texture = try {
        val bytes = BrandingAssets::class.java.getResourceAsStream(path)?.use { it.readAllBytes() }
        if (bytes == null) {
            LOGGER.warn("HUD asset not found on classpath: {}", path)
            Texture(0, 0, 0)
        } else {
            val encoded = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()
            val width = IntArray(1)
            val height = IntArray(1)
            val channels = IntArray(1)
            val image = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4)
            if (image == null) {
                LOGGER.error("Failed to decode image from {}: {}", path, STBImage.stbi_failure_reason())
                Texture(0, 0, 0)
            } else {
                try {
                    val id = GlTextures.upload(0, width[0], height[0], image, GL11.GL_LINEAR, GL11.GL_LINEAR)
                    LOGGER.info("Loaded branding asset: {} ({}x{}, GL tex: {})", path, width[0], height[0], id)
                    Texture(id, width[0], height[0])
                } finally {
                    STBImage.stbi_image_free(image)
                }
            }
        }
    } catch (failure: Exception) {
        LOGGER.error("Error loading branding asset {}", path, failure)
        Texture(0, 0, 0)
    }
}
