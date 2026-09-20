package com.rspsi.studio.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure native GL texture loader for plugins and Studio UI icons using STBImage.
 * Strictly zero AWT or JavaFX dependencies to preserve native boundary integrity.
 */
public final class TextureLoader {

    private static final Logger log = LoggerFactory.getLogger(TextureLoader.class);
    private static final Map<String, Integer> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private TextureLoader() {
    }

    /**
     * Loads a 2D OpenGL texture from a classpath resource or file path.
     * Caches texture IDs by path.
     *
     * @param path Classpath resource path (e.g. "/icons/tools/brush.png") or filesystem path.
     * @return OpenGL texture ID, or 0 if loading failed.
     */
    public static int loadTexture(String path) {
        if (path == null || path.isBlank()) return 0;
        Integer cached = TEXTURE_CACHE.get(path);
        if (cached != null && cached > 0) {
            return cached;
        }

        try {
            byte[] data = null;
            try (InputStream in = TextureLoader.class.getResourceAsStream(path)) {
                if (in != null) {
                    data = in.readAllBytes();
                }
            }

            if (data == null) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    try (FileInputStream fin = new FileInputStream(file)) {
                        data = fin.readAllBytes();
                    }
                }
            }

            if (data == null) {
                log.debug("Texture asset not found on classpath or filesystem: {}", path);
                return 0;
            }

            int texId = loadTextureFromBytes(data);
            if (texId > 0) {
                TEXTURE_CACHE.put(path, texId);
            }
            return texId;
        } catch (Exception ex) {
            log.warn("Failed to load texture from {}: {}", path, ex.getMessage());
            return 0;
        }
    }

    /**
     * Loads an RGBA 2D OpenGL texture from raw image bytes (PNG, JPEG, TGA, etc.).
     */
    public static int loadTextureFromBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return 0;

        ByteBuffer buffer = BufferUtils.createByteBuffer(imageBytes.length);
        buffer.put(imageBytes);
        buffer.flip();

        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];

        ByteBuffer image = STBImage.stbi_load_from_memory(buffer, width, height, channels, 4);
        if (image == null) {
            log.warn("STBImage failed to decode image: {}", STBImage.stbi_failure_reason());
            return 0;
        }

        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width[0], height[0], 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        STBImage.stbi_image_free(image);
        return texId;
    }

    /**
     * Frees all cached OpenGL textures.
     */
    public static void dispose() {
        for (int texId : TEXTURE_CACHE.values()) {
            if (texId > 0) {
                GL11.glDeleteTextures(texId);
            }
        }
        TEXTURE_CACHE.clear();
    }
}
