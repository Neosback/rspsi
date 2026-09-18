package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.OsrsCacheSessionService;
import imgui.ImGui;

import java.nio.file.Files;
import java.nio.file.Path;

/** Initial native application shell; services and workspaces attach here. */
public final class StudioApplication implements AutoCloseable {
    private final NativeWindow window;
    private final ImGuiHost imgui = new ImGuiHost();
    private final OsrsCacheSessionService cacheSessions = new OsrsCacheSessionService();
    private final StudioPreferences preferences = new StudioPreferences();
    private final WorkspaceManager workspaces = new WorkspaceManager();
    private final DashboardView dashboard;
    private final MapEditorView mapEditor = new MapEditorView();
    private Path lastReadyCache;
    private boolean closed;

    public StudioApplication() {
        window = new NativeWindow(1320, 860, "OpenRune Studio");
        imgui.initialize(window);
        String initialCache = System.getenv("RSPSI_OSRS_CACHE");
        if (initialCache == null || initialCache.isBlank()) initialCache = preferences.recentCache();
        dashboard = new DashboardView(initialCache);
        if (initialCache != null && !initialCache.isBlank()
                && Files.isDirectory(Path.of(initialCache))) {
            loadCache(Path.of(initialCache));
        }
    }

    public void run() {
        try {
            while (!window.shouldClose()) {
                window.pollEvents();
                imgui.beginFrame();
                drawApplication();
                imgui.endFrame();
                window.swapBuffers();
            }
        } finally {
            close();
        }
    }

    private void drawApplication() {
        if (workspaces.active() == WorkspaceManager.Workspace.DASHBOARD) {
            dashboard.render(cacheSessions.status(), this::loadCache,
                    () -> workspaces.openMapEditor(cacheSessions.status().state()));
            rememberReadyCache();
            return;
        }
        LoadedOsrsCacheSession cache = cacheSessions.current().orElse(null);
        if (cache == null || cacheSessions.status().state() != CacheSessionState.READY) {
            workspaces.openDashboard();
            return;
        }
        mapEditor.render(cache, workspaces::openDashboard);
    }

    private void loadCache(Path path) {
        if (path == null) return;
        cacheSessions.load(path);
    }

    private void rememberReadyCache() {
        cacheSessions.current().ifPresent(session -> {
            if (cacheSessions.status().state() != CacheSessionState.READY) return;
            if (session.path().equals(lastReadyCache)) return;
            lastReadyCache = session.path();
            preferences.rememberCache(session.path());
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cacheSessions.close();
        imgui.close();
        window.close();
    }
}
