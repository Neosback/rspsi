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
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSliderFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private static final String DOCKSPACE_NAME = "MapEditorDockspace";
    private static final String TOOL_RAIL_WINDOW = "Tool Rail";
    private static final String VIEWPORT_WINDOW = "Viewport";
    private static final String RIGHT_PANEL_WINDOW = "Tool Options";
    private static final String BOTTOM_WINDOW = "Studio Drawer";
    private static final float STATUS_BAR_HEIGHT = 26.0f;

    /**
     * Rails are application chrome, not user-created dock panels. Keep the
     * buttons usable while removing the resize/move affordances that make a
     * narrow layout feel unstable.
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

    private static final float TOOL_RAIL_MIN_WIDTH = 64.0f;

    /**
     * Panels are fixed furniture, not floating tools. NoMove keeps a docked
     * panel from being dragged out by its tab, so the layout the user learns
     * is the layout that stays - there is no workflow here that benefits
     * from tearing the inspector off into its own window.
     */
    private static final int DOCKED_PANEL_FLAGS = ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoMove;

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
            new RailTool("terrain", ICON_TERRAIN, "Terrain", "2",
                    new String[]{"terrain.paint-underlay", "terrain.paint-overlay",
                            "terrain.raise", "terrain.lower", "terrain.flatten",
                            "terrain.smooth", "terrain.ramp", "terrain.flags"}),
            new RailTool("objects", ICON_OBJECT, "Objects", "3",
                    new String[]{"object.place", "object.move", "object.rotate",
                            "object.duplicate", "object.delete"}),
    };

    private String activeRailId = "select";
    private String activeToolId = "selection.box";
    private NativeSceneViewport viewport;
    private boolean dockLayoutBuilt;
    private boolean layoutRestored;
    private final NativeWorkspaceLayoutStore layoutStore = new NativeWorkspaceLayoutStore();
    private final EditorToolController toolController = new EditorToolController();
    private EditorInputRouter inputRouter;
    private EditorPluginLifecycleManager inputHost;
    private boolean bottomDrawerVisible = true;
    private boolean commandPaletteOpen;
    private final ImString commandQuery = new ImString(128);
    private int drawerTab;

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
        routeRailShortcuts(pluginLifecycle);
        routeSessionShortcuts(pluginLifecycle);
        renderMainMenu(cache, openDashboard, settings, pluginLifecycle);
        renderDockHost();
        renderToolRail(pluginLifecycle, viewport);
        renderViewport(cache, plan, viewport, sceneStatus, settings);
        renderRightPanel(cache, settings, pluginLifecycle);
        renderBottomDrawer(pluginLifecycle);
        renderStatusBar(cache, plan, viewport, dirty, settings, pluginLifecycle);
        renderCommandPalette(pluginLifecycle);
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
        if (ImGui.beginMenu("Help")) {
            ImGui.menuItem("OpenRune Studio — Map Editor", null, true, false);
            ImGui.endMenu();
        }

        ImGui.sameLine(0.0f, 18.0f);
        if (ImGui.button("Search  Ctrl+P##global-search")) {
            commandPaletteOpen = true;
            commandQuery.clear();
        }
        ImGui.sameLine();
        StudioWidgets.badge("REV " + cache.identity().revision(), 0.39f, 0.33f, 0.20f);
        ImGui.endMainMenuBar();
    }

    private void visibilityToggle(SettingsStore settings, SettingKey<Boolean> key, String label) {
        ImBoolean value = new ImBoolean(settings.snapshot().get(key));
        if (ImGui.menuItem(label, null, value.get())) settings.set(key, !value.get());
    }

    // ------------------------------------------------------------------
    // Docking scaffold
    // ------------------------------------------------------------------

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
        float toolRailRatio = minimumWidthRatio(width, TOOL_RAIL_MIN_WIDTH, 0.05f, 0.08f);
        imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, toolRailRatio,
                leftId, remainingId);
        ImInt rightId = new ImInt();
        ImInt centerId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(remainingId.get(), ImGuiDir.Right, 0.24f,
                rightId, centerId);
        ImInt bottomId = new ImInt();
        ImInt viewportId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(centerId.get(), ImGuiDir.Down, 0.18f,
                bottomId, viewportId);

        imgui.internal.ImGui.dockBuilderDockWindow(TOOL_RAIL_WINDOW, leftId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(VIEWPORT_WINDOW, viewportId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(RIGHT_PANEL_WINDOW, rightId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(BOTTOM_WINDOW, bottomId.get());
        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);

        lockDockNode(leftId.get(), LOCKED_RAIL_DOCK_NODE_FLAGS);
        lockDockNode(viewportId.get(), FIXED_VIEWPORT_DOCK_NODE_FLAGS);
    }

    private static void lockDockNode(int nodeId, int flags) {
        if (nodeId == 0) return;
        var node = imgui.internal.ImGui.dockBuilderGetNode(nodeId);
        if (node != null) node.addLocalFlags(flags);
    }

    private static void lockCurrentDockNode() {
        lockDockNode(ImGui.getWindowDockID(), LOCKED_RAIL_DOCK_NODE_FLAGS);
    }

    private static void lockCurrentViewportDockNode() {
        lockDockNode(ImGui.getWindowDockID(), FIXED_VIEWPORT_DOCK_NODE_FLAGS);
    }

    private static float minimumWidthRatio(float parentWidth, float minimumWidth,
                                           float minimumRatio, float maximumRatio) {
        float ratio = minimumWidth / Math.max(1.0f, parentWidth);
        return Math.min(maximumRatio, Math.max(minimumRatio, ratio));
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

    /** Command-palette activation without a viewport hand-off. */
    private void activateTool(EditorPluginLifecycleManager pluginLifecycle,
                              String registrationId) {
        activateTool(pluginLifecycle, null, registrationId);
    }

    // ------------------------------------------------------------------
    // Tool rail (three real tool families)
    // ------------------------------------------------------------------

    private void renderToolRail(EditorPluginLifecycleManager pluginLifecycle,
                                NativeSceneViewport viewport) {
        ImGui.begin(TOOL_RAIL_WINDOW, LOCKED_RAIL_FLAGS);
        lockCurrentDockNode();
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
        ImGui.end();
    }

    // ------------------------------------------------------------------
    // Viewport
    // ------------------------------------------------------------------

    private void renderViewport(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                NativeSceneViewport viewport, String sceneStatus,
                                SettingsStore settings) {
        ImGui.begin(VIEWPORT_WINDOW, FIXED_VIEWPORT_FLAGS);
        lockCurrentViewportDockNode();
        renderViewportToolbar(settings);
        ImGui.separator();
        if (plan == null) {
            ImGui.text(sceneStatus == null ? "Preparing scene..." : sceneStatus);
        } else {
            var stats = viewport.statistics();
            ImGui.pushFont(StudioFonts.mono(), 1.0f);
            ImGui.textDisabled(stats.renderedTriangles() + " tris  ·  " + stats.drawCalls()
                    + " draws  ·  GL " + stats.firstGlError()
                    + (stats.missingTextures() == 0 ? "" : "  ·  missing tex " + stats.missingTextures()));
            ImGui.popFont();
            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()),
                    settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES),
                    new RenderConfigCompiler().compile(settings.snapshot()).presentation());
        }
        ImGui.end();
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

    // ------------------------------------------------------------------
    // Right panel: real tool settings + live selection
    // ------------------------------------------------------------------

    private void renderRightPanel(LoadedOsrsCacheSession cache, SettingsStore settings,
                                  EditorPluginLifecycleManager pluginLifecycle) {
        ImGui.begin(RIGHT_PANEL_WINDOW, DOCKED_PANEL_FLAGS);
        ImGui.beginChild("tool-options", 0.0f, 0.0f, false);

        renderPickInspector(cache, pluginLifecycle);
        ImGui.separator();
        renderActiveTool(pluginLifecycle);
        ImGui.separator();
        renderToolSettings(pluginLifecycle);
        ImGui.separator();
        renderSelectionPanel(pluginLifecycle);

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

    private static void renderEditorSetting(EditorSetting setting) {
        switch (setting.type()) {
            case INTEGER -> {
                int[] value = {((Number) setting.value()).intValue()};
                if (ImGui.sliderInt(setting.label() + "##" + setting.id(), value,
                        (int) setting.minimum(), (int) setting.maximum())) {
                    setting.setValue(value[0]);
                }
            }
            case DECIMAL -> {
                float[] value = {((Number) setting.value()).floatValue()};
                if (ImGui.sliderFloat(setting.label() + "##" + setting.id(), value,
                        (float) setting.minimum(), (float) setting.maximum(),
                        "%.2f", ImGuiSliderFlags.None)) {
                    setting.setValue(value[0]);
                }
            }
            case BOOLEAN -> {
                ImBoolean value = new ImBoolean(Boolean.TRUE.equals(setting.value()));
                if (ImGui.checkbox(setting.label() + "##" + setting.id(), value)) {
                    setting.setValue(value.get());
                }
            }
            case ENUM -> {
                List<String> options = setting.options();
                int index = Math.max(0, options.indexOf(String.valueOf(setting.value())));
                ImInt selected = new ImInt(index);
                if (ImGui.combo(setting.label() + "##" + setting.id(), selected,
                        options.toArray(new String[0]))) {
                    setting.setValue(options.get(selected.get()));
                }
            }
        }
    }

    /**
     * Reports what the last viewport click actually hit, including the
     * submission metadata of the draw command that was rendered. Layer,
     * priority and depth bias are the values that decide how a surface
     * resolves against a coplanar neighbour, so a wall or decoration that
     * renders wrong can be reported precisely rather than described.
     */
    private void renderPickInspector(LoadedOsrsCacheSession cache,
                                     EditorPluginLifecycleManager pluginLifecycle) {
        StudioWidgets.section("Inspector");
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
        ImGui.pushFont(StudioFonts.mono(), 1.0f);
        ImGui.text("tile   " + hit.tile().x() + ", " + hit.tile().y() + "   plane " + hit.plane());
        if (hit.hasSubmissionMetadata()) {
            ImGui.text("layer  " + hit.layer());
            ImGui.text("prio   " + hit.priority() + "    bias " + hit.depthBias());
            ImGui.text("tex    " + (hit.textureId() < 0 ? "none" : String.valueOf(hit.textureId())));
        }
        if (hit.objectHit()) {
            ImGui.text("objId  " + hit.objectId());
            cache.bundle().definitions().object(hit.objectId())
                    .ifPresent(definition -> ImGui.text("name   " + definition.name()));
            describePickedObject(hit, pluginLifecycle);
        } else {
            ImGui.textDisabled("terrain (no object)");
        }
        ImGui.popFont();
        if (ImGui.button("Clear##pick-clear")) viewport.clearSelection();
    }

    /**
     * Resolves the picked object back to its authored location so the shape
     * and rotation are visible. The pick reports a world tile while the
     * document is a single 64x64 region, so local coordinates are the world
     * ones reduced modulo the region size.
     */
    private void describePickedObject(com.rspsi.editor.render.PickResult hit,
                                      EditorPluginLifecycleManager pluginLifecycle) {
        EditorSession session = session(pluginLifecycle);
        if (session == null) return;
        var world = session.world();
        int localX = Math.floorMod(hit.tile().x(), Math.max(1, world.width()));
        int localY = Math.floorMod(hit.tile().y(), Math.max(1, world.length()));
        if (hit.plane() < 0 || hit.plane() >= world.planes()) return;
        for (var object : world.tile(hit.plane(), localX, localY).snapshot().objects()) {
            if (object.id() != hit.objectId()) continue;
            String shape = object.shape().map(value -> " (" + value + ")").orElse("");
            ImGui.text("shape  " + object.type() + shape);
            ImGui.text("rot    " + object.rotation());
            return;
        }
        ImGui.textDisabled("not found at " + localX + "," + localY);
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

    private void renderBottomDrawer(EditorPluginLifecycleManager pluginLifecycle) {
        if (!bottomDrawerVisible) return;
        ImGui.begin(BOTTOM_WINDOW, DOCKED_PANEL_FLAGS);
        String[] tabs = {"History", "Tasks", "Messages", "Diagnostics"};
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 6.0f);
        for (int index = 0; index < tabs.length; index++) {
            if (index > 0) ImGui.sameLine();
            if (modeButton(tabs[index], drawerTab == index)) drawerTab = index;
        }
        ImGui.popStyleVar();
        ImGui.separator();
        ImGui.beginChild("drawer-content", 0.0f, 0.0f, false);
        switch (drawerTab) {
            case 0 -> renderHistoryTab(pluginLifecycle);
            case 1 -> renderTasksTab(pluginLifecycle);
            case 2 -> renderMessagesTab(pluginLifecycle);
            default -> renderDiagnosticsTab(pluginLifecycle);
        }
        ImGui.endChild();
        ImGui.end();
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
            ImGui.textDisabled("No edits yet — terrain and object tools will appear here.");
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
        ImGui.textDisabled("Renderer diagnostics stream into the viewport header.");
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
        ImGui.pushFont(StudioFonts.mono(), 1.0f);
        int plane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        ImGui.text("REV " + cache.identity().revision()
                + "  ·  PLANE " + plane
                + "  ·  " + (dirty ? "UNSAVED" : "SAVED")
                + "  ·  " + (plan == null ? "loading scene" : "scene ready"));
        ImGui.sameLine();
        var stats = viewport.statistics();
        ImGui.textDisabled("  " + stats.renderedTriangles() + " tris  ·  " + stats.drawCalls()
                + " draws  ·  GL " + stats.firstGlError()
                + (stats.missingTextures() == 0 ? "" : "  ·  missing tex " + stats.missingTextures()));
        ImGui.popFont();
        if (pluginLifecycle != null && pluginLifecycle.host() != null) {
            var statuses = pluginLifecycle.host().registry().statusRegistrations();
            if (!statuses.isEmpty()) {
                ImGui.sameLine();
                ImGui.textDisabled("· plugins " + statuses.size());
            }
        }
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
        layoutStore.save(new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION,
                ImGui.saveIniSettingsToMemory(), bottomDrawerVisible));
    }
}
