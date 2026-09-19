package com.rspsi.studio;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.plugin.EditorCommandRegistration;
import com.rspsi.editor.plugin.EditorNotificationService;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.plugin.EditorTaskService;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.selection.FragmentSelection;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import com.rspsi.editor.selection.VertexSelection;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSliderFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.PickResult;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import com.rspsi.editor.knowledge.Evidence;
import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.KnowledgeSnapshot;
import com.rspsi.editor.knowledge.MetricKey;
import com.rspsi.editor.knowledge.RegionProfile;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.knowledge.WorldKnowledgeService;
import com.rspsi.osrs.rules.RuleTrace;
import com.rspsi.editor.integration.npc.NpcSpawn;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ContentReference;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.simulation.SimulationEngine;
import com.rspsi.editor.symbols.SymbolNamespace;
import com.rspsi.editor.symbols.SymbolService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Viewport-first native Map Editor shell over the neutral editor contracts.
 *
 * <p>Every control in this shell is bound to a real contract: registered
 * tools ({@code EditorToolRegistration}), tool settings
 * ({@code EditorSetting}), the command history ({@code CommandHistory}),
 * the selection model, task/notification services, or renderer settings that
 * {@code RenderConfigCompiler} consumes. Anything that would be a mock is
 * deliberately absent — no disabled buttons, no placeholder panels.</p>
 */
public final class MapEditorView {
    private static final String TOOL_RAIL_WINDOW = "Tool Rail";
    private static final String VIEWPORT_WINDOW = "Viewport";
    private static final String RIGHT_PANEL_WINDOW = "Tool Options";
    private static final String BOTTOM_WINDOW = "Studio Drawer";
    private static final float STATUS_BAR_HEIGHT = 26.0f;

    /**
     * Shared flags for every panel in the fixed frame. Nothing here moves,
     * resizes, docks, or persists itself: the frame owns geometry, so a
     * panel that remembered its own would only be able to disagree with it.
     */
    private static final int FIXED_PANEL_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoSavedSettings;

    /** Rails are icon strips; they never scroll. */
    private static final int LOCKED_RAIL_FLAGS = FIXED_PANEL_FLAGS | ImGuiWindowFlags.NoScrollbar;

    /** The map canvas owns the centre and paints no panel background. */
    private static final int FIXED_VIEWPORT_FLAGS = FIXED_PANEL_FLAGS
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoBackground;

    // Panel sizes are in logical points so the shell stays readable at any
    // window size rather than scaling every panel with the display.
    private static final float TOOL_RAIL_WIDTH = 78.0f;
    private static final float RIGHT_PANEL_WIDTH = 340.0f;
    private static final float DRAWER_HEIGHT = 200.0f;
    private static final float MIN_VIEWPORT_WIDTH = 160.0f;
    private static final float MIN_VIEWPORT_HEIGHT = 120.0f;

    /** Dear ImGui asserts that integer slider bounds stay inside +/- INT_MAX/2. */
    private static final long SLIDER_BOUND = Integer.MAX_VALUE / 2;

    private static final String ICON_SELECT = StudioIcons.SELECT;
    private static final String ICON_TERRAIN = StudioIcons.TERRAIN;
    private static final String ICON_OBJECT = StudioIcons.OBJECT;

    /**
     * The tool rail. Each entry maps 1:1 to a registered plugin tool
     * family; the id is the registration prefix resolved against the live
     * registry at activation time, so a missing registration simply
     * disables the button instead of lying about being usable.
     */
    private record RailTool(String id, String icon, String label, String shortcut,
                            String[] toolIds) { }

    private static final RailTool[] RAIL_TOOLS = {
            new RailTool("select", ICON_SELECT, "Select & transform", "1",
                    new String[]{"selection.box", "selection.lasso", "selection.move",
                            "selection.rotate", "selection.duplicate", "selection.replace"}),
            new RailTool("terrain", StudioIcons.TILE, "Tile Painter", "2",
                    new String[]{"terrain.paint-overlay", "terrain.paint-underlay",
                            "terrain.flags"}),
            new RailTool("objects", ICON_OBJECT, "Objects", "3",
                    new String[]{"object.place", "object.move", "object.rotate",
                            "object.duplicate", "object.delete"}),
            new RailTool("height", StudioIcons.HEIGHT, "Height Painter", "4",
                    new String[]{"terrain.raise", "terrain.lower", "terrain.flatten",
                            "terrain.smooth", "terrain.ramp"}),
            new RailTool("water", StudioIcons.WATER, "Water & Rivers", "5",
                    new String[]{"terrain.paint-overlay", "terrain.paint-underlay"}),
    };

    private static final String[] OSRS_SHAPE_NAMES = {
            "Full (0)", "Diagonal (1)", "Left (2)", "Right (3)",
            "Corner TL (4)", "Corner TR (5)", "Corner BR (6)", "Corner BL (7)",
            "Inv TL (8)", "Inv TR (9)", "Inv BR (10)", "Inv BL (11)"
    };

    private record Swatch(String name, boolean overlay, int id, float r, float g, float b) {}
    private static final Swatch[] RECENT_SWATCHES = {
            new Swatch("Grass Light", false, 42, 0.35f, 0.58f, 0.24f),
            new Swatch("Grass Dark", false, 28, 0.22f, 0.44f, 0.16f),
            new Swatch("Stone Path", true, 11, 0.55f, 0.53f, 0.50f),
            new Swatch("Cobble", true, 15, 0.45f, 0.44f, 0.48f),
            new Swatch("Sand", false, 25, 0.76f, 0.70f, 0.45f),
            new Swatch("Dirt", false, 8, 0.48f, 0.35f, 0.22f),
            new Swatch("Water", false, 32, 0.20f, 0.45f, 0.75f),
            new Swatch("Wood Planks", true, 18, 0.58f, 0.38f, 0.20f)
    };

    private String activeRailId = "select";
    private String activeToolId = "selection.box";
    private NativeSceneViewport viewport;
    // Assigned at the top of every render pass. It cannot be initialised
    // here: this view is constructed before ImGui has a context, and
    // Layout.compute reads the main viewport.
    private Layout layout;
    private boolean layoutRestored;
    private final NativeWorkspaceLayoutStore layoutStore = new NativeWorkspaceLayoutStore();
    private final EditorToolController toolController = new EditorToolController();
    private EditorInputRouter inputRouter;
    private EditorPluginLifecycleManager inputHost;
    private boolean bottomDrawerVisible = true;
    private boolean commandPaletteOpen;
    private final ImString commandQuery = new ImString(128);
    private final PreferencesWindow preferencesWindow = new PreferencesWindow();
    private final PluginManagerWindow pluginManagerWindow = new PluginManagerWindow();
    private int drawerTab;
    private int rightPanelTab;

    // Tile Painter drawer state
    private boolean tilePainterIsOverlay = true;
    private int tilePainterMaterialId = 11;
    private int tilePainterBrushSize = 3;
    private float tilePainterFalloff = 0.0f;
    private boolean tilePainterBlendEdges = true;
    private boolean tilePainterAutoSmooth = false;
    private int tilePainterShape = 0;
    private int tilePainterRotation = 0;
    private boolean tilePainterRandomizeRot = false;
    private boolean tilePainterMatchHeight = false;
    private boolean tilePainterApplyAllPlanes = false;

