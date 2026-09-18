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
    private final ImString region = new ImString("50,50", 32);

    public DashboardView(String initialPath) {
        cachePath.set(initialPath == null ? "" : initialPath);
    }

    public void render(CacheSessionStatus status,
                       Consumer<Path> loadCache,
                       Runnable openMapEditor) {
        Objects.requireNonNull(status, "cache status");
        Objects.requireNonNull(loadCache, "load cache callback");
        Objects.requireNonNull(openMapEditor, "open workspace callback");

        // Track the real window size every frame (like MapEditorView already
        // does via getIO().getDisplaySizeX/Y) instead of a hardcoded
        // 1280x800 - the dashboard previously did not resize with the
        // window at all.
        imgui.ImGuiViewport mainViewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(mainViewport.getPosX(), mainViewport.getPosY());
        ImGui.setNextWindowSize(mainViewport.getSizeX(), mainViewport.getSizeY());
        int flags = imgui.flag.ImGuiWindowFlags.NoDecoration
                | imgui.flag.ImGuiWindowFlags.NoMove
                | imgui.flag.ImGuiWindowFlags.NoSavedSettings
                | imgui.flag.ImGuiWindowFlags.NoBringToFrontOnFocus
                | imgui.flag.ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("OpenRune Studio", flags)) {
            ImGui.end();
            return;
        }

        // Center a fixed-width readable column instead of stretching every
        // widget the full window width on a wide/ultrawide display.
        float contentWidth = Math.min(720.0f, ImGui.getContentRegionAvailX());
        float margin = Math.max(24.0f, (ImGui.getContentRegionAvailX() - contentWidth) * 0.5f);
        ImGui.dummy(1.0f, 32.0f);
        ImGui.indent(margin);

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.88f, 0.91f, 0.98f, 1.0f);
        ImGui.text("OPENRUNE STUDIO");
        ImGui.popStyleColor();
        ImGui.textDisabled("Cache-first editing for RuneScape worlds");
        ImGui.dummy(1.0f, 12.0f);

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

        ImGui.inputTextWithHint("##region", "Region X,Y or region ID", region);
        ImGui.textDisabled("Example: 50,50 opens the Lumbridge region.");

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

        ImGui.unindent(margin);
        ImGui.end();
    }

    private static void renderStatus(CacheSessionStatus status) {
        switch (status.state()) {
            case EMPTY -> ImGui.textDisabled("No cache selected.");
            case LOADING -> {
                ImGui.text(status.message());
                ImGui.progressBar((float) status.progress(), -1, 0,
                        status.phase().name().replace('_', ' '));
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

    public String regionText() {
        return region.get().trim();
    }
}
