package com.rspsi.studio.ui;

import com.rspsi.studio.WorkspaceManager;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioPalette;
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
        ImGui.pushStyleColor(ImGuiCol.WindowBg, StudioPalette.CHROME_BG);

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

                ImGui.pushStyleColor(ImGuiCol.Button,
                        isActive ? StudioPalette.ACCENT : StudioPalette.CHROME_BG);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered,
                        isActive ? StudioPalette.ACCENT_HOVER : StudioPalette.FIELD_HOVER);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive,
                        isActive ? StudioPalette.ACCENT_ACTIVE : StudioPalette.ACCENT_SOFT);
                ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);

                if (ImGui.button(title + "##ws-tab-" + tabIdx)) {
                    workspaces.focus(ws);
                    switch (ws) {
                        case DASHBOARD -> { if (openDashboard != null) openDashboard.run(); }
                        case MAP_EDITOR -> { if (openMapEditor != null) openMapEditor.run(); }
                        case INTERFACE_STUDIO -> { if (openInterfaceStudio != null) openInterfaceStudio.run(); }
                        case OBJECT_STUDIO -> { if (openObjectStudio != null) openObjectStudio.run(); }
                    }
                }
                ImGui.popStyleColor(4);

                if (ws != WorkspaceManager.Workspace.DASHBOARD
                        && ImGui.beginPopupContextItem("##ws-tab-context-" + tabIdx)) {
                    if (ImGui.menuItem("Close")) {
                        if (closeWorkspace != null) closeWorkspace.accept(ws);
                        else workspaces.close(ws);
                    }
                    ImGui.endPopup();
                }
                tabIdx++;
            }

            ImGui.popStyleVar(2);
        }

        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }
}
