package com.rspsi.studio.ui;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.MapSceneSpriteView;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * GL textures for cache sprites (frame 0 of a sprite group), e.g. map-function
 * icons. Nearest filtering keeps OSRS pixel art crisp. GL thread only.
 */
public final class SpriteTextureCache {
    public record Texture(int id, int width, int height) {
    }

    private static final Texture MISSING = new Texture(0, 0, 0);
    private final Map<Integer, Texture> textures = new HashMap<>();
    private DefinitionProvider owner;

    /** Texture for a sprite group, or {@code null} when the cache has no such sprite. */
    public Texture get(DefinitionProvider definitions, int spriteGroup) {
        if (definitions != owner) {
            clear();
            owner = definitions;
        }
        Texture texture = textures.computeIfAbsent(spriteGroup, id -> definitions.sprite(id, 0)
                .map(SpriteTextureCache::upload).orElse(MISSING));
        return texture == MISSING ? null : texture;
    }

    public void clear() {
        textures.values().stream().filter(texture -> texture.id() != 0)
                .forEach(texture -> GL11.glDeleteTextures(texture.id()));
        textures.clear();
    }

    private static Texture upload(MapSceneSpriteView sprite) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(sprite.width() * sprite.height() * 4);
        for (int argb : sprite.argb()) {
            pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
        }
        pixels.flip();
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, sprite.width(), sprite.height(), 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return new Texture(id, sprite.width(), sprite.height());
    }
}
