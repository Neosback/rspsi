package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
import imgui.ImGui;
import imgui.type.ImString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Dear ImGui dashboard for cache selection and workspace activation. */
public final class DashboardView {
    private final ImString cachePath = new ImString(512);

    public DashboardView(String initialPath) {
        cachePath.set(initialPath == null ? "" : initialPath);
    }

    public void render(CacheSessionStatus status,
                       Consumer<Path> loadCache,
                       Runnable openMapEditor) {
        Objects.requireNonNull(status, "cache status");
        Objects.requireNonNull(loadCache, "load cache callback");
        Objects.requireNonNull(openMapEditor, "open workspace callback");

        int[] size = {1280, 800};
        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(size[0], size[1]);
        int flags = imgui.flag.ImGuiWindowFlags.NoDecoration
                | imgui.flag.ImGuiWindowFlags.NoMove
                | imgui.flag.ImGuiWindowFlags.NoSavedSettings
                | imgui.flag.ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin("OpenRune Studio", flags)) {
            ImGui.end();
            return;
        }

        ImGui.text("OPENRUNE STUDIO");
        ImGui.text("Cache-first editing for RuneScape worlds");
        ImGui.separator();
        ImGui.spacing();

        ImGui.separatorText("Cache");
        ImGui.textDisabled("The selected cache stays alive while workspaces change.");
        ImGui.inputTextWithHint("##cache-path", "Path to an OSRS cache directory", cachePath);
        ImGui.sameLine();
        boolean loading = status.state() == CacheSessionState.LOADING;
        ImGui.beginDisabled(loading || cachePath.isEmpty());
        if (ImGui.button(loading ? "Loading..." : "Load cache")) {
            Path path = Path.of(cachePath.get()).toAbsolutePath().normalize();
            loadCache.accept(path);
        }
        ImGui.endDisabled();

        renderStatus(status);
        ImGui.spacing();
        ImGui.separatorText("Workspaces");
        ImGui.textWrapped("Open a workspace after the cache has been validated and indexed.");
        ImGui.beginDisabled(status.state() != CacheSessionState.READY);
        if (ImGui.button("Map Editor")) openMapEditor.run();
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled();
        ImGui.button("Interface Studio  ·  Coming soon");
        ImGui.endDisabled();

        ImGui.end();
    }

    private static void renderStatus(CacheSessionStatus status) {
        switch (status.state()) {
            case EMPTY -> ImGui.textDisabled("No cache selected.");
            case LOADING -> {
                ImGui.text("Loading OpenRune cache...");
                ImGui.progressBar(0.35f, -1, 0, "Validating cache and opening filesystem");
            }
            case READY -> status.currentSession().ifPresent(session -> {
                ImGui.text("READY  ·  " + session.backendName());
                ImGui.text("Revision " + session.identity().revision()
                        + "  ·  " + session.mapCount() + " map groups");
                ImGui.textDisabled(session.path().toString());
            });
            case FAILED -> {
                ImGui.text("CACHE LOAD FAILED");
                String detail = status.failure() == null ? status.message() : status.failure().getMessage();
                ImGui.textWrapped(detail == null || detail.isBlank() ? status.message() : detail);
            }
        }
    }

    public boolean pointsToDirectory() {
        return !cachePath.isEmpty() && Files.isDirectory(Path.of(cachePath.get()));
    }
}
