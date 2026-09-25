package com.rspsi.studio

import com.rspsi.project.StudioProjectDescriptor
import com.rspsi.project.StudioProjectKind
import com.rspsi.studio.theme.StudioFonts
import com.rspsi.studio.theme.StudioPalette
import com.rspsi.studio.theme.StudioWidgets
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import kotlin.math.max
import kotlin.math.min

/** Dedicated full-window loading gate shown before any project workspace/dashboard. */
class ProjectLoadingView {
    fun render(
        project: StudioProjectDescriptor?,
        status: ProjectLoadStatus?,
        retry: Runnable?,
        backToLauncher: Runnable?,
    ) {
        val safeProject = project ?: throw NullPointerException("project")
        val safeStatus = status ?: throw NullPointerException("status")

        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.posX, viewport.posY)
        ImGui.setNextWindowSize(viewport.sizeX, viewport.sizeY)

        val flags =
            ImGuiWindowFlags.NoDecoration or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoSavedSettings or
                ImGuiWindowFlags.NoBringToFrontOnFocus or
                ImGuiWindowFlags.NoDocking

        if (!ImGui.begin("Project Loading##openrune", flags)) {
            ImGui.end()
            return
        }

        val width = min(620.0f, max(420.0f, ImGui.getContentRegionAvailX() - 80.0f))
        val x = max(24.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f)
        ImGui.setCursorPosX(ImGui.getCursorPosX() + x)
        ImGui.dummy(1.0f, max(70.0f, viewport.sizeY * 0.16f))

        ImGui.setCursorPosX(ImGui.getCursorPosX() + x)
        ImGui.beginChild("##project-loading-card", width, 390.0f, true)
        ImGui.dummy(1.0f, 16.0f)

        ImGui.pushFont(StudioFonts.display(), 31.0f)
        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.ACCENT)
        centered("OPENRUNE CONTENT STUDIO")
        ImGui.popStyleColor()
        ImGui.popFont()
        ImGui.dummy(1.0f, 22.0f)

        ImGui.pushFont(StudioFonts.heading(), 23.0f)
        ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.97f, 1.0f, 1.0f)
        centered(safeProject.name())
        ImGui.popStyleColor()
        ImGui.popFont()
        centered(
            if (safeProject.kind() == StudioProjectKind.OPENRUNE_SERVER) {
                "OpenRune-Server project"
            } else {
                "OSRS cache"
            },
        )
        ImGui.dummy(1.0f, 18.0f)

        if (safeStatus.failed()) {
            renderFailure(safeStatus, retry, backToLauncher)
        } else {
            renderProgress(safeStatus, backToLauncher)
        }

        val footerY = 390.0f - ImGui.getTextLineHeightWithSpacing() - 10.0f
        if (ImGui.getCursorPosY() < footerY) {
            ImGui.setCursorPosY(footerY)
        }
        ImGui.textDisabled(StudioBuildInfo.displayVersion())

        ImGui.endChild()
        ImGui.end()
    }

    private fun renderFailure(
        status: ProjectLoadStatus,
        retry: Runnable?,
        backToLauncher: Runnable?,
    ) {
        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.DANGER)
        centered("Project could not be opened")
        ImGui.popStyleColor()
        ImGui.dummy(1.0f, 8.0f)
        ImGui.textWrapped(status.detail().ifBlank { status.message() })
        ImGui.dummy(1.0f, 16.0f)

        if (retry != null && StudioWidgets.buttonPrimary("Retry", 120.0f, 32.0f)) {
            retry.run()
        }
        ImGui.sameLine()
        if (
            backToLauncher != null &&
            StudioWidgets.buttonSecondary("Back to Projects", 150.0f, 32.0f)
        ) {
            backToLauncher.run()
        }
    }

    private fun renderProgress(
        status: ProjectLoadStatus,
        backToLauncher: Runnable?,
    ) {
        centered(status.message())
        ImGui.dummy(1.0f, 14.0f)
        ImGui.pushStyleColor(ImGuiCol.PlotHistogram, StudioPalette.ACCENT)
        ImGui.progressBar(status.progress().toFloat(), -1.0f, 12.0f)
        ImGui.popStyleColor()
        ImGui.dummy(1.0f, 10.0f)
        centered(phaseLabel(status.phase()))

        if (status.detail().isNotBlank()) {
            ImGui.dummy(1.0f, 6.0f)
            ImGui.textDisabled(status.detail())
        }

        ImGui.dummy(1.0f, 22.0f)
        if (
            backToLauncher != null &&
            StudioWidgets.buttonSecondary("Cancel / Back", 140.0f, 30.0f)
        ) {
            backToLauncher.run()
        }
    }

    private fun phaseLabel(phase: ProjectLoadStatus.Phase): String =
        when (phase) {
            ProjectLoadStatus.Phase.READ_DESCRIPTOR,
            ProjectLoadStatus.Phase.VALIDATE_PROJECT,
            -> "Checking project"

            ProjectLoadStatus.Phase.INSPECT_INTEGRATION -> "Finding OpenRune files"
            ProjectLoadStatus.Phase.RESOLVE_CACHE_ROLES -> "Selecting project cache"
            ProjectLoadStatus.Phase.VERIFY_CACHE -> "Checking FileStore"
            ProjectLoadStatus.Phase.OPEN_CACHE_FILESYSTEM -> "Opening workspace cache"
            ProjectLoadStatus.Phase.PREPARE_DEFINITIONS -> "Preparing definitions"
            ProjectLoadStatus.Phase.BIND_REQUIRED_PROJECT_SERVICES -> "Finishing setup"
            ProjectLoadStatus.Phase.READY -> "Ready"
            ProjectLoadStatus.Phase.FAILED -> "Unable to open"
        }

    private fun centered(text: String?) {
        val value = text.orEmpty()
        val width = ImGui.calcTextSize(value).x
        ImGui.setCursorPosX(
            ImGui.getCursorPosX() +
                max(0.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f),
        )
        ImGui.textUnformatted(value)
    }
}
