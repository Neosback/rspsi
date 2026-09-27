package com.rspsi.studio.ui

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import org.lwjgl.opengl.GL11
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Real decoded OSRS ground textures for small UI swatches (Tile Inspector, Tile Painter).
 *
 * Underlays never have a texture (their definitions only carry a colour); overlays sometimes
 * do. Pixels are decoded at the client's default brightness and size, and a pure-black pixel
 * is the OSRS cutout sentinel, so it becomes transparent (as in
 * `RenderTextureResource.hasTransparentPixels`).
 */
object OverlayTextureCache {
    private val LOGGER = LoggerFactory.getLogger(OverlayTextureCache::class.java)
    private const val CLIENT_BRIGHTNESS = 0.6
    private const val CLIENT_TEXTURE_SIZE = 128

    private val handles = ConcurrentHashMap<Int, Int>()
    private val unavailable = ConcurrentHashMap.newKeySet<Int>()

    /** A cached GL texture for an OSRS texture id, or 0 when the cache has none. */
    @JvmStatic
    fun handleFor(cache: LoadedOsrsCacheSession?, osrsTextureId: Int): Int {
        if (cache == null || osrsTextureId < 0) return 0
        handles[osrsTextureId]?.let { return it }
        if (osrsTextureId in unavailable) return 0

        val pixels = cache.bundle().definitions()
            .texturePixels(osrsTextureId, CLIENT_BRIGHTNESS, CLIENT_TEXTURE_SIZE).orElse(null)
        val dimension = if (pixels == null) 0 else sqrt(pixels.size.toDouble()).toInt()
        if (pixels == null || dimension <= 0 || dimension * dimension != pixels.size) {
            unavailable += osrsTextureId
            return 0
        }
        return try {
            // Texture pixels are 0x00RRGGBB; black is transparent, everything else opaque.
            val rgba = GlTextures.rgba(pixels) { if (it and 0xFFFFFF == 0) 0 else 255 }
            GlTextures.upload(0, dimension, dimension, rgba, GL11.GL_LINEAR, GL11.GL_LINEAR)
                .also { handles[osrsTextureId] = it }
        } catch (failure: RuntimeException) {
            LOGGER.warn("Failed to upload OSRS texture {} for UI preview: {}", osrsTextureId, failure.message)
            unavailable += osrsTextureId
            0
        }
    }
}
