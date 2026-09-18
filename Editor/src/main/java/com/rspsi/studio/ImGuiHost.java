package com.rspsi.studio;

import imgui.ImGui;
import imgui.glfw.ImGuiImplGlfw;
import imgui.gl3.ImGuiImplGl3;
import imgui.flag.ImGuiConfigFlags;

import java.util.Objects;

/** Binds Dear ImGui to the application's existing GLFW/OpenGL context. */
public final class ImGuiHost implements AutoCloseable {
    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private boolean initialized;
    private boolean closed;

    public void initialize(NativeWindow window) {
        Objects.requireNonNull(window, "window");
        if (initialized) throw new IllegalStateException("ImGui is already initialized");
        ImGui.createContext();
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
        // Layout persistence will move to ~/.openrune-studio/layouts once the
        // workspace manager owns named layouts.  Never write one beside the
        // Gradle project during the host bootstrap.
        ImGui.getIO().setIniFilename(null);
        if (!glfw.initForOpenGL(window.handle(), true)) {
            ImGui.destroyContext();
            throw new IllegalStateException("Unable to initialize the Dear ImGui GLFW backend");
        }
        if (!gl3.init("#version 330 core")) {
            glfw.shutdown();
            ImGui.destroyContext();
            throw new IllegalStateException("Unable to initialize the Dear ImGui OpenGL 3 backend");
        }
        initialized = true;
    }

    public void beginFrame() {
        ensureReady();
        glfw.newFrame();
        gl3.newFrame();
        ImGui.newFrame();
    }

    public void endFrame() {
        ensureReady();
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (initialized) {
            gl3.shutdown();
            glfw.shutdown();
            ImGui.destroyContext();
            initialized = false;
        }
    }

    private void ensureReady() {
        if (!initialized || closed) throw new IllegalStateException("Dear ImGui host is not available");
    }
}
