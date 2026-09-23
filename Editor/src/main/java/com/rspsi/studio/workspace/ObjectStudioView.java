package com.rspsi.studio.workspace;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.studio.WorkspaceManager;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * First-class sibling workspace for OSRS model, location, and animation inspection.
 *
 * <p>Uses the shared Studio SimulationClock for animating models, previewing sequence frames,
 * and testing object transformations.</p>
 */
public final class ObjectStudioView {
    private final ImInt selectedObjectId = new ImInt(10583); // e.g. Bank booth
    private final ImInt selectedSequenceId = new ImInt(-1);

    public void render(LoadedOsrsCacheSession cache,
                       SettingsStore settings,
                       EditorPluginLifecycleManager pluginLifecycle,
                       Runnable openDashboard,
                       Runnable openMapEditor,
                       Runnable openInterfaceStudio,
                       WorkspaceManager workspaces,
                       Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        Objects.requireNonNull(openDashboard, "openDashboard");
        Objects.requireNonNull(openMapEditor, "openMapEditor");
        Objects.requireNonNull(openInterfaceStudio, "openInterfaceStudio");

        imgui.ImGuiViewport mainViewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(mainViewport.getPosX(), mainViewport.getPosY());
        ImGui.setNextWindowSize(mainViewport.getSizeX(), mainViewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.MenuBar;

        if (!ImGui.begin("Object Studio Workspace", flags)) {
            ImGui.end();
            return;
        }

        renderWorkspaceBar(cache, workspaces, openDashboard, openMapEditor, openInterfaceStudio, closeWorkspace);

        float fullWidth = ImGui.getContentRegionAvailX();
        float fullHeight = ImGui.getContentRegionAvailY();
        float leftWidth = Math.max(280.0f, fullWidth * 0.25f);
        float rightWidth = Math.max(320.0f, fullWidth * 0.28f);
        float centerWidth = fullWidth - leftWidth - rightWidth - 16.0f;

        // Left Panel: Object & Animation Selection
        ImGui.beginChild("##obj-selection-panel", leftWidth, fullHeight, true);
        renderSelectionPanel(cache);
        ImGui.endChild();

        ImGui.sameLine();

        // Center Panel: 3D Viewport View
        ImGui.beginChild("##obj-viewport-panel", centerWidth, fullHeight, true);
        renderViewportPanel();
        ImGui.endChild();

        ImGui.sameLine();

        // Right Panel: Model & Sequence Inspector
        ImGui.beginChild("##obj-inspector-panel", rightWidth, fullHeight, true);
        renderInspectorPanel(cache);
        ImGui.endChild();

        ImGui.end();
    }

    private void renderWorkspaceBar(LoadedOsrsCacheSession cache, WorkspaceManager workspaces,
                                     Runnable openDashboard, Runnable openMapEditor, Runnable openInterfaceStudio,
                                     Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        if (ImGui.beginMenuBar()) {
            if (workspaces != null) {
                StudioWidgets.workspaceTabs(workspaces, openDashboard, openMapEditor,
                        openInterfaceStudio, null, closeWorkspace);
            }

            ImGui.endMenuBar();
        }
    }

    private void renderSelectionPanel(LoadedOsrsCacheSession cache) {
        StudioWidgets.section("Object & Model Selection");
        ImGui.inputInt("Object ID##obj-id-input", selectedObjectId);

        if (cache != null) {
            cache.bundle().definitions().object(selectedObjectId.get()).ifPresent(def -> {
                ImGui.textColored(0xFF66FF66, def.displayName());
                ImGui.textDisabled("Size: " + def.width() + "x" + def.length() + " | Interactive: " + def.interactive());
            });
        }

        ImGui.separator();
        StudioWidgets.section("Animation Sequence");
        ImGui.inputInt("Sequence ID##seq-id-input", selectedSequenceId);

        ImGui.dummy(1.0f, 12.0f);
        if (StudioWidgets.buttonSecondary("Reset Pose", ImGui.getContentRegionAvailX(), 28.0f)) {
            selectedSequenceId.set(-1);
        }
    }

    private void renderViewportPanel() {
        ImGui.textDisabled("Model Viewport Preview");
        ImGui.separator();

        float canvasW = ImGui.getContentRegionAvailX();
        float canvasH = ImGui.getContentRegionAvailY() - 32.0f;
        float posX = ImGui.getCursorScreenPos().x;
        float posY = ImGui.getCursorScreenPos().y;

        imgui.ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(posX, posY, posX + canvasW, posY + canvasH, 0xFF141414, 4.0f);
        draw.addRect(posX, posY, posX + canvasW, posY + canvasH, 0xFF333333, 4.0f);

        // Coordinate axes in center
        float cx = posX + canvasW * 0.5f;
        float cy = posY + canvasH * 0.5f;
        draw.addLine(cx - 60, cy, cx + 60, cy, 0xFF4444FF, 1.5f);
        draw.addLine(cx, cy - 60, cx, cy + 60, 0xFF44FF44, 1.5f);
        draw.addText(StudioFonts.mono(), 12, cx - 40, cy + 80, 0xFFAAAAAA, "Object #" + selectedObjectId.get());

        // Animation playback is not implemented yet — no controls pretending otherwise.
        ImGui.setCursorPosY(ImGui.getCursorPosY() + canvasH + 6.0f);
        ImGui.textDisabled("Animation playback not yet implemented.");
    }

    private void renderInspectorPanel(LoadedOsrsCacheSession cache) {
        StudioWidgets.section("Model Geometry & Materials");
        if (cache != null) {
            cache.bundle().definitions().object(selectedObjectId.get()).ifPresent(def -> {
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("Object Name:     " + def.displayName());
                ImGui.text("Model IDs:       " + java.util.Arrays.toString(def.modelIds()));
                ImGui.text("Dimensions:      " + def.width() + "x" + def.length());
                ImGui.text("Animation ID:    " + (selectedSequenceId.get() >= 0 ? selectedSequenceId.get() : "None"));
                ImGui.popFont();
            });
        }
    }
}
