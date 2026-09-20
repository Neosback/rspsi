package com.rspsi.studio.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Pure native GL texture cache for Studio HUD and branding assets using STBImage.
 * Zero AWT or Swing dependencies.
 */
public final class BrandingAssets {

    private static final Logger log = LoggerFactory.getLogger(BrandingAssets.class);

    private static Texture compass;
    private static Texture minimapFrame;
    private static Texture worldmapIcon;
    private static Texture worldmapIconHover;

    public static Texture compass() {
        if (compass == null) {
            compass = loadTexture("/branding/hud/compass.png");
        }
        return compass;
    }

    public static Texture minimapFrame() {
        if (minimapFrame == null) {
            minimapFrame = loadTexture("/branding/hud/minimap-frame.png");
        }
        return minimapFrame;
    }

    public static Texture worldmapIcon() {
        if (worldmapIcon == null) {
            worldmapIcon = loadTexture("/branding/hud/worldmap-icon.png");
        }
        return worldmapIcon;
    }

    public static Texture worldmapIconHover() {
        if (worldmapIconHover == null) {
            worldmapIconHover = loadTexture("/branding/hud/worldmap-icon-hover.png");
        }
        return worldmapIconHover;
    }

    private static Texture loadTexture(String path) {
        try (InputStream in = BrandingAssets.class.getResourceAsStream(path)) {
            if (in == null) {
                log.warn("HUD asset not found on classpath: {}", path);
                return new Texture(0, 0, 0);
            }

            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            int[] width = new int[1];
            int[] height = new int[1];
            int[] channels = new int[1];

            ByteBuffer image = STBImage.stbi_load_from_memory(buffer, width, height, channels, 4);
            if (image == null) {
                log.error("Failed to decode image from {}: {}", path, STBImage.stbi_failure_reason());
                return new Texture(0, 0, 0);
            }

            int texId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width[0], height[0], 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            STBImage.stbi_image_free(image);
            log.info("Loaded branding asset: {} ({}x{}, GL tex: {})", path, width[0], height[0], texId);
            return new Texture(texId, width[0], height[0]);
        } catch (Exception ex) {
            log.error("Error loading branding asset {}", path, ex);
            return new Texture(0, 0, 0);
        }
    }

    public static void dispose() {
        disposeTexture(compass);
        disposeTexture(minimapFrame);
        disposeTexture(worldmapIcon);
        disposeTexture(worldmapIconHover);
        compass = null;
        minimapFrame = null;
        worldmapIcon = null;
        worldmapIconHover = null;
    }

    private static void disposeTexture(Texture tex) {
        if (tex != null && tex.id > 0) {
            GL11.glDeleteTextures(tex.id);
        }
    }

    private BrandingAssets() {
    }

    public static final class Texture {
        public final int id;
        public final int width;
        public final int height;

        public Texture(int id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        public boolean isValid() {
            return id > 0;
        }
    }
}
