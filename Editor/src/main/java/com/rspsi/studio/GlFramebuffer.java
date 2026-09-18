package com.rspsi.studio;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;
import static org.lwjgl.opengl.GL32.glTexImage2DMultisample;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;

/**
 * Render-target framebuffer with optional MSAA and a single-sample texture
 * suitable for {@code ImGui.image}.
 */
public final class GlFramebuffer implements AutoCloseable {
    private int resolveFramebuffer;
    private int resolveTexture;
    private int resolveDepth;
    private int multisampleFramebuffer;
    private int multisampleColor;
    private int multisampleDepth;
    private int width;
    private int height;
    private int samples;
    private int framebufferStatus = GL_FRAMEBUFFER_COMPLETE;
    private boolean closed;

    public void resize(int width, int height, int requestedSamples) {
        ensureOpen();
        int nextWidth = Math.max(1, width);
        int nextHeight = Math.max(1, height);
        int nextSamples = normalizeSamples(requestedSamples);
        if (this.width == nextWidth && this.height == nextHeight && this.samples == nextSamples) return;
        destroyResources();
        this.width = nextWidth;
        this.height = nextHeight;
        this.samples = nextSamples;
        createResolveTarget();
        if (samples > 1) createMultisampleTarget();
    }

    public void bindForScene() {
        ensureReady();
        glBindFramebuffer(GL_FRAMEBUFFER, samples > 1 ? multisampleFramebuffer : resolveFramebuffer);
    }

    public void resolve() {
        ensureReady();
        if (samples > 1) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, multisampleFramebuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int texture() {
        ensureReady();
        return resolveTexture;
    }

    public int width() { return width; }

    public int height() { return height; }

    public int samples() { return samples; }

    /** Status of the most recently created render target. */
    public int framebufferStatus() { return framebufferStatus; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        destroyResources();
    }

    private void createResolveTarget() {
        resolveFramebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer);
        resolveTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, resolveTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resolveTexture, 0);
        resolveDepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, resolveDepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, resolveDepth);
        checkComplete("resolve framebuffer");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void createMultisampleTarget() {
        multisampleFramebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, multisampleFramebuffer);
        multisampleColor = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, multisampleColor);
        glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, samples, GL_RGBA8, width, height, true);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D_MULTISAMPLE,
                multisampleColor, 0);
        multisampleDepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, multisampleDepth);
        org.lwjgl.opengl.GL30.glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples,
                GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER,
                multisampleDepth);
        checkComplete("multisample framebuffer");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private int normalizeSamples(int requested) {
        if (requested <= 1) return 0;
        int maximum = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_MAX_SAMPLES);
        int value = Math.min(requested, Math.max(1, maximum));
        return Integer.highestOneBit(value);
    }

    private void checkComplete(String target) {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        framebufferStatus = status;
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(target + " is incomplete: 0x" + Integer.toHexString(status));
        }
    }

    private void ensureReady() {
        ensureOpen();
        if (resolveFramebuffer == 0) throw new IllegalStateException("Framebuffer has not been resized");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Framebuffer is closed");
    }

    private void destroyResources() {
        if (multisampleFramebuffer != 0) glDeleteFramebuffers(multisampleFramebuffer);
        if (resolveFramebuffer != 0) glDeleteFramebuffers(resolveFramebuffer);
        if (multisampleColor != 0) glDeleteTextures(multisampleColor);
        if (resolveTexture != 0) glDeleteTextures(resolveTexture);
        if (multisampleDepth != 0) glDeleteRenderbuffers(multisampleDepth);
        if (resolveDepth != 0) glDeleteRenderbuffers(resolveDepth);
        multisampleFramebuffer = 0;
        resolveFramebuffer = 0;
        multisampleColor = 0;
        resolveTexture = 0;
        multisampleDepth = 0;
        resolveDepth = 0;
        framebufferStatus = GL_FRAMEBUFFER_COMPLETE;
    }
}
