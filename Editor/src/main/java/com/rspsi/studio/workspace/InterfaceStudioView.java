package com.rspsi.studio.workspace;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.studio.WorkspaceManager;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * First-class sibling workspace for OSRS interface, component, and CS2 editing.
 *
 * <p>Shares the Studio runtime, cache definitions, fonts, simulation clock, and plugin services
 * with Map Studio without being coupled to map terrain or world objects.</p>
 */
public final class InterfaceStudioView {
    public enum PreviewMode { BLANK_CANVAS, OVER_GAME_SCENE }

    private PreviewMode previewMode = PreviewMode.BLANK_CANVAS;
    private final ImInt selectedInterfaceId = new ImInt(12); // e.g. Bank interface
    private final ImInt selectedComponentId = new ImInt(0);
    private final ImString searchFilter = new ImString(64);
    private final ImBoolean previewOverGame = new ImBoolean(false);

    public void render(LoadedOsrsCacheSession cache,
                       SettingsStore settings,
                       EditorPluginLifecycleManager pluginLifecycle,
                       Runnable openDashboard,
                       Runnable openMapEditor,
                       Runnable openObjectStudio,
                       WorkspaceManager workspaces,
                       Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        Objects.requireNonNull(openDashboard, "openDashboard");
        Objects.requireNonNull(openMapEditor, "openMapEditor");
        Objects.requireNonNull(openObjectStudio, "openObjectStudio");

        imgui.ImGuiViewport mainViewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(mainViewport.getPosX(), mainViewport.getPosY());
        ImGui.setNextWindowSize(mainViewport.getSizeX(), mainViewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.MenuBar;

        if (!ImGui.begin("Interface Studio Workspace", flags)) {
            ImGui.end();
            return;
        }

        renderWorkspaceBar(cache, workspaces, openDashboard, openMapEditor, openObjectStudio, closeWorkspace);

        // Three-column workspace layout
        float fullWidth = ImGui.getContentRegionAvailX();
        float fullHeight = ImGui.getContentRegionAvailY();
        float leftWidth = Math.max(260.0f, fullWidth * 0.22f);
        float rightWidth = Math.max(300.0f, fullWidth * 0.26f);
        float centerWidth = fullWidth - leftWidth - rightWidth - 16.0f;

        // Left Panel: Widget Hierarchy
        ImGui.beginChild("##ui-hierarchy-panel", leftWidth, fullHeight, true);
        renderHierarchyPanel(cache);
        ImGui.endChild();

        ImGui.sameLine();

        // Center Panel: UI Canvas Preview
        ImGui.beginChild("##ui-canvas-panel", centerWidth, fullHeight, true);
        renderCanvasPanel(cache);
        ImGui.endChild();

        ImGui.sameLine();

        // Right Panel: Inspector & Runtime State
        ImGui.beginChild("##ui-inspector-panel", rightWidth, fullHeight, true);
        renderPropertiesPanel(cache, pluginLifecycle);
        ImGui.endChild();

        ImGui.end();
    }

    private void renderWorkspaceBar(LoadedOsrsCacheSession cache, WorkspaceManager workspaces,
                                     Runnable openDashboard, Runnable openMapEditor, Runnable openObjectStudio,
                                     Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        if (ImGui.beginMenuBar()) {
            if (workspaces != null) {
                StudioWidgets.workspaceTabs(workspaces, openDashboard, openMapEditor,
                        null, openObjectStudio, closeWorkspace);
            }

            ImGui.endMenuBar();
        }
    }

    private void renderHierarchyPanel(LoadedOsrsCacheSession cache) {
        StudioWidgets.section("Interface Hierarchy");
        ImGui.inputInt("Interface##sel-iface", selectedInterfaceId);
        ImGui.inputTextWithHint("##ui-search", "Filter components...", searchFilter);
        ImGui.separator();

        if (ImGui.treeNodeEx("Interface " + selectedInterfaceId.get() + " (Root)", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (ImGui.selectable("0: Background Window", selectedComponentId.get() == 0)) selectedComponentId.set(0);
            if (ImGui.selectable("1: Title Bar Label", selectedComponentId.get() == 1)) selectedComponentId.set(1);
            if (ImGui.selectable("2: Close Button", selectedComponentId.get() == 2)) selectedComponentId.set(2);
            if (ImGui.treeNodeEx("3: Item Container Grid", ImGuiTreeNodeFlags.DefaultOpen)) {
                if (ImGui.selectable("4: Scrollbar Track", selectedComponentId.get() == 4)) selectedComponentId.set(4);
                if (ImGui.selectable("5: Scrollbar Thumb", selectedComponentId.get() == 5)) selectedComponentId.set(5);
                ImGui.treePop();
            }
            if (ImGui.selectable("6: Footer Action Bar", selectedComponentId.get() == 6)) selectedComponentId.set(6);
            ImGui.treePop();
        }

        ImGui.dummy(1.0f, 12.0f);
        if (StudioWidgets.buttonSecondary("+ Add Child Component", ImGui.getContentRegionAvailX(), 28.0f)) {
            // Action to create widget node
        }
    }

    private void renderCanvasPanel(LoadedOsrsCacheSession cache) {
        // Mode toolbar
        if (ImGui.radioButton("Blank Canvas", previewMode == PreviewMode.BLANK_CANVAS)) {
            previewMode = PreviewMode.BLANK_CANVAS;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Preview over Game Scene", previewMode == PreviewMode.OVER_GAME_SCENE)) {
            previewMode = PreviewMode.OVER_GAME_SCENE;
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 140.0f);
        ImGui.textDisabled("Canvas: 512 x 334");
        ImGui.separator();

        // Canvas Area
        float canvasW = Math.min(512.0f, ImGui.getContentRegionAvailX());
        float canvasH = Math.min(334.0f, ImGui.getContentRegionAvailY() - 20.0f);
        float posX = ImGui.getCursorScreenPos().x + (ImGui.getContentRegionAvailX() - canvasW) * 0.5f;
        float posY = ImGui.getCursorScreenPos().y + (ImGui.getContentRegionAvailY() - canvasH) * 0.5f;

        imgui.ImDrawList draw = ImGui.getWindowDrawList();
        int bg = previewMode == PreviewMode.BLANK_CANVAS ? 0xFF181818 : 0xFF2A3A4A;
        draw.addRectFilled(posX, posY, posX + canvasW, posY + canvasH, bg, 4.0f);
        draw.addRect(posX, posY, posX + canvasW, posY + canvasH, 0xFF4A5568, 4.0f);

        // Simulated widget boundaries inside preview canvas
        draw.addRect(posX + 20, posY + 20, posX + canvasW - 20, posY + canvasH - 20, 0xFF718096, 2.0f);
        draw.addText(StudioFonts.mono(), 13, posX + 30, posY + 30, 0xFFE2E8F0, "Bank of Gielinor");
    }

    private void renderPropertiesPanel(LoadedOsrsCacheSession cache, EditorPluginLifecycleManager pluginLifecycle) {
        if (ImGui.beginTabBar("##ui-properties-tabs")) {
            if (ImGui.beginTabItem("Component")) {
                StudioWidgets.section("Widget Attributes");
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("Component ID: " + selectedComponentId.get());
                ImGui.text("Parent ID:    " + selectedInterfaceId.get());
                ImGui.text("Type:         CONTAINER (0)");
                ImGui.text("Position:     X: 20  |  Y: 20");
                ImGui.text("Dimensions:   W: 472 |  H: 294");
                ImGui.text("Hidden:       false");
                ImGui.popFont();
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("CS2 Scripts")) {
                StudioWidgets.section("ClientScript 2 Bindings");
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("on_load:   cs2.bank_init (42)");
                ImGui.text("on_click:  cs2.bank_tab_select (88)");
                ImGui.text("on_timer:  cs2.bank_refresh (104)");
                ImGui.popFont();
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Runtime State")) {
                StudioWidgets.section("Simulated Variables");
                ImGui.textDisabled("Varps and varbits driving UI conditions:");
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("varp[261] (Bank Tab):     0");
                ImGui.text("varbit[1007] (Note Mode): 1");
                ImGui.text("varbit[304] (Insert Mode):0");
                ImGui.popFont();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }
}
