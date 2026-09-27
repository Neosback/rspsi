package com.rspsi.studio.ui

import com.rspsi.cache.definition.DefinitionProvider
import com.rspsi.editor.minimap.MinimapBuilder
import com.rspsi.editor.model.WorldDocument
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.slf4j.LoggerFactory

/**
 * GL textures of the shaped-tile OSRS minimap ([MinimapBuilder.buildShaped]) for the
 * minimap panel and the HUD radar, one per plane of the current document.
 *
 * A texture is rebuilt when the document or plane changes or after [markDirty] (e.g. once a
 * terrain edit lands). Outside the region the texture is transparent, not wrapped. GL thread
 * only.
 */
class MinimapTextureService {
    private val builder = MinimapBuilder()
    private val planeTextures = HashMap<Int, Int>()
    private val textureWidths = HashMap<Int, Int>()
    private val textureHeights = HashMap<Int, Int>()

    /** Identity of the document and plane the textures were built from; 0 forces a rebuild. */
    private var builtFor = 0

    /** The minimap texture for [plane] of [document], building it when stale; 0 on failure. */
    fun textureForPlane(document: WorldDocument?, plane: Int, definitions: DefinitionProvider?): Int {
        if (document == null || definitions == null || plane < 0 || plane >= document.planes()) return 0
        val existing = planeTextures[plane]
        if (existing != null && existing > 0 && key(document, plane) == builtFor) return existing
        return rebuildTexture(document, plane, definitions)
    }

    /** Forces regeneration, e.g. after painting terrain or editing tiles. */
    fun markDirty() {
        builtFor = 0
    }

    /** Rebuilds the shaped-tile raster for [plane] and uploads it. */
    fun rebuildTexture(document: WorldDocument, plane: Int, definitions: DefinitionProvider): Int = try {
        val image = builder.buildShaped(document, plane, definitions)
        // Outside the loaded region the radar reads the transparent border colour. GL_CLAMP is
        // not a core-profile wrap mode, and GL_REPEAT showed the far side of the region.
        val id = GlTextures.upload(planeTextures[plane] ?: 0, image.width(), image.height(),
            GlTextures.rgba(image.argb()), GL11.GL_NEAREST, GL11.GL_NEAREST, GL13.GL_CLAMP_TO_BORDER)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        GL11.glTexParameterfv(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, floatArrayOf(0f, 0f, 0f, 0f))
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        planeTextures[plane] = id
        textureWidths[plane] = image.width()
        textureHeights[plane] = image.height()
        builtFor = key(document, plane)
        LOGGER.debug("Generated OSRS minimap texture for plane {} ({}x{}, GL: {})", plane, image.width(),
            image.height(), id)
        id
    } catch (failure: Exception) {
        LOGGER.error("Failed building minimap texture for plane {}: {}", plane, failure.message, failure)
        0
    }

    fun width(plane: Int): Int = textureWidths[plane] ?: DEFAULT_SIZE

    fun height(plane: Int): Int = textureHeights[plane] ?: DEFAULT_SIZE

    fun dispose() {
        planeTextures.values.filter { it > 0 }.forEach { GL11.glDeleteTextures(it) }
        planeTextures.clear()
        textureWidths.clear()
        textureHeights.clear()
        builtFor = 0
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(MinimapTextureService::class.java)
        const val DEFAULT_SIZE = 256

        fun key(document: WorldDocument, plane: Int): Int = System.identityHashCode(document) xor (plane * 31)
    }
}
