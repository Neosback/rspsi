package com.rspsi.studio;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.plugin.EditorCommandRegistration;
import com.rspsi.editor.plugin.EditorMenuRegistration;
import com.rspsi.editor.plugin.EditorNotificationService;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorTaskService;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Objects;

/** Viewport-first native Map Editor shell over the neutral editor contracts. */
public final class MapEditorView {
    private static final String DOCKSPACE_NAME = "MapEditorDockspace";
    private static final String TOOL_RAIL_WINDOW = "Tool Rail";
    private static final String VIEWPORT_WINDOW = "Viewport";
    private static final String ACTIVITY_RAIL_WINDOW = "Activity Rail";
    private static final String RIGHT_PANEL_WINDOW = "Inspector / Palette";
    private static final String BOTTOM_WINDOW = "Studio Drawer";
    private static final float STATUS_BAR_HEIGHT = 28.0f;

    /**
     * Rails are application chrome, not user-created dock panels.  Keep the
     * buttons usable while removing the resize/move affordances that make a
     * narrow Retina layout feel unstable.
     */
    private static final int LOCKED_RAIL_FLAGS = ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse;

    /** The map canvas owns the center and has no titled panel frame. */
    private static final int FIXED_VIEWPORT_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoBackground;

    private static final int NO_RAIL_DOCK_CHROME = imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar
            | imgui.internal.flag.ImGuiDockNodeFlags.NoWindowMenuButton
            | imgui.internal.flag.ImGuiDockNodeFlags.NoCloseButton
            | imgui.internal.flag.ImGuiDockNodeFlags.NoDocking;

    private static final int LOCKED_RAIL_DOCK_NODE_FLAGS = ImGuiDockNodeFlags.NoResize
            | ImGuiDockNodeFlags.NoSplit
            | NO_RAIL_DOCK_CHROME;

    private static final int FIXED_VIEWPORT_DOCK_NODE_FLAGS = ImGuiDockNodeFlags.NoSplit
            | NO_RAIL_DOCK_CHROME;

    // Logical-point widths. These are intentionally minimum-sized chrome;
    // the inspector and utility drawer remain the flexible surfaces.
    private static final float TOOL_RAIL_MIN_WIDTH = 112.0f;
    private static final float ACTIVITY_RAIL_MIN_WIDTH = 88.0f;

    private static final String ICON_MOUSE = StudioIcons.SELECT;
    private static final String ICON_TILE = StudioIcons.TILE;
    private static final String ICON_AREA = StudioIcons.AREA;
    private static final String ICON_TERRAIN = StudioIcons.TERRAIN;
    private static final String ICON_WATER = StudioIcons.WATER;
    private static final String ICON_HEIGHT = StudioIcons.HEIGHT;
    private static final String ICON_OBJECT = StudioIcons.OBJECT;
    private static final String ICON_STAMP = StudioIcons.PREFAB;
    private static final String ICON_VALIDATE = StudioIcons.VALIDATE;
    private static final String ICON_SEARCH = StudioIcons.SEARCH;
    private static final String ICON_SETTINGS = StudioIcons.SETTINGS;
    private static final String ICON_MAP = StudioIcons.MAP;
    private static final String ICON_ENVIRONMENT = StudioIcons.ENVIRONMENT;

    private String activeToolId = "select";
    private String activeSelector = "select";
    private String activeActivity = "Tiles";
    private boolean dockLayoutBuilt;
    private boolean layoutRestored;
    private final NativeWorkspaceLayoutStore layoutStore = new NativeWorkspaceLayoutStore();
    private final EditorToolController toolController = new EditorToolController();
    private EditorInputRouter inputRouter;
    private EditorPluginLifecycleManager inputHost;
    private boolean bottomDrawerVisible = true;
    private boolean commandPaletteOpen;
    private final ImString commandQuery = new ImString(128);
    private final ImString objectQuery = new ImString(128);
    private int selectedPlane;
    private boolean gridVisible;
    private boolean minimapVisible = true;
    private boolean autoShape = true;
    private boolean applyOverlay = true;
    private boolean applyUnderlay;
    private boolean applyHeight;
    private boolean applyCollision;
    private int brushSize = 3;

    public void render(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                       NativeSceneViewport viewport, String sceneStatus,
                       Runnable openDashboard, SettingsStore settings,
                       EditorPluginLifecycleManager pluginLifecycle,
                       boolean dirty) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(openDashboard, "dashboard callback");
        Objects.requireNonNull(settings, "settings");

