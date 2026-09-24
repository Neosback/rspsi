package com.rspsi.studio;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Owns the single application GLFW window and its OpenGL context. */
public final class NativeWindow implements AutoCloseable {
    private final GLFWErrorCallback errorCallback;
    private final long handle;
    private boolean closed;

    public NativeWindow(int width, int height, String title) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Window dimensions must be positive");
        Objects.requireNonNull(title, "window title");
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            errorCallback.free();
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        if (org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.MACOSX) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }
        handle = glfwCreateWindow(width, height, title, 0L, 0L);
        if (handle == 0L) {
            glfwTerminate();
            errorCallback.free();
            throw new IllegalStateException("Unable to create the OpenGL 3.3 window");
        }
        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(1);
        glfwShowWindow(handle);
    }

    public long handle() {
        return handle;
    }

    public void pollEvents() {
        ensureOpen();
        glfwPollEvents();
    }

    /** Clears the default framebuffer before Dear ImGui submits the frame. */
    public void clearFrame() {
        ensureOpen();
        int[] size = framebufferSize();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, size[0], size[1]);
        glClearColor(0.063f, 0.094f, 0.153f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void swapBuffers() {
        ensureOpen();
        glfwSwapBuffers(handle);
    }

    public boolean shouldClose() {
        ensureOpen();
        return org.lwjgl.glfw.GLFW.glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        ensureOpen();
        glfwSetWindowShouldClose(handle, true);
    }

    public void setTitle(String title) {
        ensureOpen();
        glfwSetWindowTitle(handle, Objects.requireNonNull(title, "title"));
    }

    public int[] windowSize() {
        ensureOpen();
        try (var stack = stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            glfwGetWindowSize(handle, width, height);
            return new int[]{width.get(0), height.get(0)};
        }
    }

    public int[] framebufferSize() {
        ensureOpen();
        try (var stack = stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, width, height);
            return new int[]{width.get(0), height.get(0)};
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glfwDestroyWindow(handle);
        glfwTerminate();
        errorCallback.free();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Native window is closed");
    }
}
