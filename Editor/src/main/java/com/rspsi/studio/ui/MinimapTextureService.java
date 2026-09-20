package com.rspsi.studio.ui;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.minimap.MinimapBuilder;
import com.rspsi.editor.minimap.MinimapImage;
import com.rspsi.editor.model.WorldDocument;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Native OpenGL texture cache for authentic OSRS shaped-tile minimap rasters.
 * Bridges {@link MinimapBuilder#buildShaped(WorldDocument, int, DefinitionProvider)}
 * into reusable GPU textures for the WorldMap panel and HUD radar.
 *
 * <p>Strictly native LWJGL OpenGL with zero AWT or JavaFX dependencies.</p>
 */
public final class MinimapTextureService {

    private static final Logger log = LoggerFactory.getLogger(MinimapTextureService.class);

    private final MinimapBuilder builder = new MinimapBuilder();
    private final Map<Integer, Integer> planeTextures = new HashMap<>();
    private final Map<Integer, Integer> textureWidths = new HashMap<>();
    private final Map<Integer, Integer> textureHeights = new HashMap<>();

    private int lastDocumentHash = 0;

    /**
     * Retrieves or builds the authentic OSRS minimap texture for the given document and plane.
     *
     * @return OpenGL texture ID, or 0 if building fails.
     */
    public int textureForPlane(WorldDocument document, int plane, DefinitionProvider definitions) {
        if (document == null || definitions == null) return 0;
        if (plane < 0 || plane >= document.planes()) return 0;

        int currentHash = System.identityHashCode(document) ^ (plane * 31);
        Integer existingTex = planeTextures.get(plane);

        if (existingTex != null && existingTex > 0 && currentHash == lastDocumentHash) {
            return existingTex;
        }

        return rebuildTexture(document, plane, definitions);
    }

    /**
     * Forces regeneration of the minimap raster (e.g. after painting terrain or editing tiles).
     */
    public void markDirty() {
        lastDocumentHash = 0;
    }

    /**
     * Rebuilds the shaped-tile minimap raster and uploads it to an OpenGL 2D texture.
     */
    public int rebuildTexture(WorldDocument document, int plane, DefinitionProvider definitions) {
        try {
            MinimapImage image = builder.buildShaped(document, plane, definitions);
            int w = image.width();
            int h = image.height();
            int[] argb = image.argb();

            // Convert ARGB int array to direct RGBA ByteBuffer for native GL upload
            ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
            for (int pixel : argb) {
                byte a = (byte) ((pixel >> 24) & 0xFF);
                byte r = (byte) ((pixel >> 16) & 0xFF);
                byte g = (byte) ((pixel >> 8) & 0xFF);
                byte b = (byte) (pixel & 0xFF);
                buffer.put(r);
                buffer.put(g);
                buffer.put(b);
                buffer.put(a);
            }
            buffer.flip();

            int texId;
            if (planeTextures.containsKey(plane) && planeTextures.get(plane) > 0) {
                texId = planeTextures.get(plane);
            } else {
                texId = GL11.glGenTextures();
                planeTextures.put(plane, texId);
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            textureWidths.put(plane, w);
            textureHeights.put(plane, h);
            lastDocumentHash = System.identityHashCode(document) ^ (plane * 31);

            log.debug("Generated authentic OSRS minimap texture for plane {} ({}x{}, GL: {})", plane, w, h, texId);
            return texId;
        } catch (Exception ex) {
            log.error("Failed building authentic minimap texture for plane {}: {}", plane, ex.getMessage(), ex);
            return 0;
        }
    }

    public int width(int plane) {
        return textureWidths.getOrDefault(plane, 256);
    }

    public int height(int plane) {
        return textureHeights.getOrDefault(plane, 256);
    }

    public void dispose() {
        for (int texId : planeTextures.values()) {
            if (texId > 0) {
                GL11.glDeleteTextures(texId);
            }
        }
        planeTextures.clear();
        textureWidths.clear();
        textureHeights.clear();
        lastDocumentHash = 0;
    }
}
