package com.rspsi.studio.ui;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uploads real decoded OSRS ground textures for small UI preview swatches (Tile Inspector,
 * Tile Painter), instead of the flat average-color rectangles used previously. Underlays never
 * have a texture (OSRS underlay definitions only ever carry a color); overlays sometimes do.
 */
public final class OverlayTextureCache {

    private static final Logger log = LoggerFactory.getLogger(OverlayTextureCache.class);
    private static final double CLIENT_BRIGHTNESS = 0.6;
    private static final int CLIENT_TEXTURE_SIZE = 128;

    private static final Map<Integer, Integer> HANDLES = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> UNAVAILABLE = new ConcurrentHashMap<>();

    private OverlayTextureCache() {
    }

    /** Returns a cached GL texture handle for this OSRS texture id, or 0 if none is available. */
    public static int handleFor(LoadedOsrsCacheSession cache, int osrsTextureId) {
        if (cache == null || osrsTextureId < 0) return 0;
        Integer cached = HANDLES.get(osrsTextureId);
        if (cached != null) return cached;
        if (UNAVAILABLE.containsKey(osrsTextureId)) return 0;

        var pixelsOpt = cache.bundle().definitions().texturePixels(osrsTextureId, CLIENT_BRIGHTNESS, CLIENT_TEXTURE_SIZE);
        if (pixelsOpt.isEmpty()) {
            UNAVAILABLE.put(osrsTextureId, Boolean.TRUE);
            return 0;
        }

        int[] pixels = pixelsOpt.get();
        int dimension = (int) Math.sqrt(pixels.length);
        if (dimension <= 0 || dimension * dimension != pixels.length) {
            UNAVAILABLE.put(osrsTextureId, Boolean.TRUE);
            return 0;
        }

        try {
            int texId = upload(pixels, dimension);
            HANDLES.put(osrsTextureId, texId);
            return texId;
        } catch (Exception ex) {
            log.warn("Failed to upload OSRS texture {} for UI preview: {}", osrsTextureId, ex.getMessage());
            UNAVAILABLE.put(osrsTextureId, Boolean.TRUE);
            return 0;
        }
    }

    private static int upload(int[] pixels, int dimension) {
        // Cache texture pixels are packed 0x00RRGGBB; a pure-black pixel is the OSRS cutout
        // sentinel for transparency, matching RenderTextureResource.hasTransparentPixels().
        ByteBuffer buffer = BufferUtils.createByteBuffer(pixels.length * 4);
        for (int pixel : pixels) {
            int rgb = pixel & 0xFFFFFF;
            buffer.put((byte) ((rgb >> 16) & 0xFF));
            buffer.put((byte) ((rgb >> 8) & 0xFF));
            buffer.put((byte) (rgb & 0xFF));
            buffer.put((byte) (rgb == 0 ? 0 : 255));
        }
        buffer.flip();

        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, dimension, dimension, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texId;
    }
}
