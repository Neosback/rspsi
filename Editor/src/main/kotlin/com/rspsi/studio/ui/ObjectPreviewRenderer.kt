package com.rspsi.studio.ui

import com.rspsi.cache.definition.DefinitionProvider
import com.rspsi.editor.render.ObjectPreviewScene
import org.lwjgl.opengl.GL11

/**
 * GL texture for an object turntable preview.
 *
 * Staging, framing and the one-tile scale grid live in the renderer-neutral
 * [ObjectPreviewScene]; this class caches the staged scene per (object, type, rotation),
 * re-renders only when the view changes, and uploads the software frame. Call [render] on the
 * GL thread.
 */
class ObjectPreviewRenderer {
    private var textureId = 0
    private var textureWidth = 0
    private var textureHeight = 0
    private var cachedDefinitions: DefinitionProvider? = null
    private var cachedObjectId = Int.MIN_VALUE
    private var cachedType = Int.MIN_VALUE
    private var cachedRotation = Int.MIN_VALUE
    private var cachedScene: ObjectPreviewScene? = null
    private var lastYaw = Float.NaN
    private var lastElevation = Float.NaN
    private var lastZoom = Float.NaN

    /**
     * Renders the object and returns the GL texture id, or 0 when it has no renderable model.
     * The texture is overwritten by the next call.
     *
     * @param orbitYaw radians around the object, 0 = looking at its front
     * @param elevation radians above the horizon; positive looks down on it
     * @param zoom multiplies the fit distance (1 = framed)
     */
    fun render(
        definitions: DefinitionProvider?,
        objectId: Int,
        type: Int,
        rotation: Int,
        orbitYaw: Float,
        elevation: Float,
        zoom: Float,
        width: Int,
        height: Int,
    ): Int {
        if (definitions == null || width <= 0 || height <= 0) return 0
        if (definitions !== cachedDefinitions || cachedObjectId != objectId || cachedType != type ||
            cachedRotation != rotation
        ) {
            cachedScene = ObjectPreviewScene.build(definitions, objectId, type, rotation).orElse(null)
            cachedDefinitions = definitions
            cachedObjectId = objectId
            cachedType = type
            cachedRotation = rotation
            lastYaw = Float.NaN
        }
        val scene = cachedScene ?: return 0
        if (textureId != 0 && orbitYaw == lastYaw && elevation == lastElevation && zoom == lastZoom &&
            textureWidth == width && textureHeight == height
        ) {
            return textureId
        }
        lastYaw = orbitYaw
        lastElevation = elevation
        lastZoom = zoom
        if (textureId != 0 && (textureWidth != width || textureHeight != height)) {
            GL11.glDeleteTextures(textureId)
            textureId = 0
        }
        textureWidth = width
        textureHeight = height
        textureId = GlTextures.upload(textureId, width, height,
            GlTextures.rgba(scene.render(orbitYaw, elevation, zoom, width, height)),
            GL11.GL_LINEAR, GL11.GL_NEAREST, GlTextures.CLAMP_TO_EDGE)
        return textureId
    }

    /** True once a scene was attempted for this (object, type, rotation) and it had no model. */
    fun lastAttemptWasEmpty(): Boolean = cachedObjectId != Int.MIN_VALUE && cachedScene == null

    fun dispose() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId)
            textureId = 0
        }
    }
}
