package com.rspsi.studio

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.EditorSession
import com.rspsi.editor.PlaceObjectCommand
import com.rspsi.editor.brush.BrushAwareTool
import com.rspsi.editor.brush.BrushCapability
import com.rspsi.editor.input.EditorInputRouter
import com.rspsi.editor.integration.ServerIntegrationService
import com.rspsi.editor.integration.npc.NpcSpawnService
import com.rspsi.editor.integration.reference.ReferenceService
import com.rspsi.editor.model.WorldLocation
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.plugin.EditorPluginHost
import com.rspsi.editor.plugin.event.SceneRenderedEvent
import com.rspsi.editor.plugin.event.ToolActivatedEvent
import com.rspsi.editor.render.GpuUploadPlan
import com.rspsi.editor.render.RenderConfigCompiler
import com.rspsi.editor.render.RenderSettingKeys
import com.rspsi.editor.settings.EditorSettingKeys
import com.rspsi.editor.settings.SettingsStore
import com.rspsi.editor.simulation.SimulationEngine
import com.rspsi.editor.symbols.SymbolService
import com.rspsi.editor.tool.BoxSelectTool
import com.rspsi.editor.tool.EditorToolController
import com.rspsi.editor.tool.SplinePathTool
import com.rspsi.editor.tool.ToolContext
import com.rspsi.studio.brush.StudioBrushManager
import com.rspsi.studio.feature.StudioFeatureRegistry
import com.rspsi.studio.feature.TileInfoHud
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.CommandPalette
import com.rspsi.studio.ui.FloatingToolbar
import com.rspsi.studio.ui.GoToLocationDialog
import com.rspsi.studio.ui.LeftBrushRail
import com.rspsi.studio.ui.MinimapHudOverlay
import com.rspsi.studio.ui.ObjectEditorWindow
import com.rspsi.studio.ui.StudioBottomBar
import com.rspsi.studio.ui.StudioMenuBar
import com.rspsi.studio.ui.StudioNavigation
import com.rspsi.studio.ui.StudioPanelContext
import com.rspsi.studio.ui.StudioPanelManager
import com.rspsi.studio.ui.StudioRightSidebar
import com.rspsi.studio.ui.ToolQuickPalette
import com.rspsi.studio.ui.ViewportTileContextMenu
import com.rspsi.studio.ui.WorkspaceTabBar
import com.rspsi.studio.ui.diagnostics.TerrainDiagnosticsOverlay
import com.rspsi.studio.ui.hud.BrushSettingsHud
import com.rspsi.studio.ui.hud.DeclarativeOverlayRenderer
import com.rspsi.studio.ui.hud.ViewportHudManager
import com.rspsi.studio.ui.panels.ObjectViewerPanel
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import java.util.Optional
import java.util.function.Consumer

/**
 * Map Studio's frame: arranges the fixed chrome (menu bar, workspace tabs, brush rail,
 * viewport, right sidebar, bottom drawer, status bar), routes shortcuts, and hands input to
 * the active editor tool.
 *
 * It owns presentation and tool activation only. Edits are commands executed on the editor
 * session; scene loading belongs to `MapSceneController`; the chrome pieces draw themselves.
 */
class MapEditorView {
    private var activeToolId = SINGLE_SELECT
    private var viewport: NativeSceneViewport? = null

    private val toolController = EditorToolController()
    private var inputRouter: EditorInputRouter? = null
    private var inputHost: EditorPluginHost? = null
    private var defaultToolActivated = false

    // Chrome
    private val menuBar = StudioMenuBar()
    private val workspaceTabBar = WorkspaceTabBar()
    private val leftBrushRail = LeftBrushRail()
    private val floatingToolbar = FloatingToolbar()
    private val toolQuickPalette = ToolQuickPalette()
    private val minimapHudOverlay = MinimapHudOverlay()
    private val rightSidebar = StudioRightSidebar()
    private val bottomBar = StudioBottomBar()
    private val panelManager = StudioPanelManager()
    private val features = StudioFeatureRegistry()
    private val brushManager = StudioBrushManager()
    private val hudManager = ViewportHudManager()
    private val declarativeOverlays = DeclarativeOverlayRenderer()

