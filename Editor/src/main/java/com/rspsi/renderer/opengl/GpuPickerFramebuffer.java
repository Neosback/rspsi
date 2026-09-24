package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.PickerId;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_COLOR;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_R32UI;
import static org.lwjgl.opengl.GL30.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glClearBufferuiv;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;

/**
 * Lazy single-sample integer render target for native scene picking.
 *
 * <p>The picker deliberately owns a separate {@code R32UI} target instead of
 * piggybacking on the presentation framebuffer. This keeps ordinary rendering
 * free of picker attachments and makes picking independent of presentation
 * MSAA, avoiding implementation-defined integer multisample resolve behavior.</p>
 */
final class GpuPickerFramebuffer implements AutoCloseable {
    private int framebuffer;
    private int colorTexture;
    private int depthRenderbuffer;
    private int width;
    private int height;
    private int framebufferStatus = GL_FRAMEBUFFER_COMPLETE;
    private long lastReadbackNanos;
    private boolean closed;

    void resize(int width, int height) {
        ensureOpen();
        int nextWidth = Math.max(1, width);
        int nextHeight = Math.max(1, height);
        if (framebuffer != 0 && this.width == nextWidth && this.height == nextHeight) {
            return;
        }

        destroyResources();
        this.width = nextWidth;
        this.height = nextHeight;

        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32UI, this.width, this.height,
                0, GL_RED_INTEGER, GL_UNSIGNED_INT, 0L);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, colorTexture, 0);

        depthRenderbuffer = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthRenderbuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, this.width, this.height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                GL_RENDERBUFFER, depthRenderbuffer);

        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        framebufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (framebufferStatus != GL_FRAMEBUFFER_COMPLETE) {
            destroyResources();
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            throw new IllegalStateException("GPU picker framebuffer is incomplete: 0x"
                    + Integer.toHexString(framebufferStatus));
        }

        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    void bindAndClear() {
        ensureReady();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, width, height);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // LWJGL validates the OpenGL clear-value pointer as four uints even
            // for an R32UI attachment; only the first component is consumed.
            IntBuffer clearId = stack.ints(PickerId.INVALID, 0, 0, 0);
            glClearBufferuiv(GL_COLOR, 0, clearId);
        }
        glClearDepth(0.0);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    int readTopLeft(float x, float y) {
        ensureReady();
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || x < 0.0f || y < 0.0f || x >= width || y >= height) {
            lastReadbackNanos = 0L;
            return PickerId.INVALID;
        }
        int pixelX = Math.min(width - 1, (int) x);
        int pixelY = height - 1 - Math.min(height - 1, (int) y);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer value = stack.mallocInt(1);
            long started = System.nanoTime();
            glReadPixels(pixelX, pixelY, 1, 1, GL_RED_INTEGER, GL_UNSIGNED_INT, value);
            lastReadbackNanos = Math.max(0L, System.nanoTime() - started);
            return value.get(0);
        }
    }

    boolean allocated() {
        return framebuffer != 0;
    }

    /** Releases GPU storage without permanently closing this reusable target. */
    void release() {
        ensureOpen();
        destroyResources();
    }

    int framebufferStatus() {
        return framebufferStatus;
    }

    long lastReadbackNanos() {
        return lastReadbackNanos;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        destroyResources();
    }

    private void ensureReady() {
        ensureOpen();
        if (framebuffer == 0) {
            throw new IllegalStateException("GPU picker framebuffer has not been allocated");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("GPU picker framebuffer is closed");
        }
    }

    private void destroyResources() {
        if (depthRenderbuffer != 0) glDeleteRenderbuffers(depthRenderbuffer);
        if (colorTexture != 0) glDeleteTextures(colorTexture);
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        depthRenderbuffer = 0;
        colorTexture = 0;
        framebuffer = 0;
        width = 0;
        height = 0;
        framebufferStatus = GL_FRAMEBUFFER_COMPLETE;
        lastReadbackNanos = 0L;
    }
}
