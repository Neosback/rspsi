package com.rspsi.studio.ui

import com.rspsi.cache.definition.DefinitionProvider
import com.rspsi.cache.definition.MapSceneSpriteView
import org.lwjgl.opengl.GL11

/**
 * GL textures for cache sprites (frame 0 of a sprite group), e.g. map-function icons.
 * Nearest filtering keeps OSRS pixel art crisp. GL thread only.
 *
 * The cache is tied to one [DefinitionProvider]; switching caches drops every texture.
 */
class SpriteTextureCache {
    @JvmRecord
    data class Texture(val id: Int, val width: Int, val height: Int)

    /** Cached textures; [MISSING] marks a sprite group the cache does not have. */
    private val textures = HashMap<Int, Texture>()
    private var owner: DefinitionProvider? = null

    /** The texture for a sprite group, or null when the cache has no such sprite. */
    fun get(definitions: DefinitionProvider, spriteGroup: Int): Texture? {
        if (definitions !== owner) {
            clear()
            owner = definitions
        }
        val texture = textures.getOrPut(spriteGroup) {
            definitions.sprite(spriteGroup, 0).map { upload(it) }.orElse(MISSING)
        }
        return if (texture === MISSING) null else texture
    }

    fun clear() {
        textures.values.filter { it.id != 0 }.forEach { GL11.glDeleteTextures(it.id) }
        textures.clear()
    }

    private companion object {
        val MISSING = Texture(0, 0, 0)

        fun upload(sprite: MapSceneSpriteView): Texture {
            val id = GlTextures.upload(0, sprite.width(), sprite.height(), GlTextures.rgba(sprite.argb()),
                GL11.GL_NEAREST, GL11.GL_NEAREST)
            return Texture(id, sprite.width(), sprite.height())
        }
    }
}
