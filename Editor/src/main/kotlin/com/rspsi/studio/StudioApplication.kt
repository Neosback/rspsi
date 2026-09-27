package com.rspsi.studio

import com.rspsi.api.runtime.SimulatedClient
import com.rspsi.api.worldmap.NavigatorWorldMap
import com.rspsi.cache.workspace.CacheDecoderSummary
import com.rspsi.cache.workspace.CacheSessionState
import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.cache.workspace.OsrsCacheHealth
import com.rspsi.cache.workspace.OsrsCacheSessionService
import com.rspsi.editor.assets.EmptyAssetRepository
import com.rspsi.editor.core.CoreEditorModules
import com.rspsi.editor.integration.ServerIntegrationService
import com.rspsi.editor.integration.npc.NpcSpawnService
import com.rspsi.editor.integration.reference.ReferenceService
import com.rspsi.editor.model.WorldLocation
import com.rspsi.editor.plugin.EditorNotificationService
import com.rspsi.editor.plugin.EditorPluginHost
import com.rspsi.editor.plugin.EditorSceneAccess
import com.rspsi.editor.plugin.EditorSceneSnapshot
import com.rspsi.editor.plugin.EditorTaskService
import com.rspsi.editor.render.RenderConfigCompiler
import com.rspsi.editor.render.RenderScene
import com.rspsi.editor.render.RenderSettingKeys
import com.rspsi.editor.render.ScenePresentation
import com.rspsi.editor.settings.EditorSettingKeys
import com.rspsi.editor.settings.SettingsJsonStore
import com.rspsi.editor.settings.SettingsStore
import com.rspsi.editor.simulation.SimulationEngine
import com.rspsi.editor.symbols.CacheGamevalProvider
import com.rspsi.editor.symbols.SymbolService
import com.rspsi.project.StudioProjectDescriptor
import com.rspsi.project.StudioProjectRegistry
import com.rspsi.project.StudioProjectService
import com.rspsi.server.openrune.OpenRuneServerProvider
import com.rspsi.studio.integration.IntegrationCenterWindow
import com.rspsi.studio.map.LoadedMapScene
import com.rspsi.studio.map.MapSceneController
import com.rspsi.studio.ui.WorldMapView
import com.rspsi.studio.workspace.InterfaceStudioView
import com.rspsi.studio.workspace.ObjectStudioView
import imgui.type.ImBoolean
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * The Studio shell: window, frame loop, project lifecycle and workspace tabs.
 *
 * It composes the shared services and routes each frame to the active workspace. Map scene
 * work belongs to [MapSceneController]; this class only binds a loaded map to the viewport
 * and the editor host, and decides when leaving a map needs the unsaved-changes prompt.
 */
class StudioApplication : AutoCloseable {
    private val window = NativeWindow(1120, 820, "OpenRune Studio")
    private val imgui = ImGuiHost()

    // Projects and cache sessions
    private val cacheSessions = OsrsCacheSessionService()
    private val projectRegistry = StudioProjectRegistry()
    private val projectService = StudioProjectService(projectRegistry)
    private val projectLauncher = ProjectLauncherView(projectRegistry, projectService)
    private val projectLoading = ProjectLoadingView()
    private val publications = StudioDefinitionPublicationStore(
        STUDIO_HOME.resolve("definition-publications.json"))

    // Workspaces
    private val workspaces = WorkspaceManager()
    private val contentStudio = ContentStudioView()
    private val mapEditor = MapEditorView()
    private val interfaceStudio = InterfaceStudioView()
    private val objectStudio = ObjectStudioView()
    private val worldMap = WorldMapView()
    private val integrationCenter = IntegrationCenterWindow()
    private val integrationCenterOpen = ImBoolean(false)

    // Shared runtime services
    private val symbols = SymbolService()
    private val references = ReferenceService()
    private val spawns = NpcSpawnService()
    private val simulation = SimulationEngine()
    private val tasks = EditorTaskService()
    private val notifications = EditorNotificationService()
    private val integrations = ServerIntegrationService(symbols, references, spawns)
    private val projectOpenCoordinator: ProjectOpenCoordinator
    private val settingsFile = STUDIO_HOME.resolve("settings.json")
    private val renderSettings = SettingsStore(EditorSettingKeys.registry())

