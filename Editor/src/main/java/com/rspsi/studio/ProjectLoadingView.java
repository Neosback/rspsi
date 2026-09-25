package com.rspsi.studio;

import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.Objects;

/** Dedicated full-window loading gate shown before any project workspace/dashboard. */
public final class ProjectLoadingView {

    public void render(
            StudioProjectDescriptor project,
            ProjectLoadStatus status,
            Runnable retry,
            Runnable backToLauncher) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(status, "status");

        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("Project Loading##openrune", flags)) {
            ImGui.end();
            return;
        }

        float width = Math.min(620.0f, Math.max(420.0f, ImGui.getContentRegionAvailX() - 80.0f));
        float x = Math.max(24.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + x);
        ImGui.dummy(1.0f, Math.max(70.0f, viewport.getSizeY() * 0.16f));

        ImGui.setCursorPosX(ImGui.getCursorPosX() + x);
        ImGui.beginChild("##project-loading-card", width, 390.0f, true);
        ImGui.dummy(1.0f, 16.0f);

        ImGui.pushFont(StudioFonts.display(), 31.0f);
        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.ACCENT);
        centered("OPENRUNE CONTENT STUDIO");
        ImGui.popStyleColor();
        ImGui.popFont();
        ImGui.dummy(1.0f, 22.0f);

        ImGui.pushFont(StudioFonts.heading(), 23.0f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.97f, 1.0f, 1.0f);
        centered(project.name());
        ImGui.popStyleColor();
        ImGui.popFont();
        centered(project.kind() == StudioProjectKind.OPENRUNE_SERVER
                ? "OpenRune-Server project" : "OSRS cache");
        ImGui.dummy(1.0f, 18.0f);

        if (status.failed()) {
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.DANGER);
            centered("Project could not be opened");
            ImGui.popStyleColor();
            ImGui.dummy(1.0f, 8.0f);
            ImGui.textWrapped(status.detail().isBlank() ? status.message() : status.detail());
            ImGui.dummy(1.0f, 16.0f);
            if (retry != null && StudioWidgets.buttonPrimary("Retry", 120.0f, 32.0f)) retry.run();
            ImGui.sameLine();
            if (backToLauncher != null && StudioWidgets.buttonSecondary(
                    "Back to Projects", 150.0f, 32.0f)) {
                backToLauncher.run();
            }
        } else {
            centered(status.message());
            ImGui.dummy(1.0f, 14.0f);
            ImGui.pushStyleColor(ImGuiCol.PlotHistogram, StudioPalette.ACCENT);
            ImGui.progressBar((float) status.progress(), -1.0f, 12.0f);
            ImGui.popStyleColor();
            ImGui.dummy(1.0f, 10.0f);
            centered(phaseLabel(status.phase()));
            if (!status.detail().isBlank()) {
                ImGui.dummy(1.0f, 6.0f);
                ImGui.textDisabled(status.detail());
            }
            ImGui.dummy(1.0f, 22.0f);
            if (backToLauncher != null && StudioWidgets.buttonSecondary(
                    "Cancel / Back", 140.0f, 30.0f)) {
                backToLauncher.run();
            }
        }

        float footerY = 390.0f - ImGui.getTextLineHeightWithSpacing() - 10.0f;
        if (ImGui.getCursorPosY() < footerY) ImGui.setCursorPosY(footerY);
        ImGui.textDisabled(StudioBuildInfo.displayVersion());

        ImGui.endChild();
        ImGui.end();
    }

    private static String phaseLabel(ProjectLoadStatus.Phase phase) {
        return switch (phase) {
            case READ_DESCRIPTOR, VALIDATE_PROJECT -> "Checking project";
            case INSPECT_INTEGRATION -> "Finding OpenRune files";
            case RESOLVE_CACHE_ROLES -> "Selecting project cache";
            case VERIFY_CACHE -> "Checking FileStore";
            case OPEN_CACHE_FILESYSTEM -> "Opening workspace cache";
            case PREPARE_DEFINITIONS -> "Preparing definitions";
            case BIND_REQUIRED_PROJECT_SERVICES -> "Finishing setup";
            case READY -> "Ready";
            case FAILED -> "Unable to open";
        };
    }

    private static void centered(String text) {
        String value = text == null ? "" : text;
        float width = ImGui.calcTextSize(value).x;
        ImGui.setCursorPosX(ImGui.getCursorPosX()
                + Math.max(0.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f));
        ImGui.textUnformatted(value);
    }
}
