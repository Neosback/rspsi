package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheDecoderSummary;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.OsrsCacheSessionService;
import com.rspsi.cache.map.OsrsProjectSessionLoader;
import com.rspsi.cache.store.ObjectDefinitionOutputCacheBuilder;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorPluginStateStore;
import com.rspsi.editor.plugin.EditorNotificationService;
import com.rspsi.editor.plugin.EditorSceneAccess;
import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.plugin.EditorTaskService;
import com.rspsi.editor.plugin.builtin.CoreToolsPlugin;
import com.rspsi.editor.plugin.runtime.ExternalPluginRuntimeSnapshot;
import com.rspsi.editor.plugin.runtime.PluginEcosystemService;
import com.rspsi.editor.plugin.runtime.SemanticVersion;
import com.rspsi.editor.render.AnimationRefreshScheduler;
import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.GpuScenePacketBuilder;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuUploadPlanBuilder;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlanBuilder;
import com.rspsi.editor.render.IncrementalGpuUploadPlanBuilder;
import com.rspsi.editor.render.RenderConfig;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.ScenePresentation;
import com.rspsi.editor.render.RenderWindowScene;
import com.rspsi.editor.render.RenderWindowSceneBuilder;
import com.rspsi.editor.render.RenderChanges;
import com.rspsi.editor.render.SceneWindow;
import com.rspsi.editor.render.compiler.IncrementalRenderWindowSceneCompiler;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.settings.SettingsJsonStore;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.simulation.SimulationEngine;
import com.rspsi.editor.simulation.state.RuntimeState;
import com.rspsi.api.runtime.SimulatedClient;
import com.rspsi.api.worldmap.NavigatorWorldMap;
import com.rspsi.editor.symbols.CacheGamevalProvider;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.editor.plugin.builtin.tool.TilePainterToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.SplinePathToolPlugin;
import com.rspsi.plugins.server.openrune.OpenRuneServerPlugin;
import com.rspsi.plugins.server.openrune.OpenRuneServerProvider;
import com.rspsi.project.ProjectIntegrationCapability;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.project.StudioProjectRegistry;
import com.rspsi.project.StudioProjectService;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerPathKey;
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
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Initial native application shell; services and workspaces attach here. */
public final class StudioApplication implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudioApplication.class);
    private final NativeWindow window;
    private final ImGuiHost imgui = new ImGuiHost();
    private final OsrsCacheSessionService cacheSessions = new OsrsCacheSessionService();
    private final StudioProjectRegistry projectRegistry = new StudioProjectRegistry();
    private final StudioProjectService projectService = new StudioProjectService(projectRegistry);
    private final ProjectLauncherView projectLauncher =
            new ProjectLauncherView(projectRegistry, projectService);
    private final ProjectLoadingView projectLoading = new ProjectLoadingView();
    private final WorkspaceManager workspaces = new WorkspaceManager();
    private final DashboardView dashboard = new DashboardView();
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
    /** RuneLite-shaped view of the simulated player; scenes resolve multilocs against it. */
    private SimulatedClient player;
    /** Var state the current scene was built with; a difference triggers a rebuild. */
    private RuntimeState renderedVarState = RuntimeState.EMPTY;
    private final ServerIntegrationService integrations;
    private final NativeSceneViewport sceneViewport = new NativeSceneViewport();
    private final SettingsStore renderSettings = new SettingsStore(EditorSettingKeys.registry());
    private final EditorTaskService tasks = new EditorTaskService();
    private final EditorNotificationService notifications = new EditorNotificationService();
    private final PluginEcosystemService pluginEcosystem = new PluginEcosystemService(
            Path.of("plugins"),
            Path.of(System.getProperty("user.home"), ".openrune-studio", "plugin-repositories.json"));
    private final Path settingsFile = Path.of(System.getProperty("user.home"),
            ".openrune-studio", "settings.json");
    private final StudioDefinitionPublicationStore definitionPublicationStore =
            new StudioDefinitionPublicationStore(
                    Path.of(System.getProperty("user.home"),
                            ".openrune-studio",
                            "definition-publications.json"));
    private final ExecutorService sceneExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "openrune-scene-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService projectExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "openrune-project-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong projectOpenSequence = new AtomicLong();
    private final AtomicBoolean sceneDirty = new AtomicBoolean(false);
    private final AnimationRefreshDiagnostics animationRefreshDiagnostics =
            new AnimationRefreshDiagnostics();
    private final Object sceneChangeLock = new Object();
    private final Set<TileCoordinate> pendingSceneChanges = new LinkedHashSet<>();
    private CompletableFuture<LoadedMapScene> pendingScene;
    private CompletableFuture<LoadedMapScene> pendingSceneRebuild;
    private CompletableFuture<LoadedMapScene> pendingAnimationRefresh;
    private Set<TileCoordinate> pendingRebuildChanges = Set.of();
    private LoadedMapScene loadedScene;
    private EditorPluginLifecycleManager pluginLifecycle;
    private GpuUploadPlan currentPlan;
    private GpuZonedUploadPlan currentZonedPlan;
    private long renderedSettingsRevision = -1L;
    private String sceneStatus = "Choose a region to build the scene.";
    private Path lastReadyCache;
    private volatile ApplicationState applicationState = ApplicationState.PROJECT_LAUNCHER;
    private volatile ProjectLoadStatus projectLoadStatus = ProjectLoadStatus.initial();
    private volatile StudioProjectDescriptor activeProject;
    private CompletableFuture<?> pendingProjectOpen;
    private boolean closePrompt;
    private boolean closed;

    public StudioApplication() {
        window = new NativeWindow(1320, 860, "OpenRune Studio");
        integrations = new ServerIntegrationService(symbols, references, spawns);
        integrations.registerProvider(new OpenRuneServerProvider());
        SettingsJsonStore.load(settingsFile, renderSettings);
        imgui.initialize(window);
        sceneViewport.initialize();
        mapEditor.setPluginEcosystem(pluginEcosystem, this::rescanPlugins);
        mapEditor.setDefinitionPublicationPersistence(
                this::persistDefinitionPublicationState);

        // Development/automation may open a Studio descriptor directly, but raw cache paths
        // never bypass the project lifecycle.
        String directProject = System.getenv("RSPSI_PROJECT");
        if (directProject != null && !directProject.isBlank()) {
            try {
                startProjectOpen(projectService.open(Path.of(directProject)));
            } catch (Exception failure) {
                LOGGER.warn("RSPSI_PROJECT could not be opened: {}", directProject, failure);
            }
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
        switch (applicationState) {
            case PROJECT_LAUNCHER -> projectLauncher.render(this::startProjectOpen);
            case PROJECT_LOADING -> {
                refreshProjectLoadingProgress();
                StudioProjectDescriptor project = activeProject;
                if (project == null) {
                    applicationState = ApplicationState.PROJECT_LAUNCHER;
                    return;
                }
                projectLoading.render(
                        project,
                        projectLoadStatus,
                        this::retryProjectOpen,
                        this::backToProjectLauncher);
            }
            case PROJECT_OPEN -> drawProjectShell();
        }
    }

    private void drawProjectShell() {
        LoadedOsrsCacheSession cache = cacheSessions.current().orElse(null);
        boolean cacheReady = cache != null
                && cacheSessions.status().state() == CacheSessionState.READY;
        if (workspaces.active() != WorkspaceManager.Workspace.DASHBOARD && !cacheReady) {
            openDashboard();
        }

        switch (workspaces.active()) {
            case INTERFACE_STUDIO -> {
                interfaceStudio.render(cache, renderSettings, pluginLifecycle,
                        this::openDashboard, this::openMapEditor, this::openObjectStudio,
                        workspaces, this::requestCloseWorkspace);
                integrationCenter.render(integrations, integrationCenterOpen);
            }
            case OBJECT_STUDIO -> {
                objectStudio.render(cache, renderSettings, pluginLifecycle,
                        this::openDashboard, this::openMapEditor, this::openInterfaceStudio,
                        workspaces, this::requestCloseWorkspace);
                integrationCenter.render(integrations, integrationCenterOpen);
            }
            case MAP_EDITOR -> {
                pollSceneLoad();
                pollAnimationRefresh(cache);
                pollSceneRebuild(cache);
                if (loadedScene != null && renderedSettingsRevision != renderSettings.revision()) {
                    RenderConfig config = new RenderConfigCompiler().compile(renderSettings.snapshot());
                    currentPlan = new GpuUploadPlanBuilder().build(config.apply(loadedScene.packet()));
                    currentZonedPlan = new GpuZonedUploadPlanBuilder().build(currentPlan);
                    renderedSettingsRevision = renderSettings.revision();
                }
                sceneViewport.setZonedPlan(currentZonedPlan);
                mapEditor.render(cache, currentPlan, sceneViewport, sceneStatus,
                        this::openDashboard, renderSettings, pluginLifecycle,
                        loadedScene != null
                                && (loadedScene.session().isDirty()
                                || cache.objectDefinitions().unpublishedCount() > 0),
                        this::openInterfaceStudio, this::openObjectStudio,
                        () -> integrationCenterOpen.set(true),
                        simulation, symbols, references, spawns, integrations,
                        workspaces, this::openMapEditor, this::requestCloseWorkspace);
                renderClosePrompt();
                integrationCenter.render(integrations, integrationCenterOpen);
            }
            default -> {
                StudioProjectDescriptor project = activeProject;
                if (project == null) {
                    applicationState = ApplicationState.PROJECT_LAUNCHER;
                    return;
                }
                dashboard.render(
                        project,
                        cacheSessions.status(),
                        integrations,
                        this::openMapEditor,
                        this::openInterfaceStudio,
                        this::openObjectStudio,
                        () -> integrationCenterOpen.set(true),
                        this::closeCurrentProject,
                        workspaces,
                        this::openDashboard,
                        this::requestCloseWorkspace);
                bindReadyCacheSymbols();
                integrationCenter.render(integrations, integrationCenterOpen);
            }
        }
    }

    private void startProjectOpen(StudioProjectDescriptor project) {
        if (project == null) return;

        long request = projectOpenSequence.incrementAndGet();
        closeProjectRuntime(false);
        activeProject = project;
        applicationState = ApplicationState.PROJECT_LOADING;
        projectLoadStatus = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.VALIDATE_PROJECT,
                0.10,
                "Validating project...",
                project.sourcePathValue().toString(),
                null);

        if (project.kind() == StudioProjectKind.STANDALONE_OSRS_CACHE) {
            startStandaloneProjectOpen(project, request);
        } else {
            startOpenRuneProjectOpen(project, request);
        }
    }

    private void startStandaloneProjectOpen(StudioProjectDescriptor project, long request) {
        projectLoadStatus = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.OPEN_CACHE_FILESYSTEM,
                0.35,
                "Opening standalone cache...",
                project.sourcePathValue().toString(),
                null);

        pendingProjectOpen = cacheSessions
                .load(project.sourcePathValue(), this::restoreDefinitionPublicationState)
                .toCompletableFuture()
                .whenComplete((cache, failure) -> {
                    if (request != projectOpenSequence.get()) return;
                    if (failure != null) {
                        failProjectOpen(failure);
                        return;
                    }
                    projectLoadStatus = new ProjectLoadStatus(
                            ProjectLoadStatus.Phase.BIND_REQUIRED_PROJECT_SERVICES,
                            0.95,
                            "Binding project services...",
                            "Preparing cache symbols and project workspace state",
                            null);
                    bindReadyCacheSymbols();
                    finishProjectOpen(request);
                });
    }

    private void startOpenRuneProjectOpen(StudioProjectDescriptor project, long request) {
        Path root = project.sourcePathValue();
        projectLoadStatus = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.INSPECT_INTEGRATION,
                0.18,
                "Inspecting OpenRune project...",
                root.toString(),
                null);

        pendingProjectOpen = CompletableFuture.supplyAsync(() -> {
            IntegrationOptions options = IntegrationOptions.defaults(
                    root, integrationCapabilities(project));
            var session = integrations.connect(ServerConnection.forRoot(root), options);
            if (request != projectOpenSequence.get()) {
                integrations.disconnect();
                throw new java.util.concurrent.CancellationException("Project open cancelled");
            }
            return session.projectInspection().orElseThrow(
                    () -> new IllegalStateException("OpenRune project inspection is unavailable"));
        }, projectExecutor).thenCompose(inspection -> {
            if (request != projectOpenSequence.get()) {
                throw new java.util.concurrent.CancellationException("Project open cancelled");
            }
            projectLoadStatus = new ProjectLoadStatus(
                    ProjectLoadStatus.Phase.RESOLVE_CACHE_ROLES,
                    0.42,
                    "Resolving LIVE and SERVER cache roles...",
                    "Revision " + inspection.revision(),
                    null);
            Path live = inspection.path(ServerPathKey.LIVE_CACHE).orElseThrow(
                    () -> new IllegalStateException(
                            "OpenRune LIVE cache was not found. Build or repair the project cache first."));
            projectLoadStatus = new ProjectLoadStatus(
                    ProjectLoadStatus.Phase.OPEN_CACHE_FILESYSTEM,
                    0.52,
                    "Opening project LIVE cache...",
                    live.toString(),
                    null);
            return cacheSessions.load(live, this::restoreDefinitionPublicationState)
                    .toCompletableFuture();
        }).whenComplete((cache, failure) -> {
            if (request != projectOpenSequence.get()) return;
            if (failure != null) {
                failProjectOpen(failure);
                return;
            }
            projectLoadStatus = new ProjectLoadStatus(
                    ProjectLoadStatus.Phase.BIND_REQUIRED_PROJECT_SERVICES,
                    0.95,
                    "Binding OpenRune content services...",
                    "GameVals, source semantics and content graph are ready",
                    null);
            bindReadyCacheSymbols();
            finishProjectOpen(request);
        });
    }

    private Set<IntegrationCapability> integrationCapabilities(StudioProjectDescriptor project) {
        EnumSet<IntegrationCapability> capabilities = EnumSet.of(
                IntegrationCapability.SYMBOLS,
                IntegrationCapability.GAMEVALS,
                IntegrationCapability.CONTENT_INDEX,
                IntegrationCapability.CONTENT_MANIFESTS,
                IntegrationCapability.NPC_SPAWNS,
                IntegrationCapability.AREAS,
                IntegrationCapability.CONTENT_DIAGNOSTICS,
                IntegrationCapability.LOC_REFERENCES,
                IntegrationCapability.MAP_REFERENCES,
                IntegrationCapability.INTERFACE_REFERENCES,
                IntegrationCapability.CS2_SOURCES,
                IntegrationCapability.SOURCE_NAVIGATION,
                IntegrationCapability.SOURCE_SEMANTICS,
                IntegrationCapability.CONTENT_GRAPH);
        if (project.capabilities().contains(ProjectIntegrationCapability.CACHE_BUILD)) {
            capabilities.add(IntegrationCapability.CACHE_BUILD);
        }
        return Set.copyOf(capabilities);
    }

    private void refreshProjectLoadingProgress() {
        if (projectLoadStatus.phase() != ProjectLoadStatus.Phase.OPEN_CACHE_FILESYSTEM) return;
        var cacheStatus = cacheSessions.status();
        if (cacheStatus.state() != CacheSessionState.LOADING) return;
        double mapped = 0.52 + (cacheStatus.progress() * 0.35);
        projectLoadStatus = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.OPEN_CACHE_FILESYSTEM,
                mapped,
                cacheStatus.message(),
                cacheStatus.phase().name().replace('_', ' '),
                null);
    }

    private void finishProjectOpen(long request) {
        if (request != projectOpenSequence.get()) return;
        projectLoadStatus = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.READY,
                1.0,
                "Project ready",
                "",
                null);
        workspaces.reset();
        applicationState = ApplicationState.PROJECT_OPEN;
    }

    private void failProjectOpen(Throwable failure) {
        projectLoadStatus = new ProjectLoadStatus(
                ProjectLoadStatus.Phase.FAILED,
                1.0,
                "Project could not be opened",
                rootMessage(failure),
                failure);
    }

    private void retryProjectOpen() {
        StudioProjectDescriptor project = activeProject;
        if (project != null) startProjectOpen(project);
    }

    private void backToProjectLauncher() {
        projectOpenSequence.incrementAndGet();
        if (pendingProjectOpen != null) pendingProjectOpen.cancel(true);
        pendingProjectOpen = null;
        closeProjectRuntime(true);
        activeProject = null;
        projectLoadStatus = ProjectLoadStatus.initial();
        applicationState = ApplicationState.PROJECT_LAUNCHER;
    }

    private void closeCurrentProject() {
        backToProjectLauncher();
    }

    private void closeProjectRuntime(boolean clearCache) {
        closePluginLifecycle();
        cancelPendingScene();
        loadedScene = null;
        currentPlan = null;
        currentZonedPlan = null;
        sceneViewport.setZonedPlan(null);
        lastReadyCache = null;
        symbols.unregisterProvider("osrs.cache.gamevals");
        integrations.disconnect();
        workspaces.reset();
        if (clearCache) cacheSessions.clear();
    }

    private void openInterfaceStudio() {
        workspaces.openInterfaceStudio(cacheSessions.status().state());
    }

    private void openObjectStudio() {
        workspaces.openObjectStudio(cacheSessions.status().state());
    }

    private void loadCache(Path path) {
        if (path == null) return;
        cacheSessions.load(path, this::restoreDefinitionPublicationState);
    }

    private void restoreDefinitionPublicationState(
            LoadedOsrsCacheSession cache) {
        definitionPublicationStore.loadFor(cache.path(), cache.identity())
                .ifPresent(state -> {
                    try {
                        ObjectDefinitionOutputCacheBuilder.verifyExistingOutputSnapshots(
                                cache.path(),
                                state.outputCache(),
                                cache.identity().revision(),
                                state.publishedSnapshots());
                        cache.objectDefinitions().restorePublication(
                                state.outputCache(),
                                state.publishedSnapshots());
                        LOGGER.info(
                                "Restored definition publication provenance for {} -> {} "
                                        + "({} snapshots)",
                                cache.path(),
                                state.outputCache(),
                                state.publishedSnapshots().size());
                    } catch (Exception failure) {
                        LOGGER.warn(
                                "Ignoring stale definition publication provenance for {} -> {}",
                                cache.path(),
                                state.outputCache(),
                                failure);
                    }
                });
    }

    private void persistDefinitionPublicationState(
            LoadedOsrsCacheSession cache) {
        var workspace = cache.objectDefinitions();
        Path output = workspace.publicationTarget().orElse(null);
        Map<Integer, com.rspsi.cache.definition.ObjectDefinitionRawView> snapshots =
                workspace.publishedSnapshots();
        if (output == null || snapshots.isEmpty()) {
            return;
        }

        try {
            definitionPublicationStore.save(
                    new StudioDefinitionPublicationStore.PublicationState(
                            cache.path(),
                            cache.identity(),
                            output,
                            snapshots));
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Verified definition output was published, but Studio could not persist "
                            + "its restart provenance for {}",
                    output,
                    failure);
        }
    }

    private void openMapEditor() {
        boolean alreadyOpen = workspaces.isOpen(WorkspaceManager.Workspace.MAP_EDITOR);
        if (!workspaces.openMapEditor(cacheSessions.status().state())) return;
        if (alreadyOpen) return;
        closePluginLifecycle();
        loadedScene = null;
        currentPlan = null;
        currentZonedPlan = null;
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
        int clientCycle = currentClientCycle();
        pendingScene = CompletableFuture.supplyAsync(
                () -> buildMapScene(cache, region[0], region[1], clientCycle), sceneExecutor);
    }

    private LoadedMapScene buildMapScene(LoadedOsrsCacheSession cache, int regionX, int regionY,
                                         int clientCycle) {
        long totalStart = System.nanoTime();
        OsrsProjectSessionLoader.OpenedProject opened = cache.openRegion(regionX, regionY);
        WorldRegion region = opened.worldRegion();
        WorldRegionWindow window = new WorldRegionWindow(regionX, regionY, 1, 1,
                Map.of(region.regionId(), region));

        long windowStart = System.nanoTime();
        RenderWindowScene scene = new RenderWindowSceneBuilder(
                cache.bundle().definitions(), editorPresentation(cache)).build(window, clientCycle);
        long windowNanos = System.nanoTime() - windowStart;

        SceneWindow sceneWindow = SceneWindow.from(window);
        long packetStart = System.nanoTime();
        GpuScenePacket packet = new GpuScenePacketBuilder().build(sceneWindow, scene);
        long packetNanos = System.nanoTime() - packetStart;

        // Flattening a real region creates a large immutable GPU plan. Keep
        // this work on the loader thread so the native window remains
        // responsive while the scene is being prepared.
        long settingsRevision = renderSettings.revision();
        RenderConfig config = new RenderConfigCompiler().compile(renderSettings.snapshot());
        long planStart = System.nanoTime();
        IncrementalGpuUploadPlanBuilder incrementalPlanBuilder =
                new IncrementalGpuUploadPlanBuilder();
        IncrementalGpuUploadPlanBuilder.BuildResult initialPlan =
                incrementalPlanBuilder.buildInitial(config.apply(packet));
        GpuUploadPlan plan = initialPlan.plan();
        long planNanos = System.nanoTime() - planStart;

        double centerX = sceneWindow.sceneBaseX() * 128.0 + window.worldWindow().width() * 64.0;
        double centerZ = sceneWindow.sceneBaseY() * 128.0 + window.worldWindow().length() * 64.0;

        long renderSceneStart = System.nanoTime();
        RenderScene renderScene = new RenderSceneBuilder(
                cache.bundle().definitions(), editorPresentation(cache)).build(region.document(), clientCycle);
        long renderSceneNanos = System.nanoTime() - renderSceneStart;

        EditorSession session = opened.region().session();
        if (!session.canEdit()) {
            session = new EditorSession(region.document(), region.window());
        }
        SceneBuildMetrics metrics = new SceneBuildMetrics(
                windowNanos, packetNanos, planNanos, renderSceneNanos,
                System.nanoTime() - totalStart,
                plan.vertices().size(), plan.indices().size(), plan.commands().size(),
                plan.textures().size());
        logSceneBuild("initial", regionX, regionY, metrics);
        int nextAnimationRefreshCycle = AnimationRefreshScheduler.nextPresentationCycle(
                scene, cache.bundle().definitions(), clientCycle);
        return new LoadedMapScene(opened, session, scene, renderScene, packet, plan,
                initialPlan.zonedPlan(), incrementalPlanBuilder, settingsRevision,
                new com.rspsi.editor.render.CameraState(
                (float) centerX, -2400.0f, (float) centerZ - 4200.0f,
                (float) -Math.toRadians(28.0), 0.0f), clientCycle,
                nextAnimationRefreshCycle);
    }

    private void pollSceneLoad() {
        if (pendingScene == null || !pendingScene.isDone()) return;
        try {
            loadedScene = pendingScene.join();
            renderedVarState = simulation.state();
            sceneViewport.setCamera(loadedScene.camera());
            currentPlan = loadedScene.plan();
            currentZonedPlan = loadedScene.zonedPlan();
            renderedSettingsRevision = loadedScene.settingsRevision();
            initializePlugins(loadedScene);
            loadedScene.session().addChangeListener(changedTiles -> {
                synchronized (sceneChangeLock) {
                    pendingSceneChanges.addAll(changedTiles);
                }
                sceneDirty.set(true);
            });
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

    private void pollAnimationRefresh(LoadedOsrsCacheSession cache) {
        if (pendingAnimationRefresh != null) {
            if (!pendingAnimationRefresh.isDone()) return;
            try {
                loadedScene = pendingAnimationRefresh.join();
                currentPlan = loadedScene.plan();
                currentZonedPlan = loadedScene.zonedPlan();
                renderedSettingsRevision = loadedScene.settingsRevision();
            } catch (RuntimeException failure) {
                LOGGER.error("Animation refresh failed", failure);
            } finally {
                pendingAnimationRefresh = null;
            }
        }

        // A pending edit or simulated-var change takes priority: animated scenes
        // would otherwise schedule back-to-back refreshes and starve the
        // rebuild, so var changes appeared only after unrelated input.
        if (loadedScene == null || cache == null || pendingSceneRebuild != null
                || sceneDirty.get() || !simulation.state().equals(renderedVarState)) {
            return;
        }

        int nextRefreshCycle = loadedScene.nextAnimationRefreshCycle();
        if (nextRefreshCycle < 0) return;

        int clientCycle = currentClientCycle();
        if (clientCycle < nextRefreshCycle) return;
        LoadedMapScene baseScene = loadedScene;
        pendingAnimationRefresh = CompletableFuture.supplyAsync(
                () -> refreshMapAnimation(cache, baseScene, clientCycle), sceneExecutor);
    }

    private LoadedMapScene refreshMapAnimation(LoadedOsrsCacheSession cache,
                                                LoadedMapScene baseScene,
                                                int clientCycle) {
        long totalStart = System.nanoTime();
        long heapBefore = usedHeapBytes();

        var definitions = cache.bundle().definitions();
        RenderWindowSceneBuilder windowBuilder =
                new RenderWindowSceneBuilder(definitions, editorPresentation(cache));

        long windowStart = System.nanoTime();
        RenderWindowSceneBuilder.AnimationRefreshResult animation =
                windowBuilder.refreshAnimations(baseScene.windowScene(), clientCycle);
        long windowRefreshNanos = System.nanoTime() - windowStart;
        RenderWindowScene scene = animation.scene();

        long semanticStart = System.nanoTime();
        RenderScene renderScene = new RenderSceneBuilder(definitions, editorPresentation(cache))
                .refreshAnimations(baseScene.renderScene(), clientCycle);
        long semanticSceneNanos = System.nanoTime() - semanticStart;

        GpuScenePacket packet = baseScene.packet();
        GpuScenePacketBuilder.IncrementalBuildResult packetUpdate = null;
        long packetStart = System.nanoTime();
        long settingsRevision = renderSettings.revision();
        boolean settingsChanged = settingsRevision != baseScene.settingsRevision();
        if (!animation.dirtyZones().isEmpty()) {
            SceneWindow sceneWindow = SceneWindow.from(scene.window());
            packetUpdate = new GpuScenePacketBuilder().buildIncremental(
                    baseScene.packet(), sceneWindow, scene, animation.dirtyZones());
            packet = packetUpdate.packet();
        }
        long packetNanos = System.nanoTime() - packetStart;

        GpuUploadPlan plan = baseScene.plan();
        GpuZonedUploadPlan zonedPlan = baseScene.zonedPlan();
        IncrementalGpuUploadPlanBuilder incrementalPlanBuilder = baseScene.planBuilder();
        IncrementalGpuUploadPlanBuilder.BuildResult planUpdate = null;
        long planStart = System.nanoTime();
        if (settingsChanged || !animation.dirtyZones().isEmpty()) {
            RenderConfig config = new RenderConfigCompiler().compile(renderSettings.snapshot());
            GpuScenePacket visiblePacket = config.apply(packet);
            incrementalPlanBuilder = baseScene.planBuilder().fork();
            if (settingsChanged) {
                incrementalPlanBuilder.invalidateAll();
                planUpdate = incrementalPlanBuilder.buildInitial(visiblePacket);
            } else {
                planUpdate = incrementalPlanBuilder.build(visiblePacket, animation.dirtyZones());
            }
            plan = planUpdate.plan();
            zonedPlan = planUpdate.zonedPlan();
        }
        long planNanos = System.nanoTime() - planStart;

        int nextAnimationRefreshCycle = AnimationRefreshScheduler.nextPresentationCycle(
                scene, definitions, clientCycle);
        long totalNanos = System.nanoTime() - totalStart;
        long heapAfter = usedHeapBytes();

        animationRefreshDiagnostics.record(new AnimationPipelineMetrics(
                clientCycle,
                animation.timings(),
                windowRefreshNanos,
                semanticSceneNanos,
                packetNanos,
                planNanos,
                totalNanos,
                animation.dirtyZones().size(),
                animation.changedTiles(),
                animation.rebuiltModelTiles(),
                animation.fullModelRebuild(),
                packetUpdate != null,
                packetUpdate == null ? 0 : packetUpdate.rebuiltTiles(),
                packetUpdate == null ? 0 : packetUpdate.reusedTiles(),
                planUpdate != null,
                planUpdate == null ? 0 : planUpdate.rebuiltTiles(),
                planUpdate == null ? 0 : planUpdate.reusedTiles(),
                planUpdate == null ? 0 : planUpdate.rebuiltZones(),
                planUpdate == null ? 0 : planUpdate.reusedZones(),
                heapBefore,
                heapAfter));

        return new LoadedMapScene(
                baseScene.opened(), baseScene.session(), scene, renderScene, packet, plan,
                zonedPlan, incrementalPlanBuilder, settingsRevision,
                baseScene.camera(), clientCycle, nextAnimationRefreshCycle);
    }

    /** Editor presentation bound to the simulated player of this cache session. */
    private ScenePresentation editorPresentation(LoadedOsrsCacheSession cache) {
        return ScenePresentation.EDITOR.withVarState(playerFor(cache));
    }

    /** The simulated player/client for this cache session, shared by scene builds and plugins. */
    private SimulatedClient playerFor(LoadedOsrsCacheSession cache) {
        var definitions = cache.bundle().definitions();
        SimulatedClient current = player;
        if (current == null || current.definitions() != definitions) {
            current = new SimulatedClient(simulation, definitions,
                    new NavigatorWorldMap(sceneViewport.navigationService()));
            player = current;
        }
        return current;
    }

    private int currentClientCycle() {
        return (int) (simulation.clock().clientCycles() & Integer.MAX_VALUE);
    }

    private void pollSceneRebuild(LoadedOsrsCacheSession cache) {
        if (pendingSceneRebuild != null) {
            if (!pendingSceneRebuild.isDone()) return;
            try {
                loadedScene = pendingSceneRebuild.join();
                currentPlan = loadedScene.plan();
                currentZonedPlan = loadedScene.zonedPlan();
                renderedSettingsRevision = loadedScene.settingsRevision();
                pendingRebuildChanges = Set.of();
            } catch (RuntimeException failure) {
                LOGGER.error("Scene rebuild failed", failure);
                synchronized (sceneChangeLock) {
                    pendingSceneChanges.addAll(pendingRebuildChanges);
                }
                pendingRebuildChanges = Set.of();
                sceneDirty.set(true);
            } finally {
                pendingSceneRebuild = null;
            }
        }
        if (pendingAnimationRefresh != null) return;
        RuntimeState vars = simulation.state();
        if (loadedScene != null && cache != null && !vars.equals(renderedVarState)) {
            // The simulated player's vars changed: multilocs may show another
            // state, which authored-tile deltas cannot see, so rebuild fully.
            renderedVarState = vars;
            LoadedMapScene baseScene = loadedScene;
            pendingSceneRebuild = CompletableFuture.supplyAsync(
                    () -> rebuildMapScene(cache, baseScene, Set.of(), true), sceneExecutor);
            return;
        }
        if (sceneDirty.compareAndSet(true, false)) {
            if (loadedScene != null && cache != null) {
                LoadedMapScene baseScene = loadedScene;
                Set<TileCoordinate> changedTiles = drainSceneChanges();
                if (!changedTiles.isEmpty()) {
                    pendingRebuildChanges = changedTiles;
                    pendingSceneRebuild = CompletableFuture.supplyAsync(
                            () -> rebuildMapScene(cache, baseScene, changedTiles, false), sceneExecutor);
                }
            }
        }
    }

    private Set<TileCoordinate> drainSceneChanges() {
        synchronized (sceneChangeLock) {
            Set<TileCoordinate> snapshot = Set.copyOf(pendingSceneChanges);
            pendingSceneChanges.clear();
            return snapshot;
        }
    }

    private LoadedMapScene rebuildMapScene(LoadedOsrsCacheSession cache, LoadedMapScene baseScene,
                                           Set<TileCoordinate> changedTiles, boolean varStateChanged) {
        long totalStart = System.nanoTime();
        WorldRegion region = baseScene.opened().worldRegion();
        WorldRegionWindow window = new WorldRegionWindow(region.regionX(), region.regionY(), 1, 1,
                Map.of(region.regionId(), region));
        Set<WorldTileAddress> changedWorldTiles = changedTiles.stream()
                .map(tile -> WorldTileAddress.of(
                        region.regionX() * WorldRegion.REGION_SIZE + tile.x(),
                        region.regionY() * WorldRegion.REGION_SIZE + tile.y(),
                        tile.plane()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        long windowStart = System.nanoTime();
        IncrementalRenderWindowSceneCompiler windowCompiler =
                new IncrementalRenderWindowSceneCompiler(cache.bundle().definitions(),
                        editorPresentation(cache));
        IncrementalRenderWindowSceneCompiler.UpdateResult windowUpdate = varStateChanged
                ? windowCompiler.compileFull(window, baseScene.animationCycle(), "simulated var state changed")
                : windowCompiler.compile(baseScene.windowScene(), window, changedWorldTiles,
                        baseScene.animationCycle());
        RenderWindowScene scene = windowUpdate.scene();
        long windowNanos = System.nanoTime() - windowStart;

        SceneWindow sceneWindow = SceneWindow.from(window);
        long packetStart = System.nanoTime();
        GpuScenePacketBuilder packetBuilder = new GpuScenePacketBuilder();
        GpuScenePacketBuilder.IncrementalBuildResult packetUpdate;
        if (windowUpdate.fullRebuild()) {
            GpuScenePacket fullPacket = packetBuilder.build(sceneWindow, scene);
            packetUpdate = new GpuScenePacketBuilder.IncrementalBuildResult(
                    fullPacket, fullPacket.tiles().size(), 0, true);
        } else {
            packetUpdate = packetBuilder.buildIncremental(
                    baseScene.packet(), sceneWindow, scene, windowUpdate.dirtyWorldZones());
        }
        GpuScenePacket packet = packetUpdate.packet();
        long packetNanos = System.nanoTime() - packetStart;

        long settingsRevision = renderSettings.revision();
        RenderConfig config = new RenderConfigCompiler().compile(renderSettings.snapshot());
        GpuScenePacket visiblePacket = config.apply(packet);
        long planStart = System.nanoTime();
        IncrementalGpuUploadPlanBuilder incrementalPlanBuilder = baseScene.planBuilder().fork();
        IncrementalGpuUploadPlanBuilder.BuildResult planUpdate;
        if (windowUpdate.fullRebuild() || settingsRevision != baseScene.settingsRevision()) {
            incrementalPlanBuilder.invalidateAll();
            planUpdate = incrementalPlanBuilder.buildInitial(visiblePacket);
        } else {
            planUpdate = incrementalPlanBuilder.build(
                    visiblePacket, windowUpdate.dirtyWorldZones());
        }
        GpuUploadPlan plan = planUpdate.plan();
        long planNanos = System.nanoTime() - planStart;

        long renderSceneStart = System.nanoTime();
        RenderSceneBuilder renderSceneBuilder = new RenderSceneBuilder(
                cache.bundle().definitions(), editorPresentation(cache));
        RenderScene renderScene = varStateChanged
                ? renderSceneBuilder.build(region.document(), baseScene.animationCycle())
                : renderSceneBuilder.update(baseScene.renderScene(), new RenderChanges(changedTiles),
                        baseScene.animationCycle());
        long renderSceneNanos = System.nanoTime() - renderSceneStart;

        SceneBuildMetrics metrics = new SceneBuildMetrics(
                windowNanos, packetNanos, planNanos, renderSceneNanos,
                System.nanoTime() - totalStart,
                plan.vertices().size(), plan.indices().size(), plan.commands().size(),
                plan.textures().size());
        logSceneBuild("edit", region.regionX(), region.regionY(), metrics);
        LOGGER.info("Map scene edit window mode={}, reason={}, dirtyZones={}, worldZones={}, "
                        + "compiledVisibleTiles={}, packetRebuilt={}, packetReused={}, "
                        + "planRebuilt={}, planReused={}",
                windowUpdate.fullRebuild() ? "full" : "incremental",
                windowUpdate.reason(), windowUpdate.dirtyZones().size(),
                windowUpdate.dirtyWorldZones().size(), windowUpdate.compiledVisibleTiles(),
                packetUpdate.rebuiltTiles(), packetUpdate.reusedTiles(),
                planUpdate.rebuiltTiles(), planUpdate.reusedTiles());
        int nextAnimationRefreshCycle = AnimationRefreshScheduler.nextPresentationCycle(
                scene, cache.bundle().definitions(), baseScene.animationCycle());
        return new LoadedMapScene(baseScene.opened(), baseScene.session(), scene, renderScene,
                packet, plan, planUpdate.zonedPlan(), incrementalPlanBuilder,
                settingsRevision, baseScene.camera(), baseScene.animationCycle(),
                nextAnimationRefreshCycle);
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

    private void bindReadyCacheSymbols() {
        cacheSessions.current().ifPresent(session -> {
            if (cacheSessions.status().state() != CacheSessionState.READY) return;
            if (session.path().equals(lastReadyCache)) return;
            lastReadyCache = session.path();
            symbols.unregisterProvider("osrs.cache.gamevals");
            symbols.registerProvider(new CacheGamevalProvider(session.bundle().definitions()));
        });
    }

    private void initializePlugins(LoadedMapScene scene) {
        List<EditorPlugin> candidates = new ArrayList<>(CoreToolsPlugin.builtIns());
        // Not covered by any of the "vertical" builtIns() groups above — this is the
        // only place "terrain.tile-painter" (the composite multi-channel brush the
        // Tile Painter palette and rail/bottom-bar tool both target) gets registered.
        // Without it, selecting the tool highlights fine but painting silently no-ops.
        candidates.add(new TilePainterToolPlugin());
        candidates.add(new SplinePathToolPlugin());
        candidates.add(new OpenRuneServerPlugin());
        Map<String, SemanticVersion> hostPluginVersions = new java.util.LinkedHashMap<>();
        for (EditorPlugin candidate : candidates) {
            try {
                hostPluginVersions.put(candidate.id(),
                        SemanticVersion.parse(candidate.descriptor().version()));
            } catch (RuntimeException ignored) {
                hostPluginVersions.put(candidate.id(), new SemanticVersion(0, 0, 0, ""));
            }
        }
        ExternalPluginRuntimeSnapshot discovery = pluginEcosystem.scan(
                Thread.currentThread().getContextClassLoader(), hostPluginVersions);
        candidates.addAll(discovery.plugins());
        for (var failure : discovery.failures()) {
            LOGGER.warn("External plugin {} was not loaded: {}",
                    failure.jarPath(), failure.message(), failure.cause());
        }
        EditorSession session = scene.session();
        AssetRepository assets = cacheSessions.current()
                .map(LoadedOsrsCacheSession::bundle)
                .map(com.rspsi.cache.workspace.OsrsBundle::assets)
                .orElse(EmptyAssetRepository.INSTANCE);
        EditorSceneAccess sceneAccess = () -> EditorSceneSnapshot.from(
                loadedScene != null ? loadedScene.renderScene() : scene.renderScene(),
                scene.opened().worldRegion().window());
        CacheDecoderSummary decodedSummary = cacheSessions.current()
                .map(LoadedOsrsCacheSession::decoderSummary)
                .orElse(CacheDecoderSummary.empty());
        EditorPluginLifecycleManager next = EditorPluginLifecycleManager.start(
                candidates,
                EditorPluginStateStore.defaultStore(),
                session,
                assets,
                sceneAccess,
                enabled -> {
                    EditorPluginHost host = EditorPluginHost.initialize(enabled, session,
                            assets, sceneAccess, renderSettings, tasks, notifications,
                            null, null, symbols, references, spawns, simulation, integrations);
                    host.context().services().decodedData().mergeSummary(decodedSummary);
                    host.context().services().bindClient(
                            () -> cacheSessions.current().map(this::playerFor).orElse(null));
                    return host;
                },
                discovery);
        pluginLifecycle = next;
    }

    /** Re-discovers plugin JARs and rebuilds the active host without reloading the scene. */
    private void rescanPlugins() {
        if (loadedScene == null) return;
        closePluginLifecycle();
        initializePlugins(loadedScene);
    }

    /** Focuses the Dashboard tab. It is always open, so this never tears anything down. */
    private void openDashboard() {
        workspaces.openDashboard();
    }

    /** Closes one workspace tab, gated by an unsaved-changes prompt for Map Studio. */
    private void requestCloseWorkspace(WorkspaceManager.Workspace workspace) {
        if (workspace == WorkspaceManager.Workspace.MAP_EDITOR) {
            LoadedOsrsCacheSession cache = cacheSessions.current().orElse(null);
            boolean definitionDirty = cache != null
                    && cache.objectDefinitions().unpublishedCount() > 0;
            if (loadedScene != null
                    && (loadedScene.session().isDirty() || definitionDirty)) {
                closePrompt = true;
                return;
            }
            closeMapEditorTab();
        } else {
            workspaces.close(workspace);
        }
    }

    /** Tears down the loaded scene and plugin lifecycle, then removes the Map Studio tab. */
    private void closeMapEditorTab() {
        closePluginLifecycle();
        cancelPendingScene();
        loadedScene = null;
        currentPlan = null;
        currentZonedPlan = null;
        sceneViewport.setZonedPlan(null);
        closePrompt = false;
        workspaces.close(WorkspaceManager.Workspace.MAP_EDITOR);
    }

    private void renderClosePrompt() {
        if (closePrompt) ImGui.openPopup("Unsaved Studio changes##dashboard");
        if (!ImGui.beginPopupModal("Unsaved Studio changes##dashboard")) return;

        EditorSession session = loadedScene == null ? null : loadedScene.session();
        LoadedOsrsCacheSession cache = cacheSessions.current().orElse(null);
        boolean definitionDirty = cache != null
                && cache.objectDefinitions().unpublishedCount() > 0;
        boolean externalDirty = definitionDirty
                || (session != null && session.hasUnsavedExternalState());
        if (externalDirty) {
            ImGui.textWrapped(
                    "This Studio session contains unpublished definition edits. "
                            + "Map Save does not write those definitions. "
                            + "Publish them from Object Viewer > Properties to a separate "
                            + "output cache, or Discard & Close to lose the in-memory preview.");
        } else {
            ImGui.textWrapped("This map has unsaved changes. Save before closing Map Studio?");
        }

        boolean canSaveMap = session != null
                && session.canSave()
                && session.isSessionSaveDirty();
        ImGui.beginDisabled(!canSaveMap);
        if (ImGui.button(externalDirty ? "Save Map" : "Save")) {
            try {
                session.save();
                boolean definitionsRemain = cache != null
                        && cache.objectDefinitions().unpublishedCount() > 0;
                if (session.hasUnsavedExternalState() || definitionsRemain) {
                    sceneStatus = "Map changes saved. Definition edits still need an output-cache build.";
                } else {
                    ImGui.closeCurrentPopup();
                    closeMapEditorTab();
                }
            } catch (RuntimeException failure) {
                sceneStatus = "Save failed: " + rootMessage(failure);
                notifications.error("Map save failed", sceneStatus);
            }
        }
        ImGui.endDisabled();

        ImGui.sameLine();
        if (ImGui.button(externalDirty ? "Discard & Close" : "Discard")) {
            ImGui.closeCurrentPopup();
            closeMapEditorTab();
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
        if (pendingSceneRebuild != null) pendingSceneRebuild.cancel(true);
        pendingSceneRebuild = null;
        if (pendingAnimationRefresh != null) pendingAnimationRefresh.cancel(true);
        pendingAnimationRefresh = null;
        pendingRebuildChanges = Set.of();
        sceneDirty.set(false);
        synchronized (sceneChangeLock) {
            pendingSceneChanges.clear();
        }
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
        projectOpenSequence.incrementAndGet();
        if (pendingProjectOpen != null) pendingProjectOpen.cancel(true);
        projectExecutor.shutdownNow();
        sceneExecutor.shutdownNow();
        closePluginLifecycle();
        mapEditor.close();
        sceneViewport.close();
        SettingsJsonStore.save(settingsFile, renderSettings);
        cacheSessions.close();
        imgui.close();
        window.close();
    }

    private static void logSceneBuild(String kind, int regionX, int regionY,
                                      SceneBuildMetrics metrics) {
        LOGGER.info("Map scene {} {} , {}: total={} ms, window={} ms, packet={} ms, plan={} ms, "
                        + "renderScene={} ms, vertices={}, indices={}, commands={}, textures={}, geometry={} KiB",
                kind, regionX, regionY,
                metrics.totalMillis(), metrics.windowMillis(), metrics.packetMillis(),
                metrics.planMillis(), metrics.renderSceneMillis(),
                metrics.vertices(), metrics.indices(), metrics.commands(), metrics.textures(),
                metrics.geometryKiB());
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record AnimationPipelineMetrics(
            int clientCycle,
            RenderWindowSceneBuilder.AnimationRefreshTimings windowTimings,
            long windowRefreshNanos,
            long semanticSceneNanos,
            long packetNanos,
            long planNanos,
            long totalNanos,
            int dirtyZones,
            int changedTiles,
            int rebuiltModelTiles,
            boolean fullModelRebuild,
            boolean packetUpdated,
            int packetRebuiltTiles,
            int packetReusedTiles,
            boolean planUpdated,
            int planRebuiltTiles,
            int planReusedTiles,
            int planRebuiltZones,
            int planReusedZones,
            long heapBeforeBytes,
            long heapAfterBytes
    ) {
        private AnimationPipelineMetrics {
            if (clientCycle < 0 || windowRefreshNanos < 0L || semanticSceneNanos < 0L
                    || packetNanos < 0L || planNanos < 0L || totalNanos < 0L
                    || dirtyZones < 0 || changedTiles < 0 || rebuiltModelTiles < 0
                    || packetRebuiltTiles < 0 || packetReusedTiles < 0
                    || planRebuiltTiles < 0 || planReusedTiles < 0
                    || planRebuiltZones < 0 || planReusedZones < 0
                    || heapBeforeBytes < 0L || heapAfterBytes < 0L) {
                throw new IllegalArgumentException("Animation pipeline metrics cannot be negative");
            }
            windowTimings = java.util.Objects.requireNonNull(windowTimings, "windowTimings");
        }
    }

    /**
     * Low-overhead rolling diagnostics for the animation hot path. Detailed
     * per-refresh data stays at DEBUG; an aggregate INFO line is emitted every
     * 100 refreshes so normal Studio logs can reveal sustained CPU/allocation
     * pressure without producing one line per animation frame.
     */
    private static final class AnimationRefreshDiagnostics {
        private static final int REPORT_INTERVAL = 100;

        private int samples;
        private int fullModelRebuilds;
        private long totalNanos;
        private long windowNanos;
        private long paddedWorldNanos;
        private long modelRebuildNanos;
        private long semanticNanos;
        private long packetNanos;
        private long planNanos;
        private long maxTotalNanos;
        private long peakHeapBytes;
        private long changedTiles;
        private long rebuiltModelTiles;
        private long packetRebuiltTiles;
        private long planRebuiltTiles;
        private long planRebuiltZones;

        synchronized void record(AnimationPipelineMetrics metric) {
            RenderWindowSceneBuilder.AnimationRefreshTimings inner = metric.windowTimings();
            long heapDelta = metric.heapAfterBytes() - metric.heapBeforeBytes();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Animation perf cycle={} total={}ms window={}ms "
                                + "(scan={} padded={} normalMerge={} models={} detect={}) "
                                + "semantic={}ms packet={}ms plan={}ms dirtyZones={} changedTiles={} "
                                + "rebuiltModelTiles={} fullModelRebuild={} packetUpdated={} "
                                + "packetTiles={}/{} planUpdated={} planTiles={}/{} planZones={}/{} "
                                + "heap={}MiB delta={}KiB",
                        metric.clientCycle(),
                        millis(metric.totalNanos()),
                        millis(metric.windowRefreshNanos()),
                        millis(inner.activeScanNanos()),
                        millis(inner.paddedWorldNanos()),
                        millis(inner.normalMergeCheckNanos()),
                        millis(inner.modelRebuildNanos()),
                        millis(inner.changeDetectionNanos()),
                        millis(metric.semanticSceneNanos()),
                        millis(metric.packetNanos()),
                        millis(metric.planNanos()),
                        metric.dirtyZones(),
                        metric.changedTiles(),
                        metric.rebuiltModelTiles(),
                        metric.fullModelRebuild(),
                        metric.packetUpdated(),
                        metric.packetRebuiltTiles(),
                        metric.packetReusedTiles(),
                        metric.planUpdated(),
                        metric.planRebuiltTiles(),
                        metric.planReusedTiles(),
                        metric.planRebuiltZones(),
                        metric.planReusedZones(),
                        mebibytes(metric.heapAfterBytes()),
                        kibibytes(heapDelta));
            }

            samples++;
            if (metric.fullModelRebuild()) fullModelRebuilds++;
            totalNanos += metric.totalNanos();
            windowNanos += metric.windowRefreshNanos();
            paddedWorldNanos += inner.paddedWorldNanos();
            modelRebuildNanos += inner.modelRebuildNanos();
            semanticNanos += metric.semanticSceneNanos();
            packetNanos += metric.packetNanos();
            planNanos += metric.planNanos();
            maxTotalNanos = Math.max(maxTotalNanos, metric.totalNanos());
            peakHeapBytes = Math.max(peakHeapBytes, metric.heapAfterBytes());
            changedTiles += metric.changedTiles();
            rebuiltModelTiles += metric.rebuiltModelTiles();
            packetRebuiltTiles += metric.packetRebuiltTiles();
            planRebuiltTiles += metric.planRebuiltTiles();
            planRebuiltZones += metric.planRebuiltZones();

            if (samples >= REPORT_INTERVAL) {
                LOGGER.info(
                        "Animation perf {} refreshes: avg total={}ms window={}ms "
                                + "(padded={} models={}) semantic={}ms packet={}ms plan={}ms, "
                                + "max={}ms, fullModelRebuilds={}, avgChangedTiles={}, "
                                + "avgRebuiltModelTiles={}, avgPacketRebuiltTiles={}, "
                                + "avgPlanRebuiltTiles={}, avgPlanRebuiltZones={}, peakHeap={}MiB",
                        samples,
                        millis(totalNanos / samples),
                        millis(windowNanos / samples),
                        millis(paddedWorldNanos / samples),
                        millis(modelRebuildNanos / samples),
                        millis(semanticNanos / samples),
                        millis(packetNanos / samples),
                        millis(planNanos / samples),
                        millis(maxTotalNanos),
                        fullModelRebuilds,
                        ratio(changedTiles, samples),
                        ratio(rebuiltModelTiles, samples),
                        ratio(packetRebuiltTiles, samples),
                        ratio(planRebuiltTiles, samples),
                        ratio(planRebuiltZones, samples),
                        mebibytes(peakHeapBytes));
                reset();
            }
        }

        private void reset() {
            samples = 0;
            fullModelRebuilds = 0;
            totalNanos = 0L;
            windowNanos = 0L;
            paddedWorldNanos = 0L;
            modelRebuildNanos = 0L;
            semanticNanos = 0L;
            packetNanos = 0L;
            planNanos = 0L;
            maxTotalNanos = 0L;
            peakHeapBytes = 0L;
            changedTiles = 0L;
            rebuiltModelTiles = 0L;
            packetRebuiltTiles = 0L;
            planRebuiltTiles = 0L;
            planRebuiltZones = 0L;
        }

        private static double millis(long nanos) {
            return nanos / 1_000_000.0;
        }

        private static double mebibytes(long bytes) {
            return bytes / (1024.0 * 1024.0);
        }

        private static double kibibytes(long bytes) {
            return bytes / 1024.0;
        }

        private static double ratio(long total, int count) {
            return count == 0 ? 0.0 : (double) total / count;
        }
    }

    private record SceneBuildMetrics(long windowNanos,
                                     long packetNanos,
                                     long planNanos,
                                     long renderSceneNanos,
                                     long totalNanos,
                                     int vertices,
                                     int indices,
                                     int commands,
                                     int textures) {
        private long windowMillis() { return windowNanos / 1_000_000L; }
        private long packetMillis() { return packetNanos / 1_000_000L; }
        private long planMillis() { return planNanos / 1_000_000L; }
        private long renderSceneMillis() { return renderSceneNanos / 1_000_000L; }
        private long totalMillis() { return totalNanos / 1_000_000L; }

        /**
         * Native ZoneVboManager uploads twelve floats per vertex and one int
         * per index. This is the flattened-plan upper bound before unchanged
         * zone allocations are fingerprint-reused on the GPU.
         */
        private long geometryKiB() {
            long bytes = (long) vertices * 12L * Float.BYTES
                    + (long) indices * Integer.BYTES;
            return (bytes + 1023L) / 1024L;
        }
    }

    private record LoadedMapScene(OsrsProjectSessionLoader.OpenedProject opened,
                                  EditorSession session,
                                  RenderWindowScene windowScene,
                                  RenderScene renderScene,
                                  GpuScenePacket packet,
                                  GpuUploadPlan plan,
                                  GpuZonedUploadPlan zonedPlan,
                                  IncrementalGpuUploadPlanBuilder planBuilder,
                                  long settingsRevision,
                                  com.rspsi.editor.render.CameraState camera,
                                  int animationCycle,
                                  int nextAnimationRefreshCycle) {
        private LoadedMapScene {
            if (animationCycle < 0) {
                throw new IllegalArgumentException("Animation cycle cannot be negative");
            }
            if (nextAnimationRefreshCycle < AnimationRefreshScheduler.NONE) {
                throw new IllegalArgumentException("Invalid next animation refresh cycle");
            }
            if (nextAnimationRefreshCycle >= 0
                    && nextAnimationRefreshCycle <= animationCycle) {
                throw new IllegalArgumentException(
                        "Next animation refresh cycle must be in the future");
            }
        }
    }
}