    // Map Studio
    private val sceneViewport = NativeSceneViewport()
    private val mapScenes: MapSceneController
    private val unsavedPrompt = UnsavedMapChangesPrompt()

    /** RuneLite-shaped view of the simulated player; scenes resolve multilocs against it. */
    private var player: SimulatedClient? = null

    /** Core modules bound to the loaded map, or null while no map is loaded. */
    private var editorHost: EditorPluginHost? = null

    // Project lifecycle state; written by the project-open future, read by the frame loop.
    private val projectOpenSequence = AtomicLong()

    @Volatile private var applicationState = ApplicationState.PROJECT_LAUNCHER

    @Volatile private var projectLoadStatus = ProjectLoadStatus.initial()

    @Volatile private var activeProject: StudioProjectDescriptor? = null

    @Volatile private var activeCachePath: Path? = null

    @Volatile private var activeCacheHealth: OsrsCacheHealth? = null

    @Volatile private var pendingWorkspaceOpen: WorkspaceManager.Workspace? = null
    private var pendingProjectOpen: CompletableFuture<*>? = null
    private var lastReadyCache: Path? = null
    private var closed = false

    /**
     * Development/automation: `RSPSI_REGION` (any Go To text, e.g. `12850` or `3222,3218`)
     * opens Map Studio there once the project is open. Consumed once.
     */
    private var autoOpenRegion: WorldLocation? = System.getenv("RSPSI_REGION")?.let { WorldLocation.parse(it) }

    init {
        integrations.registerProvider(OpenRuneServerProvider())
        projectOpenCoordinator = ProjectOpenCoordinator(projectService, integrations)
        SettingsJsonStore.load(settingsFile, renderSettings)
        mapScenes = MapSceneController(
            { cache -> scenePresentation(cache) },
            { currentClientCycle() },
            { simulation.state() },
            RenderConfigCompiler().compile(renderSettings.snapshot()),
            renderSettings.snapshot().get(RenderSettingKeys.OBJECT_ANIMATIONS),
        )
        renderSettings.addListener { change ->
            if (change.key() == RenderSettingKeys.OBJECT_ANIMATIONS) {
                mapScenes.setObjectAnimations(change.newValue() == true, currentCache())
            } else {
                mapScenes.updateRenderConfig(RenderConfigCompiler().compile(renderSettings.snapshot()))
            }
        }
        imgui.initialize(window)
        sceneViewport.initialize()
        mapEditor.setDefinitionPublicationPersistence { cache -> publications.persistFrom(cache) }
        mapEditor.setLocationNavigator { location -> goToLocation(location) }
        mapEditor.setOpenWorldMapWorkspace { openWorldMapWorkspace() }

        // Development/automation may open a Studio descriptor directly; raw cache paths never
        // bypass the project lifecycle.
        System.getenv("RSPSI_PROJECT")?.takeIf { it.isNotBlank() }?.let { descriptor ->
            try {
                startProjectOpen(projectService.open(Path.of(descriptor)))
            } catch (failure: Exception) {
                LOGGER.warn("RSPSI_PROJECT could not be opened: {}", descriptor, failure)
            }
        }
    }

    /** Runs the frame loop until the window closes, then releases everything. */
    fun run() {
        try {
            var lastFrameTime = System.nanoTime()
            while (!window.shouldClose()) {
                val now = System.nanoTime()
                simulation.update(minOf(now - lastFrameTime, MAX_FRAME_DELTA_NANOS))
                lastFrameTime = now
                window.pollEvents()
                imgui.beginFrame()
                window.clearFrame()
                drawApplication()
                imgui.endFrame()
                window.swapBuffers()
            }
        } finally {
            close()
        }
    }

    // ---------------------------------------------------------------- frame routing

    private fun drawApplication() {
        when (applicationState) {
            ApplicationState.PROJECT_LAUNCHER -> projectLauncher.render { project -> startProjectOpen(project) }
            ApplicationState.PROJECT_LOADING -> {
                val project = activeProject ?: return run { applicationState = ApplicationState.PROJECT_LAUNCHER }
                projectLoading.render(project, projectLoadStatus, { retryProjectOpen() }, { backToProjectLauncher() })
            }
            ApplicationState.PROJECT_OPEN -> drawProjectShell()
        }
    }