    // Asset Browser state
    private final ImString assetSearchQuery = new ImString(64);
    private int assetFilterCategory = 0;
    private final ImString customTagInput = new ImString(32);

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
                       ServerIntegrationService integrations) {
        this.openInterfaceStudio = openInterfaceStudio;
        this.openObjectStudio = openObjectStudio;
        this.openIntegrationCenter = openIntegrationCenter;
        this.simulation = simulation;
        this.symbols = symbols;
        this.references = references;
        this.spawns = spawns;
        this.integrations = integrations;
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
        routeRailShortcuts(pluginLifecycle);
        routeSessionShortcuts(pluginLifecycle);
        renderMainMenu(cache, openDashboard, settings, pluginLifecycle);
        layout = Layout.compute(bottomDrawerVisible);
        renderToolRail(pluginLifecycle, viewport);
        renderViewport(cache, plan, viewport, sceneStatus, settings, pluginLifecycle);
        renderRightPanel(cache, settings, pluginLifecycle);
        renderBottomDrawer(cache, pluginLifecycle);
        renderStatusBar(cache, plan, viewport, dirty, settings, pluginLifecycle);
        renderCommandPalette(pluginLifecycle);
        preferencesWindow.render(settings, pluginLifecycle != null && pluginLifecycle.host() != null
                ? pluginLifecycle.host().context().settingsService() : null);
        pluginManagerWindow.render(pluginLifecycle);
    }

    private void handleGlobalShortcuts() {
        var io = ImGui.getIO();
        if (!io.getWantTextInput() && (io.getKeyCtrl() || io.getKeySuper())
                && ImGui.isKeyPressed(ImGuiKey.Comma, false)) {
            preferencesWindow.toggle();
        }
    }

    // ------------------------------------------------------------------
    // Menu bar
    // ------------------------------------------------------------------

    private void renderMainMenu(LoadedOsrsCacheSession cache, Runnable openDashboard,
                                SettingsStore settings,
                                EditorPluginLifecycleManager pluginLifecycle) {
        if (!ImGui.beginMainMenuBar()) return;
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("Dashboard")) openDashboard.run();
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Edit")) {
            EditorSession session = session(pluginLifecycle);
            boolean canUndo = session != null && session.history().canUndo();
            boolean canRedo = session != null && session.history().canRedo();
            if (ImGui.menuItem("Undo", "Ctrl+Z", false, canUndo)) undo(pluginLifecycle);
            if (ImGui.menuItem("Redo", "Ctrl+Shift+Z", false, canRedo)) redo(pluginLifecycle);
            ImGui.separator();
            if (ImGui.menuItem("Reset layout")) resetLayout();
            if (ImGui.menuItem("Utility drawer", "Ctrl+Space", bottomDrawerVisible)) {
                bottomDrawerVisible = !bottomDrawerVisible;
            }
            ImGui.separator();
            if (ImGui.menuItem("Preferences...", "Ctrl+,", preferencesWindow.isOpen())) {
                preferencesWindow.toggle();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Tools")) {
            for (RailTool tool : RAIL_TOOLS) {
                if (ImGui.menuItem(tool.label(), tool.shortcut(),
                        tool.id().equals(activeRailId),
                        firstAvailableTool(pluginLifecycle, tool) != null)) {
                    activateRail(pluginLifecycle, tool.id());
                }
            }
            ImGui.separator();
            if (ImGui.menuItem("Command palette", "Ctrl+P")) {
                commandPaletteOpen = true;
                commandQuery.clear();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("View")) {
            visibilityToggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain");
            visibilityToggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Objects");
            visibilityToggle(settings, RenderSettingKeys.WALLS_VISIBLE, "Walls");
            visibilityToggle(settings, RenderSettingKeys.WALL_DECORATIONS_VISIBLE, "Wall decorations");
            visibilityToggle(settings, RenderSettingKeys.GROUND_OBJECTS_VISIBLE, "Ground objects");
            visibilityToggle(settings, RenderSettingKeys.GROUND_DECORATIONS_VISIBLE, "Ground decorations");
            visibilityToggle(settings, RenderSettingKeys.ROOFS_VISIBLE, "Roofs");
            visibilityToggle(settings, RenderSettingKeys.BRIDGE_TILES_VISIBLE, "Bridge tiles");
            visibilityToggle(settings, RenderSettingKeys.HIDDEN_TILES_VISIBLE, "Hidden tiles");
            visibilityToggle(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision overlay");
            ImGui.separator();
            visibilityToggle(settings, RenderSettingKeys.WIREFRAME, "Wireframe");
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Cache")) {
            ImGui.menuItem("Revision " + cache.identity().revision(), null, true, false);
            if (cache.identity().subRevision() != null) {
                ImGui.menuItem("Sub-revision " + cache.identity().subRevision(), null, true, false);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Server")) {
            if (openIntegrationCenter != null) {
                if (ImGui.menuItem("Integration Center...")) openIntegrationCenter.run();
            }
            if (integrations != null && integrations.isConnected()) {
                var session = integrations.activeSession().get();
                ImGui.textDisabled("Connected: " + session.provider().name());
                if (ImGui.menuItem("Disconnect Server Project")) {
                    integrations.disconnect();
                }
            } else {
                ImGui.textDisabled("No server project connected");
            }
            ImGui.separator();
            if (ImGui.menuItem("Show Server NPC Spawns", null, showServerSpawns)) {
                showServerSpawns = !showServerSpawns;
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Help")) {
            ImGui.menuItem("OpenRune Studio · Map Editor", null, true, false);
            ImGui.separator();
            if (ImGui.menuItem("Plugins...", null, pluginManagerWindow.isOpen())) {
                pluginManagerWindow.toggle();
            }
            ImGui.endMenu();
        }

        ImGui.sameLine(0.0f, 16.0f);
        renderWorkspaceTabs(openDashboard);

        ImGui.sameLine(0.0f, 14.0f);
        if (ImGui.button("Search  Ctrl+P##global-search")) {
            commandPaletteOpen = true;
            commandQuery.clear();
        }

        float rightMargin = 260.0f;
        float avail = ImGui.getWindowWidth();
        ImGui.setCursorPosX(Math.max(ImGui.getCursorPosX(), avail - rightMargin));
        StudioWidgets.badge("OpenGL 3.3", 0.18f, 0.26f, 0.38f);
        ImGui.sameLine(0.0f, 6.0f);
        StudioWidgets.badge("Cache: OSRS (" + cache.identity().revision() + ")", 0.39f, 0.33f, 0.20f);

        ImGui.endMainMenuBar();
    }

    private void renderWorkspaceTabs(Runnable openDashboard) {
        String[] workspaces = {"Dashboard", "Map Editor", "Object Studio", "Interface Studio"};
        int activeIndex = 1; // Map Editor is active
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10.0f, 3.0f);
        for (int i = 0; i < workspaces.length; i++) {
            boolean isActive = (i == activeIndex);
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.18f, 0.38f, 0.65f, 1.0f));
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 1.0f));
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.13f, 0.15f, 0.18f, 0.85f));
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(0.65f, 0.68f, 0.75f, 1.0f));
            }
            if (ImGui.button(workspaces[i] + "##ws-" + i)) {
                if (i == 0 && openDashboard != null) {
                    openDashboard.run();
                } else if (i == 2 && openObjectStudio != null) {
                    openObjectStudio.run();
                } else if (i == 3 && openInterfaceStudio != null) {
                    openInterfaceStudio.run();
                }
            }
            if (i > 1 && ImGui.isItemHovered()) {
                ImGui.setTooltip("Switch workspace: " + workspaces[i]);
            }
            ImGui.popStyleColor(2);
            ImGui.sameLine();
        }
        ImGui.popStyleVar(2);
    }

    private void visibilityToggle(SettingsStore settings, SettingKey<Boolean> key, String label) {
        ImBoolean value = new ImBoolean(settings.snapshot().get(key));
        if (ImGui.menuItem(label, null, value.get())) settings.set(key, !value.get());
    }

    // ------------------------------------------------------------------
    // Docking scaffold
    // ------------------------------------------------------------------

    /**
     * The shell is a fixed frame, not a dockspace.
     *
     * <p>This deliberately replaces an ImGui dockspace whose node tree was
     * persisted to disk. That arrangement could not survive the shell
     * changing: a saved layout pinned windows by name, so when panels were
     * renamed or removed the restored tree still referenced dead windows
     * ("Activity Rail", "Inspector / Palette") while the live ones had no
     * node at all - and because a non-blank saved ini also suppressed
     * rebuilding the default layout, the broken arrangement was reloaded on
     * every launch and never healed.</p>
     *
     * <p>Every panel here is permanent furniture, so there is nothing to
     * persist and nothing to tear off. Rectangles are recomputed from the
     * main viewport each frame, which makes the layout correct by
     * construction at any window size and after any change to the panel
     * set.</p>
     */
    private record Layout(float x, float y, float width, float height,
                          float railX, float railWidth,
                          float rightX, float rightWidth,
                          float centerX, float centerWidth,
                          float viewportY, float viewportHeight,
                          float drawerY, float drawerHeight) {

        private static Layout compute(boolean drawerVisible) {
            imgui.ImGuiViewport main = ImGui.getMainViewport();
            float menuBar = ImGui.getFrameHeight();
            float x = main.getPosX();
            float y = main.getPosY() + menuBar;
            float width = Math.max(1.0f, main.getSizeX());
            float height = Math.max(1.0f, main.getSizeY() - menuBar - STATUS_BAR_HEIGHT);

            // Rails and the inspector are sized in points, not ratios, so
            // they stay legible on a small window and do not eat the canvas
            // on a large one. The viewport takes whatever is left.
            float railWidth = TOOL_RAIL_WIDTH;
            float rightWidth = Math.min(RIGHT_PANEL_WIDTH, Math.max(0.0f, width * 0.4f));
            float centerWidth = Math.max(MIN_VIEWPORT_WIDTH, width - railWidth - rightWidth);
            float drawerHeight = drawerVisible
                    ? Math.min(DRAWER_HEIGHT, Math.max(0.0f, height * 0.5f)) : 0.0f;
            float viewportHeight = Math.max(MIN_VIEWPORT_HEIGHT, height - drawerHeight);

            return new Layout(x, y, width, height,
                    x, railWidth,
                    x + railWidth + centerWidth, rightWidth,
                    x + railWidth, centerWidth,
                    y, viewportHeight,
                    y + viewportHeight, drawerHeight);
        }
    }

    /** Places the next window at an exact rectangle of the fixed frame. */
    private static void placeWindow(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
    }

    private void restoreLayout() {
        if (layoutRestored) return;
        layoutRestored = true;
        NativeWorkspaceLayoutStore.State saved = layoutStore.load();
        // Only the drawer toggle is restored. The window arrangement itself
        // is derived, never loaded, so a stale file cannot deform the shell.
        if (saved != null) bottomDrawerVisible = saved.bottomDrawerVisible();
    }

    private void resetLayout() {
        layoutStore.reset();
        bottomDrawerVisible = true;
    }


    // ------------------------------------------------------------------
    // Session plumbing
    // ------------------------------------------------------------------

    private static EditorSession session(EditorPluginLifecycleManager pluginLifecycle) {
        return pluginLifecycle != null && pluginLifecycle.host() != null
                ? pluginLifecycle.host().context().session() : null;
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

    private void undo(EditorPluginLifecycleManager pluginLifecycle) {
        EditorSession session = session(pluginLifecycle);
        if (session != null) session.undo();
    }

    private void redo(EditorPluginLifecycleManager pluginLifecycle) {
        EditorSession session = session(pluginLifecycle);
        if (session != null) session.redo();
    }

    private void routeSessionShortcuts(EditorPluginLifecycleManager pluginLifecycle) {
        var io = ImGui.getIO();
        if (io.getWantTextInput()) return;
        boolean ctrl = io.getKeyCtrl() || io.getKeySuper();
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.Z, false)) {
            if (io.getKeyShift()) redo(pluginLifecycle);
            else undo(pluginLifecycle);
        } else if (ctrl && ImGui.isKeyPressed(ImGuiKey.Y, false)) {
            redo(pluginLifecycle);
        } else if (ctrl && ImGui.isKeyPressed(ImGuiKey.S, false)) {
            EditorSession session = session(pluginLifecycle);
            if (session != null && session.canSave()) session.save();
        }
    }

    private void routeRailShortcuts(EditorPluginLifecycleManager pluginLifecycle) {
        if (ImGui.getIO().getWantTextInput()) return;
        if (ImGui.isKeyPressed(ImGuiKey._1, false)) activateRail(pluginLifecycle, "select");
        else if (ImGui.isKeyPressed(ImGuiKey._2, false)) activateRail(pluginLifecycle, "terrain");
        else if (ImGui.isKeyPressed(ImGuiKey._3, false)) activateRail(pluginLifecycle, "objects");
        else if (ImGui.isKeyPressed(ImGuiKey._4, false)) activateRail(pluginLifecycle, "height");
        else if (ImGui.isKeyPressed(ImGuiKey._5, false)) activateRail(pluginLifecycle, "water");
    }

    private static String firstAvailableTool(EditorPluginLifecycleManager pluginLifecycle,
                                             RailTool railTool) {
        if (pluginLifecycle == null || pluginLifecycle.host() == null) return null;
        var registered = pluginLifecycle.host().registry().toolRegistrations();
        for (String candidate : railTool.toolIds()) {
            for (EditorToolRegistration registration : registered) {
                if (candidate.equals(registration.id())) return candidate;
            }
        }
        return null;
    }

    private void activateRail(EditorPluginLifecycleManager pluginLifecycle, String railId) {
        RailTool railTool = null;
        for (RailTool candidate : RAIL_TOOLS) {
            if (candidate.id().equals(railId)) railTool = candidate;
        }
        if (railTool == null) return;
        String toolId = firstAvailableTool(pluginLifecycle, railTool);
        if (toolId == null) return; // no live registration: do not pretend
        activeRailId = railId;
        activateTool(pluginLifecycle, toolId);
    }

    private void activateTool(EditorPluginLifecycleManager pluginLifecycle,
                              NativeSceneViewport viewport, String registrationId) {
        activeToolId = registrationId;
        if (inputRouter == null || pluginLifecycle == null || pluginLifecycle.host() == null) return;
        var registration = pluginLifecycle.host().registry().toolRegistrations().stream()
                .filter(tool -> registrationId.equals(tool.id()))
                .findFirst()
                .orElse(null);
        if (registration == null) return;
        toolController.activate(registration.factory().get(),
                new ToolContext(pluginLifecycle.host().context().session(),
                        pluginLifecycle.host().context().assets(), viewport));
    }

    /**
     * Activation from the rail or the command palette. These call sites have
     * no viewport argument of their own, so they hand over the one this view
     * is currently rendering - ToolContext requires a real viewport, and
     * passing null here made every rail click throw.
     */
    private void activateTool(EditorPluginLifecycleManager pluginLifecycle,
                              String registrationId) {
        activateTool(pluginLifecycle, viewport, registrationId);
    }

    // ------------------------------------------------------------------
    // Tool rail (three real tool families)
    // ------------------------------------------------------------------

    private void renderToolRail(EditorPluginLifecycleManager pluginLifecycle,
                                NativeSceneViewport viewport) {
        placeWindow(layout.railX(), layout.viewportY(), layout.railWidth(),
                layout.viewportHeight() + layout.drawerHeight());
        ImGui.begin(TOOL_RAIL_WINDOW, LOCKED_RAIL_FLAGS);
        ImGui.dummy(0.0f, 4.0f);
        for (RailTool tool : RAIL_TOOLS) {
            boolean available = firstAvailableTool(pluginLifecycle, tool) != null;
            if (!available) {
                ImGui.beginDisabled(true);
            }
            if (StudioWidgets.railButton("rail-" + tool.id(), tool.icon, tool.label,
                    tool.id().equals(activeRailId), tool.shortcut())) {
                activateRail(pluginLifecycle, tool.id());
            }
            if (!available) {
                ImGui.endDisabled();
            }
        }
        float availY = ImGui.getContentRegionAvailY();
        if (availY > 44.0f) {
            ImGui.dummy(0.0f, availY - 44.0f);
            if (StudioWidgets.railButton("rail-settings", StudioIcons.SETTINGS, "Preferences",
                    preferencesWindow.isOpen(), "Ctrl+,")) {
                preferencesWindow.toggle();
            }
        }
        ImGui.end();
    }

    // ------------------------------------------------------------------
    // Viewport
    // ------------------------------------------------------------------

    private void renderViewport(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                NativeSceneViewport viewport, String sceneStatus,
                                SettingsStore settings,
                                EditorPluginLifecycleManager pluginLifecycle) {
        placeWindow(layout.centerX(), layout.viewportY(),
                layout.centerWidth(), layout.viewportHeight());
        ImGui.begin(VIEWPORT_WINDOW, FIXED_VIEWPORT_FLAGS);
        renderViewportToolbar(settings);
        ImGui.separator();
        if (plan == null) {
            ImGui.text(sceneStatus == null ? "Preparing scene..." : sceneStatus);
        } else {
            var stats = viewport.statistics();
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.textDisabled(stats.renderedTriangles() + " tris  ·  " + stats.drawCalls()
                    + " draws  ·  GL " + stats.firstGlError()
                    + (stats.missingTextures() == 0 ? "" : "  ·  missing tex " + stats.missingTextures()));
            ImGui.popFont();
            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()),
                    settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES),
                    new RenderConfigCompiler().compile(settings.snapshot()).presentation());
            viewport.renderOverlays(toolController.activeTool(), pluginLifecycle);
            if (spawns != null && showServerSpawns) {
                renderServerSpawnOverlays(viewport, settings);
            }
            renderViewportHudCards(cache, viewport, pluginLifecycle, settings);
        }
        ImGui.end();
    }

    private void renderServerSpawnOverlays(NativeSceneViewport viewport, SettingsStore settings) {
        if (spawns == null || viewport == null) return;
        ViewportOverlayDraw draw = viewport.createOverlayDraw();
        int activePlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        int camTileX = Math.max(0, (int) (viewport.navigation().camera().x() / 128.0f));
        int camTileY = Math.max(0, (int) (viewport.navigation().camera().z() / 128.0f));
        List<NpcSpawn> visibleSpawns = spawns.spawns(activePlane, camTileX - 32, camTileY - 32, camTileX + 32, camTileY + 32);
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

    /** Plane selector + scene-visibility toggles — every one a real setting. */
    private void renderViewportToolbar(SettingsStore settings) {
        SettingsSnapshot snapshot = settings.snapshot();
        ImGui.textDisabled("PLANE");
        ImGui.sameLine();
        int plane = snapshot.get(RenderSettingKeys.ACTIVE_PLANE);
        for (int candidate = 0; candidate < 4; candidate++) {
            ImGui.sameLine();
            if (ImGui.radioButton(String.valueOf(candidate), plane == candidate)) {
                settings.set(RenderSettingKeys.ACTIVE_PLANE, candidate);
            }
        }
        ImGui.sameLine(0.0f, 14.0f);
        var selection = snapshot.get(RenderSettingKeys.PLANE_SELECTION);
        ImGui.textDisabled("SHOW");
        ImGui.sameLine();
        if (modeButton("All planes", selection
                == com.rspsi.editor.render.SceneVisibilityPolicy.PlaneSelection.ALL)) {
            settings.set(RenderSettingKeys.PLANE_SELECTION,
                    com.rspsi.editor.render.SceneVisibilityPolicy.PlaneSelection.ALL);
        }
        ImGui.sameLine();
        if (modeButton("Authored", selection
                == com.rspsi.editor.render.SceneVisibilityPolicy.PlaneSelection.AUTHORED_PLANE)) {
            settings.set(RenderSettingKeys.PLANE_SELECTION,
                    com.rspsi.editor.render.SceneVisibilityPolicy.PlaneSelection.AUTHORED_PLANE);
        }
        ImGui.sameLine();
        if (modeButton("Effective", selection
                == com.rspsi.editor.render.SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE)) {
            settings.set(RenderSettingKeys.PLANE_SELECTION,
                    com.rspsi.editor.render.SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE);
        }
        ImGui.sameLine(0.0f, 14.0f);
        ImGui.textDisabled("LAYER");
        ImGui.sameLine();
        compactToggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain");
        ImGui.sameLine();
        compactToggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Objects");
        ImGui.sameLine();
        compactToggle(settings, RenderSettingKeys.ROOFS_VISIBLE, "Roofs");
        ImGui.sameLine();
        compactToggle(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision");
        ImGui.sameLine(0.0f, 14.0f);
        compactToggle(settings, RenderSettingKeys.WIREFRAME, "Wireframe");
        renderCullControl();
    }

    /**
     * Live back-face culling selector.
     *
     * <p>Thin decorations draw both of their skins with culling off, which
     * is what makes a hanging banner shimmer where its cloth and backing
     * nearly meet. Culling fixes that only with the correct front-face
     * winding, and the winding this projection produces has never been
     * verified - the value BackfacePolicy documents turned the scene inside
     * out. Rather than keep guessing, both windings are selectable here so
     * the right one can be identified by looking at the scene.</p>
     */
    private void renderCullControl() {
        if (viewport == null) return;
        ImGui.sameLine(0.0f, 14.0f);
        ImGui.textDisabled("BACKFACES");
        String[] labels = {"Off", "Cull CCW", "Cull CW"};
        for (int mode = 0; mode < labels.length; mode++) {
            ImGui.sameLine();
            if (modeButton(labels[mode], viewport.cullMode() == mode)) {
                viewport.setCullMode(mode);
            }
        }
    }

    private boolean modeButton(String label, boolean selected) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 3.0f);
        boolean clicked = ImGui.button(label + "##mode-" + label, 0.0f, 0.0f);
        ImGui.popStyleVar();
        if (selected) {
            ImVec2 min = ImGui.getItemRectMin();
            ImVec2 max = ImGui.getItemRectMax();
            ImGui.getWindowDrawList().addRectFilled(min.x, max.y - 2.0f, max.x, max.y,
                    ImGui.getColorU32(0.24f, 0.56f, 0.90f, 1.0f), 1.0f);
        }
        return clicked;
    }

    private void compactToggle(SettingsStore settings, SettingKey<Boolean> key, String label) {
        boolean value = settings.snapshot().get(key);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, value
                ? ImGui.getColorU32(0.62f, 0.76f, 0.94f, 1.0f)
                : ImGui.getColorU32(0.52f, 0.55f, 0.62f, 1.0f));
        if (ImGui.button(label + "##vt-" + label)) settings.set(key, !value);
        ImGui.popStyleColor();
    }

    private void renderViewportHudCards(LoadedOsrsCacheSession cache,
                                        NativeSceneViewport viewport,
                                        EditorPluginLifecycleManager pluginLifecycle,
                                        SettingsStore settings) {
        float windowW = ImGui.getWindowWidth();
        float windowH = ImGui.getWindowHeight();
        float margin = 14.0f;

        // Top-Right Floating HUD: Simulation Debugger
        if (simulation != null) {
            float simW = 340.0f;
            float simH = 92.0f;
            if (windowW > simW + margin * 2.0f) {
                ImGui.setCursorPos(windowW - simW - margin, margin + 28.0f);
                ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.getColorU32(0.08f, 0.10f, 0.13f, 0.88f));
                ImGui.pushStyleColor(ImGuiCol.Border, ImGui.getColorU32(0.24f, 0.28f, 0.36f, 0.75f));
                ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
                ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.0f);
                ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 6.0f);
                if (ImGui.beginChild("hud-simulation", simW, simH, true, ImGuiWindowFlags.NoScrollbar)) {
                    renderSimulationHudContent();
                }
                ImGui.endChild();
                ImGui.popStyleVar(3);
                ImGui.popStyleColor(2);
            }
        }

        // Bottom-Left Floating HUD: Tile inspection & coordinates
        float blW = 240.0f;
        float blH = 135.0f;
        if (windowH > blH + 60.0f && windowW > blW * 2.0f) {
            ImGui.setCursorPos(margin, windowH - blH - margin);
            ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.getColorU32(0.08f, 0.10f, 0.13f, 0.88f));
            ImGui.pushStyleColor(ImGuiCol.Border, ImGui.getColorU32(0.24f, 0.28f, 0.36f, 0.75f));
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 8.0f);
            if (ImGui.beginChild("hud-bottom-left", blW, blH, true, ImGuiWindowFlags.NoScrollbar)) {
                renderHudBottomLeftContent(cache, viewport, pluginLifecycle, settings);
            }
            ImGui.endChild();
            ImGui.popStyleVar(3);
            ImGui.popStyleColor(2);
        }

        // Bottom-Right Floating HUD: Contextual tool controls & hints
        float brW = 220.0f;
        float brH = 135.0f;
        if (windowH > brH + 60.0f && windowW > blW + brW + margin * 3.0f) {
            ImGui.setCursorPos(windowW - brW - margin, windowH - brH - margin);
            ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.getColorU32(0.08f, 0.10f, 0.13f, 0.88f));
            ImGui.pushStyleColor(ImGuiCol.Border, ImGui.getColorU32(0.24f, 0.28f, 0.36f, 0.75f));
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 8.0f);
            if (ImGui.beginChild("hud-bottom-right", brW, brH, true, ImGuiWindowFlags.NoScrollbar)) {
                renderHudBottomRightContent();
            }
            ImGui.endChild();
            ImGui.popStyleVar(3);
            ImGui.popStyleColor(2);
        }
    }

    private void renderSimulationHudContent() {
        var clock = simulation.clock();
        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textColored(ImGui.getColorU32(0.40f, 0.70f, 1.0f, 1.0f), "SIMULATION CLOCK");
        ImGui.sameLine(220.0f);
        ImGui.textDisabled(clock.isPaused() ? "[PAUSED]" : "[RUNNING]");
        ImGui.popFont();

        if (ImGui.button(clock.isPaused() ? "Play##sim-play" : "Pause##sim-pause", 54, 22)) {
            clock.togglePause();
        }
        ImGui.sameLine();
        ImGui.beginDisabled(!clock.isPaused());
        if (ImGui.button("Step Cycle##sim-sc", 76, 22)) {
            simulation.stepClientCycle();
        }
        ImGui.sameLine();
        if (ImGui.button("Step Tick##sim-st", 70, 22)) {
            simulation.stepServerTick();
        }
        ImGui.endDisabled();

        ImGui.sameLine();
        float[] speeds = {0.25f, 0.5f, 1.0f, 2.0f, 4.0f};
        String[] speedLabels = {"0.25x", "0.5x", "1x", "2x", "4x"};
        for (int i = 0; i < speeds.length; i++) {
            if (i > 0) ImGui.sameLine();
            boolean isCur = Math.abs(clock.speed() - speeds[i]) < 0.01f;
            if (isCur) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.24f, 0.56f, 0.90f, 1.0f));
            }
            if (ImGui.button(speedLabels[i] + "##sim-spd-" + i, 40, 20)) {
                clock.setSpeed(speeds[i]);
            }
            if (isCur) {
                ImGui.popStyleColor();
            }
        }

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textDisabled("Cycle: " + clock.clientCycles() + " (50Hz)  ·  Tick: " + clock.serverTicks() + " (600ms)");
        ImGui.popFont();
    }

    private void renderHudBottomLeftContent(LoadedOsrsCacheSession cache,
                                            NativeSceneViewport viewport,
                                            EditorPluginLifecycleManager pluginLifecycle,
                                            SettingsStore settings) {
        EditorSession session = session(pluginLifecycle);
        WorldDocument world = session != null ? session.world() : null;
        var picked = viewport != null ? viewport.selection() : java.util.Optional.<PickResult>empty();

        int tileX = 0, tileY = 0, plane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        if (picked.isPresent()) {
            var hit = picked.get();
            tileX = hit.tile().x();
            tileY = hit.tile().y();
            plane = hit.plane();
        } else if (viewport != null) {
            tileX = Math.max(0, (int) (viewport.navigation().camera().x() / 128.0f));
            tileY = Math.max(0, (int) (viewport.navigation().camera().z() / 128.0f));
        }

        int localX = world != null ? Math.floorMod(tileX, Math.max(1, world.width())) : tileX & 63;
        int localY = world != null ? Math.floorMod(tileY, Math.max(1, world.length())) : tileY & 63;
        int regionX = tileX >> 6;
        int regionY = tileY >> 6;
        int regionId = (regionX << 8) | regionY;

        int height = 0;
        String underlay = "None";
        String overlay = "None";
        String flags = "None";

        if (world != null && plane >= 0 && plane < world.planes()) {
            var snapshot = world.tile(plane, localX, localY).snapshot();
            height = (snapshot.southWestHeight() + snapshot.southEastHeight()
                    + snapshot.northEastHeight() + snapshot.northWestHeight()) >> 2;

            if (snapshot.underlayId() > 0) {
                var uDef = cache.bundle().definitions().underlay(snapshot.underlayId());
                String color = uDef.map(u -> String.format("#%06X", u.rgb())).orElse("?");
                underlay = "#" + snapshot.underlayId() + " (" + color + ")";
            }

            if (snapshot.overlayId() > 0) {
                var oDef = cache.bundle().definitions().overlay(snapshot.overlayId());
                String color = oDef.map(o -> String.format("#%06X", o.rgb())).orElse("?");
                overlay = "#" + snapshot.overlayId() + " (" + color + ", s:" + snapshot.overlayShape() + ")";
            }

            int f = snapshot.flags();
            if (f != 0) {
                flags = "0x" + Integer.toHexString(f);
                if (OsrsTileFlags.hasBridge(f)) flags += " (Bridge)";
            }
        }

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textColored(ImGui.getColorU32(0.40f, 0.70f, 1.0f, 1.0f), "TILE INSPECTION");
        ImGui.text("Tile:     " + tileX + ", " + tileY + " (P:" + plane + ")");
        ImGui.text("Region:   " + regionX + ", " + regionY + " [" + regionId + "]");
        ImGui.text("Height:   " + height);
        ImGui.text("Underlay: " + underlay);
        ImGui.text("Overlay:  " + overlay);
        ImGui.text("Flags:    " + flags);
        ImGui.popFont();
    }

    private void renderHudBottomRightContent() {
        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textColored(ImGui.getColorU32(0.40f, 0.70f, 1.0f, 1.0f), "CONTROLS: " + activeRailId.toUpperCase());
        if ("terrain".equals(activeRailId) || "water".equals(activeRailId)) {
            ImGui.text("LMB: Paint tiles");
            ImGui.text("RMB: Pick material");
            ImGui.text("Shift+LMB: Fill area");
            ImGui.text("Ctrl+Z: Undo edit");
            ImGui.text("Alt+Drag: Rotate shape");
            ImGui.text("[ / ]: Brush size (" + tilePainterBrushSize + "x" + tilePainterBrushSize + ")");
        } else if ("objects".equals(activeRailId)) {
            ImGui.text("LMB: Place object");
            ImGui.text("RMB: Rotate object");
            ImGui.text("Shift+LMB: Duplicate");
            ImGui.text("Delete: Remove object");
            ImGui.text("Ctrl+Z: Undo edit");
        } else if ("height".equals(activeRailId)) {
            ImGui.text("LMB: Raise height");
            ImGui.text("RMB: Lower height");
            ImGui.text("Shift+LMB: Flatten");
            ImGui.text("Ctrl+LMB: Smooth");
            ImGui.text("Ctrl+Z: Undo edit");
        } else {
            ImGui.text("LMB Drag: Select box");
            ImGui.text("Shift+LMB: Add to select");
            ImGui.text("Ctrl+A: Select all");
            ImGui.text("Delete: Clear selection");
            ImGui.text("F: Frame selection");
        }
        ImGui.popFont();
    }

    // ------------------------------------------------------------------
    // Right panel: real tool settings + live selection
    // ------------------------------------------------------------------

    private void renderRightPanel(LoadedOsrsCacheSession cache, SettingsStore settings,
                                  EditorPluginLifecycleManager pluginLifecycle) {
        placeWindow(layout.rightX(), layout.viewportY(), layout.rightWidth(),
                layout.viewportHeight() + layout.drawerHeight());
        ImGui.begin(RIGHT_PANEL_WINDOW, FIXED_PANEL_FLAGS);
        ImGui.beginChild("right-panel-content", 0.0f, 0.0f, false);
        // Wrap at the panel edge rather than letting hint text and long
        // values run past it, which is what clipped the selection hint and
        // the setting captions in a fixed-width column.
        ImGui.pushTextWrapPos(0.0f);

        String[] tabs = {"Properties", "Knowledge", "Asset Browser", "Outliner", "Tools"};
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 6.0f);
        for (int index = 0; index < tabs.length; index++) {
            if (index > 0) ImGui.sameLine();
            if (modeButton(tabs[index], rightPanelTab == index)) rightPanelTab = index;
        }
        ImGui.popStyleVar();
        ImGui.separator();

        switch (rightPanelTab) {
            case 0 -> {
                renderPickInspector(cache, pluginLifecycle);
                ImGui.separator();
                renderSelectionPanel(pluginLifecycle);
            }
            case 1 -> renderKnowledgeTab(cache, pluginLifecycle);
            case 2 -> renderAssetBrowserTab(cache, pluginLifecycle);
            case 3 -> renderOutlinerTab(cache, settings, pluginLifecycle);
            default -> {
                renderActiveTool(pluginLifecycle);
                ImGui.separator();
                renderToolSettings(pluginLifecycle);
            }
        }

        ImGui.popTextWrapPos();
        ImGui.endChild();
        ImGui.end();
    }

    private void renderActiveTool(EditorPluginLifecycleManager pluginLifecycle) {
        String label = activeToolId;
        for (RailTool tool : RAIL_TOOLS) {
            for (String candidate : tool.toolIds()) {
                if (candidate.equals(activeToolId)) label = tool.label() + " · " + toolLabel(pluginLifecycle, candidate);
            }
        }
        StudioWidgets.heading("Active tool", "Registered tools from the built-in plugins.");
        ImGui.text(label);
    }

    private static String toolLabel(EditorPluginLifecycleManager pluginLifecycle, String toolId) {
        if (pluginLifecycle == null || pluginLifecycle.host() == null) return toolId;
        return pluginLifecycle.host().registry().toolRegistrations().stream()
                .filter(tool -> toolId.equals(tool.id()))
                .map(EditorToolRegistration::label)
                .findFirst()
                .orElse(toolId);
    }

    private void renderToolSettings(EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("Tool options");
        if (pluginLifecycle == null || pluginLifecycle.host() == null) {
            ImGui.textDisabled("Plugin host unavailable.");
            return;
        }
        List<EditorSetting> toolSettings = pluginLifecycle.host().registry()
                .settingsForTool(pluginLifecycle.host().context(), activeToolId);
        if (toolSettings.isEmpty()) {
            ImGui.textDisabled("This tool has no settings.");
            return;
        }
        for (EditorSetting setting : toolSettings) {
            renderEditorSetting(setting);
        }
    }

    /**
     * Renders one tool setting as a named field: caption on its own line,
     * control spanning the panel. Every control is bound straight to the
     * EditorSetting, so what is shown is the live value.
     */
    private static void renderEditorSetting(EditorSetting setting) {
        String id = "##" + setting.id();
        switch (setting.type()) {
            case INTEGER -> {
                int[] value = {((Number) setting.value()).intValue()};
                long minimum = (long) setting.minimum();
                long maximum = (long) setting.maximum();
                StudioWidgets.fieldLabel(setting.label());
                // Dear ImGui's integer slider asserts that both bounds sit
                // inside +/- INT_MAX/2 (imgui_widgets.cpp), and a setting
                // declared over the full int range trips it and kills the
                // frame. Such a range makes a useless slider anyway, so fall
                // back to a drag field for it.
                if (minimum < -SLIDER_BOUND || maximum > SLIDER_BOUND) {
                    if (ImGui.dragInt(id, value)) setting.setValue(value[0]);
                } else if (ImGui.sliderInt(id, value, (int) minimum, (int) maximum)) {
                    setting.setValue(value[0]);
                }
            }
            case DECIMAL -> {
                float[] value = {((Number) setting.value()).floatValue()};
                float minimum = (float) setting.minimum();
                float maximum = (float) setting.maximum();
                StudioWidgets.fieldLabel(setting.label());
                // A finite double can still overflow to infinity as a float,
                // which the slider cannot represent either.
                if (!Float.isFinite(minimum) || !Float.isFinite(maximum)) {
                    if (ImGui.dragFloat(id, value)) setting.setValue(value[0]);
                } else if (ImGui.sliderFloat(id, value, minimum, maximum,
                        "%.2f", ImGuiSliderFlags.None)) {
                    setting.setValue(value[0]);
                }
            }
            case BOOLEAN -> {
                // A checkbox reads better with its label beside it.
                ImBoolean value = new ImBoolean(Boolean.TRUE.equals(setting.value()));
                if (ImGui.checkbox(setting.label() + id, value)) {
                    setting.setValue(value.get());
                }
            }
            case ENUM -> {
                List<String> options = setting.options();
                int index = Math.max(0, options.indexOf(String.valueOf(setting.value())));
                ImInt selected = new ImInt(index);
                StudioWidgets.fieldLabel(setting.label());
                if (ImGui.combo(id, selected, options.toArray(new String[0]))) {
                    setting.setValue(options.get(selected.get()));
                }
            }
        }
    }

    /**
     * Reports what the last viewport click actually hit, including the
     * submission metadata of the draw command that was rendered, coordinates,
     * heights, underlay/overlay definitions, flags, appearance, and collision.
     */
    private void renderPickInspector(LoadedOsrsCacheSession cache,
                                     EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("Properties");
        if (viewport == null) {
            ImGui.textDisabled("No viewport.");
            return;
        }
        var picked = viewport.selection();
        if (picked.isEmpty()) {
            ImGui.textDisabled("Click a tile or object in the viewport to inspect it.");
            return;
        }
        var hit = picked.get();
        EditorSession session = session(pluginLifecycle);
        WorldDocument world = session != null ? session.world() : null;
        int localX = world != null ? Math.floorMod(hit.tile().x(), Math.max(1, world.width())) : hit.tile().x() & 63;
        int localY = world != null ? Math.floorMod(hit.tile().y(), Math.max(1, world.length())) : hit.tile().y() & 63;
        int regionX = hit.tile().x() >> 6;
        int regionY = hit.tile().y() >> 6;
        int regionId = (regionX << 8) | regionY;
        int effectivePlane = (world != null && hit.plane() >= 0 && hit.plane() < world.planes())
                ? world.effectivePlane(hit.plane(), localX, localY)
                : hit.plane();

        var snapshotOpt = (world != null && hit.plane() >= 0 && hit.plane() < world.planes())
                ? java.util.Optional.of(world.tile(hit.plane(), localX, localY).snapshot())
                : java.util.Optional.<com.rspsi.editor.model.TileSnapshot>empty();

        int height = snapshotOpt.map(s -> (s.southWestHeight() + s.southEastHeight()
                + s.northEastHeight() + s.northWestHeight()) >> 2).orElse(0);

        // Section 1: Selection
        if (ImGui.collapsingHeader("Selection", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.text("Tile:        " + hit.tile().x() + ", " + hit.tile().y());
            ImGui.text("Plane:       Authored " + hit.plane() + " (Effective " + effectivePlane + ")");
            ImGui.text("Region:      " + regionId + " (" + regionX + ", " + regionY + ")");
            ImGui.text("Local:       " + localX + ", " + localY);
            ImGui.text("Type:        " + (hit.objectHit() ? "World Object" : "Terrain Tile"));
            ImGui.popFont();
        }

        // Section 2: Object Properties
        if (ImGui.collapsingHeader("Object Properties", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            if (hit.objectHit()) {
                ImGui.text("ID:          " + hit.objectId());
                if (symbols != null) {
                    symbols.primaryName(SymbolNamespace.LOC, hit.objectId()).ifPresent(sym -> {
                        ImGui.text("Symbol:      " + SymbolNamespace.LOC.qualify(sym));
                    });
                }
                cache.bundle().definitions().object(hit.objectId()).ifPresent(def -> {
                    ImGui.text("Name:        " + (def.name().isEmpty() ? "(unnamed)" : def.name()));
                    ImGui.text("Size:        " + def.width() + "x" + def.length());
                    ImGui.text("Interactive: " + def.interactive());
                });
                cache.bundle().definitions().objectAppearance(hit.objectId()).ifPresent(app -> {
                    ImGui.text("Shadow:      " + (app.castsShadow() ? "Casts Shadow" : "No Shadow"));
                    ImGui.text("Occlusion:   " + (app.occludes() ? "Occludes" : "Non-occluding"));
                });
            } else {
                snapshotOpt.ifPresent(snap -> {
                    ImGui.text("Underlay ID: " + (snap.underlayId() > 0 ? snap.underlayId() : "None"));
                    ImGui.text("Overlay ID:  " + (snap.overlayId() > 0 ? snap.overlayId() : "None"));
                    ImGui.text("Anchor H:    " + height);
                });
            }
            ImGui.popFont();
        }

        // Section 3: Model & Textures
        if (ImGui.collapsingHeader("Model & Textures", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            if (hit.objectHit()) {
                cache.bundle().definitions().object(hit.objectId()).ifPresent(def -> {
                    int[] models = def.modelIds();
                    String mStr = models.length > 4
                            ? models[0] + ", " + models[1] + "... (" + models.length + ")"
                            : java.util.Arrays.toString(models);
                    ImGui.text("Models:      " + mStr);
                });
                cache.bundle().definitions().objectAppearance(hit.objectId()).ifPresent(app -> {
                    ImGui.text("Normals:     " + (app.mergeNormals() ? "Merge" : "Separate"));
                    ImGui.text("Contrast:    " + app.contrast());
                });
                if (hit.hasSubmissionMetadata()) {
                    ImGui.text("Texture ID:  " + (hit.textureId() < 0 ? "None" : hit.textureId()));
                }
            } else {
                snapshotOpt.ifPresent(snap -> {
                    if (snap.overlayId() > 0) {
                        cache.bundle().definitions().overlay(snap.overlayId()).ifPresent(o -> {
                            ImGui.text("Color:       " + String.format("#%06X", o.rgb()));
                            ImGui.text("Texture:     " + (o.texture() >= 0 ? o.texture() : "None"));
                        });
                    }
                    if (snap.underlayId() > 0) {
                        cache.bundle().definitions().underlay(snap.underlayId()).ifPresent(u -> {
                            ImGui.text("Underlay:    " + String.format("#%06X", u.rgb()));
                        });
                    }
                });
            }
            ImGui.popFont();
        }

        // Section 4: Offsets & Transform
        if (ImGui.collapsingHeader("Offsets & Transform", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.text("X:           " + localX);
            ImGui.text("Y:           " + localY);
            ImGui.text("Z (Height):  " + height);
            if (hit.objectHit()) {
                describePickedObject(hit, pluginLifecycle);
            } else {
                snapshotOpt.ifPresent(snap -> {
                    ImGui.text("Shape:       " + snap.overlayShape() + " (" + OSRS_SHAPE_NAMES[Math.min(11, snap.overlayShape())] + ")");
                    ImGui.text("Rotation:    " + (snap.overlayRotation() * 90) + "° (" + snap.overlayRotation() + ")");
                });
            }
            ImGui.popFont();
        }

        // Section 5: Interaction & Collision
        if (ImGui.collapsingHeader("Interaction & Collision", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            if (hit.objectHit()) {
                cache.bundle().definitions().object(hit.objectId()).ifPresent(def -> {
                    List<String> actions = def.interactions().stream().filter(a -> !a.isBlank()).toList();
                    ImGui.text("Actions:     " + (actions.isEmpty() ? "None" : String.join(", ", actions)));
                });
                cache.bundle().definitions().objectCollision(hit.objectId()).ifPresent(col -> {
                    ImGui.text("Walkable:    " + (col.blockWalk() == 0));
                    ImGui.text("Projectile:  " + (!col.blockProjectile()));
                    ImGui.text("Clip Type:   " + col.clipType());
                });
            } else {
                snapshotOpt.ifPresent(snap -> {
                    int flags = snap.flags();
                    ImGui.text("Walkable:    " + ((flags & OsrsTileFlags.BLOCK_MAP_SQUARE) == 0));
                    ImGui.text("Bridge:      " + OsrsTileFlags.hasBridge(flags));
                    ImGui.text("Remove Roof: " + OsrsTileFlags.removesRoofs(flags));
                    ImGui.text("Raw Flags:   0x" + Integer.toHexString(flags));
                });
            }
            ImGui.popFont();
        }

        // Section 6: Server References
        if (hit.objectHit() && references != null) {
            String symName = symbols != null ? symbols.primaryName(SymbolNamespace.LOC, hit.objectId()).orElse(null) : null;
            List<ContentReference> refs = references.referencesFor(SymbolNamespace.LOC, hit.objectId(), symName);
            if (!refs.isEmpty()) {
                if (ImGui.collapsingHeader("Server References (" + refs.size() + ")", ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.pushFont(StudioFonts.mono(), 0.0f);
                    for (ContentReference ref : refs) {
                        ImGui.bulletText(ref.displayLocation());
                        if (ref.snippet() != null && !ref.snippet().isBlank()) {
                            ImGui.textDisabled("  " + ref.snippet());
                        }
                    }
                    ImGui.popFont();
                }
            }
        }

        if (ImGui.button("Clear Selection##pick-clear")) viewport.clearSelection();
    }

    private void renderKnowledgeTab(LoadedOsrsCacheSession cache, EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("World Knowledge");
        if (pluginLifecycle == null || pluginLifecycle.host() == null) {
            ImGui.textDisabled("Knowledge service unavailable.");
            return;
        }

        WorldKnowledgeService knowledge = pluginLifecycle.host().context().knowledge();
        if (knowledge == null) {
            ImGui.textDisabled("Knowledge service uninitialized.");
            return;
        }

        KnowledgeSnapshot snapshot = knowledge.snapshot();

        // 1. World & Region Profile Summary
        if (ImGui.collapsingHeader("Region & World Intelligence", ImGuiTreeNodeFlags.DefaultOpen)) {
            RegionProfile worldProfile = snapshot.worldProfile();
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.text("Total Tiles:     " + worldProfile.tileCount() + " (" + worldProfile.planes() + " planes)");
            ImGui.text("Dominant Floor:  Underlay #" + worldProfile.dominantUnderlay().orElse(-1)
                    + " | Overlay #" + worldProfile.dominantOverlay().orElse(-1));
            ImGui.text("Elevation:       Min " + worldProfile.minHeight() + " | Max " + worldProfile.maxHeight()
                    + " | Avg " + String.format("%.1f", worldProfile.averageHeight()));

            worldProfile.metric(MetricKey.MAX_SLOPE).ifPresent(maxSlope ->
                    ImGui.text("Steepest Slope:  " + String.format("%.1f", maxSlope) + "°"));
            worldProfile.metric(MetricKey.WALKABLE_RATIO).ifPresent(walkable ->
                    ImGui.text("Walkable Space:  " + String.format("%.1f%%", walkable * 100)));
            ImGui.popFont();
        }

        // 2. Selection Semantics & Explain Classification
        var picked = viewport != null ? viewport.selection() : java.util.Optional.<PickResult>empty();
        if (picked.isPresent()) {
            PickResult hit = picked.get();
            TileCoordinate coord = hit.tile();
            StudioWidgets.section("Selection Semantics");
            ImGui.text("Selected Tile: (" + coord.plane() + ", " + coord.x() + ", " + coord.y() + ")");

            WorldDocument world = pluginLifecycle.host().context().world();
            WorldObject pickedObject = null;
            if (hit.objectHit() && world != null && coord.plane() >= 0 && coord.plane() < world.planes()) {
                int lx = Math.floorMod(coord.x(), Math.max(1, world.width()));
                int ly = Math.floorMod(coord.y(), Math.max(1, world.length()));
                for (WorldObject obj : world.tile(coord.plane(), lx, ly).snapshot().objects()) {
                    if (obj.id() == hit.objectId()) {
                        pickedObject = obj;
                        break;
                    }
                }
            }

            // Derived Topology
            snapshot.topologyAt(coord).ifPresent(topo -> {
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("Topology: Slope " + String.format("%.1f", topo.slopeMagnitude())
                        + " | Aspect " + topo.aspect() + " | Curvature " + topo.curvature());
                if (topo.isCliff()) ImGui.textColored(0xFF6666FF, "⚠ Terrain marked as CLIFF");
                ImGui.popFont();
            });

            // Semantic Tags
            Set<SemanticTag> tags = snapshot.tagsAt(coord);
            if (tags.isEmpty()) {
                ImGui.textDisabled("No semantic tags active on this tile.");
            } else {
                ImGui.text("Semantic Tags:");
                for (SemanticTag tag : tags) {
                    ImGui.bulletText(tag.qualifiedName());
                }
            }

            // Explain Classification breakdown
            if (ImGui.collapsingHeader("Explain Classification", ImGuiTreeNodeFlags.DefaultOpen)) {
                List<KnowledgeFact<SemanticTag>> facts = snapshot.factsAt(coord);
                boolean hasObjFacts = pickedObject != null && !knowledge.classifyObject(pickedObject).isEmpty();
                if (facts.isEmpty() && !hasObjFacts) {
                    ImGui.textDisabled("No inferred classifications active.");
                } else {
                    for (KnowledgeFact<SemanticTag> fact : facts) {
                        String header = fact.value().qualifiedName() + String.format(" [%.0f%%]", fact.confidence() * 100)
                                + " (" + fact.source() + ")";
                        ImGui.text(header);
                        if (!fact.evidence().isEmpty()) {
                            ImGui.indent();
                            ImGui.pushFont(StudioFonts.mono(), 0.0f);
                            for (Evidence ev : fact.evidence()) {
                                ImGui.text("• " + ev.description() + " (signal: " + String.format("%.2f", ev.weight()) + ")");
                            }
                            ImGui.popFont();
                            ImGui.unindent();
                        }
                    }

                    if (pickedObject != null) {
                        List<KnowledgeFact<SemanticTag>> objFacts = knowledge.classifyObject(pickedObject);
                        for (KnowledgeFact<SemanticTag> fact : objFacts) {
                            String header = fact.value().qualifiedName() + String.format(" [%.0f%%]", fact.confidence() * 100)
                                    + " (" + fact.source() + ")";
                            ImGui.text(header);
                            if (!fact.evidence().isEmpty()) {
                                ImGui.indent();
                                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                                for (Evidence ev : fact.evidence()) {
                                    ImGui.text("• " + ev.description() + " (signal: " + String.format("%.2f", ev.weight()) + ")");
                                }
                                ImGui.popFont();
                                ImGui.unindent();
                            }
                        }
                    }
                }
            }

            // Explain Rendering (Rule Trace)
            if (ImGui.collapsingHeader("Explain Rendering (Rule Trace)", ImGuiTreeNodeFlags.DefaultOpen)) {
                RuleTrace.TileRuleTrace tileTrace =
                        RuleTrace.traceTile(pluginLifecycle.host().context().world(), coord);
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("Tile Elevation:   " + tileTrace.elevation());
                ImGui.text("Effective Plane:  " + tileTrace.effectivePlane());
                ImGui.text("Tile Flags:       Blocked=" + tileTrace.flags().blocked()
                        + " Bridge=" + tileTrace.flags().bridge() + " Roof=" + tileTrace.flags().underRoof());

                if (pickedObject != null) {
                    final WorldObject objRef = pickedObject;
                    RuleTrace.traceObject(pickedObject, pluginLifecycle.host().context().world(),
                            cache != null ? cache.bundle().definitions() : null).ifPresent(trace -> {
                        ImGui.separator();
                        ImGui.text("Object Shape:     " + (trace.shapeDescriptor() != null ? trace.shapeDescriptor().name() : "Shape " + objRef.type()));
                        ImGui.text("Loc Variants:     " + trace.variantCount() + " (Mirror: " + trace.mirrorApplied() + ")");
                        ImGui.text("Displacement:     " + trace.displacementUsed());
                        ImGui.text("Merge Normals:    " + (trace.mergeNormalsEligible() ? "Yes (Opcode 22)" : "No"));
                        ImGui.text("Ground Contour:   " + (trace.contourGroundApplied() ? "Type " + trace.contourGroundType() : "Disabled"));
                    });
                }
                ImGui.popFont();
            }

            // User Overrides
            if (ImGui.collapsingHeader("User Metadata Overrides")) {
                ImGui.inputTextWithHint("##custom-tag", "New tag (e.g. core:SPAWN)", customTagInput);
                ImGui.sameLine();
                if (ImGui.button("Add Tag##add-user-tag") && !customTagInput.get().isBlank()) {
                    knowledge.userOverrides().addTileTag(coord, SemanticTag.of(customTagInput.get()));
                    knowledge.invalidate();
                    customTagInput.set("");
                }
            }
        } else {
            ImGui.textDisabled("Select or pick a tile in the viewport to inspect semantic knowledge and rule traces.");
        }
    }

    private void renderAssetBrowserTab(LoadedOsrsCacheSession cache, EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("Asset Browser");
        ImGui.inputTextWithHint("##asset-search", "Search assets...", assetSearchQuery);
        String filter = assetSearchQuery.get().toLowerCase().trim();

        String[] categories = {"All", "Objects", "Underlays", "Overlays"};
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) ImGui.sameLine();
            if (modeButton(categories[i], assetFilterCategory == i)) assetFilterCategory = i;
        }
        ImGui.separator();

        ImGui.beginChild("asset-browser-list", 0.0f, 0.0f, false);
        int shown = 0;
        if (assetFilterCategory == 0 || assetFilterCategory == 1) {
            ImGui.textDisabled("--- OBJECTS ---");
            for (int id = 0; id < 2000 && shown < 64; id++) {
                var defOpt = cache.bundle().definitions().object(id);
                if (defOpt.isPresent()) {
                    var def = defOpt.get();
                    String name = def.name();
                    if (filter.isEmpty() || name.toLowerCase().contains(filter) || String.valueOf(id).contains(filter)) {
                        shown++;
                        if (ImGui.selectable(String.format("#%04d  %s", id, name.isEmpty() ? "(unnamed)" : name))) {
                            activeRailId = "objects";
                            activateRail(pluginLifecycle, "objects");
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("Object #" + id + " (" + def.width() + "x" + def.length() + ")");
                        }
                    }
                }
            }
        }

        if ((assetFilterCategory == 0 || assetFilterCategory == 3) && shown < 64) {
            ImGui.textDisabled("--- OVERLAYS ---");
            for (int id = 1; id < 128 && shown < 64; id++) {
                var defOpt = cache.bundle().definitions().overlay(id);
                if (defOpt.isPresent()) {
                    var def = defOpt.get();
                    String hex = String.format("#%06X", def.rgb());
                    if (filter.isEmpty() || hex.toLowerCase().contains(filter) || String.valueOf(id).contains(filter)) {
                        shown++;
                        if (ImGui.selectable(String.format("#%03d  Overlay (%s)", id, hex))) {
                            tilePainterIsOverlay = true;
                            tilePainterMaterialId = id;
                            activeRailId = "terrain";
                            activateRail(pluginLifecycle, "terrain");
                        }
                    }
                }
            }
        }

        if ((assetFilterCategory == 0 || assetFilterCategory == 2) && shown < 64) {
            ImGui.textDisabled("--- UNDERLAYS ---");
            for (int id = 1; id < 128 && shown < 64; id++) {
                var defOpt = cache.bundle().definitions().underlay(id);
                if (defOpt.isPresent()) {
                    var def = defOpt.get();
                    String hex = String.format("#%06X", def.rgb());
                    if (filter.isEmpty() || hex.toLowerCase().contains(filter) || String.valueOf(id).contains(filter)) {
                        shown++;
                        if (ImGui.selectable(String.format("#%03d  Underlay (%s)", id, hex))) {
                            tilePainterIsOverlay = false;
                            tilePainterMaterialId = id;
                            activeRailId = "terrain";
                            activateRail(pluginLifecycle, "terrain");
                        }
                    }
                }
            }
        }
        ImGui.endChild();
    }

    /**
     * Resolves the picked object back to its authored location so the shape
     * and rotation are visible.
     */
    private void describePickedObject(PickResult hit,
                                      EditorPluginLifecycleManager pluginLifecycle) {
        EditorSession session = session(pluginLifecycle);
        if (session == null) return;
        var world = session.world();
        int localX = Math.floorMod(hit.tile().x(), Math.max(1, world.width()));
        int localY = Math.floorMod(hit.tile().y(), Math.max(1, world.length()));
        if (hit.plane() < 0 || hit.plane() >= world.planes()) return;
        var snapshot = world.tile(hit.plane(), localX, localY).snapshot();
        for (var object : snapshot.objects()) {
            if (object.id() != hit.objectId()) continue;
            String shape = object.shape().map(value -> " (" + value + ")").orElse("");
            ImGui.text("shape    " + object.type() + shape);
            ImGui.text("rot      " + object.rotation());
            return;
        }
        ImGui.textDisabled("not found at " + localX + "," + localY);
    }

    private void renderOutlinerTab(LoadedOsrsCacheSession cache, SettingsStore settings,
                                   EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("Outliner");
        EditorSession session = session(pluginLifecycle);
        if (session == null) {
            ImGui.textDisabled("No active session.");
            return;
        }
        WorldDocument world = session.world();
        int activePlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);

        int regionId = 0;
        if (viewport != null) {
            int camTileX = Math.max(0, (int) (viewport.navigation().camera().x() / 128.0f));
            int camTileY = Math.max(0, (int) (viewport.navigation().camera().z() / 128.0f));
            regionId = ((camTileX >> 6) << 8) | (camTileY >> 6);
        }

        String regionTitle = "Region " + (regionId > 0 ? regionId : "") + " (" + world.width() + "x" + world.length() + ")";
        if (ImGui.treeNodeEx(regionTitle, ImGuiTreeNodeFlags.DefaultOpen)) {
            for (int plane = 0; plane < world.planes(); plane++) {
                boolean isActive = plane == activePlane;
                int flags = isActive ? ImGuiTreeNodeFlags.DefaultOpen : 0;
                String planeTitle = "Plane " + plane + (isActive ? " (Active)" : "");
                if (ImGui.treeNodeEx(planeTitle + "##p-" + plane, flags)) {
                    if (ImGui.treeNode("Terrain##p" + plane)) {
                        ImGui.textDisabled(world.width() + "x" + world.length() + " tiles");
                        if (ImGui.smallButton("Frame Center##center-" + plane)) {
                            if (viewport != null) {
                                float cx = world.width() * 64.0f;
                                float cz = world.length() * 64.0f;
                                viewport.navigation().frameSelection(cx, 0.0f, cz);
                            }
                        }
                        ImGui.treePop();
                    }

                    renderOutlinerCategory(world, plane, ObjectCategory.WALL, cache);
                    renderOutlinerCategory(world, plane, ObjectCategory.WALL_DECOR, cache);
                    renderOutlinerCategory(world, plane, ObjectCategory.GROUND, cache);
                    renderOutlinerCategory(world, plane, ObjectCategory.GROUND_DECOR, cache);
                    renderOutlinerServerContent(world, plane);

                    ImGui.treePop();
                }
            }
            ImGui.treePop();
        }
    }

    private void renderOutlinerServerContent(WorldDocument world, int plane) {
        if (spawns == null) return;
        List<NpcSpawn> planeSpawns = spawns.spawns(plane, 0, 0, world.width() * 64, world.length() * 64);
        if (planeSpawns.isEmpty()) return;
        String title = "Server NPC Spawns (" + planeSpawns.size() + ")##p" + plane + "-server-npcs";
        if (ImGui.treeNode(title)) {
            for (int i = 0; i < planeSpawns.size(); i++) {
                NpcSpawn spawn = planeSpawns.get(i);
                String label = String.format("[%02d,%02d] %s (id:%d)##spawn-%d-%d",
                        spawn.coordinate().x() & 63, spawn.coordinate().y() & 63,
                        spawn.symbolicName(), spawn.id(), plane, i);
                if (ImGui.selectable(label)) {
                    if (viewport != null) {
                        float cx = (spawn.coordinate().x() & 63) * 128.0f + 64.0f;
                        float cz = (spawn.coordinate().y() & 63) * 128.0f + 64.0f;
                        viewport.navigation().frameSelection(cx, 0.0f, cz);
                    }
                }
            }
            ImGui.treePop();
        }
    }

    private void renderOutlinerCategory(WorldDocument world, int plane,
                                        ObjectCategory category,
                                        LoadedOsrsCacheSession cache) {
        List<WorldObject> matching = new ArrayList<>();
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.length(); y++) {
                for (WorldObject obj : world.tile(plane, x, y).snapshot().objects()) {
                    if (obj.category() == category) {
                        matching.add(obj);
                    }
                }
            }
        }
        if (matching.isEmpty()) {
            ImGui.textDisabled("  " + category.displayName() + " (0)");
            return;
        }
        String nodeTitle = category.displayName() + " (" + matching.size() + ")##p" + plane + "-" + category.name();
        if (ImGui.treeNode(nodeTitle)) {
            for (int i = 0; i < matching.size(); i++) {
                WorldObject obj = matching.get(i);
                String name = cache.bundle().definitions().object(obj.id())
                        .map(ObjectDefinitionView::name)
                        .filter(n -> !n.isBlank())
                        .orElse("Object " + obj.id());
                String itemLabel = String.format("[%02d,%02d] %s##obj-%d-%d", obj.x(), obj.y(), name, plane, i);
                if (ImGui.selectable(itemLabel)) {
                    if (viewport != null) {
                        viewport.setSelection(new PickResult(
                                new TileCoordinate(plane, obj.x(), obj.y()),
                                plane, obj.id(), 0.0f));
                    }
                }
                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                    if (viewport != null) {
                        float tx = obj.x() * 128.0f + 64.0f;
                        float tz = obj.y() * 128.0f + 64.0f;
                        var snap = world.tile(plane, obj.x(), obj.y()).snapshot();
                        float ty = (snap.southWestHeight() + snap.southEastHeight()
                                + snap.northEastHeight() + snap.northWestHeight()) >> 2;
                        viewport.navigation().frameSelection(tx, ty, tz);
                    }
                }
            }
            ImGui.treePop();
        }
    }

    private static int maxOf(int a, int b, int c, int d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static int minOf(int a, int b, int c, int d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private void renderSelectionPanel(EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("Selection");
        EditorSession session = session(pluginLifecycle);
        if (session == null) {
            ImGui.textDisabled("No session.");
            return;
        }
        int tiles = session.selection().tiles().size();
        if (tiles == 0) {
            ImGui.textDisabled("Nothing selected. Use box select (1) and drag in the viewport.");
        } else {
            ImGui.text(tiles + (tiles == 1 ? " tile" : " tiles"));
            if (ImGui.beginTable("selection-table", 3,
                    ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersInnerH
                            | ImGuiTableFlags.ScrollY, 0.0f, ImGui.getTextLineHeightWithSpacing() * 8.0f)) {
                ImGui.tableSetupColumn("Plane");
                ImGui.tableSetupColumn("X");
                ImGui.tableSetupColumn("Y");
                ImGui.tableSetupScrollFreeze(0, 1);
                ImGui.tableHeadersRow();
                int shown = 0;
                for (TileCoordinate coordinate : session.selection().tiles()) {
                    if (shown++ >= 64) break;
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    ImGui.text(String.valueOf(coordinate.plane()));
                    ImGui.tableNextColumn();
                    ImGui.text(String.valueOf(coordinate.x()));
                    ImGui.tableNextColumn();
                    ImGui.text(String.valueOf(coordinate.y()));
                }
                ImGui.endTable();
            }
            ImGui.textDisabled(describeSelection(session.selection().current()));
        }
    }

    // ------------------------------------------------------------------
    // Bottom drawer: History / Tasks / Messages / Diagnostics — all live
    // ------------------------------------------------------------------

    private void renderBottomDrawer(LoadedOsrsCacheSession cache, EditorPluginLifecycleManager pluginLifecycle) {
        if (!bottomDrawerVisible) return;
        placeWindow(layout.centerX(), layout.drawerY(),
                layout.centerWidth(), layout.drawerHeight());
        ImGui.begin(BOTTOM_WINDOW, FIXED_PANEL_FLAGS);
        String[] tabs = {"Tile Painter", "History", "Tasks", "Messages", "Diagnostics"};
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 6.0f);
        for (int index = 0; index < tabs.length; index++) {
            if (index > 0) ImGui.sameLine();
            if (modeButton(tabs[index], drawerTab == index)) drawerTab = index;
        }
        ImGui.popStyleVar();
        ImGui.separator();
        ImGui.beginChild("drawer-content", 0.0f, 0.0f, false);
        switch (drawerTab) {
            case 0 -> renderTilePainterTab(cache, pluginLifecycle);
            case 1 -> renderHistoryTab(pluginLifecycle);
            case 2 -> renderTasksTab(pluginLifecycle);
            case 3 -> renderMessagesTab(pluginLifecycle);
            default -> renderDiagnosticsTab(pluginLifecycle);
        }
        ImGui.endChild();
        ImGui.end();
    }

    private void renderTilePainterTab(LoadedOsrsCacheSession cache, EditorPluginLifecycleManager pluginLifecycle) {
        if (ImGui.beginTable("tile-painter-columns", 3, ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableSetupColumn("Material & Brush");
            ImGui.tableSetupColumn("Tile Shapes (OSRS 0–11)");
            ImGui.tableSetupColumn("Rotation & Swatches");
            ImGui.tableHeadersRow();

            // Column 1: Material & Brush
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.textDisabled("MATERIAL TYPE");
            if (ImGui.radioButton("Underlay", !tilePainterIsOverlay)) tilePainterIsOverlay = false;
            ImGui.sameLine(0.0f, 16.0f);
            if (ImGui.radioButton("Overlay", tilePainterIsOverlay)) tilePainterIsOverlay = true;

            int[] matId = {tilePainterMaterialId};
            if (ImGui.sliderInt("ID##mat-id", matId, 0, 255)) {
                tilePainterMaterialId = matId[0];
            }
            if (tilePainterIsOverlay) {
                var def = cache.bundle().definitions().overlay(tilePainterMaterialId);
                String name = def.map(o -> String.format("#%06X (Tex %d)", o.rgb(), o.texture())).orElse("None");
                ImGui.textDisabled("Def: " + name);
            } else {
                var def = cache.bundle().definitions().underlay(tilePainterMaterialId);
                String name = def.map(u -> String.format("#%06X", u.rgb())).orElse("None");
                ImGui.textDisabled("Def: " + name);
            }

            int[] bSize = {tilePainterBrushSize};
            if (ImGui.sliderInt("Brush Size", bSize, 1, 16)) tilePainterBrushSize = bSize[0];
            float[] falloff = {tilePainterFalloff};
            if (ImGui.sliderFloat("Falloff", falloff, 0.0f, 1.0f)) tilePainterFalloff = falloff[0];

            ImBoolean blend = new ImBoolean(tilePainterBlendEdges);
            if (ImGui.checkbox("Blend Edges", blend)) tilePainterBlendEdges = blend.get();
            ImGui.sameLine();
            ImBoolean smooth = new ImBoolean(tilePainterAutoSmooth);
            if (ImGui.checkbox("Auto Smooth", smooth)) tilePainterAutoSmooth = smooth.get();

            // Column 2: 12 OSRS Tile Shapes
            ImGui.tableNextColumn();
            ImGui.textDisabled("SELECT SHAPE (0–11)");
            for (int i = 0; i < 12; i++) {
                boolean isSelected = (tilePainterShape == i);
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
                }
                if (ImGui.button(OSRS_SHAPE_NAMES[i] + "##shape-" + i, 88.0f, 24.0f)) {
                    tilePainterShape = i;
                }
                if (isSelected) {
                    ImGui.popStyleColor();
                }
                if ((i + 1) % 4 != 0) {
                    ImGui.sameLine();
                }
            }

            // Column 3: Rotation & Swatches
            ImGui.tableNextColumn();
            ImGui.textDisabled("QUICK ROTATE");
            if (ImGui.button("↺ CCW##rot-ccw", 54.0f, 24.0f)) {
                tilePainterRotation = (tilePainterRotation + 3) & 3;
            }
            ImGui.sameLine();
            if (ImGui.button("↻ CW##rot-cw", 54.0f, 24.0f)) {
                tilePainterRotation = (tilePainterRotation + 1) & 3;
            }
            ImGui.sameLine();
            if (ImGui.button("↕ 180°##rot-180", 54.0f, 24.0f)) {
                tilePainterRotation = (tilePainterRotation + 2) & 3;
            }
            ImGui.sameLine();
            ImGui.textDisabled("Rot: " + (tilePainterRotation * 90) + "°");

            ImBoolean randRot = new ImBoolean(tilePainterRandomizeRot);
            if (ImGui.checkbox("Randomize rotation", randRot)) tilePainterRandomizeRot = randRot.get();
            ImBoolean matchH = new ImBoolean(tilePainterMatchHeight);
            if (ImGui.checkbox("Match height", matchH)) tilePainterMatchHeight = matchH.get();
            ImBoolean allPlanes = new ImBoolean(tilePainterApplyAllPlanes);
            if (ImGui.checkbox("Apply to all levels", allPlanes)) tilePainterApplyAllPlanes = allPlanes.get();

            ImGui.textDisabled("RECENT SWATCHES");
            for (int s = 0; s < RECENT_SWATCHES.length; s++) {
                Swatch sw = RECENT_SWATCHES[s];
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(sw.r, sw.g, sw.b, 1.0f));
                if (ImGui.button("##swatch-" + s, 22.0f, 22.0f)) {
                    tilePainterIsOverlay = sw.overlay;
                    tilePainterMaterialId = sw.id;
                }
                ImGui.popStyleColor();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(sw.name + " (" + (sw.overlay ? "Overlay" : "Underlay") + " #" + sw.id + ")");
                }
                if (s < RECENT_SWATCHES.length - 1) ImGui.sameLine();
            }

            ImGui.endTable();
        }
    }

    private void renderHistoryTab(EditorPluginLifecycleManager pluginLifecycle) {
        EditorSession session = session(pluginLifecycle);
        if (session == null) {
            ImGui.textDisabled("No session loaded.");
            return;
        }
        var history = session.history();
        List<EditorCommand> commands = history.commands();
        int cursor = history.cursor();
        ImGui.textDisabled("History " + cursor + "/" + commands.size()
                + "  ·  undoable " + history.canUndo() + "  ·  redoable " + history.canRedo());
        if (commands.isEmpty()) {
            ImGui.textDisabled("No edits yet. Terrain and object tools will appear here.");
            return;
        }
        ImGui.beginChild("history-list", 0.0f, 0.0f, false);
        int shown = 0;
        for (int index = commands.size() - 1; index >= 0 && shown < 128; index--, shown++) {
            EditorCommand command = commands.get(index);
            boolean applied = index < cursor;
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, applied
                    ? ImGui.getColorU32(0.85f, 0.87f, 0.92f, 1.0f)
                    : ImGui.getColorU32(0.45f, 0.48f, 0.55f, 1.0f));
            boolean jump = ImGui.selectable((applied ? "●  " : "○  ") + commandLabel(command)
                    + "  #" + index);
            ImGui.popStyleColor();
            if (jump && index != cursor - 1) {
                session.jumpToHistory(index + 1);
            }
        }
        ImGui.endChild();
    }

    private static String describeSelection(Selection selection) {
        if (selection instanceof TileSelection tile) {
            return "Tile " + tile.coordinate().x() + ", " + tile.coordinate().y()
                    + " (plane " + tile.coordinate().plane() + ")";
        }
        if (selection instanceof TileAreaSelection area) {
            return "Area " + area.bounds().width() + "x" + area.bounds().height()
                    + " on plane " + area.plane();
        }
        if (selection instanceof TileSetSelection) return "Tile set";
        if (selection instanceof VertexSelection vertex) {
            return "Vertex " + vertex.x() + ", " + vertex.y()
                    + " corner " + vertex.corner() + " (plane " + vertex.plane() + ")";
        }
        if (selection instanceof ObjectSelection object) {
            return "Object " + object.object().id() + " (type " + object.object().type() + ")";
        }
        if (selection instanceof ObjectSetSelection) return "Object set";
        if (selection instanceof FragmentSelection) return "Fragment";
        return "Custom selection";
    }

    private static String commandLabel(EditorCommand command) {
        String name = command.getClass().getSimpleName();
        return name.endsWith("Command") ? name.substring(0, name.length() - "Command".length()) : name;
    }

    private void renderTasksTab(EditorPluginLifecycleManager pluginLifecycle) {
        if (pluginLifecycle == null || pluginLifecycle.host() == null) {
            ImGui.textDisabled("No workspace tasks.");
            return;
        }
        List<EditorTaskService.TaskSnapshot> tasks = pluginLifecycle.host().context()
                .tasks().snapshots();
        if (tasks.isEmpty()) {
            ImGui.textDisabled("No running tasks.");
            return;
        }
        for (EditorTaskService.TaskSnapshot task : tasks) {
            ImGui.text(task.label() + " · " + task.state());
            ImGui.progressBar((float) task.progress(), -1, 0, task.message());
        }
    }

    private void renderMessagesTab(EditorPluginLifecycleManager pluginLifecycle) {
        if (pluginLifecycle == null || pluginLifecycle.host() == null) {
            ImGui.textDisabled("No workspace notifications.");
            return;
        }
        List<EditorNotificationService.Notification> notifications = pluginLifecycle.host()
                .context().notifications().recent();
        if (notifications.isEmpty()) {
            ImGui.textDisabled("No notifications.");
            return;
        }
        for (EditorNotificationService.Notification notification : notifications) {
            ImGui.text(notification.level() + " · " + notification.title());
            ImGui.textWrapped(notification.message());
        }
    }

    private void renderDiagnosticsTab(EditorPluginLifecycleManager pluginLifecycle) {
        if (viewport != null) {
            var stats = viewport.statistics();
            float fps = ImGui.getIO().getFramerate();
            float frameMs = 1000.0f / Math.max(1.0f, fps);
            ImGui.text(String.format("Performance: %.2f ms/frame (%.1f FPS)", frameMs, fps));
            int vboKb = (stats.sourceVertices() * 32) / 1024;
            int iboKb = (stats.sourceIndices() * 4) / 1024;
            ImGui.text("Buffer memory: VBO " + vboKb + " KB  ·  IBO " + iboKb + " KB  (total " + (vboKb + iboKb) + " KB)");
            ImGui.text("Triangles: " + stats.renderedTriangles() + " (Terrain: " + stats.terrainTriangles() + ", Objects: " + stats.objectTriangles() + ")");
            ImGui.text("Draw calls: " + stats.drawCalls() + "  ·  GL Error: " + stats.firstGlError());
            ImGui.text("Uploads: geometry " + stats.geometryUploaded() + "  ·  texture " + stats.textureUploaded() + "  ·  occlusion " + stats.occlusionApplied());
            ImGui.text("Textures: decoded " + stats.decodedTextures() + "  ·  fallback " + stats.fallbackTextures()
                    + "  ·  missing " + stats.missingTextures() + "  ·  unavailable " + stats.unavailableTextures());
            ImGui.textDisabled("GL: " + stats.vendor() + " · " + stats.renderer() + " · " + stats.version());
            ImGui.separator();
        }
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var overlays = pluginLifecycle.host().registry().overlayRegistrations();
            if (!overlays.isEmpty()) {
                ImGui.textDisabled("Scene overlays: " + overlays.size()
                        + "  ·  hover the viewport with a tool active to see them.");
            }
            var menus = pluginLifecycle.host().registry().menuRegistrations();
            ImGui.textDisabled("Plugin commands: " + pluginLifecycle.host().registry()
                    .commandRegistrations().size()
                    + "  ·  menus: " + menus.size()
                    + "  ·  validators: " + pluginLifecycle.host().registry()
                    .validatorRegistrations().size());
        }
    }

    // ------------------------------------------------------------------
    // Status bar
    // ------------------------------------------------------------------

    private void renderStatusBar(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                 NativeSceneViewport viewport, boolean dirty,
                                 SettingsStore settings,
                                 EditorPluginLifecycleManager pluginLifecycle) {
        imgui.ImGuiViewport main = ImGui.getMainViewport();
        ImGui.setNextWindowPos(main.getPosX(), main.getPosY() + main.getSizeY() - STATUS_BAR_HEIGHT,
                ImGuiCond.Always);
        ImGui.setNextWindowSize(main.getSizeX(), STATUS_BAR_HEIGHT, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.NoSavedSettings;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12.0f, 4.0f);
        ImGui.begin("StudioStatusBar", flags);
        ImGui.pushFont(StudioFonts.mono(), 0.0f);

        var picked = viewport != null ? viewport.selection() : java.util.Optional.<PickResult>empty();
        int tileX = 0, tileY = 0, plane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        int regId = 0, regX = 0, regY = 0;
        String selectedDesc = "None";

        if (picked.isPresent()) {
            var hit = picked.get();
            tileX = hit.tile().x();
            tileY = hit.tile().y();
            plane = hit.plane();
            regX = tileX >> 6;
            regY = tileY >> 6;
            regId = (regX << 8) | regY;
            if (hit.objectHit()) {
                String name = cache.bundle().definitions().object(hit.objectId())
                        .map(ObjectDefinitionView::name).filter(n -> !n.isBlank()).orElse("Object");
                selectedDesc = "#" + hit.objectId() + " (" + name + ")";
            } else {
                selectedDesc = "Tile [" + tileX + "," + tileY + "]";
            }
        } else if (viewport != null) {
            tileX = Math.max(0, (int) (viewport.navigation().camera().x() / 128.0f));
            tileY = Math.max(0, (int) (viewport.navigation().camera().z() / 128.0f));
            regX = tileX >> 6;
            regY = tileY >> 6;
            regId = (regX << 8) | regY;
        }

        ImGui.textColored(ImGui.getColorU32(0.35f, 0.75f, 0.45f, 1.0f), "Ready");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textDisabled("|");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.text("Region: " + regX + ", " + regY + " (" + regId + ")");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textDisabled("|");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.text("Tile: " + tileX + ", " + tileY + " (P:" + plane + ")");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textDisabled("|");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.text("Selected: " + selectedDesc);

        var stats = viewport != null ? viewport.statistics() : null;
        float fps = ImGui.getIO().getFramerate();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        float rightAreaWidth = 540.0f;
        float startRightX = Math.max(ImGui.getCursorPosX(), main.getSizeX() - rightAreaWidth);
        ImGui.setCursorPosX(startRightX);

        if (stats != null) {
            ImGui.text("Objects: " + (stats.objectTriangles() / 2));
            ImGui.sameLine(0.0f, 10.0f);
            ImGui.textDisabled("|");
            ImGui.sameLine(0.0f, 10.0f);
            ImGui.text("Meshes: " + stats.renderedTriangles());
            ImGui.sameLine(0.0f, 10.0f);
            ImGui.textDisabled("|");
        }
        ImGui.text(String.format("FPS: %.0f", fps));
        ImGui.sameLine(0.0f, 10.0f);
        ImGui.textDisabled("|");
        ImGui.sameLine(0.0f, 10.0f);
        ImGui.text("RAM: " + usedMb + " MB / " + maxMb + " MB");
        ImGui.sameLine(0.0f, 10.0f);
        ImGui.textDisabled("|");
        ImGui.sameLine(0.0f, 10.0f);
        if (dirty) {
            ImGui.textColored(ImGui.getColorU32(0.9f, 0.6f, 0.2f, 1.0f), "● Unsaved");
        } else {
            ImGui.textColored(ImGui.getColorU32(0.35f, 0.75f, 0.45f, 1.0f), "● Project saved");
        }

        ImGui.popFont();
        ImGui.end();
        ImGui.popStyleVar();
    }

    // ------------------------------------------------------------------
    // Command palette: real registrations only
    // ------------------------------------------------------------------

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
            ImGui.openPopup("Command palette##global");
            commandPaletteOpen = false;
        }
        ImGui.setNextWindowSize(480.0f, 360.0f, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal("Command palette##global", null,
                ImGuiWindowFlags.NoCollapse)) return;
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0);
        ImGui.inputTextWithHint("##command-query", "Type a tool or command...",
                commandQuery, ImGuiInputTextFlags.None);
        String query = commandQuery.get().toLowerCase();
        ImGui.beginChild("palette-results", 0.0f, -ImGui.getFrameHeightWithSpacing(), false);
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var registry = pluginLifecycle.host().registry();
            boolean any = false;
            for (EditorToolRegistration tool : registry.toolRegistrations()) {
                if (!query.isBlank() && !tool.label().toLowerCase().contains(query)
                        && !tool.id().toLowerCase().contains(query)) continue;
                any = true;
                if (ImGui.selectable(tool.label() + "  ##tool-" + tool.id())) {
                    activateTool(pluginLifecycle, null, tool.id());
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(tool.id());
            }
            for (EditorCommandRegistration command : registry.commandRegistrations()) {
                if (!query.isBlank() && !command.label().toLowerCase().contains(query)
                        && !command.id().toLowerCase().contains(query)) continue;
                any = true;
                if (ImGui.selectable(command.label() + "  ##command-" + command.id())) {
                    executeCommand(pluginLifecycle, command);
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.isItemHovered()) ImGui.setItemTooltip(command.id());
            }
            if (!any) ImGui.textDisabled("No matching tools or commands.");
        } else {
            ImGui.textDisabled("Plugin host unavailable.");
        }
        ImGui.endChild();
        if (ImGui.button("Close##command-close")) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private void executeCommand(EditorPluginLifecycleManager pluginLifecycle,
                                EditorCommandRegistration registration) {
        try {
            EditorCommand command = pluginLifecycle.host().registry().createCommand(registration.id());
            pluginLifecycle.host().context().session().execute(command);
        } catch (RuntimeException failure) {
            pluginLifecycle.host().context().notifications().error(
                    "Command failed", registration.label() + ": " + failure.getMessage());
        }
    }

    /** Saves only native frontend layout state; world/project data is untouched. */
    public void close() {
        if (!layoutRestored) return;
        // Only the drawer toggle survives a restart. The arrangement is
        // derived every frame, so persisting it could only reintroduce the
        // stale-layout failure this shell was rebuilt to remove.
        layoutStore.save(new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION, "", bottomDrawerVisible));
    }
}
