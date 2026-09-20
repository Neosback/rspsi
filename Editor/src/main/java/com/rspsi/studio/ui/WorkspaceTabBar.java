package com.rspsi.studio.ui;

import com.rspsi.studio.WorkspaceManager;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dedicated workspace tab strip beneath the main menu bar.
 * Decouples workspace navigation from the program menu bar so they never clash.
 */
public final class WorkspaceTabBar {
    public static final float HEIGHT = 28.0f;
    private static final int BAR_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoScrollbar;

    public void render(WorkspaceManager workspaces,
                       Runnable openDashboard,
                       Runnable openMapEditor,
                       Runnable openInterfaceStudio,
                       Runnable openObjectStudio,
                       Consumer<WorkspaceManager.Workspace> closeWorkspace,
                       Runnable openCommandPalette,
                       float x, float y, float width) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, HEIGHT, ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 2.0f);
        // Same panel gray as the menu bar/other chrome, instead of a near-black that clashed.
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ImGui.getColorU32(0x26 / 255.0f, 0x28 / 255.0f, 0x2B / 255.0f, 1.0f));

        ImGui.begin("StudioWorkspaceTabBar", BAR_FLAGS);

        if (workspaces != null) {
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8.0f, 3.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2.0f, 0.0f);

            int tabIdx = 0;
            for (WorkspaceManager.Workspace ws : workspaces.open()) {
                if (tabIdx > 0) ImGui.sameLine(0.0f, 4.0f);
                boolean isActive = workspaces.active() == ws;

                String title = switch (ws) {
                    case DASHBOARD -> StudioIcons.HOME + " Dashboard";
                    case MAP_EDITOR -> StudioIcons.MAP + " Map Studio";
                    case INTERFACE_STUDIO -> StudioIcons.VIEWPORT + " Interface Studio";
                    case OBJECT_STUDIO -> StudioIcons.OBJECT + " Object Studio";
                };

                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.18f, 0.38f, 0.65f, 1.0f));
                    ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 1.0f));
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.12f, 0.14f, 0.18f, 0.90f));
                    ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(0.65f, 0.68f, 0.75f, 1.0f));
                }

                if (ImGui.button(title + "##ws-tab-" + tabIdx)) {
                    workspaces.focus(ws);
                    switch (ws) {
                        case DASHBOARD -> { if (openDashboard != null) openDashboard.run(); }
                        case MAP_EDITOR -> { if (openMapEditor != null) openMapEditor.run(); }
                        case INTERFACE_STUDIO -> { if (openInterfaceStudio != null) openInterfaceStudio.run(); }
                        case OBJECT_STUDIO -> { if (openObjectStudio != null) openObjectStudio.run(); }
                    }
                }

                // Close button for non-dashboard tabs
                if (ws != WorkspaceManager.Workspace.DASHBOARD) {
                    ImGui.sameLine(0.0f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(0.70f, 0.20f, 0.20f, 1.0f));
                    if (ImGui.button(StudioIcons.CLOSE + "##ws-close-" + tabIdx)) {
                        if (closeWorkspace != null) {
                            closeWorkspace.accept(ws);
                        } else {
                            workspaces.close(ws);
                        }
                    }
                    ImGui.popStyleColor();
                }

                ImGui.popStyleColor(2);
                tabIdx++;
            }

            ImGui.popStyleVar(2);
        }

        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }
}
