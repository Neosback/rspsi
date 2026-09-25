package com.rspsi.studio.ui

import com.rspsi.studio.WorkspaceManager
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.theme.StudioPalette
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import java.util.function.Consumer

/**
 * Dedicated workspace tab strip beneath the main menu bar.
 * Keeps workspace navigation separate from the program menu bar.
 */
class WorkspaceTabBar {
    @Suppress("UNUSED_PARAMETER")
    fun render(
        workspaces: WorkspaceManager?,
        openDashboard: Runnable?,
        openMapEditor: Runnable?,
        openInterfaceStudio: Runnable?,
        openObjectStudio: Runnable?,
        closeWorkspace: Consumer<WorkspaceManager.Workspace>?,
        openCommandPalette: Runnable?,
        x: Float,
        y: Float,
        width: Float,
    ) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always)
        ImGui.setNextWindowSize(width, HEIGHT, ImGuiCond.Always)
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID())

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 2.0f)
        ImGui.pushStyleColor(ImGuiCol.WindowBg, StudioPalette.CHROME_BG)

        ImGui.begin("StudioWorkspaceTabBar", BAR_FLAGS)

        if (workspaces != null) {
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8.0f, 3.0f)
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2.0f, 0.0f)

            for ((tabIndex, workspace) in workspaces.open().withIndex()) {
                if (tabIndex > 0) {
                    ImGui.sameLine(0.0f, 4.0f)
                }

                val isActive = workspaces.active() == workspace
                pushTabColors(isActive)

                if (ImGui.button("${titleFor(workspace)}##ws-tab-$tabIndex")) {
                    workspaces.focus(workspace)
                    openActionFor(
                        workspace,
                        openDashboard,
                        openMapEditor,
                        openInterfaceStudio,
                        openObjectStudio,
                    )?.run()
                }

                ImGui.popStyleColor(4)

                if (
                    workspace != WorkspaceManager.Workspace.DASHBOARD &&
                    ImGui.beginPopupContextItem("##ws-tab-context-$tabIndex")
                ) {
                    if (ImGui.menuItem("Close")) {
                        closeWorkspace?.accept(workspace) ?: workspaces.close(workspace)
                    }
                    ImGui.endPopup()
                }
            }

            ImGui.popStyleVar(2)
        }

        ImGui.end()
        ImGui.popStyleColor()
        ImGui.popStyleVar()
    }

    private fun pushTabColors(active: Boolean) {
        ImGui.pushStyleColor(
            ImGuiCol.Button,
            if (active) StudioPalette.ACCENT else StudioPalette.CHROME_BG,
        )
        ImGui.pushStyleColor(
            ImGuiCol.ButtonHovered,
            if (active) StudioPalette.ACCENT_HOVER else StudioPalette.FIELD_HOVER,
        )
        ImGui.pushStyleColor(
            ImGuiCol.ButtonActive,
            if (active) StudioPalette.ACCENT_ACTIVE else StudioPalette.ACCENT_SOFT,
        )
        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT)
    }

    private fun titleFor(workspace: WorkspaceManager.Workspace): String =
        when (workspace) {
            WorkspaceManager.Workspace.DASHBOARD -> "Content Studio"
            WorkspaceManager.Workspace.MAP_EDITOR -> "${StudioIcons.MAP} Map Studio"
            WorkspaceManager.Workspace.INTERFACE_STUDIO -> "${StudioIcons.VIEWPORT} Interface Studio"
            WorkspaceManager.Workspace.OBJECT_STUDIO -> "${StudioIcons.OBJECT} Object Studio"
        }

    private fun openActionFor(
        workspace: WorkspaceManager.Workspace,
        openDashboard: Runnable?,
        openMapEditor: Runnable?,
        openInterfaceStudio: Runnable?,
        openObjectStudio: Runnable?,
    ): Runnable? =
        when (workspace) {
            WorkspaceManager.Workspace.DASHBOARD -> openDashboard
            WorkspaceManager.Workspace.MAP_EDITOR -> openMapEditor
            WorkspaceManager.Workspace.INTERFACE_STUDIO -> openInterfaceStudio
            WorkspaceManager.Workspace.OBJECT_STUDIO -> openObjectStudio
        }

    companion object {
        const val HEIGHT = 28.0f

        private const val BAR_FLAGS =
            ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoDocking or
                ImGuiWindowFlags.NoBringToFrontOnFocus or
                ImGuiWindowFlags.NoSavedSettings or
                ImGuiWindowFlags.NoScrollbar
    }
}
