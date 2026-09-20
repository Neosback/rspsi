package com.rspsi.studio;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.npc.NpcSpawn;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.plugin.EditorCommandRegistration;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.simulation.SimulationEngine;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.brush.StudioBrushManager;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.FloatingToolbar;
import com.rspsi.studio.ui.MinimapHudOverlay;
import com.rspsi.studio.ui.panels.MinimapPanel;
import com.rspsi.studio.ui.StudioBottomBar;
import com.rspsi.studio.ui.StudioRightSidebar;
import com.rspsi.studio.ui.StudioToolRail;
import com.rspsi.studio.ui.StudioMenuBar;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.StudioPanelManager;
import com.rspsi.studio.ui.WorkspaceTabBar;
import com.rspsi.studio.ui.hud.ViewportHudManager;
import com.rspsi.studio.ui.hud.TilePainterHud;
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

import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.DeleteObjectCommand;
import com.rspsi.editor.PlaceObjectCommand;
import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
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
    private TileCoordinate contextTile;

    private final EditorToolController toolController = new EditorToolController();
    private EditorInputRouter inputRouter;
    private EditorPluginLifecycleManager inputHost;

    private final StudioMenuBar menuBar = new StudioMenuBar();
    private final WorkspaceTabBar workspaceTabBar = new WorkspaceTabBar();
    private final StudioToolRail toolRail = new StudioToolRail();
    private final FloatingToolbar floatingToolbar = new FloatingToolbar();
    private final MinimapHudOverlay minimapHudOverlay = new MinimapHudOverlay();
    private final StudioRightSidebar rightSidebar = new StudioRightSidebar();
    private final StudioBottomBar bottomBar = new StudioBottomBar();
    private final StudioPanelManager panelManager = new StudioPanelManager();
    private final StudioPluginManager studioPluginManager = new StudioPluginManager();
    private final StudioBrushManager brushManager = new StudioBrushManager();
    private final ViewportHudManager hudManager = new ViewportHudManager();
    {
        studioPluginManager.setOwnedPanelSink(panelManager::register);
    }

    {
        minimapHudOverlay.setOnWorldMapClick(() -> panelManager.setActiveRightPanelId(MinimapPanel.ID));
        studioPluginManager.register(new TileInfoHudPlugin());
        studioPluginManager.register(new TilePainterHud());
    }

    private final PreferencesWindow preferencesWindow = new PreferencesWindow();
    private final PluginManagerWindow pluginManagerWindow = new PluginManagerWindow();
    private final NativeWorkspaceLayoutStore layoutStore = new NativeWorkspaceLayoutStore();
    private boolean layoutRestored;
    private boolean defaultToolActivated;

    private boolean commandPaletteOpen;
    private final ImString commandQuery = new ImString(128);
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

        Layout layout = Layout.compute(bottomBar, showLeftToolRail);

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
                hudManager);

        // 4. Left Tool Rail (TOOL_RAIL slot: Selection, Paint, Height, Path, Objects) - Optional toggle
        if (showLeftToolRail) {
            toolRail.render(panelContext, layout.x(), layout.contentY(), layout.contentHeight(),
                    toolId -> activateTool(pluginLifecycle, toolId), activeToolId);
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

        ImGui.begin(VIEWPORT_WINDOW, FIXED_VIEWPORT_FLAGS);
        if (plan == null) {
            ImGui.text(sceneStatus == null ? "Preparing scene..." : sceneStatus);
        } else {
            // Even with other planes visible ("show all levels"), clicks must only land on the
            // plane actually being edited - a tile on a plane rendered above/below is not a
            // valid pick just because it's visible.
            viewport.setPickPlaneRestriction(settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE));

            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()),
                    settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES),
                    new RenderConfigCompiler().compile(settings.snapshot()).presentation());

            // Feed real mouse input to the active EditorTool (selection, tile-painter brush,
            // height sculptor, etc). Must run before any other ImGui widget call this frame so
            // isItemHovered() still refers to the scene image.
            viewport.dispatchToolInput(toolController);

            viewport.renderOverlays(toolController.activeTool(), pluginLifecycle);
            studioPluginManager.renderOverlays(ImGui.getWindowDrawList(), panelContext);

            if (spawns != null && showServerSpawns) {
                renderServerSpawnOverlays(viewport, settings);
            }

            handleViewportDragDrop(viewport, settings, pluginLifecycle);
            handleViewportContextMenu(cache, viewport, settings, pluginLifecycle);

            // Circular OSRS Minimap HUD in the top-right corner of the viewport
            minimapHudOverlay.render(panelContext, layout.viewportX(), layout.contentY(), layout.viewportWidth(), layout.viewportHeight());

            // Managed HUD stack: plugins request quadrant slots instead of choosing pixels.
            hudManager.beginFrame(layout.viewportX(), layout.contentY(),
                    layout.viewportWidth(), layout.viewportHeight());
            studioPluginManager.renderHUDs(panelContext);

            // Floating Tool Rail (Frosted Acrylic Capsule)
            floatingToolbar.render(panelContext, layout.viewportX(), layout.contentY(),
                    toolId -> activateTool(pluginLifecycle, toolId), activeToolId);
        }
        ImGui.end();
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
        activeToolId = registrationId;
        // Tools with nothing to show in the shelf (e.g. Single/Multi Select, which report into
        // the Tile Inspector panel instead) auto-collapse the drawer rather than showing it empty.
        studioPluginManager.toolPlugin(registrationId)
                .filter(tool -> !tool.hasContextDrawerContent())
                .ifPresent(tool -> bottomBar.setDrawerOpen(false));
        if (inputRouter == null || pluginLifecycle == null || pluginLifecycle.host() == null) return;

        // Single Select / Multi Select are Studio-level presentation ids; both drive the one
        // real "selection.box" engine tool, distinguished by its Mode.
        String engineId = switch (registrationId) {
            case "selection.single", "selection.multi" -> "selection.box";
            default -> registrationId;
        };

        var registration = pluginLifecycle.host().registry().toolRegistrations().stream()
                .filter(tool -> engineId.equals(tool.id()))
                .findFirst()
                .orElse(null);
        if (registration == null) return;

        var tool = registration.factory().get();
        if (tool instanceof BoxSelectTool boxSelectTool) {
            boxSelectTool.setMode("selection.single".equals(registrationId)
                    ? BoxSelectTool.Mode.SINGLE : BoxSelectTool.Mode.MULTI);
        }
        toolController.activate(tool,
                new ToolContext(pluginLifecycle.host().context().session(),
                        pluginLifecycle.host().context().assets(), viewport));
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
            } else if (ImGui.isKeyPressed(ImGuiKey.E, false)) {
                activateTool(pluginLifecycle, "terrain.raise");
            } else if (ImGui.isKeyPressed(ImGuiKey.O, false)) {
                activateTool(pluginLifecycle, "object.place");
            } else if (ImGui.isKeyPressed(ImGuiKey.X, false) || ImGui.isKeyPressed(ImGuiKey.Delete, false)) {
                EditorSession s = session(pluginLifecycle);
                if (s != null) s.selection().clear();
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
                    if (s != null && s.world().contains(coord)) {
                        int type = settings.snapshot().get(EditorSettingKeys.OBJECT_TYPE);
                        int rot = settings.snapshot().get(EditorSettingKeys.OBJECT_ROTATION);
                        WorldObject worldObj = new WorldObject(droppedId, type, rot, coord.plane(), coord.x(), coord.y());
                        s.execute(new PlaceObjectCommand(worldObj, "Spawn Object #" + droppedId));
                    }
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

        // 1. Selection Options
        if (ImGui.menuItem("Select Tile")) {
            s.selection().clear();
            s.selection().select(contextTile);
        }
        if (ImGui.menuItem("Add to Selection")) {
            s.selection().select(contextTile);
        }
        if (ImGui.menuItem("Clear Selection")) {
            s.selection().clear();
        }

        // 2. Objects on Tile
        var tile = s.world().contains(contextTile) ? s.world().tile(contextTile) : null;
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
            if (TilePainterPalette.INSTANCE != null && s.world().contains(contextTile)) {
                TilePainterPalette.INSTANCE.sampleTile(s, contextTile);
            }
        }
        if (ImGui.menuItem("Paint Tile with Active Brush")) {
            if (TilePainterPalette.INSTANCE != null) {
                CompositeTilePainterTool tool = new CompositeTilePainterTool();
                tool.setApplyUnderlay(TilePainterPalette.INSTANCE.applyUnderlay());
                tool.setUnderlayId(TilePainterPalette.INSTANCE.underlayId());
                tool.setApplyOverlay(TilePainterPalette.INSTANCE.applyOverlay());
                tool.setOverlayId(TilePainterPalette.INSTANCE.overlayId());
                tool.setApplyShape(TilePainterPalette.INSTANCE.applyShape());
                tool.setShape(TilePainterPalette.INSTANCE.shape());
                tool.setApplyRotation(TilePainterPalette.INSTANCE.applyRotation());
                tool.setRotation(TilePainterPalette.INSTANCE.rotation());
                tool.setApplyFlags(TilePainterPalette.INSTANCE.applyFlags());
                tool.setFlags(TilePainterPalette.INSTANCE.flags());
                tool.setApplyHeight(TilePainterPalette.INSTANCE.applyHeight());
                tool.setHeight(TilePainterPalette.INSTANCE.height());
                tool.applyToCoordinates(Set.of(contextTile), s);
            }
        }

        // 4. Quick Height Actions
        if (snap != null) {
            ImGui.separator();
            ImGui.textDisabled("Height:");
            if (ImGui.menuItem("Flatten Tile")) {
                int avg = (snap.southWestHeight() + snap.southEastHeight() + snap.northEastHeight() + snap.northWestHeight()) / 4;
                TileSnapshot after = new TileSnapshot(avg, avg, avg, avg,
                        snap.underlayId(), snap.overlayId(), snap.overlayShape(), snap.overlayRotation(),
                        snap.flags(), snap.objects());
                s.execute(new SetTileCommand(contextTile, snap, after, "Flatten tile"));
            }
            if (ImGui.menuItem("Raise (+32)")) {
                TileSnapshot after = new TileSnapshot(snap.southWestHeight() + 32, snap.southEastHeight() + 32,
                        snap.northEastHeight() + 32, snap.northWestHeight() + 32,
                        snap.underlayId(), snap.overlayId(), snap.overlayShape(), snap.overlayRotation(),
                        snap.flags(), snap.objects());
                s.execute(new SetTileCommand(contextTile, snap, after, "Raise tile"));
            }
            if (ImGui.menuItem("Lower (-32)")) {
                TileSnapshot after = new TileSnapshot(snap.southWestHeight() - 32, snap.southEastHeight() - 32,
                        snap.northEastHeight() - 32, snap.northWestHeight() - 32,
                        snap.underlayId(), snap.overlayId(), snap.overlayShape(), snap.overlayRotation(),
                        snap.flags(), snap.objects());
                s.execute(new SetTileCommand(contextTile, snap, after, "Lower tile"));
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
                s.execute(new SetTileCommand(contextTile, snap, after, "Toggle blocked flag"));
            }
            boolean bridge = (snap.flags() & 0x02) != 0;
            if (ImGui.menuItem((bridge ? "[x] " : "[ ] ") + "Bridge Tile (0x02)")) {
                int nextFlags = snap.flags() ^ 0x02;
                TileSnapshot after = new TileSnapshot(snap.southWestHeight(), snap.southEastHeight(),
                        snap.northEastHeight(), snap.northWestHeight(),
                        snap.underlayId(), snap.overlayId(), snap.overlayShape(), snap.overlayRotation(),
                        nextFlags, snap.objects());
                s.execute(new SetTileCommand(contextTile, snap, after, "Toggle bridge flag"));
            }
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
        ImGui.setNextWindowPos(center.x, center.y - 120.0f, ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(520.0f, 320.0f, ImGuiCond.Appearing);

        if (!ImGui.beginPopupModal("CommandPaletteModal", null, ImGuiWindowFlags.NoDecoration)) return;
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0);

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 8.0f, 6.0f);
        ImGui.inputTextWithHint("##cmd-query", StudioIcons.SEARCH + "  Type a tool, command, or region ID (e.g. 50,50)...", commandQuery, ImGuiInputTextFlags.None);
        ImGui.popStyleVar();

        ImGui.separator();

        String query = commandQuery.get().toLowerCase().trim();
        ImGui.beginChild("palette-results", 0.0f, -32.0f, false);
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var registry = pluginLifecycle.host().registry();
            boolean any = false;
            for (EditorToolRegistration tool : registry.toolRegistrations()) {
                if (!query.isBlank() && !tool.label().toLowerCase().contains(query)
                        && !tool.id().toLowerCase().contains(query)) continue;
                any = true;
                if (ImGui.selectable(StudioIcons.BRUSH + "  " + tool.label() + "  ##tool-" + tool.id())) {
                    activateTool(pluginLifecycle, tool.id());
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(tool.id());
            }
            for (EditorCommandRegistration command : registry.commandRegistrations()) {
                if (!query.isBlank() && !command.label().toLowerCase().contains(query)
                        && !command.id().toLowerCase().contains(query)) continue;
                any = true;
                if (ImGui.selectable(StudioIcons.TERMINAL + "  " + command.label() + "  ##command-" + command.id())) {
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
            if (!any) ImGui.textDisabled("No matching tools or commands.");
        } else {
            ImGui.textDisabled("Plugin host unavailable.");
        }
        ImGui.endChild();

        ImGui.separator();
        if (ImGui.button(StudioIcons.CLOSE + " Close##cmd-close", 80.0f, 22.0f) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
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
        if (saved != null) bottomBar.setDrawerOpen(saved.bottomDrawerVisible());
    }

    private void resetLayout() {
        layoutStore.reset();
        bottomBar.setDrawerOpen(true);
        studioPluginManager.setEnabled(TileInfoHudPlugin.ID, true);
        showLeftToolRail = false;
        floatingToolbar.resetPosition();
    }

    public void close() {
        if (!layoutRestored) return;
        layoutStore.save(new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION, "", bottomBar.isDrawerOpen()));
    }

    private record Layout(float x, float y, float width, float height,
                          float contentY, float contentHeight,
                          float viewportX, float viewportWidth, float viewportHeight,
                          float rightX, float rightWidth, float rightHeight,
                          float bottomY, float bottomWidth, float bottomHeight,
                          float drawerHeight) {

        private static Layout compute(StudioBottomBar bottomBar, boolean showLeftToolRail) {
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

            float leftRailW = showLeftToolRail ? StudioToolRail.RAIL_WIDTH : 0.0f;
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
