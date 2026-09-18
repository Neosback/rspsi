package com.rspsi.studio;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.Objects;

/** Initial native Map Editor chrome; renderer and neutral panels attach here. */
public final class MapEditorView {
    public void render(LoadedOsrsCacheSession cache, Runnable openDashboard) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(openDashboard, "dashboard callback");

        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Dashboard")) openDashboard.run();
                ImGui.endMenu();
            }
            ImGui.sameLine();
            ImGui.text("Map Editor  ·  Revision " + cache.identity().revision());
            ImGui.endMainMenuBar();
        }

        renderTools();
        renderViewport();
        renderInspector();
        renderBottomPanel();
    }

    private static void renderTools() {
        ImGui.begin("Tools", ImGuiWindowFlags.NoCollapse);
        ImGui.separatorText("Terrain");
        ImGui.button("Select");
        ImGui.button("Paint underlay");
        ImGui.button("Paint overlay");
        ImGui.button("Raise / lower");
        ImGui.separatorText("Objects");
        ImGui.button("Place object");
        ImGui.button("Move object");
        ImGui.button("Rotate object");
        ImGui.end();
    }

    private static void renderViewport() {
        ImGui.begin("Viewport", ImGuiWindowFlags.NoCollapse);
        ImGui.textDisabled("OpenGL scene FBO attaches here next.");
        ImGui.textWrapped("The viewport is intentionally an ImGui panel. It will display the existing GpuUploadPlan through a resolved framebuffer texture.");
        ImGui.spacing();
        ImGui.text("Scene pipeline");
        ImGui.textDisabled("WorldDocument  →  OsrsSceneResolver  →  OsrsRenderPlanner  →  GpuUploadPlan");
        ImGui.end();
    }

    private static void renderInspector() {
        ImGui.begin("Inspector", ImGuiWindowFlags.NoCollapse);
        ImGui.separatorText("Selection");
        ImGui.textDisabled("Nothing selected");
        ImGui.separatorText("Visibility");
        ImGui.checkbox("Terrain", true);
        ImGui.checkbox("Objects", true);
        ImGui.checkbox("Collision", false);
        ImGui.checkbox("Roofs", true);
        ImGui.end();
    }

    private static void renderBottomPanel() {
        ImGui.begin("Assets / History / Validation", ImGuiWindowFlags.NoCollapse);
        if (ImGui.beginTabBar("editor-bottom-tabs")) {
            if (ImGui.beginTabItem("Assets")) {
                ImGui.textDisabled("Asset browser will consume the neutral AssetRepository.");
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("History")) {
                ImGui.textDisabled("Undo history is owned by EditorSession.");
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Validation")) {
                ImGui.textDisabled("WorldValidator diagnostics will appear here.");
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.end();
    }
}
