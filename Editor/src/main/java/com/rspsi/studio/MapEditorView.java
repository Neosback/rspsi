package com.rspsi.studio;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.npc.NpcSpawn;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.plugin.EditorCommandRegistration;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.runtime.PluginEcosystemService;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.render.SceneVisibilityPolicy;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.simulation.SimulationEngine;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.brush.StudioBrushManager;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.ui.FloatingToolbar;
import com.rspsi.studio.ui.MinimapHudOverlay;
import com.rspsi.studio.ui.panels.MinimapPanel;
import com.rspsi.studio.ui.StudioBottomBar;
import com.rspsi.studio.ui.StudioRightSidebar;
import com.rspsi.studio.ui.LeftBrushRail;
import com.rspsi.studio.ui.StudioMenuBar;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.StudioPanelManager;
import com.rspsi.studio.ui.WorkspaceTabBar;
import com.rspsi.studio.ui.hud.ViewportHudManager;
import com.rspsi.studio.ui.hud.DeclarativeOverlayRenderer;
import com.rspsi.studio.ui.hud.BrushSettingsHud;
import com.rspsi.studio.ui.diagnostics.TerrainDiagnosticsOverlay;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.builtin.TileInfoHudPlugin;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.SplinePathTool;

import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.DeleteObjectCommand;
import com.rspsi.editor.PlaceObjectCommand;
import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.SetTileFlagsCommand;
import com.rspsi.editor.SetTerrainHeightCommand;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.terrain.TerrainVertexLattice;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.studio.ui.panels.TilePainterPalette;
import imgui.flag.ImGuiMouseButton;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Modular native Map Editor view faithful to Displee's Map Editor UI layout.
 *
 * <p>Orchestrates dedicated UI components: {@link StudioMenuBar}, {@link WorkspaceTabBar},
 * {@link TileInfoHudPlugin}, {@link StudioRightSidebar}, {@link StudioBottomBar}, and
 * {@link StudioPanelManager}.</p>
 */
public final class MapEditorView {
    private static final String VIEWPORT_WINDOW = "StudioViewport";
    private static final int FIXED_VIEWPORT_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoBackground;

    private String activeToolId = "selection.single";
    private NativeSceneViewport viewport;
    private WorldTile contextTile;

    private final EditorToolController toolController = new EditorToolController();
    private EditorInputRouter inputRouter;
    private EditorPluginLifecycleManager inputHost;

    private final StudioMenuBar menuBar = new StudioMenuBar();
    private final WorkspaceTabBar workspaceTabBar = new WorkspaceTabBar();
    private final LeftBrushRail leftBrushRail = new LeftBrushRail();
    private final FloatingToolbar floatingToolbar = new FloatingToolbar();
    private final MinimapHudOverlay minimapHudOverlay = new MinimapHudOverlay();
    private final StudioRightSidebar rightSidebar = new StudioRightSidebar();
    private final StudioBottomBar bottomBar = new StudioBottomBar();
    private final StudioPanelManager panelManager = new StudioPanelManager();
    private final StudioPluginManager studioPluginManager = new StudioPluginManager();
    private final StudioBrushManager brushManager = new StudioBrushManager();
    private final ViewportHudManager hudManager = new ViewportHudManager();
    private final DeclarativeOverlayRenderer declarativeOverlays = new DeclarativeOverlayRenderer();
    {
        studioPluginManager.setOwnedPanelSink(panelManager::register);
    }

    {
        minimapHudOverlay.setOnWorldMapClick(() -> panelManager.setActiveRightPanelId(MinimapPanel.ID));
        studioPluginManager.register(new TileInfoHudPlugin());
        studioPluginManager.register(new BrushSettingsHud());
        studioPluginManager.register(new TerrainDiagnosticsOverlay());
    }

    private final PreferencesWindow preferencesWindow = new PreferencesWindow();
    private final PluginManagerWindow pluginManagerWindow = new PluginManagerWindow();
    private final NativeWorkspaceLayoutStore layoutStore = new NativeWorkspaceLayoutStore();
    private boolean layoutRestored;
    private boolean defaultToolActivated;

    private boolean commandPaletteOpen;
    private final ImString commandQuery = new ImString(128);
    // Off by default: the rail auto-shows itself for brush tools (see
    // LeftBrushRail.isBrushToolActive); this is only the View > Left Brush
    // Rail override that forces it to stay up regardless of active tool.
    private boolean showLeftToolRail = false;

    // Shared studio runtime services & sibling workspace callbacks
    private Runnable openInterfaceStudio;
    private Runnable openObjectStudio;
    private Runnable openIntegrationCenter;
    private SimulationEngine simulation;
    private SymbolService symbols;
    private ReferenceService references;
    private NpcSpawnService spawns;
    private ServerIntegrationService integrations;
    private boolean showServerSpawns = true;
    private WorkspaceManager workspaces;
    private Runnable openMapEditor;
    private Consumer<WorkspaceManager.Workspace> closeWorkspace;
    private Consumer<LoadedOsrsCacheSession> definitionPublicationPersistence =
            ignored -> { };

    public void setPluginEcosystem(PluginEcosystemService ecosystem, Runnable rescanPlugins) {
        pluginManagerWindow.setEcosystem(ecosystem, rescanPlugins);
    }

