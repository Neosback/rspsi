package com.rspsi.studio.ui;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.render.ObjectPreviewScene;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * GL texture for an object turntable preview. Staging, framing and the
 * one-tile scale grid live in the renderer-neutral {@link ObjectPreviewScene};
 * this class caches the staged scene per (object, type, rotation) and uploads
 * its software frame. Call {@link #render} from the thread that owns the GL
 * context.
 */
public final class ObjectPreviewRenderer {
    private int textureId;
    private int textureWidth;
    private int textureHeight;
    private DefinitionProvider cachedDefinitions;
    private int cachedObjectId = Integer.MIN_VALUE;
    private int cachedType = Integer.MIN_VALUE;
    private int cachedRotation = Integer.MIN_VALUE;
    private ObjectPreviewScene cachedScene;
    private float lastYaw = Float.NaN;
    private float lastElevation = Float.NaN;
    private float lastZoom = Float.NaN;

    /**
     * Renders the object and returns the GL texture id, or 0 when the object
     * has no renderable model. The texture is overwritten by the next call.
     *
     * @param orbitYaw  radians around the object, 0 = looking at its front
     * @param elevation radians above the horizon; positive looks down on it
     * @param zoom      multiplies the fit distance (1 = framed)
     */
    public int render(DefinitionProvider definitions, int objectId, int type, int rotation,
                      float orbitYaw, float elevation, float zoom, int width, int height) {
        if (definitions == null || width <= 0 || height <= 0) return 0;
        if (definitions != cachedDefinitions || cachedObjectId != objectId || cachedType != type
                || cachedRotation != rotation) {
            Optional<ObjectPreviewScene> scene = ObjectPreviewScene.build(definitions, objectId, type, rotation);
            cachedScene = scene.orElse(null);
            cachedDefinitions = definitions;
            cachedObjectId = objectId;
            cachedType = type;
            cachedRotation = rotation;
            lastYaw = Float.NaN;
        }
        if (cachedScene == null) return 0;
        if (textureId != 0 && orbitYaw == lastYaw && elevation == lastElevation && zoom == lastZoom
                && textureWidth == width && textureHeight == height) {
            return textureId;
        }
        lastYaw = orbitYaw;
        lastElevation = elevation;
        lastZoom = zoom;
        upload(cachedScene.render(orbitYaw, elevation, zoom, width, height), width, height);
        return textureId;
    }

    /** True once a scene has been attempted for this (object, type, rotation) and it had no model. */
    public boolean lastAttemptWasEmpty() {
        return cachedObjectId != Integer.MIN_VALUE && cachedScene == null;
    }

    public void dispose() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    private void upload(int[] argb, int w, int h) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
        for (int pixel : argb) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();

        if (textureId == 0 || textureWidth != w || textureHeight != h) {
            if (textureId != 0) GL11.glDeleteTextures(textureId);
            textureId = GL11.glGenTextures();
            textureWidth = w;
            textureHeight = h;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
