package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.TextureAnimation;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL31.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31.glTexBuffer;

/**
 * GPU-resident per-texture state indexed by the native texture-array layer.
 *
 * <p>Each RGBA32F entry stores {@code scaleU, scaleV, animationUPerCycle,
 * animationVPerCycle}. The cache texture id is also the texture-array layer,
 * so the shader can fetch state directly with {@code uTextureLayer} without
 * any second indirection or per-command animation uniforms.</p>
 */
final class TextureStateBuffer implements AutoCloseable {
    static final int FLOATS_PER_ENTRY = 4;
    static final Entry DEFAULT = new Entry(1.0f, 1.0f, 0.0f, 0.0f);

    record Entry(float scaleU, float scaleV,
                 float animationUPerCycle, float animationVPerCycle) {
    }

    private int buffer;
    private int texture;
    private int entryCount;

    static Entry entryFor(RenderTextureResource resource) {
        Objects.requireNonNull(resource, "resource");
        if (!resource.hasGpuPixels()) return DEFAULT;
        TextureAnimation.UvOffset rate = TextureAnimation.rate(resource);
        return new Entry(1.0f, 1.0f, rate.u(), rate.v());
    }

    void upload(Map<Integer, RenderTextureResource> resources, int minimumCapacity) {
        Objects.requireNonNull(resources, "resources");
        if (minimumCapacity < 1) {
            throw new IllegalArgumentException("minimumCapacity must be positive");
        }

        int largestTextureId = resources.values().stream()
                .filter(RenderTextureResource::hasGpuPixels)
                .mapToInt(RenderTextureResource::id)
                .max()
                .orElse(-1);
        entryCount = Math.max(minimumCapacity, largestTextureId + 1);

        FloatBuffer data = BufferUtils.createFloatBuffer(entryCount * FLOATS_PER_ENTRY);
        for (int textureId = 0; textureId < entryCount; textureId++) {
            RenderTextureResource resource = resources.get(textureId);
            Entry entry = resource == null ? DEFAULT : entryFor(resource);
            data.put(entry.scaleU())
                    .put(entry.scaleV())
                    .put(entry.animationUPerCycle())
                    .put(entry.animationVPerCycle());
        }
        data.flip();

        if (buffer == 0) buffer = glGenBuffers();
        if (texture == 0) texture = glGenTextures();

        glBindBuffer(GL_TEXTURE_BUFFER, buffer);
        glBufferData(GL_TEXTURE_BUFFER, data, GL_STATIC_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER, texture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, buffer);
        glBindTexture(GL_TEXTURE_BUFFER, 0);
        glBindBuffer(GL_TEXTURE_BUFFER, 0);
    }

    void bind() {
        if (texture == 0) {
            throw new IllegalStateException("Texture state buffer has not been uploaded");
        }
        glBindTexture(GL_TEXTURE_BUFFER, texture);
    }

    void unbind() {
        glBindTexture(GL_TEXTURE_BUFFER, 0);
    }

    int entryCount() {
        return entryCount;
    }

    @Override
    public void close() {
        if (texture != 0) glDeleteTextures(texture);
        if (buffer != 0) glDeleteBuffers(buffer);
        texture = 0;
        buffer = 0;
        entryCount = 0;
    }
}