    private fun drawProjectShell() {
        if (autoOpenRegion != null && pendingWorkspaceOpen == null &&
            !workspaces.isOpen(WorkspaceManager.Workspace.MAP_EDITOR)
        ) {
            openMapEditor()
        }
        pollPendingWorkspaceOpen()
        val cache = currentCache()
        val cacheReady = cache != null && cacheSessions.status().state() == CacheSessionState.READY
        if (workspaces.active() != WorkspaceManager.Workspace.DASHBOARD && !cacheReady) openDashboard()

        when (workspaces.active()) {
            WorkspaceManager.Workspace.INTERFACE_STUDIO -> interfaceStudio.render(
                cache, renderSettings, editorHost, { openDashboard() }, { openMapEditor() },
                { openObjectStudio() }, { openWorldMapWorkspace() }, workspaces, { requestCloseWorkspace(it) },
            )
            WorkspaceManager.Workspace.OBJECT_STUDIO -> objectStudio.render(
                cache, renderSettings, editorHost, { openDashboard() }, { openMapEditor() },
                { openInterfaceStudio() }, { openWorldMapWorkspace() }, workspaces, { requestCloseWorkspace(it) },
            )
            WorkspaceManager.Workspace.WORLD_MAP -> {
                val region = mapScenes.scene()?.region()
                worldMap.render(
                    cache, region?.regionX ?: DEFAULT_REGION, region?.regionY ?: DEFAULT_REGION,
                    renderSettings.snapshot().get(RenderSettingKeys.CURRENT_HEIGHT),
                    Consumer { goToLocation(it) }, workspaces, { openDashboard() }, { openMapEditor() },
                    { openInterfaceStudio() }, { openObjectStudio() }, Consumer { requestCloseWorkspace(it) },
                )
            }
            WorkspaceManager.Workspace.MAP_EDITOR -> drawMapStudio(cache)
            else -> {
                val project = activeProject ?: return run { applicationState = ApplicationState.PROJECT_LAUNCHER }
                contentStudio.render(
                    project, cacheSessions.status(), activeCacheHealth, integrations,
                    { openMapEditor() }, { openInterfaceStudio() }, { openObjectStudio() },
                    { openWorldMapWorkspace() }, { integrationCenterOpen.set(true) }, { backToProjectLauncher() },
                    workspaces, { openDashboard() }, { requestCloseWorkspace(it) },
                )
                bindReadyCacheSymbols()
            }
        }
        integrationCenter.render(integrations, integrationCenterOpen)
    }

    private fun drawMapStudio(cache: LoadedOsrsCacheSession?) {
        mapScenes.poll(cache)?.let { onMapSceneLoaded(it) }
        sceneViewport.setZonedPlan(mapScenes.zonedPlan())
        sceneViewport.setPickPlan(mapScenes.pickPlan(), mapScenes.pickZonedPlan())
        val scene = mapScenes.scene()
        val dirty = scene != null &&
            (scene.session.isDirty || (cache?.objectDefinitions()?.unpublishedCount() ?: 0) > 0)
        mapEditor.render(
            cache, mapScenes.plan(), sceneViewport, mapScenes.status, { openDashboard() }, renderSettings,
            editorHost, dirty, { openInterfaceStudio() }, { openObjectStudio() },
            { integrationCenterOpen.set(true) }, simulation, symbols, references, spawns, integrations,
            workspaces, { openMapEditor() }, { requestCloseWorkspace(it) },
        )
        unsavedPrompt.render(scene?.session, cache, notifications) { mapScenes.status = it }
    }

    // ---------------------------------------------------------------- project lifecycle

