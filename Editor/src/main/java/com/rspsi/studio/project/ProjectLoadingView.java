package com.rspsi.studio.project;

import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.Objects;

/** Dedicated full-window loading gate shown before the project Dashboard exists. */
public final class ProjectLoadingView {

    public void render(ProjectLoadStatus status, Runnable retry, Runnable cancelOrBack) {
        Objects.requireNonNull(status, "status");
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("OpenRune Studio##project-loading", flags)) {
            ImGui.end();
            return;
        }

        float width = Math.min(760.0f, ImGui.getContentRegionAvailX() - 40.0f);
        float x = Math.max(20.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + x);
        if (ImGui.beginChild("##loading-panel", width, 0.0f, false)) {
            ImGui.dummy(1.0f, 42.0f);
            ImGui.textDisabled("OPENRUNE STUDIO");
            StudioProjectDescriptor project = status.project();
            ImGui.setWindowFontScale(1.25f);
            ImGui.text(project == null ? "Loading project" : project.name());
            ImGui.setWindowFontScale(1.0f);
            if (project != null) {
                ImGui.textDisabled(project.kind().displayName() + " · " + project.sourcePath());
            }

            ImGui.dummy(1.0f, 20.0f);
            float fraction = (float) status.progress();
            ImGui.progressBar(fraction, -1.0f, 10.0f, "");
            ImGui.dummy(1.0f, 14.0f);

            for (ProjectLoadCheck check : status.checks()) {
                renderCheck(check);
            }

            ImGui.dummy(1.0f, 16.0f);
            if (status.state() == ProjectLoadState.FAILED) {
                StudioWidgets.beginCard("project-load-error", -1.0f, 92.0f);
                ImGui.textColored(StudioDrawColors.abgr(0xFFF87171), "[X] Project open failed");
                ImGui.textWrapped(status.message());
                StudioWidgets.endCard();
                ImGui.dummy(1.0f, 10.0f);
                if (StudioWidgets.buttonPrimary("Retry", 110.0f, 32.0f)) retry.run();
                ImGui.sameLine();
                if (StudioWidgets.buttonGhost("Back to Launcher", 150.0f, 32.0f)) cancelOrBack.run();
            } else if (status.state() == ProjectLoadState.LOADING) {
                ImGui.textDisabled(status.message());
                ImGui.dummy(1.0f, 8.0f);
                if (StudioWidgets.buttonGhost("Cancel", 100.0f, 30.0f)) cancelOrBack.run();
            } else if (status.state() == ProjectLoadState.CANCELLED) {
                if (StudioWidgets.buttonPrimary("Back to Launcher", 150.0f, 32.0f)) {
                    cancelOrBack.run();
                }
            }
        }
        ImGui.endChild();
        ImGui.end();
    }

    private static void renderCheck(ProjectLoadCheck check) {
        int color;
        String icon;
        switch (check.state()) {
            case SUCCESS -> {
                color = StudioDrawColors.abgr(0xFF4ADE80);
                icon = "[OK]";
            }
            case FAILED -> {
                color = StudioDrawColors.abgr(0xFFF87171);
                icon = "[X]";
            }
            case RUNNING -> {
                color = StudioDrawColors.abgr(0xFF60A5FA);
                icon = "[..]";
            }
            case SKIPPED -> {
                color = StudioDrawColors.abgr(0xFF94A3B8);
                icon = "[-]";
            }
            default -> {
                color = StudioDrawColors.abgr(0xFF64748B);
                icon = "[ ]";
            }
        }

        ImGui.textColored(color, icon + "  " + check.step().label());
        if (!check.detail().isBlank()) {
            ImGui.sameLine();
            ImGui.textDisabled("  " + check.detail());
        }
        ImGui.dummy(1.0f, 3.0f);
    }
}
