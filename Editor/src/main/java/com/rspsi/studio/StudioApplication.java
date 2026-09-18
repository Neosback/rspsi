package com.rspsi.studio;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** Initial native application shell; services and workspaces attach here. */
public final class StudioApplication implements AutoCloseable {
    private final NativeWindow window;
    private final ImGuiHost imgui = new ImGuiHost();
    private boolean closed;

    public StudioApplication() {
        window = new NativeWindow(1320, 860, "OpenRune Studio");
        imgui.initialize(window);
    }

    public void run() {
        try {
            while (!window.shouldClose()) {
                window.pollEvents();
                imgui.beginFrame();
                drawDashboard();
                imgui.endFrame();
                window.swapBuffers();
            }
        } finally {
            close();
        }
    }

    private void drawDashboard() {
        int[] size = window.windowSize();
        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(size[0], size[1]);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin("OpenRune Studio", flags)) {
            ImGui.end();
            return;
        }
        ImGui.text("OPENRUNE STUDIO");
        ImGui.text("Native workspace host");
        ImGui.separator();
        ImGui.text("Dashboard");
        ImGui.textWrapped("The GLFW/OpenGL 3.3 context and Dear ImGui docking host are online.");
        ImGui.spacing();
        ImGui.text("Cache session");
        ImGui.textDisabled("Cache loading and workspace activation attach in the next slice.");
        ImGui.spacing();
        ImGui.text("Workspaces");
        ImGui.beginDisabled();
        ImGui.button("Map Editor  ·  Cache required");
        ImGui.endDisabled();
        ImGui.end();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        imgui.close();
        window.close();
    }
}