    // Windows and dialogs
    private val preferencesWindow = PreferencesWindow()
    private val objectEditor = ObjectEditorWindow()
    private val tileContextMenu = ViewportTileContextMenu(objectEditor, brushManager)
    private val commandPalette = CommandPalette()
    private val goToDialog = GoToLocationDialog()

    private val layoutStore = NativeWorkspaceLayoutStore()
    private var layoutRestored = false

    /** View > Left Brush Rail: keeps the rail up even when no brush tool is active. */
    private var showLeftToolRail = false
    private var showServerSpawns = true

    // Set by the application
    private var openWorldMapWorkspace: Runnable? = null
    private var locationNavigator = Consumer<WorldLocation> { }
    private var definitionPublicationPersistence = Consumer<LoadedOsrsCacheSession> { }

    // This frame's workspace routing, used by shortcuts outside render()
    private var workspaces: WorkspaceManager? = null
    private var closeWorkspace: Consumer<WorkspaceManager.Workspace>? = null

    init {
        features.setOwnedPanelSink { panelManager.register(it) }
        minimapHudOverlay.setOnWorldMapClick { openWorldMap() }
        features.register(TileInfoHud())
        features.register(TerrainDiagnosticsOverlay())
    }

    fun setDefinitionPublicationPersistence(persistence: Consumer<LoadedOsrsCacheSession>?) {
        definitionPublicationPersistence = persistence ?: Consumer { }
    }

    /** Receives requests to open the World Map workspace tab. */
    fun setOpenWorldMapWorkspace(open: Runnable?) {
        openWorldMapWorkspace = open
    }

    /** Receives Go To requests; the application frames the tile or loads its region. */
    fun setLocationNavigator(navigator: Consumer<WorldLocation>?) {
        locationNavigator = navigator ?: Consumer { }
    }

    /** Draws one Map Studio frame. */
    fun render(
        cache: LoadedOsrsCacheSession,
        plan: GpuUploadPlan?,
        viewport: NativeSceneViewport,
        sceneStatus: String?,
        openDashboard: Runnable,
        settings: SettingsStore,
        editorHost: EditorPluginHost?,
        openInterfaceStudio: Runnable,
        openObjectStudio: Runnable,
        openIntegrationCenter: Runnable,
        simulation: SimulationEngine,
        symbols: SymbolService,
        references: ReferenceService,
        spawns: NpcSpawnService?,
        integrations: ServerIntegrationService,
        workspaces: WorkspaceManager,
        openMapEditor: Runnable,
        closeWorkspace: Consumer<WorkspaceManager.Workspace>,
    ) {
        this.viewport = viewport
        this.workspaces = workspaces
        this.closeWorkspace = closeWorkspace

        restoreLayout()
        bindInputRouter(editorHost)
        handleGlobalShortcuts()
        routeSessionShortcuts(editorHost, settings)
        syncEditorHost(editorHost)

        val brushSettings = features.feature(BrushSettingsHud.ID).orElse(null) as? BrushSettingsHud
        val sharedBrushSettings = features.usesSharedBrushSettings(activeToolId)
        val brushRailVisible = showLeftToolRail || sharedBrushSettings
        val brushDockWidth = if (sharedBrushSettings && brushSettings != null && brushSettings.isVisible &&
            brushSettings.isDocked
        ) {
            BrushSettingsHud.DOCKED_WIDTH
        } else {
            0.0f
        }
        val layout = MapStudioLayout.compute(bottomBar, brushRailVisible, brushDockWidth,
            rightSidebar.preferredWidth(panelManager))
        val activate = Consumer<String> { activateTool(editorHost, it) }

        menuBar.render(
            cache, editorHost, integrations, showServerSpawns, { showServerSpawns = it }, preferencesWindow,
            openIntegrationCenter, { commandPalette.open() }, { resetLayout() }, bottomBar.isDrawerOpen,
            { bottomBar.toggleDrawer() }, features.isEnabled(TileInfoHud.ID),
            { features.setEnabled(TileInfoHud.ID, !features.isEnabled(TileInfoHud.ID)) },
            showLeftToolRail, { showLeftToolRail = !showLeftToolRail }, isWorldMapWorkspaceOpen(),
            { toggleWorldMap() },
        )
        workspaceTabBar.render(workspaces, openDashboard, openMapEditor, openInterfaceStudio, openObjectStudio,
            openWorldMapWorkspace, closeWorkspace, { commandPalette.open() }, layout.x, layout.y, layout.width)

        val panelContext = StudioPanelContext(
            cache, settings, session(editorHost), editorHost, viewport, simulation, symbols, references, spawns,
            integrations, activate, activeToolId, toolController, features, brushManager, hudManager,
            navigation(), definitionPublicationPersistence,
        )

        // The left brush rail shows for brush tools, or always when forced from the View menu.
        if (brushRailVisible) {
            leftBrushRail.render(panelContext, layout.x, layout.contentY, layout.contentHeight, activate,
                activeToolId, showLeftToolRail)
        }
        renderViewport(cache, plan, viewport, sceneStatus, settings, editorHost, layout, panelContext, spawns)
        rightSidebar.render(panelManager, panelContext, layout.rightX, layout.contentY, layout.rightWidth,
            layout.rightHeight)
        bottomBar.render(panelManager, panelContext, layout.x, layout.bottomY, layout.bottomWidth,
            layout.bottomHeight, activate, activeToolId)
        renderStatusBar(cache)

        features.renderFloating(panelContext)
        commandPalette.render(editorHost, activate, locationNavigator)
        goToDialog.render(locationNavigator)
        preferencesWindow.render(settings, editorHost?.context()?.settingsService())
        objectEditor.render(cache, session(editorHost))
    }

