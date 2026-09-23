package com.rspsi.renderer.opengl;

import org.junit.jupiter.api.Assumptions;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;

/**
 * Hidden OpenGL 3.3 core context for pixel-level acceptance tests.
 *
 * <p>CI supplies an Xvfb display and must create the context. Elsewhere the
 * tests skip when no context is available - including macOS, where LWJGL
 * refuses GLFW off the process's first thread (Gradle test workers never run
 * there without {@code -XstartOnFirstThread}).</p>
 */
final class HeadlessGlContext {
    private HeadlessGlContext() {
    }

    /** Returns a current hidden window, or aborts the calling test class as skipped. */
    static long createOrSkip(int size, String title) {
        boolean initialized;
        try {
            initialized = glfwInit();
        } catch (IllegalStateException threadCheck) {
            unavailable("GLFW unavailable on this thread: " + threadCheck.getMessage());
            return 0L;
        }
        if (!initialized) {
            unavailable("No GLFW display/context available");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(size, size, title, 0L, 0L);
        if (window == 0L) {
            glfwTerminate();
            unavailable("OpenGL 3.3 core context unavailable");
        }
        glfwMakeContextCurrent(window);
        return window;
    }

    /** Releases a window from {@link #createOrSkip}; a no-op when the class was skipped. */
    static void destroy(long window) {
        if (window == 0L) return;
        glfwMakeContextCurrent(window);
        GL.setCapabilities(null);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private static void unavailable(String reason) {
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            fail("CI must provide a working OpenGL 3.3 core context: " + reason);
        }
        Assumptions.assumeTrue(false, reason);
    }
}
