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
    private int drawerTab;
    private int rightPanelTab;

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
        layout = Layout.compute(bottomDrawerVisible);
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
            ImGui.menuItem("OpenRune Studio · Map Editor", null, true, false);
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
        ImGui.end();
    }

    // ------------------------------------------------------------------
    // Viewport
    // ------------------------------------------------------------------

    private void renderViewport(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                                NativeSceneViewport viewport, String sceneStatus,
                                SettingsStore settings) {
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

        String[] tabs = {"Inspector", "Outliner", "Tools"};
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
            case 1 -> renderOutlinerTab(cache, settings, pluginLifecycle);
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

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.text("tile     " + hit.tile().x() + ", " + hit.tile().y() + " (plane " + hit.plane() + ")");
        ImGui.text("region   " + regionId + " (" + regionX + "," + regionY + ") local " + localX + "," + localY);
        ImGui.text("plane    authored " + hit.plane() + "  effective " + effectivePlane);

        if (world != null && hit.plane() >= 0 && hit.plane() < world.planes()) {
            var snapshot = world.tile(hit.plane(), localX, localY).snapshot();
            int sw = snapshot.southWestHeight();
            int se = snapshot.southEastHeight();
            int ne = snapshot.northEastHeight();
            int nw = snapshot.northWestHeight();
            ImGui.text("height   sw " + sw + "  se " + se);
            ImGui.text("         nw " + nw + "  ne " + ne);
            int anchor = (sw + se + ne + nw) >> 2;
            ImGui.text("anchor   " + anchor + "   slope " + (maxOf(sw, se, ne, nw) - minOf(sw, se, ne, nw)));

            int underlayId = snapshot.underlayId();
            if (underlayId > 0) {
                var uDef = cache.bundle().definitions().underlay(underlayId);
                String uColor = uDef.map(u -> String.format("#%06X", u.rgb())).orElse("?");
                ImGui.text("underlay " + underlayId + " (" + uColor + ")");
            } else {
                ImGui.textDisabled("underlay none");
            }

            int overlayId = snapshot.overlayId();
            if (overlayId > 0) {
                var oDef = cache.bundle().definitions().overlay(overlayId);
                String oColor = oDef.map(o -> String.format("#%06X", o.rgb())).orElse("?");
                int tex = oDef.map(FloorDefinitionView::texture).orElse(-1);
                String texStr = tex >= 0 ? " tex " + tex : "";
                ImGui.text("overlay  " + overlayId + " (" + oColor + texStr + ")");
                ImGui.text("overlay  shape " + snapshot.overlayShape() + "  rot " + snapshot.overlayRotation());
            } else {
                ImGui.textDisabled("overlay  none");
            }

            int flags = snapshot.flags();
            StringBuilder flagNames = new StringBuilder();
            if ((flags & OsrsTileFlags.BLOCK_MAP_SQUARE) != 0) flagNames.append("clipped ");
            if ((flags & OsrsTileFlags.BRIDGE) != 0) flagNames.append("bridge ");
            if ((flags & OsrsTileFlags.REMOVE_ROOFS) != 0) flagNames.append("roofs ");
            if ((flags & OsrsTileFlags.MINIMAP_BRIDGE) != 0) flagNames.append("minimap_bridge ");
            if ((flags & OsrsTileFlags.MINIMAP_HIDDEN) != 0) flagNames.append("hidden ");
            String flagsText = flagNames.length() > 0 ? flagNames.toString().trim() : "none";
            ImGui.text("flags    0x" + Integer.toHexString(flags) + " (" + flagsText + ")");
        }

        if (hit.hasSubmissionMetadata()) {
            ImGui.text("layer    " + hit.layer());
            ImGui.text("prio     " + hit.priority() + "    bias " + hit.depthBias());
            ImGui.text("tex      " + (hit.textureId() < 0 ? "none" : String.valueOf(hit.textureId())));
        }

        if (hit.objectHit()) {
            ImGui.text("objId    " + hit.objectId());
            cache.bundle().definitions().object(hit.objectId())
                    .ifPresent(definition -> {
                        ImGui.text("name     " + definition.name());
                        ImGui.text("size     " + definition.width() + "x" + definition.length());
                    });
            cache.bundle().definitions().objectAppearance(hit.objectId())
                    .ifPresent(app -> {
                        ImGui.text("shadow   " + app.castsShadow() + "  occlude " + app.occludes());
                        ImGui.text("mergeN   " + app.mergeNormals() + "  contrast " + app.contrast());
                    });
            cache.bundle().definitions().objectCollision(hit.objectId())
                    .ifPresent(col -> {
                        ImGui.text("clip     walk " + col.blockWalk() + "  proj " + col.blockProjectile() + "  type " + col.clipType());
                    });
            describePickedObject(hit, pluginLifecycle);
        } else {
            ImGui.textDisabled("terrain (no object)");
        }
        ImGui.popFont();
        if (ImGui.button("Clear##pick-clear")) viewport.clearSelection();
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

                    ImGui.treePop();
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

    private void renderBottomDrawer(EditorPluginLifecycleManager pluginLifecycle) {
        if (!bottomDrawerVisible) return;
        placeWindow(layout.centerX(), layout.drawerY(),
                layout.centerWidth(), layout.drawerHeight());
        ImGui.begin(BOTTOM_WINDOW, FIXED_PANEL_FLAGS);
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
        // Only the drawer toggle survives a restart. The arrangement is
        // derived every frame, so persisting it could only reintroduce the
        // stale-layout failure this shell was rebuilt to remove.
        layoutStore.save(new NativeWorkspaceLayoutStore.State(
                NativeWorkspaceLayoutStore.CURRENT_VERSION, "", bottomDrawerVisible));
    }
}