    private fun startProjectOpen(project: StudioProjectDescriptor?) {
        if (project == null) return
        val request = projectOpenSequence.incrementAndGet()
        closeProjectRuntime()
        activeProject = project
        applicationState = ApplicationState.PROJECT_LOADING
        projectLoadStatus = ProjectLoadStatus(
            ProjectLoadStatus.Phase.VALIDATE_PROJECT, 0.10, "Validating project...",
            project.sourcePathValue().toString(), null,
        )
        pendingProjectOpen = projectOpenCoordinator
            .openAsync(project) { status -> if (request == projectOpenSequence.get()) projectLoadStatus = status }
            .whenComplete { snapshot, failure ->
                if (request != projectOpenSequence.get()) return@whenComplete
                if (failure != null) {
                    projectLoadStatus = ProjectLoadStatus(
                        ProjectLoadStatus.Phase.FAILED, 1.0, "Project could not be opened",
                        rootMessage(failure), failure,
                    )
                    return@whenComplete
                }
                activeCachePath = snapshot.cachePath
                activeCacheHealth = snapshot.cacheHealth
                projectLoadStatus = ProjectLoadStatus(ProjectLoadStatus.Phase.READY, 1.0, "Project ready", "", null)
                workspaces.reset()
                applicationState = ApplicationState.PROJECT_OPEN
            }
    }

    private fun retryProjectOpen() {
        activeProject?.let { startProjectOpen(it) }
    }

    private fun backToProjectLauncher() {
        projectOpenSequence.incrementAndGet()
        pendingProjectOpen?.cancel(true)
        pendingProjectOpen = null
        closeProjectRuntime()
        activeProject = null
        projectLoadStatus = ProjectLoadStatus.initial()
        applicationState = ApplicationState.PROJECT_LAUNCHER
    }

    /** Releases everything the open project owns: map, cache session, symbols, server link. */
    private fun closeProjectRuntime() {
        unloadMapScene()
        lastReadyCache = null
        activeCachePath = null
        activeCacheHealth = null
        pendingWorkspaceOpen = null
        symbols.unregisterProvider(GAMEVAL_PROVIDER)
        integrations.disconnect()
        workspaces.reset()
        cacheSessions.clear()
    }

    /** Registers the cache's gamevals as symbols once per ready cache session. */
    private fun bindReadyCacheSymbols() {
        val session = currentCache() ?: return
        if (cacheSessions.status().state() != CacheSessionState.READY || session.path() == lastReadyCache) return
        lastReadyCache = session.path()
        symbols.unregisterProvider(GAMEVAL_PROVIDER)
        symbols.registerProvider(CacheGamevalProvider(session.bundle().definitions()))
    }

    // ---------------------------------------------------------------- workspaces

    private fun openDashboard() = workspaces.openDashboard()

    private fun openMapEditor() = requestWorkspaceOpen(WorkspaceManager.Workspace.MAP_EDITOR)

    private fun openInterfaceStudio() = requestWorkspaceOpen(WorkspaceManager.Workspace.INTERFACE_STUDIO)

    private fun openObjectStudio() = requestWorkspaceOpen(WorkspaceManager.Workspace.OBJECT_STUDIO)

    private fun openWorldMapWorkspace() = requestWorkspaceOpen(WorkspaceManager.Workspace.WORLD_MAP)

    /**
     * Opens a workspace, loading the project's cache session first when needed; the tab
     * opens once the session is ready ([pollPendingWorkspaceOpen]).
     */
    private fun requestWorkspaceOpen(workspace: WorkspaceManager.Workspace) {
        if (workspace == WorkspaceManager.Workspace.DASHBOARD) return openDashboard()
        if (cacheSessions.status().state() == CacheSessionState.READY && currentCache() != null) {
            return openWorkspaceReady(workspace)
        }
        val cachePath = activeCachePath ?: return
        if (activeCacheHealth == null) return
        pendingWorkspaceOpen = workspace
        if (cacheSessions.status().state() == CacheSessionState.LOADING) return
        cacheSessions.load(cachePath) { cache -> publications.restoreInto(cache) }
            .whenComplete { _, failure -> if (failure != null && pendingWorkspaceOpen == workspace) pendingWorkspaceOpen = null }
    }

    private fun pollPendingWorkspaceOpen() {
        val workspace = pendingWorkspaceOpen ?: return
        when (cacheSessions.status().state()) {
            CacheSessionState.READY -> if (currentCache() != null) {
                pendingWorkspaceOpen = null
                bindReadyCacheSymbols()
                openWorkspaceReady(workspace)
            }
            CacheSessionState.FAILED -> pendingWorkspaceOpen = null
            else -> {}
        }
    }