    /** Mirrors the loaded map's core-module registrations (tools, brushes, panels) into chrome. */
    private fun syncEditorHost(editorHost: EditorPluginHost?) {
        if (editorHost == null) {
            features.bindEditorRegistry(null)
            brushManager.syncHostBrushes(emptyList())
            return
        }
        features.bindEditorRegistry(editorHost.registry())
        brushManager.syncHostBrushes(editorHost.context().services().brushes().brushes())
        panelManager.syncPluginContributions(editorHost.registry().panelRegistrations())
        panelManager.syncUiSurfaces(editorHost.registry().uiSurfaceContributions())
        if (!defaultToolActivated) {
            // The default tool's button looks active from the start, so activate it for real
            // the first frame the host exists instead of waiting for a click.
            defaultToolActivated = true
            activateTool(editorHost, activeToolId)
        }
    }

    /** Lets panels open the object viewer or the object editor. */
    private fun navigation() = object : StudioNavigation {
        override fun inspectObject(`object`: WorldObject?) {
            if (`object` == null) return
            val viewer = panelManager.panel(ObjectViewerPanel.ID).orElse(null) as? ObjectViewerPanel ?: return
            viewer.inspectObject(`object`)
            panelManager.setActiveRightPanelId(ObjectViewerPanel.ID)
        }

        override fun editObject(`object`: WorldObject?) {
            if (`object` != null) objectEditor.open(`object`)
        }
    }

    // ---------------------------------------------------------------- viewport