    public void setDefinitionPublicationPersistence(
            Consumer<LoadedOsrsCacheSession> persistence) {
        definitionPublicationPersistence = persistence == null
                ? ignored -> { }
                : persistence;
    }

    public void render(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                       NativeSceneViewport viewport, String sceneStatus,
                       Runnable openDashboard, SettingsStore settings,
                       EditorPluginLifecycleManager pluginLifecycle,
                       boolean dirty,
                       Runnable openInterfaceStudio,
                       Runnable openObjectStudio,
                       Runnable openIntegrationCenter,
                       SimulationEngine simulation,
                       SymbolService symbols,
                       ReferenceService references,
                       NpcSpawnService spawns,
                       ServerIntegrationService integrations,
                       WorkspaceManager workspaces,
                       Runnable openMapEditor,
                       Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        this.openInterfaceStudio = openInterfaceStudio;
        this.openObjectStudio = openObjectStudio;
        this.openIntegrationCenter = openIntegrationCenter;
        this.simulation = simulation;
        this.symbols = symbols;
        this.references = references;
        this.spawns = spawns;
        this.integrations = integrations;
        this.workspaces = workspaces;
        this.openMapEditor = openMapEditor;
        this.closeWorkspace = closeWorkspace;
        render(cache, plan, viewport, sceneStatus, openDashboard, settings, pluginLifecycle, dirty);
    }

    public void render(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                       NativeSceneViewport viewport, String sceneStatus,
                       Runnable openDashboard, SettingsStore settings,
                       EditorPluginLifecycleManager pluginLifecycle,
                       boolean dirty) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(openDashboard, "dashboard callback");
        Objects.requireNonNull(settings, "settings");
        this.viewport = viewport;