    private fun openWorkspaceReady(workspace: WorkspaceManager.Workspace) {
        when (workspace) {
            WorkspaceManager.Workspace.MAP_EDITOR -> openMapEditorReady()
            WorkspaceManager.Workspace.INTERFACE_STUDIO -> workspaces.openInterfaceStudio(CacheSessionState.READY)
            WorkspaceManager.Workspace.OBJECT_STUDIO -> workspaces.openObjectStudio(CacheSessionState.READY)
            WorkspaceManager.Workspace.WORLD_MAP -> workspaces.openWorldMap(CacheSessionState.READY)
            WorkspaceManager.Workspace.DASHBOARD -> openDashboard()
        }
    }

    /** Closes one workspace tab; Map Studio asks first when the map or definitions are unsaved. */
    private fun requestCloseWorkspace(workspace: WorkspaceManager.Workspace) {
        if (workspace != WorkspaceManager.Workspace.MAP_EDITOR) return workspaces.close(workspace)
        val definitionsUnpublished = (currentCache()?.objectDefinitions()?.unpublishedCount() ?: 0) > 0
        val scene = mapScenes.scene()
        if (scene != null && (scene.session.isDirty || definitionsUnpublished)) {
            unsavedPrompt.ask(leavingRegion = false) { closeMapEditorTab() }
        } else {
            closeMapEditorTab()
        }
    }

    private fun closeMapEditorTab() {
        unloadMapScene()
        workspaces.close(WorkspaceManager.Workspace.MAP_EDITOR)
    }

    // ---------------------------------------------------------------- Map Studio

    private fun openMapEditorReady() {
        val alreadyOpen = workspaces.isOpen(WorkspaceManager.Workspace.MAP_EDITOR)
        if (!workspaces.openMapEditor(CacheSessionState.READY) || alreadyOpen) return
        val location = autoOpenRegion ?: WorldLocation.parse(contentStudio.regionText())
        autoOpenRegion = null
        if (location == null) {
            unloadMapScene()
            mapScenes.status = "Enter a region X,Y, a region ID, or a world tile X,Y."
            return
        }
        loadRegion(location)
    }

    /**
     * Goes to [location]: frames it when it is in the loaded region, otherwise loads its
     * region, asking first when the map has unsaved edits. Definition edits belong to the
     * cache session and survive a region switch, so they never block it.
     */
    private fun goToLocation(location: WorldLocation?) {
        if (location == null) return
        if (mapScenes.isLoading()) {
            mapScenes.status = "A region is still loading."
            return
        }
        val scene = mapScenes.scene()
        if (scene != null && scene.region().regionX == location.regionX() &&
            scene.region().regionY == location.regionY()
        ) {
            return frameLocation(location, recordHistory = true)
        }
        if (scene != null && scene.session.isDirty) {
            unsavedPrompt.ask(leavingRegion = true) { loadRegion(location) }
        } else {
            loadRegion(location)
        }
    }

    private fun loadRegion(location: WorldLocation) {
        unloadMapScene()
        val cache = currentCache()
        if (cache == null) {
            mapScenes.status = "Cache session is no longer available."
            return
        }
        mapScenes.load(cache, location)
    }

    /** Drops the loaded map, its editor host and the viewport's geometry. */
    private fun unloadMapScene() {
        unsavedPrompt.dismiss()
        closeEditorHost()
        mapScenes.unload()
        sceneViewport.setZonedPlan(null)
        sceneViewport.setEditableRegion(null)
    }

    /** Binds a freshly loaded region to the viewport and core modules, then frames its focus. */
    private fun onMapSceneLoaded(loaded: MapSceneController.Loaded) {
        val scene = loaded.scene
        sceneViewport.setCamera(scene.camera)
        sceneViewport.setEditableRegion(scene.region())
        startEditorHost(scene)
        loaded.focus?.let { frameLocation(it, recordHistory = false) }
    }

