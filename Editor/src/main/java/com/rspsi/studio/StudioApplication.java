package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.OsrsCacheSessionService;
import com.rspsi.cache.map.OsrsProjectSessionLoader;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorPluginLoader;
import com.rspsi.editor.plugin.EditorPluginStateStore;
import com.rspsi.editor.plugin.EditorNotificationService;
import com.rspsi.editor.plugin.EditorSceneAccess;
import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.plugin.EditorTaskService;
import com.rspsi.editor.plugin.PluginDiscovery;
import com.rspsi.editor.plugin.builtin.CoreToolsPlugin;
import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.GpuScenePacketBuilder;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuUploadPlanBuilder;
import com.rspsi.editor.render.RenderConfig;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.RenderWindowScene;
import com.rspsi.editor.render.RenderWindowSceneBuilder;
import com.rspsi.editor.render.SceneWindow;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.settings.SettingsJsonStore;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.simulation.SimulationEngine;
import com.rspsi.editor.symbols.CacheGamevalProvider;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.plugins.server.openrune.OpenRuneServerPlugin;
import com.rspsi.plugins.server.openrune.OpenRuneServerProvider;
import com.rspsi.studio.integration.IntegrationCenterWindow;
import com.rspsi.studio.workspace.InterfaceStudioView;
import com.rspsi.studio.workspace.ObjectStudioView;
import imgui.ImGui;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Initial native application shell; services and workspaces attach here. */
public final class StudioApplication implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudioApplication.class);
    private final NativeWindow window;
    private final ImGuiHost imgui = new ImGuiHost();
    private final OsrsCacheSessionService cacheSessions = new OsrsCacheSessionService();
    private final StudioPreferences preferences = new StudioPreferences();
    private final WorkspaceManager workspaces = new WorkspaceManager();
    private final DashboardView dashboard;
    private final MapEditorView mapEditor = new MapEditorView();
    private final InterfaceStudioView interfaceStudio = new InterfaceStudioView();
    private final ObjectStudioView objectStudio = new ObjectStudioView();
    private final IntegrationCenterWindow integrationCenter = new IntegrationCenterWindow();
    private final ImBoolean integrationCenterOpen = new ImBoolean(false);

    // Shared Studio platform runtime services
    private final SymbolService symbols = new SymbolService();
    private final ReferenceService references = new ReferenceService();
    private final NpcSpawnService spawns = new NpcSpawnService();
    private final SimulationEngine simulation = new SimulationEngine();
    private final ServerIntegrationService integrations;
    private final NativeSceneViewport sceneViewport = new NativeSceneViewport();
    private final SettingsStore renderSettings = new SettingsStore(EditorSettingKeys.registry());
    private final EditorTaskService tasks = new EditorTaskService();
    private final EditorNotificationService notifications = new EditorNotificationService();
    private final Path settingsFile = Path.of(System.getProperty("user.home"),
            ".openrune-studio", "settings.json");
    private final ExecutorService sceneExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "openrune-scene-loader");
        thread.setDaemon(true);
        return thread;
    });
    private CompletableFuture<LoadedMapScene> pendingScene;
    private LoadedMapScene loadedScene;
    private EditorPluginLifecycleManager pluginLifecycle;
    private GpuUploadPlan currentPlan;
    private long renderedSettingsRevision = -1L;
    private String sceneStatus = "Choose a region to build the scene.";
    private Path lastReadyCache;
    private boolean closePrompt;
    private boolean closed;

    public StudioApplication() {
        window = new NativeWindow(1320, 860, "OpenRune Studio");
        integrations = new ServerIntegrationService(symbols, references, spawns);
        integrations.registerProvider(new OpenRuneServerProvider());
        SettingsJsonStore.load(settingsFile, renderSettings);
        imgui.initialize(window);
        sceneViewport.initialize();
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
            long lastFrameTime = System.nanoTime();
            while (!window.shouldClose()) {
                long now = System.nanoTime();
                long deltaNanos = Math.min(now - lastFrameTime, 100_000_000L);
                lastFrameTime = now;
                simulation.update(deltaNanos);

                window.pollEvents();
                imgui.beginFrame();
                window.clearFrame();
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
                    this::openMapEditor,
                    this::openInterfaceStudio,
                    this::openObjectStudio,
                    integrations,
                    () -> integrationCenterOpen.set(true));
            rememberReadyCache();
            integrationCenter.render(integrations, integrationCenterOpen);
            return;
        }
        LoadedOsrsCacheSession cache = cacheSessions.current().orElse(null);
        if (cache == null || cacheSessions.status().state() != CacheSessionState.READY) {
            openDashboard();
            return;
        }

        if (workspaces.active() == WorkspaceManager.Workspace.INTERFACE_STUDIO) {
            interfaceStudio.render(cache, renderSettings, pluginLifecycle,
                    this::requestDashboard, this::openMapEditor, this::openObjectStudio);
            integrationCenter.render(integrations, integrationCenterOpen);
            return;
        }

        if (workspaces.active() == WorkspaceManager.Workspace.OBJECT_STUDIO) {
            objectStudio.render(cache, renderSettings, pluginLifecycle,
                    this::requestDashboard, this::openMapEditor, this::openInterfaceStudio);
            integrationCenter.render(integrations, integrationCenterOpen);
            return;
        }

        pollSceneLoad();
        if (loadedScene != null && renderedSettingsRevision != renderSettings.revision()) {
            RenderConfig config = new RenderConfigCompiler().compile(renderSettings.snapshot());
            currentPlan = new GpuUploadPlanBuilder().build(config.apply(loadedScene.packet()));
            renderedSettingsRevision = renderSettings.revision();
        }
        mapEditor.render(cache, currentPlan, sceneViewport, sceneStatus,
                this::requestDashboard, renderSettings, pluginLifecycle,
                loadedScene != null && loadedScene.opened().region().session().isDirty(),
                this::openInterfaceStudio, this::openObjectStudio,
                () -> integrationCenterOpen.set(true),
                simulation, symbols, references, spawns, integrations);
        renderClosePrompt();
        integrationCenter.render(integrations, integrationCenterOpen);
    }

    private void openInterfaceStudio() {
        workspaces.openInterfaceStudio(cacheSessions.status().state());
    }

    private void openObjectStudio() {
        workspaces.openObjectStudio(cacheSessions.status().state());
    }

    private void loadCache(Path path) {
        if (path == null) return;
        cacheSessions.load(path);
    }

    private void openMapEditor() {
        if (!workspaces.openMapEditor(cacheSessions.status().state())) return;
        closePluginLifecycle();
        loadedScene = null;
        currentPlan = null;
        renderedSettingsRevision = -1L;
        cancelPendingScene();
        sceneStatus = "Loading terrain, objects, and GPU buffers...";
        int[] region = parseRegion(dashboard.regionText());
        if (region == null) {
            sceneStatus = "Enter a valid region as X,Y or a region ID.";
            return;
        }
        LoadedOsrsCacheSession cache = cacheSessions.current().orElse(null);
        if (cache == null) {
            sceneStatus = "Cache session is no longer available.";
            return;
        }
        pendingScene = CompletableFuture.supplyAsync(() -> buildMapScene(cache, region[0], region[1]), sceneExecutor);
    }

    private LoadedMapScene buildMapScene(LoadedOsrsCacheSession cache, int regionX, int regionY) {
        OsrsProjectSessionLoader.OpenedProject opened = cache.openRegion(regionX, regionY);
        WorldRegion region = opened.worldRegion();
        WorldRegionWindow window = new WorldRegionWindow(regionX, regionY, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder(cache.bundle().definitions()).build(window);
        SceneWindow sceneWindow = SceneWindow.from(window);
        GpuScenePacket packet = new GpuScenePacketBuilder().build(sceneWindow, scene);
        // Flattening a real region creates a large immutable GPU plan. Keep
        // this work on the loader thread so the native window remains
        // responsive while the scene is being prepared.
        long settingsRevision = renderSettings.revision();
        RenderConfig config = new RenderConfigCompiler().compile(renderSettings.snapshot());
        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(config.apply(packet));
        double centerX = sceneWindow.sceneBaseX() * 128.0 + window.worldWindow().width() * 64.0;
        double centerZ = sceneWindow.sceneBaseY() * 128.0 + window.worldWindow().length() * 64.0;
        RenderScene renderScene = new RenderSceneBuilder(cache.bundle().definitions()).build(region.document());
        return new LoadedMapScene(opened, renderScene, packet, plan, settingsRevision,
                new com.rspsi.editor.render.CameraState(
                (float) centerX, -2400.0f, (float) centerZ - 4200.0f,
                (float) -Math.toRadians(28.0), 0.0f));
    }

    private void pollSceneLoad() {
        if (pendingScene == null || !pendingScene.isDone()) return;
        try {
            loadedScene = pendingScene.join();
            sceneViewport.setCamera(loadedScene.camera());
            currentPlan = loadedScene.plan();
            renderedSettingsRevision = loadedScene.settingsRevision();
            initializePlugins(loadedScene);
            sceneStatus = "Region " + loadedScene.opened().region().regionX()
                    + "," + loadedScene.opened().region().regionY() + " ready.";
        } catch (RuntimeException failure) {
            // The status bar only has room for a short message; without this
            // the actual cause (and its stack trace) was silently dropped,
            // making "Unable to load region" undiagnosable from the log.
            LOGGER.error("Region load failed", failure);
            sceneStatus = "Unable to load region: " + rootMessage(failure);
        } finally {
            pendingScene = null;
        }
    }

    private static int[] parseRegion(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replace(" ", "");
        try {
            if (normalized.contains(",")) {
                String[] parts = normalized.split(",");
                if (parts.length != 2) return null;
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                return x >= 0 && x <= 255 && y >= 0 && y <= 255 ? new int[]{x, y} : null;
            }
            int id = Integer.parseInt(normalized);
            int x = (id >>> 8) & 0xFF;
            int y = id & 0xFF;
            return id >= 0 && id <= 0xFFFF ? new int[]{x, y} : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void rememberReadyCache() {
        cacheSessions.current().ifPresent(session -> {
            if (cacheSessions.status().state() != CacheSessionState.READY) return;
            if (session.path().equals(lastReadyCache)) return;
            lastReadyCache = session.path();
            preferences.rememberCache(session.path());
            symbols.unregisterProvider("osrs.cache.gamevals");
            symbols.registerProvider(new CacheGamevalProvider(session.bundle().definitions()));
        });
    }

    private void initializePlugins(LoadedMapScene scene) {
        List<EditorPlugin> candidates = new ArrayList<>(CoreToolsPlugin.builtIns());
        candidates.add(new OpenRuneServerPlugin());
        PluginDiscovery discovery = EditorPluginLoader.discoverOwned(
                Path.of("plugins"), Thread.currentThread().getContextClassLoader());
        candidates.addAll(discovery.plugins());
        EditorSession session = scene.opened().region().session();
        AssetRepository assets = cacheSessions.current()
                .map(LoadedOsrsCacheSession::bundle)
                .map(com.rspsi.cache.workspace.OsrsBundle::assets)
                .orElse(EmptyAssetRepository.INSTANCE);
        EditorSceneAccess sceneAccess = () -> EditorSceneSnapshot.from(scene.renderScene());
        EditorPluginLifecycleManager next = EditorPluginLifecycleManager.start(
                candidates,
                EditorPluginStateStore.defaultStore(),
                session,
                assets,
                sceneAccess,
                enabled -> EditorPluginHost.initialize(enabled, session,
                        assets, sceneAccess, renderSettings, tasks, notifications,
                        null, null, symbols, references, spawns, simulation, integrations),
                discovery);
        pluginLifecycle = next;
    }

    private void openDashboard() {
        closePluginLifecycle();
        cancelPendingScene();
        loadedScene = null;
        currentPlan = null;
        closePrompt = false;
        workspaces.openDashboard();
    }

    private void requestDashboard() {
        if (loadedScene != null && loadedScene.opened().region().session().isDirty()) {
            closePrompt = true;
            return;
        }
        openDashboard();
    }

    private void renderClosePrompt() {
        if (closePrompt) ImGui.openPopup("Unsaved map changes##dashboard");
        if (!ImGui.beginPopupModal("Unsaved map changes##dashboard")) return;
        ImGui.textWrapped("This map has unsaved changes. Save before returning to Dashboard?");
        if (ImGui.button("Save")) {
            try {
                if (loadedScene == null || !loadedScene.opened().region().session().canSave()) {
                    throw new IllegalStateException("This map session is read-only");
                }
                loadedScene.opened().region().session().save();
                ImGui.closeCurrentPopup();
                openDashboard();
            } catch (RuntimeException failure) {
                sceneStatus = "Save failed: " + rootMessage(failure);
                notifications.error("Map save failed", sceneStatus);
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Discard")) {
            ImGui.closeCurrentPopup();
            openDashboard();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
            closePrompt = false;
        }
        ImGui.endPopup();
    }

    private void cancelPendingScene() {
        if (pendingScene != null) pendingScene.cancel(true);
        pendingScene = null;
    }

    private void closePluginLifecycle() {
        if (pluginLifecycle == null) return;
        pluginLifecycle.close();
        pluginLifecycle = null;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        sceneExecutor.shutdownNow();
        closePluginLifecycle();
        mapEditor.close();
        sceneViewport.close();
        SettingsJsonStore.save(settingsFile, renderSettings);
        cacheSessions.close();
        imgui.close();
        window.close();
    }

    private record LoadedMapScene(OsrsProjectSessionLoader.OpenedProject opened,
                                  RenderScene renderScene,
                                  GpuScenePacket packet,
                                  GpuUploadPlan plan,
                                  long settingsRevision,
                                  com.rspsi.editor.render.CameraState camera) { }
}
