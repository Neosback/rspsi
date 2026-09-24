package com.rspsi.studio;

import imgui.ImGui;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_T;

/** Owns the OpenRune wordmark texture used by the launcher and loading gate. */
public final class StudioBranding {
    private static final String WORDMARK_RESOURCE = "/images/openrune-wordmark.png";

    private static int wordmarkTexture;
    private static int wordmarkWidth;
    private static int wordmarkHeight;

    private StudioBranding() {
    }

    public static void drawWordmark(float displayWidth) {
        ensureLoaded();
        float width = Math.max(1.0f, displayWidth);
        float height = width * wordmarkHeight / (float) wordmarkWidth;
        ImGui.image(wordmarkTexture, width, height);
    }

    public static float wordmarkHeight(float displayWidth) {
        ensureLoaded();
        return Math.max(1.0f, displayWidth) * wordmarkHeight / (float) wordmarkWidth;
    }

    public static void close() {
        if (wordmarkTexture != 0) {
            glDeleteTextures(wordmarkTexture);
            wordmarkTexture = 0;
            wordmarkWidth = 0;
            wordmarkHeight = 0;
        }
    }

    private static void ensureLoaded() {
        if (wordmarkTexture != 0) return;

        byte[] bytes;
        try (InputStream input = StudioBranding.class.getResourceAsStream(WORDMARK_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing OpenRune wordmark: " + WORDMARK_RESOURCE);
            }
            bytes = input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read OpenRune wordmark", failure);
        }

        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        try {
            encoded.put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(
                        encoded, width, height, channels, 4);
                if (pixels == null) {
                    throw new IllegalStateException(
                            "Unable to decode OpenRune wordmark: " + STBImage.stbi_failure_reason());
                }
                try {
                    wordmarkWidth = width.get(0);
                    wordmarkHeight = height.get(0);
                    wordmarkTexture = glGenTextures();
                    glBindTexture(GL_TEXTURE_2D, wordmarkTexture);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8,
                            wordmarkWidth, wordmarkHeight, 0,
                            GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    glBindTexture(GL_TEXTURE_2D, 0);
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }
}