        restoreLayout();
        bindInputRouter(pluginLifecycle);
        openCommandPaletteShortcut();
        handleGlobalShortcuts();
        routeSessionShortcuts(pluginLifecycle, settings);

        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            panelManager.syncPluginContributions(pluginLifecycle.host().registry().panelRegistrations());
            panelManager.syncUiSurfaces(pluginLifecycle.host().registry().uiSurfaceContributions());
            if (!defaultToolActivated) {
                // The default tool's button shows as active from the field default alone, but
                // nothing actually calls toolController.activate(...) until the user clicks it -
                // so it visually looks "on" while doing nothing until re-clicked once. Activate
                // it for real the first frame the engine host is ready.
                defaultToolActivated = true;
                activateTool(pluginLifecycle, activeToolId);
            }
        }

        boolean brushRailVisible = showLeftToolRail
                || LeftBrushRail.isBrushToolActive(studioPluginManager, activeToolId);
        Layout layout = Layout.compute(bottomBar, brushRailVisible);

        // 1. Program-owned Menu Bar (File, Edit, View, Cache, Plugins, Server, Help)
        menuBar.render(cache, pluginLifecycle, integrations, showServerSpawns,
                val -> this.showServerSpawns = val,
                preferencesWindow, pluginManagerWindow, openIntegrationCenter,
                () -> { commandPaletteOpen = true; commandQuery.clear(); },
                this::resetLayout,
                bottomBar.isDrawerOpen(),
                bottomBar::toggleDrawer,
                studioPluginManager.isEnabled(TileInfoHudPlugin.ID),
                () -> studioPluginManager.setEnabled(TileInfoHudPlugin.ID, !studioPluginManager.isEnabled(TileInfoHudPlugin.ID)),
                showLeftToolRail,
                () -> this.showLeftToolRail = !this.showLeftToolRail);

        // 2. Dedicated Workspace Tab Bar
        workspaceTabBar.render(workspaces, openDashboard, openMapEditor,
                openInterfaceStudio, openObjectStudio, closeWorkspace,
                () -> { commandPaletteOpen = true; commandQuery.clear(); },
                layout.x(), layout.y(), layout.width());

        // 3. Studio Panel Context
        StudioPanelContext panelContext = new StudioPanelContext(
                cache, settings, session(pluginLifecycle), pluginLifecycle,
                viewport, simulation, symbols, references, spawns, integrations,
                toolId -> activateTool(pluginLifecycle, toolId),
                activeToolId,
                toolController,
                studioPluginManager,
                brushManager,
                hudManager,
                definitionPublicationPersistence);

        // 4. Left Brush Rail (TOOL_RAIL slot: brush settings for Tile Painter/Height Sculptor) -
        // renders itself only when a brush tool is active, or always when forced via the View menu.
        if (brushRailVisible) {
            leftBrushRail.render(panelContext, layout.x(), layout.contentY(), layout.contentHeight(),
                    toolId -> activateTool(pluginLifecycle, toolId), activeToolId, showLeftToolRail);
        }

        // 5. Viewport (Displee 3D Canvas) with FloatingToolbar, Minimap HUD, and Tile HUD
        renderViewport(cache, plan, viewport, sceneStatus, settings, pluginLifecycle, layout, panelContext);

        // 6. Right Sidebar (Vertical icon rail + active panel host) - spans the full content
        // height so it runs all the way down next to the bottom drawer instead of stopping short.
        rightSidebar.render(panelManager, panelContext,
                layout.rightX(), layout.contentY(), layout.rightWidth(), layout.rightHeight());

        // 7. Bottom Bar (Context-Sensitive Tool Shelf & Drawer) - stops before the right sidebar
        // instead of running underneath it.
        bottomBar.render(panelManager, panelContext,
                layout.x(), layout.bottomY(), layout.bottomWidth(), layout.bottomHeight(),
                toolId -> activateTool(pluginLifecycle, toolId),
                activeToolId);

        // 7. Pinned Status Bar (Pinned to absolute bottom of window, never scrolls)
        renderAppStatusBar(panelContext, cache);

        // 8. Overlays & Windows
        studioPluginManager.renderFloating(panelContext);
        renderCommandPalette(pluginLifecycle);
        preferencesWindow.render(settings, pluginLifecycle != null && pluginLifecycle.host() != null
                ? pluginLifecycle.host().context().settingsService() : null);
        pluginManagerWindow.render(pluginLifecycle);
    }

    private void renderViewport(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                NativeSceneViewport viewport, String sceneStatus,
                                SettingsStore settings,
                                EditorPluginLifecycleManager pluginLifecycle,
                                Layout layout,
                                StudioPanelContext panelContext) {
        ImGui.setNextWindowPos(layout.viewportX(), layout.contentY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(layout.viewportWidth(), layout.viewportHeight(), ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.begin(VIEWPORT_WINDOW, FIXED_VIEWPORT_FLAGS);
        if (plan == null) {
            ImGui.text(sceneStatus == null ? "Preparing scene..." : sceneStatus);
        } else {
            // When all planes are visible, allow clicks on any plane's rendered geometry;
            // otherwise restrict picks to the active editing plane.
            var planeSelection = settings.snapshot().get(RenderSettingKeys.PLANE_SELECTION);
            if (planeSelection == SceneVisibilityPolicy.PlaneSelection.ALL) {
                viewport.setPickPlaneRestriction(null);
            } else {
                viewport.setPickPlaneRestriction(settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE));
            }

            viewport.setCullMode(settings.snapshot().get(RenderSettingKeys.NATIVE_CULLING_MODE));
            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()),
                    settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES),
                    new RenderConfigCompiler().compile(settings.snapshot()).presentation());
            if (pluginLifecycle != null && pluginLifecycle.host() != null) {
                pluginLifecycle.host().context().events().publish(
                        new com.rspsi.editor.plugin.event.SceneRenderedEvent(
                                plan.fingerprint(), 0L));
            }

            // Resolve a ray-picked (tile, objectId) pair into the real placed WorldObject -
            // wired here (not inside NativeSceneViewport) since only Studio holds the live
            // session/world document a tile+id pair needs to become an actual object.
            viewport.setObjectResolver((objectTile, objectId) -> {
                if (pluginLifecycle == null || pluginLifecycle.host() == null) return java.util.Optional.empty();
                var session = pluginLifecycle.host().context().session();
                if (session == null) return java.util.Optional.empty();
                return session.coordinates().toLocal(objectTile)
                        .map(local -> session.world().tile(local).snapshot())
                        .flatMap(snapshot -> snapshot.objects().stream()
                                .filter(o -> o.id() == objectId)
                                .findFirst());
            });

            // Feed real mouse input to the active EditorTool (selection, tile-painter brush,
            // height sculptor, etc). Must run before any other ImGui widget call this frame so
            // isItemHovered() still refers to the scene image.
            viewport.dispatchToolInput(toolController);

            viewport.renderOverlays(toolController.activeTool(), pluginLifecycle);
            // SelectionOverlayPlugin (the object/tile selection hull highlight) is
            // dispatched here now, alongside every other Studio plugin's overlay -
            // no more hardcoded field/call wiring it in specially.
            studioPluginManager.renderOverlays(ImGui.getWindowDrawList(), panelContext);

            if (spawns != null && showServerSpawns) {
                renderServerSpawnOverlays(viewport, settings);
            }

            handleViewportDragDrop(viewport, settings, pluginLifecycle);
            handleViewportContextMenu(cache, viewport, settings, pluginLifecycle);

            // All viewport HUDs share one managed stack and cannot overlap.
            hudManager.beginFrame(layout.viewportX(), layout.contentY(),
                    layout.viewportWidth(), layout.viewportHeight());
            minimapHudOverlay.render(panelContext, layout.viewportX(), layout.contentY(),
                    layout.viewportWidth(), layout.viewportHeight());
            declarativeOverlays.render(panelContext, pluginLifecycle);
            studioPluginManager.renderHUDs(panelContext);

            // Dedicated selection-mode switcher (Single/Multi Select) - the
            // one draggable frosted-glass rail, distinct from the docked
            // brush rail and the bottom bar.
            floatingToolbar.render(panelContext, layout.viewportX(), layout.contentY(),
                    toolId -> activateTool(pluginLifecycle, toolId), activeToolId);
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderServerSpawnOverlays(NativeSceneViewport viewport, SettingsStore settings) {
        if (spawns == null || viewport == null) return;
        var draw = viewport.createOverlayDraw();
        int activePlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        int camTileX = Math.max(0, (int) (viewport.navigation().camera().x() / 128.0f));
        int camTileY = Math.max(0, (int) (viewport.navigation().camera().z() / 128.0f));
        var visibleSpawns = spawns.spawns(activePlane, camTileX - 32, camTileY - 32, camTileX + 32, camTileY + 32);
        for (NpcSpawn spawn : visibleSpawns) {
            draw.tileOutline(spawn.coordinate(), 0x3388FFFF);
            float wx = spawn.coordinate().x() * 128.0f + 64.0f;
            float wz = spawn.coordinate().y() * 128.0f + 64.0f;
            if (spawn.wanderRadius() > 0) {
                draw.circle(wx, 0.0f, wz, spawn.wanderRadius() * 128.0f, 0x3388FF55, 1.0f);
            }
            draw.worldLabel(spawn.symbolicName(), wx, -80.0f, wz, 0xFFFFFFFF, 0x1A3A6BEE);
        }
    }

    private void bindInputRouter(EditorPluginLifecycleManager pluginLifecycle) {
        if (pluginLifecycle == null || pluginLifecycle.host() == null) {
            inputRouter = null;
            inputHost = null;
            return;
        }
        if (inputHost != pluginLifecycle) {
            inputHost = pluginLifecycle;
            inputRouter = new EditorInputRouter(pluginLifecycle.host().context(), toolController);
        }
    }

    private void activateTool(EditorPluginLifecycleManager pluginLifecycle, String registrationId) {
        String previousToolId = activeToolId;
        activeToolId = registrationId;
        // Auto-open context drawer for tools with shelf content (Path Builder, Tile Painter),
        // and collapse it for tools without shelf content (Single/Multi Select).
        studioPluginManager.toolPlugin(registrationId)
                .ifPresent(tool -> bottomBar.setDrawerOpen(tool.hasContextDrawerContent()));
        if (inputRouter == null || pluginLifecycle == null || pluginLifecycle.host() == null) return;

        // Single/Multi (tile) Select and Single/Multi Select Objects are all Studio-level
        // presentation ids; all four drive the one real "selection.box" engine tool,
        // distinguished by its Mode and Target.
        String engineId = switch (registrationId) {
            case "selection.single", "selection.multi",
                 "selection.object.single", "selection.object.multi" -> "selection.box";
            default -> registrationId;
        };

        var registration = pluginLifecycle.host().registry().toolRegistrations().stream()
                .filter(tool -> engineId.equals(tool.id()))
                .findFirst()
                .orElse(null);
        if (registration == null) return;

        var tool = registration.factory().get();
        if (tool instanceof BoxSelectTool boxSelectTool) {
            boolean single = "selection.single".equals(registrationId)
                    || "selection.object.single".equals(registrationId);
            boxSelectTool.setMode(single ? BoxSelectTool.Mode.SINGLE : BoxSelectTool.Mode.MULTI);
            boolean objects = "selection.object.single".equals(registrationId)
                    || "selection.object.multi".equals(registrationId);
            boxSelectTool.setTarget(objects ? BoxSelectTool.Target.OBJECTS : BoxSelectTool.Target.TILES);
        }
        toolController.activate(tool,
                new ToolContext(pluginLifecycle.host().context().session(),
                        pluginLifecycle.host().context().assets(), viewport));
        pluginLifecycle.host().context().events().publish(
                new com.rspsi.editor.plugin.event.ToolActivatedEvent(
                        previousToolId, registrationId));
    }

    private static EditorSession session(EditorPluginLifecycleManager pluginLifecycle) {
        return pluginLifecycle != null && pluginLifecycle.host() != null
                ? pluginLifecycle.host().context().session() : null;
    }

    private void routeSessionShortcuts(EditorPluginLifecycleManager pluginLifecycle, SettingsStore settings) {
        var io = ImGui.getIO();
        if (io.getWantTextInput()) return;
        boolean ctrl = io.getKeyCtrl() || io.getKeySuper();
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.Z, false)) {
            EditorSession s = session(pluginLifecycle);
            if (s != null) {
                if (io.getKeyShift()) s.redo();
                else s.undo();
            }
        } else if (ctrl && ImGui.isKeyPressed(ImGuiKey.Y, false)) {
            EditorSession s = session(pluginLifecycle);
            if (s != null) s.redo();
        } else if (ctrl && ImGui.isKeyPressed(ImGuiKey.S, false)) {
            EditorSession s = session(pluginLifecycle);
            if (s != null && s.canSave()) s.save();
        } else if (!ctrl && !io.getKeyShift() && !io.getKeyAlt()) {
            // Plane cycling: PageUp / PageDown
            if (ImGui.isKeyPressed(ImGuiKey.PageUp, false)) {
                int curPlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
                if (curPlane < 3) settings.set(RenderSettingKeys.ACTIVE_PLANE, curPlane + 1);
            } else if (ImGui.isKeyPressed(ImGuiKey.PageDown, false)) {
                int curPlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
                if (curPlane > 0) settings.set(RenderSettingKeys.ACTIVE_PLANE, curPlane - 1);
            }
            // Tool hotkeys:
            else if (ImGui.isKeyPressed(ImGuiKey.V, false)) {
                activateTool(pluginLifecycle, "selection.single");
            } else if (ImGui.isKeyPressed(ImGuiKey.B, false)) {
                activateTool(pluginLifecycle, "terrain.tile-painter");
            } else if (ImGui.isKeyPressed(ImGuiKey.R, false) || ImGui.isKeyPressed(ImGuiKey.H, false)) {
                activateTool(pluginLifecycle, "terrain.raise");
            } else if (ImGui.isKeyPressed(ImGuiKey.O, false)) {
                activateTool(pluginLifecycle, "object.place");
            } else if (ImGui.isKeyPressed(ImGuiKey.P, false)) {
                activateTool(pluginLifecycle, "path.spline");
            } else if (ImGui.isKeyPressed(ImGuiKey.X, false) || ImGui.isKeyPressed(ImGuiKey.Delete, false)) {
                EditorSession s = session(pluginLifecycle);
                if (s != null) s.selection().clear();
            }
        }

        // Active Spline Path Tool shortcuts: Enter=Build, Esc=Clear, [/]=Width
        if (toolController.activeTool() instanceof SplinePathTool pathTool) {
            if (ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)) {
                pathTool.buildPath();
            } else if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
                pathTool.clear();
            } else if (ImGui.isKeyPressed(ImGuiKey.LeftBracket, false)) {
                pathTool.setWidth(Math.max(1, pathTool.width() - 1));
            } else if (ImGui.isKeyPressed(ImGuiKey.RightBracket, false)) {
                pathTool.setWidth(Math.min(16, pathTool.width() + 1));
            }
        }
    }

    private void handleViewportDragDrop(NativeSceneViewport viewport, SettingsStore settings,
                                        EditorPluginLifecycleManager pluginLifecycle) {
        if (viewport == null) return;

        // Visual drag ghost highlight when dragging over viewport
        if (ImGui.getDragDropPayload() != null) {
            float localX = ImGui.getIO().getMousePosX() - viewport.imageOriginX();
            float localY = ImGui.getIO().getMousePosY() - viewport.imageOriginY();
            if (localX >= 0 && localX <= viewport.lastWidth() && localY >= 0 && localY <= viewport.lastHeight()) {
                viewport.tileAt(localX, localY).ifPresent(coord -> {
                    ViewportOverlayDraw draw = viewport.createOverlayDraw();
                    draw.tileFilled(coord, 0x5538BDF8);
                    draw.tileOutline(coord, 0xFF38BDF8);
                });
            }
        }

        // Accept drop payload
        if (ImGui.beginDragDropTarget()) {
            Integer droppedId = ImGui.acceptDragDropPayload("DND_OBJECT_ID", Integer.class);
            if (droppedId != null) {
                float localX = ImGui.getIO().getMousePosX() - viewport.imageOriginX();
                float localY = ImGui.getIO().getMousePosY() - viewport.imageOriginY();
                viewport.tileAt(localX, localY).ifPresent(coord -> {
                    EditorSession s = session(pluginLifecycle);
                    if (s == null) return;
                    LocalTile local = s.coordinates().toLocal(coord).orElse(null);
                    if (local == null) return;
                    int type = settings.snapshot().get(EditorSettingKeys.OBJECT_TYPE);
                    int rot = settings.snapshot().get(EditorSettingKeys.OBJECT_ROTATION);
                    WorldObject worldObj = new WorldObject(
                            droppedId, type, rot, local.plane(), local.x(), local.y());
                    s.execute(new PlaceObjectCommand(worldObj, "Spawn Object #" + droppedId));
                });
            }
            ImGui.endDragDropTarget();
        }
    }

    private void handleViewportContextMenu(LoadedOsrsCacheSession cache, NativeSceneViewport viewport,
                                           SettingsStore settings, EditorPluginLifecycleManager pluginLifecycle) {
        if (viewport == null) return;

        // Right-click release inside viewport (ignoring camera orbit drag)
        if (ImGui.isWindowHovered() && ImGui.isMouseReleased(ImGuiMouseButton.Right)
                && !ImGui.isMouseDragging(ImGuiMouseButton.Right, 3.0f)) {
            float localX = ImGui.getIO().getMousePosX() - viewport.imageOriginX();
            float localY = ImGui.getIO().getMousePosY() - viewport.imageOriginY();
            viewport.pickAt(localX, localY).ifPresent(pick -> {
                contextTile = pick.tile();
                ImGui.openPopup("viewport_tile_context");
            });
        }

        if (ImGui.beginPopup("viewport_tile_context")) {
            renderTileContextMenu(cache, viewport, settings, pluginLifecycle);
            ImGui.endPopup();
        }
    }

    private void renderTileContextMenu(LoadedOsrsCacheSession cache, NativeSceneViewport viewport,
                                       SettingsStore settings, EditorPluginLifecycleManager pluginLifecycle) {
        if (contextTile == null) return;
        EditorSession s = session(pluginLifecycle);
        if (s == null) {
            ImGui.textDisabled("No active session");
            return;
        }

        ImGui.textColored(0xFF38BDF8, String.format("Tile (%d, %d, Pl %d)", contextTile.x(), contextTile.y(), contextTile.plane()));
        ImGui.separator();

        LocalTile contextLocal = s.coordinates().toLocal(contextTile).orElse(null);
        if (contextLocal == null) {
            ImGui.textDisabled("Tile is outside the active document");
            return;
        }
        TileCoordinate localCoordinate = contextLocal.coordinate();

        // 1. Selection Options
        if (ImGui.menuItem("Select Tile")) {
            s.selection().clear();
            s.selection().select(localCoordinate);
        }
        if (ImGui.menuItem("Add to Selection")) {
            s.selection().select(localCoordinate);
        }
        if (ImGui.menuItem("Clear Selection")) {
            s.selection().clear();
        }

        // 2. Objects on Tile
        var tile = s.world().tile(contextLocal);
        TileSnapshot snap = tile != null ? tile.snapshot() : null;
        if (snap != null && !snap.objects().isEmpty()) {
            ImGui.separator();
            ImGui.textDisabled("Objects (" + snap.objects().size() + "):");
            for (WorldObject obj : snap.objects()) {
                String objName = "Object #" + obj.id();
                if (cache != null) {
                    objName = cache.bundle().definitions().object(obj.id())
                            .map(ObjectDefinitionView::name)
                            .filter(n -> !n.isBlank())
                            .orElse("Object #" + obj.id());
                }

                if (ImGui.beginMenu(objName + " (Type " + obj.type() + ", Rot " + obj.rotation() + ")##ctx-obj-" + obj.id())) {
                    if (ImGui.menuItem("Rotate +90° (Clockwise)")) {
                        int nextRot = (obj.rotation() + 1) % 4;
                        s.execute(new RotateObjectCommand(obj, nextRot, "Rotate " + objName));
                    }
                    if (ImGui.menuItem("Rotate -90° (Counter-CW)")) {
                        int nextRot = (obj.rotation() + 3) % 4;
                        s.execute(new RotateObjectCommand(obj, nextRot, "Rotate " + objName));
                    }
                    if (ImGui.menuItem("Delete Object")) {
                        s.execute(new DeleteObjectCommand(obj, "Delete " + objName));
                    }
                    ImGui.endMenu();
                }
            }
        }

        // 3. Tile Painter Actions
        ImGui.separator();
        ImGui.textDisabled("Painter:");
        if (ImGui.menuItem("Sample Tile (Eyedropper)")) {
            if (TilePainterPalette.INSTANCE != null) {
                TilePainterPalette.INSTANCE.sampleTile(s, localCoordinate);
            }
        }
        if (ImGui.menuItem("Paint Tile with Active Brush")) {
            if (TilePainterPalette.INSTANCE != null) {
                CompositeTilePainterTool tool = new CompositeTilePainterTool();
                EditorBrush activeBrush = brushManager.activeBrush(
                        "terrain.tile-painter",
                        Set.of(com.rspsi.editor.brush.BrushCapability.SPATIAL_FOOTPRINT));
                if (activeBrush != null) tool.setBrush(activeBrush);
                tool.bindState(TilePainterPalette.INSTANCE.state());
                tool.applyToCoordinates(Set.of(localCoordinate), s);
            }
        }

        // 4. Quick Height Actions
        if (snap != null) {
            ImGui.separator();
            ImGui.textDisabled("Height:");
            if (ImGui.menuItem("Flatten Tile")) {
                int avg = (snap.southWestHeight() + snap.southEastHeight()
                        + snap.northEastHeight() + snap.northWestHeight()) / 4;
                applyQuickHeight(s, localCoordinate, avg, true);
            }
            if (ImGui.menuItem("Raise (+32)")) {
                applyQuickHeight(s, localCoordinate, 32, false);
            }
            if (ImGui.menuItem("Lower (-32)")) {
                applyQuickHeight(s, localCoordinate, -32, false);
            }

            // 5. Tile Flags
            ImGui.separator();
            ImGui.textDisabled("Flags (0x" + Integer.toHexString(snap.flags()) + "):");
            boolean blocked = (snap.flags() & 0x01) != 0;
            if (ImGui.menuItem((blocked ? "[x] " : "[ ] ") + "Blocked Walk (0x01)")) {
                int nextFlags = snap.flags() ^ 0x01;
                TileSnapshot after = new TileSnapshot(snap.southWestHeight(), snap.southEastHeight(),
                        snap.northEastHeight(), snap.northWestHeight(),
                        snap.underlayId(), snap.overlayId(), snap.overlayShape(), snap.overlayRotation(),
                        nextFlags, snap.objects());
                s.execute(new SetTileFlagsCommand(localCoordinate, snap, after, "Toggle blocked flag"));
            }
            boolean bridge = (snap.flags() & 0x02) != 0;
            if (ImGui.menuItem((bridge ? "[x] " : "[ ] ") + "Bridge Tile (0x02)")) {
                int nextFlags = snap.flags() ^ 0x02;
                TileSnapshot after = new TileSnapshot(snap.southWestHeight(), snap.southEastHeight(),
                        snap.northEastHeight(), snap.northWestHeight(),
                        snap.underlayId(), snap.overlayId(), snap.overlayShape(), snap.overlayRotation(),
                        nextFlags, snap.objects());
                s.execute(new SetTileFlagsCommand(localCoordinate, snap, after, "Toggle bridge flag"));
            }
        }
    }

    private static void applyQuickHeight(EditorSession session, TileCoordinate coordinate,
                                         int value, boolean absolute) {
        if (session == null || coordinate == null || !session.world().contains(coordinate)) return;
        var original = session.world();
        var predicted = original.copy();
        TerrainVertexLattice source = new TerrainVertexLattice(original);
        TerrainVertexLattice target = new TerrainVertexLattice(predicted);
        java.util.Set<TileCoordinate> affected = new java.util.LinkedHashSet<>();
        int[][] vertices = {
                {coordinate.x(), coordinate.y()},
                {coordinate.x() + 1, coordinate.y()},
                {coordinate.x() + 1, coordinate.y() + 1},
                {coordinate.x(), coordinate.y() + 1}
        };
        for (int[] vertex : vertices) {
            int height = absolute
                    ? value
                    : source.height(coordinate.plane(), vertex[0], vertex[1]) + value;
            affected.addAll(target.setHeight(coordinate.plane(), vertex[0], vertex[1], height));
        }
        java.util.List<EditorCommand> commands = new java.util.ArrayList<>();
        for (TileCoordinate changed : affected) {
            TileSnapshot before = original.tile(changed).snapshot();
            TileSnapshot after = predicted.tile(changed).snapshot();
            if (!before.equals(after)) {
                commands.add(new SetTerrainHeightCommand(changed, before, after,
                        before.heightSource(), after.heightSource(),
                        (absolute ? "Flatten" : value >= 0 ? "Raise" : "Lower") + " terrain at " + changed));
            }
        }
        if (!commands.isEmpty()) {
            session.execute(new CompositeEditCommand(
                    absolute ? "Flatten terrain" : value >= 0 ? "Raise terrain" : "Lower terrain",
                    commands));
        }
    }

    private void handleGlobalShortcuts() {
        var io = ImGui.getIO();
        if (!io.getWantTextInput() && (io.getKeyCtrl() || io.getKeySuper())
                && ImGui.isKeyPressed(ImGuiKey.Comma, false)) {
            preferencesWindow.toggle();
        }
    }

    private void openCommandPaletteShortcut() {
        var io = ImGui.getIO();
        if (!io.getWantTextInput() && (io.getKeyCtrl() || io.getKeySuper())
                && ImGui.isKeyPressed(ImGuiKey.P, false)) {
            commandPaletteOpen = true;
            commandQuery.clear();
        }
    }

    private void renderCommandPalette(EditorPluginLifecycleManager pluginLifecycle) {
        if (commandPaletteOpen) {
            ImGui.openPopup("CommandPaletteModal");
            commandPaletteOpen = false;
        }

        imgui.ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y - 100.0f, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(560.0f, 360.0f, ImGuiCond.Appearing);

        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0xF80E1015);
        ImGui.pushStyleColor(ImGuiCol.Border, 0xD0272C38);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 12.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 14.0f, 14.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowBorderSize, 1.0f);

        if (!ImGui.beginPopupModal("CommandPaletteModal", null, ImGuiWindowFlags.NoDecoration)) {
            ImGui.popStyleVar(3);
            ImGui.popStyleColor(2);
            return;
        }
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0);

        // Spotlight search input
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 12.0f, 8.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 8.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0xFF181A22);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0xFF222634);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0xFF262B3B);
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##cmd-query", StudioIcons.SEARCH + "  Type a tool, command, or region ID (e.g. 50,50)...", commandQuery, ImGuiInputTextFlags.None);
        ImGui.popStyleColor(3);
        ImGui.popStyleVar(2);

        ImGui.dummy(1.0f, 4.0f);
        ImGui.separator();
        ImGui.dummy(1.0f, 4.0f);

        String query = commandQuery.get().toLowerCase().trim();
        ImGui.beginChild("palette-results", 0.0f, -36.0f, false);
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var registry = pluginLifecycle.host().registry();
            boolean any = false;

            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.SelectableTextAlign, 0.0f, 0.5f);
            for (EditorToolRegistration tool : registry.toolRegistrations()) {
                if (!query.isBlank() && !tool.label().toLowerCase().contains(query)
                        && !tool.id().toLowerCase().contains(query)) continue;
                any = true;
                if (ImGui.selectable(StudioIcons.BRUSH + "  " + tool.label() + "##tool-" + tool.id(), false, 0, 0.0f, 26.0f)) {
                    activateTool(pluginLifecycle, tool.id());
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(tool.id());
            }
            for (EditorCommandRegistration command : registry.commandRegistrations()) {
                if (!query.isBlank() && !command.label().toLowerCase().contains(query)
                        && !command.id().toLowerCase().contains(query)) continue;
                any = true;
                if (ImGui.selectable(StudioIcons.TERMINAL + "  " + command.label() + "##command-" + command.id(), false, 0, 0.0f, 26.0f)) {
                    try {
                        EditorCommand cmd = registry.createCommand(command.id());
                        pluginLifecycle.host().context().session().execute(cmd);
                    } catch (RuntimeException failure) {
                        pluginLifecycle.host().context().notifications().error("Command failed", failure.getMessage());
                    }
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(command.id());
            }
            ImGui.popStyleVar();
            if (!any) ImGui.textDisabled("No matching tools or commands.");
        } else {
            ImGui.textDisabled("Plugin host unavailable.");
        }
        ImGui.endChild();

        ImGui.separator();
        ImGui.dummy(1.0f, 2.0f);
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("ESC to close  ·  Enter to run");
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 64.0f);
        if (StudioWidgets.buttonGhost(StudioIcons.CLOSE + " Close", 64.0f, 22.0f) || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private void renderAppStatusBar(StudioPanelContext context, LoadedOsrsCacheSession cache) {
        float statusBarHeight = 24.0f;
        imgui.ImGuiViewport vp = ImGui.getMainViewport();
        float vpX = vp.getPosX();
        float vpY = vp.getPosY();
        float vpW = vp.getSizeX();
        float vpH = vp.getSizeY();

        ImGui.setNextWindowPos(vpX, vpY + vpH - statusBarHeight, ImGuiCond.Always);
        ImGui.setNextWindowSize(vpW, statusBarHeight, ImGuiCond.Always);
        ImGui.setNextWindowViewport(vp.getID());

        int statusFlags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoSavedSettings;

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8.0f, 3.0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ImGui.getColorU32(0.12f, 0.13f, 0.15f, 1.0f));

        if (ImGui.begin("##AppStatusBar", statusFlags)) {
            ImGui.text("Ready");

            // Trailing diagnostic info: cache path and FPS only - no icons, no tile/selection dump
            // (that already lives in the Tile Inspector panel and viewport tile-info overlay).
            String cachePath = cache != null ? cache.path().toString() : "No cache loaded";
            float fps = ImGui.getIO().getFramerate();
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            String trailing = String.format("%s  |  %.0f FPS  |  %d MB", cachePath, fps, usedMb);
            float trailingWidth = ImGui.calcTextSize(trailing).x;
            if (vpW - trailingWidth - 16.0f > ImGui.getCursorPosX()) {
                ImGui.sameLine(vpW - trailingWidth - 16.0f);
                ImGui.textDisabled(trailing);
            }
        }
        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }

    private void restoreLayout() {
        if (layoutRestored) return;
        layoutRestored = true;
        var saved = layoutStore.load();
        if (saved != null) {
            bottomBar.setDrawerOpen(saved.bottomDrawerVisible());
            hudManager.restore(saved.huds());
        }
    }

    private void resetLayout() {
        layoutStore.reset();
        bottomBar.setDrawerOpen(true);
        studioPluginManager.setEnabled(TileInfoHudPlugin.ID, true);
        showLeftToolRail = false;
        floatingToolbar.resetPosition();
        hudManager.resetUserState();
    }

    public void close() {
        if (!layoutRestored) return;
        layoutStore.save(new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION, "", bottomBar.isDrawerOpen(),
                hudManager.snapshot()));
    }

    private record Layout(float x, float y, float width, float height,
                          float contentY, float contentHeight,
                          float viewportX, float viewportWidth, float viewportHeight,
                          float rightX, float rightWidth, float rightHeight,
                          float bottomY, float bottomWidth, float bottomHeight,
                          float drawerHeight) {

        private static Layout compute(StudioBottomBar bottomBar, boolean brushRailVisible) {
            imgui.ImGuiViewport main = ImGui.getMainViewport();
            float menuBarH = ImGui.getFrameHeight();
            float wsBarH = WorkspaceTabBar.HEIGHT;
            float statusBarH = 24.0f;
            // Small gap below the native menu bar so the workspace tab strip never looks tucked
            // under it.
            float menuBarGap = 3.0f;
            float x = main.getPosX();
            float y = main.getPosY() + menuBarH + menuBarGap;
            float width = Math.max(1.0f, main.getSizeX());
            float height = Math.max(1.0f, main.getSizeY() - menuBarH - menuBarGap);

            float leftRailW = brushRailVisible ? LeftBrushRail.RAIL_WIDTH : 0.0f;
            float rightWidth = Math.min(330.0f, Math.max(260.0f, width * 0.28f));
            float viewportX = x + leftRailW;
            float viewportWidth = Math.max(160.0f, width - leftRailW - rightWidth);
            float rightX = viewportX + viewportWidth;

            float availContentH = Math.max(100.0f, height - wsBarH - statusBarH);

            float drawerH = bottomBar != null ? bottomBar.currentHeight() : StudioBottomBar.COLLAPSED_HEIGHT;
            float contentH = Math.max(100.0f, availContentH - drawerH);

            float contentY = y + wsBarH;
            float bottomY = contentY + contentH;
            // The bottom drawer must stop where the right sidebar begins instead of running
            // underneath it; the right sidebar spans the full content height (viewport + drawer
            // rows combined) so it never looks cut short next to the drawer.
            float bottomWidth = Math.max(160.0f, width - rightWidth);

            return new Layout(x, y, width, height,
                    contentY, contentH,
                    viewportX, viewportWidth, contentH,
                    rightX, rightWidth, availContentH,
                    bottomY, bottomWidth, drawerH,
                    drawerH);
        }
    }
}
