package com.rspsi.studio;

import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
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
        ImGui.beginChild("##project-loading-card", width, 330.0f, true);
        ImGui.dummy(1.0f, 18.0f);

        ImGui.pushStyleColor(ImGuiCol.Text, 0.90f, 0.93f, 1.0f, 1.0f);
        centered(project.name());
        ImGui.popStyleColor();
        centered(project.kind() == StudioProjectKind.OPENRUNE_SERVER
                ? "OpenRune Server Project" : "Standalone OSRS Cache Project");
        ImGui.dummy(1.0f, 18.0f);

        if (status.failed()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.38f, 0.38f, 1.0f);
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
            ImGui.progressBar((float) status.progress(), -1.0f, 12.0f);
            ImGui.dummy(1.0f, 10.0f);
            centered(status.phase().name().replace('_', ' '));
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

        ImGui.endChild();
        ImGui.end();
    }

    private static void centered(String text) {
        String value = text == null ? "" : text;
        float width = ImGui.calcTextSize(value).x;
        ImGui.setCursorPosX(ImGui.getCursorPosX()
                + Math.max(0.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f));
        ImGui.textUnformatted(value);
    }
}