        restoreLayout();
        bindInputRouter(pluginLifecycle);
        openCommandPaletteShortcut();
        routeSceneShortcuts();
        routeToolRailShortcuts(pluginLifecycle, viewport);
        renderMainMenu(cache, openDashboard, settings, pluginLifecycle);
        renderDockHost();
        renderToolRail(pluginLifecycle, viewport);
        renderViewport(cache, plan, viewport, sceneStatus, settings);
        renderActivityRail();
        renderRightPanel(settings, pluginLifecycle);
        renderBottomDrawer(pluginLifecycle);
        renderStatusBar(cache, plan, viewport, dirty);
        renderCommandPalette(pluginLifecycle);
    }

    private void renderMainMenu(LoadedOsrsCacheSession cache, Runnable openDashboard,
                                SettingsStore settings,
                                EditorPluginLifecycleManager pluginLifecycle) {
        if (!ImGui.beginMainMenuBar()) return;
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("Dashboard")) openDashboard.run();
            ImGui.separator();
            ImGui.menuItem("New region", null, false, false);
            ImGui.menuItem("Save", "Ctrl+S", false, false);
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Edit")) {
            ImGui.menuItem("Undo", "Ctrl+Z", false, false);
            ImGui.menuItem("Redo", "Ctrl+Y", false, false);
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("View")) {
            if (ImGui.menuItem("Reset layout")) resetLayout();
            if (ImGui.menuItem("Utility drawer", "Ctrl+Space", bottomDrawerVisible)) {
                bottomDrawerVisible = !bottomDrawerVisible;
            }
            ImGui.menuItem("Command palette", "Ctrl+P");
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Tools")) {
            if (ImGui.menuItem("Select tiles", "T")) activeSelector = "tile";
            if (ImGui.menuItem("Select objects", "O")) activeSelector = "object";
            if (ImGui.menuItem("Select area", "A")) activeSelector = "area";
            ImGui.separator();
            if (ImGui.menuItem("Tile tools")) activeActivity = "Tiles";
            if (ImGui.menuItem("Object tools")) activeActivity = "Objects";
            if (ImGui.menuItem("World map tools")) activeActivity = "World";
            if (ImGui.menuItem("Environment tools")) activeActivity = "Environment";
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Cache")) {
            ImGui.menuItem("Revision " + cache.identity().revision(), null, true, false);
            ImGui.menuItem("Texture diagnostics", null, false, false);
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Settings")) {
            ImGui.menuItem("Renderer settings", null, false, false);
            ImGui.menuItem("Input and shortcuts", null, false, false);
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Help")) {
            ImGui.menuItem("OpenRune Studio roadmap", null, false, false);
            ImGui.endMenu();
        }

        ImGui.sameLine(0.0f, 18.0f);
        if (ImGui.button(ICON_SEARCH + "  Search  Ctrl+P##global-search")) {
            commandPaletteOpen = true;
            commandQuery.clear();
        }
        ImGui.sameLine();
        StudioWidgets.badge("REV " + cache.identity().revision(), 0.39f, 0.33f, 0.20f);
        ImGui.sameLine();
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            renderPluginMenus(pluginLifecycle);
        }
        ImGui.textDisabled("Map Editor");
        ImGui.endMainMenuBar();
    }

    private void renderPluginMenus(EditorPluginLifecycleManager pluginLifecycle) {
        List<EditorMenuRegistration> menus = pluginLifecycle.host().registry().menuRegistrations();
        if (!menus.isEmpty()) {
            ImGui.sameLine();
            ImGui.textDisabled("· " + menus.size() + " plugin commands");
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

    private void renderDockHost() {
        imgui.ImGuiViewport viewport = ImGui.getMainViewport();
        float menuBarHeight = ImGui.getFrameHeight();
        float dockHeight = Math.max(1.0f, viewport.getSizeY() - menuBarHeight - STATUS_BAR_HEIGHT);
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY() + menuBarHeight);
        ImGui.setNextWindowSize(viewport.getSizeX(), dockHeight);
        ImGui.setNextWindowViewport(viewport.getID());
        int hostFlags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoDocking;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.begin("MapEditorDockHost", hostFlags);
        ImGui.popStyleVar(3);

        int dockspaceId = ImGui.getID(DOCKSPACE_NAME);
        if (!dockLayoutBuilt) {
            buildDefaultLayout(dockspaceId, viewport.getSizeX(), dockHeight);
            dockLayoutBuilt = true;
        }
        ImGui.dockSpace(dockspaceId, 0.0f, 0.0f, ImGuiDockNodeFlags.PassthruCentralNode);
        ImGui.end();
    }

    private void restoreLayout() {
        if (layoutRestored) return;
        layoutRestored = true;
        NativeWorkspaceLayoutStore.State saved = layoutStore.load();
        if (saved == null) return;
        if (!saved.nativeIni().isBlank()) ImGui.loadIniSettingsFromMemory(saved.nativeIni());
        bottomDrawerVisible = saved.bottomDrawerVisible();
        // Loading an ImGui ini already restores the user's docking tree. Do
        // not immediately replace it with the default Displee-style split.
        dockLayoutBuilt = !saved.nativeIni().isBlank();
    }

    private void resetLayout() {
        layoutStore.reset();
        ImGui.getIO().setIniFilename(null);
        dockLayoutBuilt = false;
        bottomDrawerVisible = true;
    }

    private void buildDefaultLayout(int dockspaceId, float width, float height) {
        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.PassthruCentralNode);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, width, height);

        ImInt leftId = new ImInt();
        ImInt remainingId = new ImInt();
        float toolRailRatio = minimumWidthRatio(width, TOOL_RAIL_MIN_WIDTH, 0.075f, 0.12f);
        imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, toolRailRatio,
                leftId, remainingId);
        ImInt rightId = new ImInt();
        ImInt centerId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(remainingId.get(), ImGuiDir.Right, 0.30f,
                rightId, centerId);
        ImInt activityId = new ImInt();
        ImInt inspectorId = new ImInt();
        float activityRailRatio = minimumWidthRatio(width * 0.30f,
                ACTIVITY_RAIL_MIN_WIDTH, 0.16f, 0.24f);
        imgui.internal.ImGui.dockBuilderSplitNode(rightId.get(), ImGuiDir.Left, activityRailRatio,
                activityId, inspectorId);
        ImInt bottomId = new ImInt();
        ImInt viewportId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(centerId.get(), ImGuiDir.Down, 0.20f,
                bottomId, viewportId);

        imgui.internal.ImGui.dockBuilderDockWindow(TOOL_RAIL_WINDOW, leftId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(VIEWPORT_WINDOW, viewportId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(ACTIVITY_RAIL_WINDOW, activityId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(RIGHT_PANEL_WINDOW, inspectorId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(BOTTOM_WINDOW, bottomId.get());
        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);

        lockDockNode(leftId.get(), LOCKED_RAIL_DOCK_NODE_FLAGS);
        lockDockNode(activityId.get(), LOCKED_RAIL_DOCK_NODE_FLAGS);
        lockDockNode(viewportId.get(), FIXED_VIEWPORT_DOCK_NODE_FLAGS);
    }

    /**
     * Apply the lock to the live node rather than only to the default layout.
     * This also covers a layout restored from the versioned ImGui ini.
     */
    private static void lockCurrentDockNode() {
        lockDockNode(ImGui.getWindowDockID(), LOCKED_RAIL_DOCK_NODE_FLAGS);
    }

    private static void lockDockNode(int nodeId, int flags) {
        if (nodeId == 0) return;
        var node = imgui.internal.ImGui.dockBuilderGetNode(nodeId);
        if (node != null) node.addLocalFlags(flags);
    }

    private static void lockCurrentViewportDockNode() {
        lockDockNode(ImGui.getWindowDockID(), FIXED_VIEWPORT_DOCK_NODE_FLAGS);
    }

    private static float minimumWidthRatio(float parentWidth, float minimumWidth,
                                            float minimumRatio, float maximumRatio) {
        float ratio = minimumWidth / Math.max(1.0f, parentWidth);
        return Math.min(maximumRatio, Math.max(minimumRatio, ratio));
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

    private void routeSceneShortcuts() {
        if (inputRouter == null || ImGui.getIO().getWantTextInput()) return;
        var io = ImGui.getIO();
        int[] keys = {ImGuiKey.Q, ImGuiKey.T, ImGuiKey.O, ImGuiKey.A};
        String[] names = {"Q", "T", "O", "A"};
        for (int index = 0; index < keys.length; index++) {
            if (ImGui.isKeyPressed(keys[index], false)) {
                inputRouter.key(new EditorKeyEvent(names[index], true, false,
                        io.getKeyShift(), io.getKeyCtrl(), io.getKeyAlt(), io.getKeySuper()), false);
            }
        }
    }

    private void renderToolRail(EditorPluginLifecycleManager pluginLifecycle,
                                NativeSceneViewport viewport) {
        ImGui.begin(TOOL_RAIL_WINDOW, LOCKED_RAIL_FLAGS);
        lockCurrentDockNode();
        ImGui.dummy(0.0f, 4.0f);
        if (StudioWidgets.railButton("selector", ICON_MOUSE, "Select — interact, inspect", "select".equals(activeSelector), "1")) {
            activateSelector(pluginLifecycle, viewport, "select");
        }
        if (StudioWidgets.railButton("terrain-tool", ICON_TERRAIN,
                "Terrain — paint, slope, blend", "terrain".equals(activeSelector), "2")) {
            activateSelector(pluginLifecycle, viewport, "terrain");
        }
        if (StudioWidgets.railButton("object-selector", ICON_OBJECT,
                "Objects — place, move, delete", "object".equals(activeSelector), "3")) {
            activateSelector(pluginLifecycle, viewport, "object");
        }
        if (StudioWidgets.railButton("water-tool", ICON_WATER,
                "Water — rivers, lakes, banks", "water".equals(activeSelector), "4")) {
            activateSelector(pluginLifecycle, viewport, "water");
        }
        if (StudioWidgets.railButton("area-selector", ICON_AREA,
                "Area — multi-tile select · Alt+click = sample", "area".equals(activeSelector), "5")) {
            activateSelector(pluginLifecycle, viewport, "area");
        }
        ImGui.separator();
        if (StudioWidgets.railButton("selection-settings", ICON_SETTINGS,
                "Selection settings", false, null)) {
            activeActivity = "Environment";
        }
        ImGui.end();
    }

    /**
     * The five primary tools are always reachable by number, independent of
     * plugin/tool registration state — a newcomer should never need to learn
     * a registration ID to switch what they are doing.
     */
    private void routeToolRailShortcuts(EditorPluginLifecycleManager pluginLifecycle,
                                        NativeSceneViewport viewport) {
        if (ImGui.getIO().getWantTextInput()) return;
        if (ImGui.isKeyPressed(ImGuiKey._1, false)) activateSelector(pluginLifecycle, viewport, "select");
        else if (ImGui.isKeyPressed(ImGuiKey._2, false)) activateSelector(pluginLifecycle, viewport, "terrain");
        else if (ImGui.isKeyPressed(ImGuiKey._3, false)) activateSelector(pluginLifecycle, viewport, "object");
        else if (ImGui.isKeyPressed(ImGuiKey._4, false)) activateSelector(pluginLifecycle, viewport, "water");
        else if (ImGui.isKeyPressed(ImGuiKey._5, false)) activateSelector(pluginLifecycle, viewport, "area");
    }

    private void activateSelector(EditorPluginLifecycleManager pluginLifecycle,
                                  NativeSceneViewport viewport, String selector) {
        activeSelector = selector;
        activeToolId = "selector." + selector;
        // Picking a primary tool also reveals its context panel and drawer —
        // a newcomer should never have to separately click a second rail to
        // see the controls for the tool they just chose. Select/Area are
        // modifiers over whatever context is already active, so they leave
        // it alone.
        switch (selector) {
            case "terrain" -> activeActivity = "Tiles";
            case "object" -> activeActivity = "Objects";
            case "water" -> activeActivity = "Water";
            default -> { }
        }
        // Keep the existing neutral selection registration as the shared
        // scene interaction surface until selector-specific commands land.
        activateTool(pluginLifecycle, viewport, "selection.box", activeToolId);
    }

    private void activateTool(EditorPluginLifecycleManager pluginLifecycle,
                              NativeSceneViewport viewport, String registrationId,
                              String displayId) {
        activeToolId = displayId;
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

    private void renderViewport(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                NativeSceneViewport viewport, String sceneStatus,
                                SettingsStore settings) {
        ImGui.begin(VIEWPORT_WINDOW, FIXED_VIEWPORT_FLAGS);
        lockCurrentViewportDockNode();
        renderViewportToolbar(settings);
        ImGui.separator();
        if (plan == null) {
            ImGui.text(sceneStatus == null ? "Preparing scene..." : sceneStatus);
            ImGui.progressBar(0.35f, -1, 0, "Scene data");
        } else {
            var stats = viewport.statistics();
            ImGui.pushFont(StudioFonts.mono(), 1.0f);
            ImGui.textDisabled("Revision " + cache.identity().revision() + "  ·  "
                    + stats.renderedTriangles() + " tris  ·  " + stats.drawCalls() + " draws"
                    + "  ·  GL " + stats.firstGlError()
                    + (stats.missingTextures() == 0 ? "" : "  ·  missing tex " + stats.missingTextures()));
            ImGui.popFont();
            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()),
                    settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES),
                    new RenderConfigCompiler().compile(settings.snapshot()).presentation());
        }
        ImGui.end();
    }

    private void renderViewportToolbar(SettingsStore settings) {
        ImGui.textDisabled("VIEW");
        ImGui.sameLine();
        ImGui.button("3D##view-3d");
        ImGui.sameLine();
        ImGui.button("2D##view-2d");
        ImGui.sameLine();
        ImGui.button("POV##view-pov");
        ImGui.sameLine(0.0f, 12.0f);
        ImGui.textDisabled("PLANE");
        for (int plane = 0; plane < 4; plane++) {
            ImGui.sameLine();
            if (ImGui.radioButton(String.valueOf(plane), selectedPlane == plane)) selectedPlane = plane;
        }
        ImGui.sameLine(0.0f, 12.0f);
        ImBoolean objects = new ImBoolean(settings.snapshot().get(RenderSettingKeys.OBJECTS_VISIBLE));
        if (ImGui.checkbox("Objects", objects)) settings.set(RenderSettingKeys.OBJECTS_VISIBLE, objects.get());
        ImGui.sameLine();
        ImBoolean roofs = new ImBoolean(settings.snapshot().get(RenderSettingKeys.ROOFS_VISIBLE));
        if (ImGui.checkbox("Roofs", roofs)) settings.set(RenderSettingKeys.ROOFS_VISIBLE, roofs.get());
        ImGui.sameLine();
        if (ImGui.checkbox("Grid", gridVisible)) gridVisible = !gridVisible;
        ImGui.sameLine();
        if (ImGui.checkbox("Minimap", minimapVisible)) minimapVisible = !minimapVisible;
        ImGui.sameLine(0.0f, 12.0f);
        if (ImGui.smallButton("Home##camera-home")) ImGui.setItemTooltip("Home: frame the loaded region");
        ImGui.sameLine();
        if (ImGui.smallButton("Orbit##camera-orbit")) ImGui.setItemTooltip("Orbit camera");
    }

    private void renderRightPanel(SettingsStore settings,
                                   EditorPluginLifecycleManager pluginLifecycle) {
        ImGui.begin(RIGHT_PANEL_WINDOW, ImGuiWindowFlags.NoCollapse);
        ImGui.beginChild("context-panel", 0.0f, 0.0f, false);
        switch (activeActivity) {
            case "Tiles" -> renderTerrainContext();
            case "Objects" -> renderObjectContext();
            case "Water" -> renderWaterContext();
            case "World" -> renderWorldContext();
            case "Environment" -> renderEnvironmentContext(settings);
            case "Validation" -> renderValidationContext();
            default -> renderSelectionContext(pluginLifecycle);
        }
        ImGui.endChild();
        ImGui.end();
    }

    private void renderActivityRail() {
        ImGui.begin(ACTIVITY_RAIL_WINDOW, LOCKED_RAIL_FLAGS);
        lockCurrentDockNode();
        String[] labels = {"Tiles", "Objects", "Water", "World", "Environment", "Validation"};
        String[] tooltips = {"Tile tools", "Object tools", "Water tools", "World map tools",
                "Environment tools", "Validation"};
        String[] icons = {ICON_TILE, ICON_OBJECT, ICON_WATER, ICON_MAP, ICON_ENVIRONMENT, ICON_VALIDATE};
        for (int index = 0; index < labels.length; index++) {
            if (StudioWidgets.railButton("activity-" + labels[index], icons[index], labels[index],
                    labels[index].equals(activeActivity), tooltips[index])) activeActivity = labels[index];
        }
        ImGui.end();
    }

    private void renderSelectionContext(EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.heading("Selection", "Context follows the hovered or selected scene item.");
        StudioWidgets.info("Nothing selected");
        StudioWidgets.section("Active tool");
        ImGui.text(activeSelector + " selector");
        ImGui.textDisabled(activeToolId);
        StudioWidgets.section("Editor state");
        ImGui.textDisabled(pluginLifecycle == null ? "Plugin host unavailable" : "Plugin contributions live");
    }

    private void renderTerrainContext() {
        StudioWidgets.heading("Terrain painter", "Paint only the channels you enable.");
        StudioWidgets.searchHint("Search overlays and underlays...");
        ImBoolean overlay = new ImBoolean(applyOverlay);
        if (ImGui.checkbox("Overlay##paint-channel", overlay)) applyOverlay = overlay.get();
        ImBoolean underlay = new ImBoolean(applyUnderlay);
        if (ImGui.checkbox("Underlay##paint-channel", underlay)) applyUnderlay = underlay.get();
        ImBoolean height = new ImBoolean(applyHeight);
        if (ImGui.checkbox("Height##paint-channel", height)) applyHeight = height.get();
        ImBoolean collision = new ImBoolean(applyCollision);
        if (ImGui.checkbox("Collision##paint-channel", collision)) applyCollision = collision.get();
        ImBoolean shape = new ImBoolean(autoShape);
        if (ImGui.checkbox("Auto-shape / smart blending##paint-channel", shape)) autoShape = shape.get();
        StudioWidgets.section("Brush");
        int[] size = {brushSize};
        if (ImGui.sliderInt("Size", size, 1, 12)) brushSize = size[0];
        float[] falloff = {0.65f};
        ImGui.sliderFloat("Falloff", falloff, 0.0f, 1.0f);
        StudioWidgets.section("In This Region");
        ImGui.textDisabled("Region palette will show cache-backed swatches here.");
        ImGui.button("Stone path##region-swatch", -1.0f, 28.0f);
        ImGui.button("Grass edge##region-swatch", -1.0f, 28.0f);
        ImGui.button("Water / animated##region-swatch", -1.0f, 28.0f);
    }

    private void renderWaterContext() {
        StudioWidgets.heading("Water", "Draw a path for the river or lake — banks blend automatically.");
        ImGui.radioButton("River path##water-mode", true);
        ImGui.sameLine();
        ImGui.radioButton("Lake / pool##water-mode", false);
        StudioWidgets.section("Shape");
        int[] width = {3};
        ImGui.sliderInt("Width", width, 1, 12);
        float[] flow = {0.0f};
        ImGui.sliderFloat("Flow direction", flow, 0.0f, 360.0f);
        ImBoolean autoBank = new ImBoolean(true);
        ImGui.checkbox("Auto-blend banks into surrounding terrain##water-channel", autoBank);
        ImGui.checkbox("Animate surface##water-channel", new ImBoolean(true));
        StudioWidgets.section("Crossings");
        ImGui.button("Place bridge##water-bridge", -1.0f, 28.0f);
        ImGui.textDisabled("A bridge snaps to the river's centerline and sets the effective plane.");
        StudioWidgets.section("In This Region");
        ImGui.textDisabled("Water swatches will show cache-backed materials here.");
        ImGui.button("Underground river##region-swatch", -1.0f, 28.0f);
        ImGui.button("Still pool##region-swatch", -1.0f, 28.0f);
    }

    private void renderHeightContext() {
        StudioWidgets.heading("Height sculptor", "Use preview/apply for multi-tile edits.");
        ImGui.radioButton("Raise / lower", true);
        ImGui.radioButton("Smooth / relax", false);
        ImGui.radioButton("Flatten", false);
        ImGui.radioButton("Linear ramp", false);
        ImGui.radioButton("Gradient shade", false);
        StudioWidgets.section("Ramp endpoints");
        ImGui.textDisabled("Start  0     End  -180");
        ImGui.sliderFloat("Strength", new float[]{0.50f}, 0.0f, 1.0f);
        ImGui.sliderInt("Radius", new int[]{3}, 1, 12);
        ImGui.button("Preview change##height-preview", -1.0f, 28.0f);
        ImGui.button("Apply as undoable command##height-apply", -1.0f, 28.0f);
    }

    private void renderObjectContext() {
        StudioWidgets.heading("Object placement", "Search by name, tag, or cache ID.");
        ImGui.inputTextWithHint("##object-search", ICON_SEARCH + "  rock, door, tree...", objectQuery);
        ImGui.textDisabled("Live model preview");
        ImGui.button("[ 3D preview ]##object-preview", -1.0f, 120.0f);
        StudioWidgets.section("Placement");
        ImGui.combo("Rotation", new ImInt(0), new String[]{"0°", "90°", "180°", "270°"});
        ImGui.checkbox("Snap to tile grid", new ImBoolean(true));
        ImGui.checkbox("Random 90° rotation", new ImBoolean(false));
        ImGui.checkbox("Show collision bounds", new ImBoolean(true));
        StudioWidgets.section("In This Region");
        ImGui.textDisabled("Rocks · plants · walls · doors");
    }

    private void renderPrefabContext() {
        StudioWidgets.heading("Prefabs / stamps", "Capture a selection and reuse it safely.");
        ImGui.button("Box select##prefab-box", -1.0f, 28.0f);
        ImGui.button("Lasso select##prefab-lasso", -1.0f, 28.0f);
        ImGui.button("Save selection as prefab##prefab-save", -1.0f, 28.0f);
        StudioWidgets.section("Recent stamps");
        ImGui.button("Yanille guardhouse##prefab-item", -1.0f, 28.0f);
        ImGui.button("Wooden bridge##prefab-item", -1.0f, 28.0f);
        ImGui.button("Dungeon corridor##prefab-item", -1.0f, 28.0f);
        ImGui.textDisabled("Ghost preview · R rotates · click stamps");
    }

    private void renderEnvironmentContext(SettingsStore settings) {
        StudioWidgets.heading("Environment & visibility", "Presentation controls do not mutate map data.");
        SettingsSnapshot snapshot = settings.snapshot();
        toggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain", snapshot);
        toggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Objects", snapshot);
        toggle(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision", snapshot);
        toggle(settings, RenderSettingKeys.ROOFS_VISIBLE, "Roofs", snapshot);
        ImGui.checkbox("Bridge / effective plane", new ImBoolean(true));
        ImGui.checkbox("Occluders", new ImBoolean(false));
        StudioWidgets.section("Renderer");
        msaaSlider(settings, snapshot);
        StudioWidgets.info("Deferred: full environment authoring and positional audio.");
    }

    private void renderOutlinerContext() {
        StudioWidgets.heading("World outliner", "Navigate the loaded region without changing scene truth.");
        if (ImGui.treeNode("Region")) {
            ImGui.bulletText("Plane 0");
            ImGui.bulletText("Terrain");
            ImGui.bulletText("Objects");
            ImGui.bulletText("Markers");
            ImGui.treePop();
        }
        ImGui.textDisabled("Selection and visibility follow the shared scene snapshot.");
    }

    private void renderWorldContext() {
        StudioWidgets.heading("World map tools", "Navigate regions and place world-level markers.");
        ImGui.button("Jump to region##world-jump", -1.0f, 30.0f);
        ImGui.button("Map icons##world-icons", -1.0f, 30.0f);
        ImGui.button("Outliner##world-outliner", -1.0f, 30.0f);
        StudioWidgets.section("World context");
        ImGui.textDisabled("Region boundaries, planes, bridges, and effective levels.");
    }

    private void renderValidationContext() {
        StudioWidgets.heading("Validation", "Problems remain actionable and near their source.");
        StudioWidgets.badge("READY", 0.30f, 0.55f, 0.35f);
        ImGui.text("No shell validation issues");
        StudioWidgets.section("Deferred checks");
        ImGui.bulletText("Terrain holes and map boundaries");
        ImGui.bulletText("Texture coverage and cache provenance");
        ImGui.bulletText("Bridge/effective-plane consistency");
    }

    private void renderBottomDrawer(EditorPluginLifecycleManager pluginLifecycle) {
        if (!bottomDrawerVisible) return;
        ImGui.begin(BOTTOM_WINDOW, ImGuiWindowFlags.NoCollapse);
        if ("Tiles".equals(activeActivity)) {
            renderTerrainDrawer();
        } else if ("Objects".equals(activeActivity)) {
            renderAssetDrawer();
        } else if ("Water".equals(activeActivity)) {
            renderWaterDrawer();
        } else {
            renderUtilityTabs(pluginLifecycle);
        }
        ImGui.end();
    }

    private void renderTerrainDrawer() {
        ImGui.textDisabled("TERRAIN PAINTER");
        ImGui.sameLine();
        ImGui.text("Channels");
        ImGui.sameLine();
        if (ImGui.checkbox("Overlay", new ImBoolean(applyOverlay))) applyOverlay = !applyOverlay;
        ImGui.sameLine();
        if (ImGui.checkbox("Underlay", new ImBoolean(applyUnderlay))) applyUnderlay = !applyUnderlay;
        ImGui.sameLine();
        if (ImGui.checkbox("Shape", new ImBoolean(autoShape))) autoShape = !autoShape;
        ImGui.sameLine();
        if (ImGui.checkbox("Height", new ImBoolean(applyHeight))) applyHeight = !applyHeight;
        ImGui.sameLine();
        if (ImGui.checkbox("Collision", new ImBoolean(applyCollision))) applyCollision = !applyCollision;
        ImGui.separator();
        ImGui.textDisabled("Yanille palette");
        ImGui.sameLine();
        ImGui.button("Stone path##drawer-swatch");
        ImGui.sameLine();
        ImGui.button("Road cobble##drawer-swatch");
        ImGui.sameLine();
        ImGui.button("Grass edge##drawer-swatch");
    }

    private void renderHeightDrawer() {
        ImGui.textDisabled("HEIGHT SCULPTOR");
        ImGui.sameLine();
        ImGui.radioButton("Ramp", true);
        ImGui.sameLine();
        ImGui.radioButton("Smooth", false);
        ImGui.sameLine();
        ImGui.radioButton("Flatten", false);
        ImGui.sameLine();
        ImGui.radioButton("Gradient", false);
        ImGui.sameLine();
        ImGui.textDisabled("Preview before apply · all edits are undoable");
    }

    private void renderWaterDrawer() {
        ImGui.textDisabled("WATER TOOL");
        ImGui.sameLine();
        ImGui.radioButton("River##drawer-water-mode", true);
        ImGui.sameLine();
        ImGui.radioButton("Lake##drawer-water-mode", false);
        ImGui.sameLine();
        ImGui.checkbox("Auto-blend banks", new ImBoolean(true));
        ImGui.sameLine();
        ImGui.button("Place bridge##drawer-bridge");
        ImGui.sameLine();
        ImGui.textDisabled("Ghost preview · click to commit the path");
    }

    private void renderAssetDrawer() {
        ImGui.textDisabled("ASSET BROWSER");
        ImGui.sameLine();
        ImGui.text("In This Region");
        ImGui.sameLine();
        ImGui.button("Rocks##asset-drawer");
        ImGui.sameLine();
        ImGui.button("Plants##asset-drawer");
        ImGui.sameLine();
        ImGui.button("Walls##asset-drawer");
        ImGui.sameLine();
        ImGui.button("Doors##asset-drawer");
        ImGui.sameLine();
        ImGui.textDisabled("Search and previews are cache-backed");
    }

    private void renderUtilityTabs(EditorPluginLifecycleManager pluginLifecycle) {
        if (!ImGui.beginTabBar("studio-drawer-tabs")) return;
        if (ImGui.beginTabItem("Assets")) {
            StudioWidgets.info("Asset browser is shared by all tools and providers.");
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("History")) {
            StudioWidgets.info("Undo history is owned by EditorSession.");
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("Validation")) {
            StudioWidgets.info("WorldValidator diagnostics appear here.");
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("Tasks")) {
            if (pluginLifecycle == null || pluginLifecycle.host() == null) {
                ImGui.textDisabled("No active workspace tasks.");
            } else {
                for (EditorTaskService.TaskSnapshot task
                        : pluginLifecycle.host().context().tasks().snapshots()) {
                    ImGui.text(task.label() + " · " + task.state());
                    ImGui.progressBar((float) task.progress(), -1, 0, task.message());
                }
            }
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("Messages")) {
            if (pluginLifecycle == null || pluginLifecycle.host() == null) {
                ImGui.textDisabled("No workspace notifications.");
            } else {
                for (EditorNotificationService.Notification notification
                        : pluginLifecycle.host().context().notifications().recent()) {
                    ImGui.text(notification.level() + " · " + notification.title());
                    ImGui.textWrapped(notification.message());
                }
            }
            ImGui.endTabItem();
        }
        ImGui.endTabBar();
    }

    private void renderStatusBar(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                 NativeSceneViewport viewport, boolean dirty) {
        imgui.ImGuiViewport main = ImGui.getMainViewport();
        ImGui.setNextWindowPos(main.getPosX(), main.getPosY() + main.getSizeY() - STATUS_BAR_HEIGHT, ImGuiCond.Always);
        ImGui.setNextWindowSize(main.getSizeX(), STATUS_BAR_HEIGHT, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.NoSavedSettings;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12.0f, 5.0f);
        ImGui.begin("StudioStatusBar", flags);
        ImGui.pushFont(StudioFonts.mono(), 1.0f);
        ImGui.text("REV " + cache.identity().revision() + "  ·  PLANE " + selectedPlane
                + "  ·  " + (dirty ? "DIRTY" : "CLEAN")
                + "  ·  " + (plan == null ? "loading scene" : "scene ready"));
        ImGui.sameLine();
        var stats = viewport.statistics();
        ImGui.textDisabled("  " + stats.renderedTriangles() + " tris  ·  " + stats.drawCalls() + " draws  ·  GL "
                + stats.firstGlError() + (stats.missingTextures() == 0
                ? "" : "  ·  missing tex " + stats.missingTextures()));
        ImGui.popFont();
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderCommandPalette(EditorPluginLifecycleManager pluginLifecycle) {
        if (commandPaletteOpen) {
            ImGui.openPopup("Command palette##global");
            commandPaletteOpen = false;
        }
        if (!ImGui.beginPopupModal("Command palette##global", new ImBoolean(true),
                ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.text("Search commands, tools, regions, and assets");
        ImGui.inputTextWithHint("##command-query", ICON_SEARCH + "  Type a command...", commandQuery);
        String query = commandQuery.get().toLowerCase();
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var registry = pluginLifecycle.host().registry();
            StudioWidgets.section("Commands");
            for (EditorCommandRegistration command : registry.commandRegistrations()) {
                if (!query.isBlank() && !command.label().toLowerCase().contains(query)
                        && !command.id().toLowerCase().contains(query)) continue;
                if (ImGui.selectable(command.label() + "##command-" + command.id())) {
                    executeCommand(pluginLifecycle, command);
                    ImGui.closeCurrentPopup();
                }
            }
            StudioWidgets.section("Tools");
            for (EditorToolRegistration tool : registry.toolRegistrations()) {
                if (!query.isBlank() && !tool.label().toLowerCase().contains(query)) continue;
                if (ImGui.selectable(tool.label() + "##tool-" + tool.id())) {
                    activeToolId = tool.id();
                    activeActivity = "Tiles";
                    ImGui.closeCurrentPopup();
                }
            }
        }
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

    private static void toggle(SettingsStore settings, SettingKey<Boolean> key,
                               String label, SettingsSnapshot snapshot) {
        ImBoolean value = new ImBoolean(snapshot.get(key));
        if (ImGui.checkbox(label, value)) settings.set(key, value.get());
    }

    private static void msaaSlider(SettingsStore settings, SettingsSnapshot snapshot) {
        int[] value = {snapshot.get(RenderSettingKeys.MSAA_SAMPLES)};
        if (ImGui.sliderInt("MSAA samples", value, 0, 8)) {
            settings.set(RenderSettingKeys.MSAA_SAMPLES, value[0]);
        }
        ImGui.textDisabled("Unsupported renderer features remain explicitly gated.");
    }

    /** Saves only native frontend layout state; world/project data is untouched. */
    public void close() {
        if (!layoutRestored) return;
        layoutStore.save(new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION,
                ImGui.saveIniSettingsToMemory(), bottomDrawerVisible));
    }
}