    private fun renderViewport(
        cache: LoadedOsrsCacheSession,
        plan: GpuUploadPlan?,
        viewport: NativeSceneViewport,
        sceneStatus: String?,
        settings: SettingsStore,
        editorHost: EditorPluginHost?,
        layout: MapStudioLayout,
        panelContext: StudioPanelContext,
        spawns: NpcSpawnService?,
    ) {
        ImGui.setNextWindowPos(layout.viewportX, layout.contentY, ImGuiCond.Always)
        ImGui.setNextWindowSize(layout.viewportWidth, layout.viewportHeight, ImGuiCond.Always)
        ImGui.setNextWindowViewport(ImGui.getMainViewport().id)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f)
        ImGui.begin(VIEWPORT_WINDOW, VIEWPORT_FLAGS)
        if (plan == null) {
            ImGui.text(sceneStatus ?: "Preparing scene...")
        } else {
            drawScene(cache, plan, viewport, settings, editorHost, layout, panelContext, spawns)
        }
        ImGui.end()
        ImGui.popStyleVar()
    }

    private fun drawScene(
        cache: LoadedOsrsCacheSession,
        plan: GpuUploadPlan,
        viewport: NativeSceneViewport,
        settings: SettingsStore,
        editorHost: EditorPluginHost?,
        layout: MapStudioLayout,
        panelContext: StudioPanelContext,
        spawns: NpcSpawnService?,
    ) {
        // One compiled snapshot drives all viewport state, never individual setting keys.
        val renderConfig = RenderConfigCompiler().compile(settings.snapshot())
        // Showing every plane allows picks on any plane; otherwise only the current one.
        viewport.setPickPlaneRestriction(if (renderConfig.allHeightsVisible()) null else renderConfig.currentHeight())
        viewport.setCullMode(renderConfig.nativeCullingMode())
        viewport.setPlaneFilter(renderConfig.planeFilter())
        viewport.setHighlightSession(session(editorHost))
        viewport.render(plan, ImGui.getContentRegionAvailX(), maxOf(160.0f, ImGui.getContentRegionAvailY()),
            renderConfig.msaaSamples(), renderConfig.presentation())
        editorHost?.context()?.events()?.publish(SceneRenderedEvent(plan.fingerprint(), 0L))

        // A picked (tile, object id) becomes the placed WorldObject here, because only Studio
        // holds the live session a tile and id resolve against.
        viewport.setObjectResolver { tile, objectId ->
            val session = session(editorHost) ?: return@setObjectResolver Optional.empty()
            session.coordinates().toLocal(tile)
                .map { session.world().tile(it).snapshot() }
                .flatMap { snapshot -> snapshot.objects().stream().filter { it.id == objectId }.findFirst() }
        }

        // Tool input must run before any other widget this frame, while the scene image is
        // still ImGui's last item.
        viewport.dispatchToolInput(toolController)
        viewport.renderOverlays(toolController.activeTool(), editorHost)
        features.renderOverlays(ImGui.getWindowDrawList(), panelContext)
        if (spawns != null && showServerSpawns) renderServerSpawns(viewport, settings, spawns)
        handleObjectDrop(viewport, settings, editorHost)
        tileContextMenu.render(cache, viewport, session(editorHost))

        // Viewport HUDs share one managed stack and cannot overlap.
        hudManager.beginFrame(layout.viewportX, layout.contentY, layout.viewportWidth, layout.viewportHeight)
        minimapHudOverlay.render(panelContext, layout.viewportX, layout.contentY, layout.viewportWidth,
            layout.viewportHeight)
        declarativeOverlays.render(panelContext, editorHost)
        features.renderHUDs(panelContext)
        floatingToolbar.render(panelContext, layout.viewportX, layout.contentY,
            { activateTool(editorHost, it) }, activeToolId)
        toolQuickPalette.render(panelContext, layout.viewportX, layout.contentY, layout.viewportWidth,
            layout.viewportHeight)
    }

    /** Outlines server NPC spawns within 32 tiles of the camera on the current plane. */
    private fun renderServerSpawns(viewport: NativeSceneViewport, settings: SettingsStore, spawns: NpcSpawnService) {
        val draw = viewport.createOverlayDraw()
        val plane = settings.snapshot().get(RenderSettingKeys.CURRENT_HEIGHT)
        val camera = viewport.navigation().camera()
        val cameraX = maxOf(0, (camera.x() / 128.0f).toInt())
        val cameraY = maxOf(0, (camera.z() / 128.0f).toInt())
        for (spawn in spawns.spawns(plane, cameraX - 32, cameraY - 32, cameraX + 32, cameraY + 32)) {
            draw.tileOutline(spawn.coordinate(), 0x3388FFFF)
            val worldX = spawn.coordinate().x * 128.0f + 64.0f
            val worldZ = spawn.coordinate().y * 128.0f + 64.0f
            if (spawn.wanderRadius() > 0) {
                draw.circle(worldX, 0.0f, worldZ, spawn.wanderRadius() * 128.0f, 0x3388FF55, 1.0f)
            }
            draw.worldLabel(spawn.symbolicName(), worldX, -80.0f, worldZ, 0xFFFFFFFF.toInt(), 0x1A3A6BEE)
        }
    }

    /** Object ids dragged from the object viewer: a tile highlight while hovering, a placement on drop. */
    private fun handleObjectDrop(viewport: NativeSceneViewport, settings: SettingsStore, editorHost: EditorPluginHost?) {
        val localX = ImGui.getIO().mousePosX - viewport.imageOriginX()
        val localY = ImGui.getIO().mousePosY - viewport.imageOriginY()
        if (ImGui.getDragDropPayload<Any>() != null && localX >= 0 && localX <= viewport.lastWidth() &&
            localY >= 0 && localY <= viewport.lastHeight()
        ) {
            viewport.tileAt(localX, localY).ifPresent { tile ->
                val draw = viewport.createOverlayDraw()
                draw.tileFilled(tile, 0x5538BDF8)
                draw.tileOutline(tile, 0xFF38BDF8.toInt())
            }
        }
        if (!ImGui.beginDragDropTarget()) return
        val droppedId = ImGui.acceptDragDropPayload(OBJECT_PAYLOAD, Int::class.javaObjectType)
        if (droppedId != null) {
            viewport.tileAt(localX, localY).ifPresent { tile ->
                val session = session(editorHost) ?: return@ifPresent
                val local = session.coordinates().toLocal(tile).orElse(null) ?: return@ifPresent
                val snapshot = settings.snapshot()
                // TODO(migration): route drops through the object placement tool instead of
                // executing its command from the view (AGENTS.md editing flow).
                session.execute(PlaceObjectCommand(
                    WorldObject(droppedId, snapshot.get(EditorSettingKeys.OBJECT_TYPE),
                        snapshot.get(EditorSettingKeys.OBJECT_ROTATION), local.plane, local.x, local.y),
                    "Spawn Object #$droppedId"))
            }
        }
        ImGui.endDragDropTarget()
    }

    // ---------------------------------------------------------------- tools and shortcuts

    private fun bindInputRouter(editorHost: EditorPluginHost?) {
        if (editorHost == null) {
            inputRouter = null
            inputHost = null
        } else if (inputHost !== editorHost) {
            inputHost = editorHost
            inputRouter = EditorInputRouter(editorHost.context(), toolController)
        }
    }

    /**
     * Activates a tool by its Studio id: shows its drawer, creates the engine tool from the
     * core module's registration, applies shared brush settings and publishes the switch.
     */
    private fun activateTool(editorHost: EditorPluginHost?, registrationId: String) {
        val previous = activeToolId
        activeToolId = registrationId
        // Tools with drawer content bring it forward; picker tools leave the drawer alone.
        if (features.toolView(registrationId).map { it.hasContextDrawerContent }.orElse(false)) {
            bottomBar.isDrawerOpen = true
        }
        if (inputRouter == null || editorHost == null) return

        // TODO(migration): the four selection buttons drive one engine tool ("selection.box")
        // by mode and target; that mapping belongs to their StudioToolUi, not this view.
        val engineId = if (registrationId in SELECTION_TOOL_IDS) "selection.box" else registrationId
        val registration = editorHost.registry().toolRegistrations().firstOrNull { it.id() == engineId } ?: return
        val tool = registration.factory().get()

        if (features.usesSharedBrushSettings(registrationId) && tool is BrushAwareTool) {
            brushManager.activeBrush(registrationId, setOf(BrushCapability.SPATIAL_FOOTPRINT))?.let { tool.setBrush(it) }
            tool.setBrushRadius(brushManager.brushRadius())
        }
        if (tool is BoxSelectTool) {
            val single = registrationId == SINGLE_SELECT || registrationId == "selection.object.single"
            tool.setMode(if (single) BoxSelectTool.Mode.SINGLE else BoxSelectTool.Mode.MULTI)
            val objects = registrationId.startsWith("selection.object.")
            tool.setTarget(if (objects) BoxSelectTool.Target.OBJECTS else BoxSelectTool.Target.TILES)
        }
        toolController.activate(tool, ToolContext(editorHost.context().session(), editorHost.context().assets(), viewport))
        editorHost.context().events().publish(ToolActivatedEvent(previous, registrationId))
    }

    /** Ctrl shortcuts that work regardless of the active tool. */
    private fun handleGlobalShortcuts() {
        val io = ImGui.getIO()
        if (io.wantTextInput || !(io.keyCtrl || io.keySuper)) return
        when {
            ImGui.isKeyPressed(ImGuiKey.Comma, false) -> preferencesWindow.toggle()
            ImGui.isKeyPressed(ImGuiKey.G, false) -> openGoTo()
            ImGui.isKeyPressed(ImGuiKey.M, false) -> toggleWorldMap()
            ImGui.isKeyPressed(ImGuiKey.P, false) -> commandPalette.open()
        }
    }

    /** Undo/redo/save, plane stepping, tool hotkeys and the spline tool's keys. */
    private fun routeSessionShortcuts(editorHost: EditorPluginHost?, settings: SettingsStore) {
        val io = ImGui.getIO()
        if (io.wantTextInput) return
        val ctrl = io.keyCtrl || io.keySuper
        val session = session(editorHost)
        when {
            ctrl && ImGui.isKeyPressed(ImGuiKey.Z, false) -> if (io.keyShift) session?.redo() else session?.undo()
            ctrl && ImGui.isKeyPressed(ImGuiKey.Y, false) -> session?.redo()
            ctrl && ImGui.isKeyPressed(ImGuiKey.S, false) -> if (session != null && session.canSave()) session.save()
            !ctrl && !io.keyShift && !io.keyAlt -> {
                val plane = settings.snapshot().get(RenderSettingKeys.CURRENT_HEIGHT)
                when {
                    ImGui.isKeyPressed(ImGuiKey.PageUp, false) ->
                        if (plane < 3) settings.set(RenderSettingKeys.CURRENT_HEIGHT, plane + 1)
                    ImGui.isKeyPressed(ImGuiKey.PageDown, false) ->
                        if (plane > 0) settings.set(RenderSettingKeys.CURRENT_HEIGHT, plane - 1)
                    ImGui.isKeyPressed(ImGuiKey.V, false) -> activateTool(editorHost, SINGLE_SELECT)
                    ImGui.isKeyPressed(ImGuiKey.B, false) -> activateTool(editorHost, "terrain.tile-painter")
                    ImGui.isKeyPressed(ImGuiKey.R, false) || ImGui.isKeyPressed(ImGuiKey.H, false) ->
                        activateTool(editorHost, "terrain.raise")
                    ImGui.isKeyPressed(ImGuiKey.O, false) -> activateTool(editorHost, "object.place")
                    ImGui.isKeyPressed(ImGuiKey.P, false) -> activateTool(editorHost, "path.spline")
                    ImGui.isKeyPressed(ImGuiKey.X, false) || ImGui.isKeyPressed(ImGuiKey.Delete, false) ->
                        session?.selection()?.clear()
                }
            }
        }
        // Spline path: Enter builds, Escape clears, [ and ] change the width.
        val pathTool = toolController.activeTool() as? SplinePathTool ?: return
        when {
            ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false) ->
                pathTool.buildPath()
            ImGui.isKeyPressed(ImGuiKey.Escape, false) -> pathTool.clear()
            ImGui.isKeyPressed(ImGuiKey.LeftBracket, false) -> pathTool.setWidth(maxOf(1, pathTool.width() - 1))
            ImGui.isKeyPressed(ImGuiKey.RightBracket, false) -> pathTool.setWidth(minOf(16, pathTool.width() + 1))
        }
    }

    // ---------------------------------------------------------------- world map, go to, status

    /** True when the World Map workspace is the focused tab. */
    fun isWorldMapWorkspaceOpen(): Boolean = workspaces?.active() == WorkspaceManager.Workspace.WORLD_MAP

    fun openWorldMap() {
        openWorldMapWorkspace?.run()
    }

    fun toggleWorldMap() {
        if (isWorldMapWorkspaceOpen()) closeWorkspace?.accept(WorkspaceManager.Workspace.WORLD_MAP) else openWorldMap()
    }

    private fun openGoTo() = goToDialog.open(viewport?.navigationService()?.current()?.orElse(null))

    /** The pinned bottom status bar: Go To, then cache path, FPS and heap on the right. */
    private fun renderStatusBar(cache: LoadedOsrsCacheSession?) {
        val main = ImGui.getMainViewport()
        ImGui.setNextWindowPos(main.posX, main.posY + main.sizeY - MapStudioLayout.STATUS_BAR_HEIGHT, ImGuiCond.Always)
        ImGui.setNextWindowSize(main.sizeX, MapStudioLayout.STATUS_BAR_HEIGHT, ImGuiCond.Always)
        ImGui.setNextWindowViewport(main.id)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 3.0f)
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ImGui.getColorU32(0.12f, 0.13f, 0.15f, 1.0f))
        if (ImGui.begin("##AppStatusBar", STATUS_FLAGS)) {
            ImGui.text("Ready")
            ImGui.sameLine(0.0f, 16.0f)
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 0.0f)
            if (ImGui.smallButton("${StudioIcons.NAVIGATION} Go to...##status-goto")) openGoTo()
            ImGui.popStyleVar()
            if (ImGui.isItemHovered()) ImGui.setItemTooltip("Go to a world tile, region, or region ID (Ctrl+G)")

            // Cache path, FPS and heap only; tile and selection detail live in their panels.
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val trailing = String.format("%s  |  %.0f FPS  |  %d MB",
                cache?.path()?.toString() ?: "No cache loaded", ImGui.getIO().framerate, usedMb)
            val trailingWidth = ImGui.calcTextSize(trailing).x
            if (main.sizeX - trailingWidth - 16.0f > ImGui.getCursorPosX()) {
                ImGui.sameLine(main.sizeX - trailingWidth - 16.0f)
                ImGui.textDisabled(trailing)
            }
        }
        ImGui.end()
        ImGui.popStyleColor()
        ImGui.popStyleVar()
    }

    // ---------------------------------------------------------------- layout persistence

    private fun restoreLayout() {
        if (layoutRestored) return
        layoutRestored = true
        layoutStore.load()?.let { saved ->
            bottomBar.isDrawerOpen = saved.bottomDrawerVisible()
            hudManager.restore(saved.huds())
        }
    }

    private fun resetLayout() {
        layoutStore.reset()
        bottomBar.isDrawerOpen = true
        features.setEnabled(TileInfoHud.ID, true)
        showLeftToolRail = false
        floatingToolbar.resetPosition()
        hudManager.resetUserState()
    }

    /** Saves drawer and HUD layout for the next session. */
    fun close() {
        if (!layoutRestored) return
        layoutStore.save(NativeWorkspaceLayoutStore.State(NativeWorkspaceLayoutStore.CURRENT_VERSION, "",
            bottomBar.isDrawerOpen, hudManager.snapshot()))
    }

    private companion object {
        const val VIEWPORT_WINDOW = "StudioViewport"
        const val OBJECT_PAYLOAD = "DND_OBJECT_ID"
        const val SINGLE_SELECT = "selection.single"
        val SELECTION_TOOL_IDS = setOf(SINGLE_SELECT, "selection.multi", "selection.object.single",
            "selection.object.multi")

        val VIEWPORT_FLAGS = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoDocking or
            ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoSavedSettings or
            ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoBackground

        val STATUS_FLAGS = ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoSavedSettings

        fun session(editorHost: EditorPluginHost?): EditorSession? = editorHost?.context()?.session()
    }
}