    /** Frames a location in the loaded region and switches to its plane when it names one. */
    private fun frameLocation(location: WorldLocation, recordHistory: Boolean) {
        val activePlane = renderSettings.snapshot().get(RenderSettingKeys.CURRENT_HEIGHT)
        if (location.plane >= 0 && location.plane != activePlane) {
            renderSettings.set(RenderSettingKeys.CURRENT_HEIGHT, location.plane)
        }
        val navigation = sceneViewport.navigationService()
        if (recordHistory) navigation.synchronizeFromCamera(activePlane)
        navigation.jumpTo(location.tile(activePlane), recordHistory)
        mapScenes.status = "At ${location.describe()}."
    }

    /**
     * Composes the editor host for a loaded map: every core module registers its tools,
     * panels, overlays and commands against the map's session and scene. Studio has no
     * external plugins (ROADMAP section 3), so there is nothing to discover.
     */
    private fun startEditorHost(scene: LoadedMapScene) {
        val session = scene.session
        val cache = currentCache()
        val assets = cache?.bundle()?.assets() ?: EmptyAssetRepository.INSTANCE
        val host = EditorPluginHost.initializeWithCoreModules(
            CoreEditorModules.all(), emptyList(), session, assets, CachedSceneAccess(scene),
            renderSettings, tasks, notifications, null, null, symbols, references, spawns, simulation,
            integrations,
        )
        host.context().services().decodedData().mergeSummary(cache?.decoderSummary() ?: CacheDecoderSummary.empty())
        host.context().services().bindClient { currentCache()?.let { playerFor(it) } }
        editorHost = host
    }

    /**
     * Semantic scene snapshots for core modules. Overlays ask every frame and copying the
     * whole scene each time cost more than drawing it, so a snapshot is rebuilt only when
     * the scene generation changes or the session reports an edit.
     */
    private inner class CachedSceneAccess(private val loaded: LoadedMapScene) : EditorSceneAccess {
        private val sessionEdits = AtomicLong()
        private var cachedSource: RenderScene? = null
        private var cachedEdits = -1L
        private var cached: EditorSceneSnapshot? = null

        init {
            loaded.session.addChangeListener { sessionEdits.incrementAndGet() }
        }

        @Synchronized
        override fun snapshot(): EditorSceneSnapshot {
            val source = mapScenes.scene()?.renderScene ?: loaded.renderScene
            val edits = sessionEdits.get()
            val current = cached
            if (current != null && source === cachedSource && edits == cachedEdits) return current
            return EditorSceneSnapshot.from(source, loaded.region().window()).also {
                cached = it
                cachedSource = source
                cachedEdits = edits
            }
        }
    }

    private fun closeEditorHost() {
        editorHost?.close()
        editorHost = null
    }

    /** Scene presentation bound to the simulated player of this cache session. */
    private fun scenePresentation(cache: LoadedOsrsCacheSession): ScenePresentation =
        ScenePresentation.EDITOR.withVarState(playerFor(cache))

    /** The simulated player for this cache session, shared by scene builds and core modules. */
    private fun playerFor(cache: LoadedOsrsCacheSession): SimulatedClient {
        val definitions = cache.bundle().definitions()
        player?.takeIf { it.definitions() === definitions }?.let { return it }
        return SimulatedClient(simulation, definitions, NavigatorWorldMap(sceneViewport.navigationService()))
            .also { player = it }
    }

    private fun currentClientCycle(): Int = (simulation.clock().clientCycles() and Int.MAX_VALUE.toLong()).toInt()

    private fun currentCache(): LoadedOsrsCacheSession? = cacheSessions.current().orElse(null)

    override fun close() {
        if (closed) return
        closed = true
        projectOpenSequence.incrementAndGet()
        pendingProjectOpen?.cancel(true)
        projectOpenCoordinator.close()
        mapScenes.close()
        closeEditorHost()
        mapEditor.close()
        sceneViewport.close()
        SettingsJsonStore.save(settingsFile, renderSettings)
        cacheSessions.close()
        StudioBranding.close()
        imgui.close()
        window.close()
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(StudioApplication::class.java)
        val STUDIO_HOME: Path = Path.of(System.getProperty("user.home"), ".openrune-studio")
        const val GAMEVAL_PROVIDER = "osrs.cache.gamevals"
        const val DEFAULT_REGION = 50

        /** Longest simulation step per frame, so a stall never fast-forwards the clock. */
        const val MAX_FRAME_DELTA_NANOS = 100_000_000L
    }
}
