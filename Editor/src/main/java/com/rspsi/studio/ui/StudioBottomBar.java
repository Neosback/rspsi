package com.rspsi.studio.ui;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.ui.panels.HeightToolPanel;
import com.rspsi.studio.ui.panels.TilePainterPalette;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiSliderFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modernized Studio bottom chrome bar with an integrated tool rail (Paint, Height, Path,
 * Select, Object) and a collapsible, context-sensitive tool console drawer.
 */
public final class StudioBottomBar {

    public static final float COLLAPSED_HEIGHT = 34.0f;
    public static final float EXPANDED_HEIGHT = 220.0f;

    private static final int BAR_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings;

    public enum DrawerMode {
        AUTO_TOOL,
        HISTORY,
        TASKS,
        NOTIFICATIONS,
        DIAGNOSTICS,
        CUSTOM_PANEL
    }

    private boolean drawerOpen = true;
    private DrawerMode drawerMode = DrawerMode.AUTO_TOOL;
    private String lastDrawerToolId;

    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    public void setDrawerOpen(boolean drawerOpen) {
        this.drawerOpen = drawerOpen;
    }

    public void toggleDrawer() {
        this.drawerOpen = !this.drawerOpen;
    }

    public DrawerMode drawerMode() {
        return drawerMode;
    }

    public void setDrawerMode(DrawerMode mode) {
        this.drawerMode = mode;
        this.drawerOpen = true;
    }

    public void toggleDrawerMode(DrawerMode mode) {
        if (this.drawerOpen && this.drawerMode == mode) {
            this.drawerOpen = false;
        } else {
            this.drawerMode = mode;
            this.drawerOpen = true;
        }
    }

    public float currentHeight() {
        return drawerOpen ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT;
    }

    public void render(StudioPanelManager panelManager,
                       StudioPanelContext context,
                       float x, float y, float width, float height,
                       Consumer<String> activateTool,
                       String activeToolId) {

        boolean activeToolHasDrawer = context == null || context.studioPlugins() == null
                || context.studioPlugins().toolView(activeToolId)
                        .map(StudioPluginManager.StudioToolView::hasContextDrawerContent)
                        .orElse(true);
        if (panelManager != null && activeToolId != null) {
            activeToolHasDrawer = activeToolHasDrawer
                    && panelManager.managedRegionForTool(activeToolId)
                            .map(region -> region == DockRegion.BOTTOM)
                            .orElse(true);
        }
        if (activeToolHasDrawer && activeToolId != null) {
            lastDrawerToolId = activeToolId;
        }

        // Picker/inspection tools with no drawer never destroy the user's existing
        // drawer state. AUTO_TOOL simply keeps showing the last tool that owned UI.
        String drawerToolId = activeToolHasDrawer ? activeToolId : lastDrawerToolId;
        boolean drawerHasContent = drawerMode != DrawerMode.AUTO_TOOL || drawerToolId != null;

        float curH = currentHeight();

        // Exact positioning from Layout - zero dead space
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, curH, ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 4.0f);
        ImGui.begin("StudioBottomBar", BAR_FLAGS);

        // 1. Horizontal Activity Bar (Square tool buttons linked to the drawer below)
        renderActivityBar(panelManager, context, activateTool, activeToolId, drawerHasContent);

        // 2. Expandable Drawer Body. When the active picker has no shelf, retain
        // the last real tool shelf instead of collapsing the whole bottom area.
        if (drawerOpen && drawerHasContent) {
            renderDrawerBody(panelManager, context, drawerToolId, curH - COLLAPSED_HEIGHT - 6.0f);
        }

        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderActivityBar(StudioPanelManager panelManager,
                                   StudioPanelContext context,
                                   Consumer<String> activateTool,
                                   String activeToolId,
                                   boolean toolHasDrawer) {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2.0f, 2.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6.0f);

        List<StudioPluginManager.StudioToolView> toolPlugins =
                (context != null && context.studioPlugins() != null)
                        ? context.studioPlugins().toolViews()
                        : Collections.emptyList();

        float btnW = 32.0f;
        float btnH = 26.0f;

        if (!toolPlugins.isEmpty()) {
            for (StudioPluginManager.StudioToolView tool : toolPlugins) {
                java.util.Optional<DockRegion> managedRegion =
                        panelManager != null ? panelManager.managedRegionForTool(tool.toolId())
                                : java.util.Optional.empty();
                if (managedRegion.isPresent() && managedRegion.get() != DockRegion.BOTTOM) {
                    continue;
                }
                if (managedRegion.isEmpty()
                        && !tool.surfaces().contains(StudioToolPlugin.ToolSurface.BOTTOM_BAR)) {
                    continue;
                }
                boolean isActive = tool.toolIds().contains(activeToolId) || tool.id().equals(activeToolId);

                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.ACCENT); // Indigo 500
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.ACCENT_HOVER);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_ACTIVE);
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFFFFFFFF);
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.CHROME_BG); // Zinc dark surface
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.FIELD_HOVER);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_SOFT);
                    ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT_MUTED);
                }

                ImGui.pushFont(StudioFonts.icon(), 0.0f);
                if (ImGui.button(tool.icon() + "##btm-act-" + tool.id(), btnW, btnH)) {
                    if (isActive) {
                        drawerOpen = !drawerOpen;
                    } else {
                        if (activateTool != null) activateTool.accept(tool.toolId());
                        drawerOpen = true;
                        drawerMode = DrawerMode.AUTO_TOOL;
                    }
                }
                ImGui.popFont();

                ImGui.popStyleColor(4);

                if (ImGui.isItemHovered()) {
                    String shortcutSuffix = tool.shortcut().isEmpty() ? "" : " [" + tool.shortcut() + "]";
                    ImGui.setTooltip(tool.name() + shortcutSuffix + (isActive && drawerOpen ? " (Click to collapse)" : " (Click to open shelf)"));
                }

                ImGui.sameLine(0.0f, 4.0f);
            }
        } else {
            renderFallbackToolButtons(activateTool, activeToolId, btnW, btnH);
        }

        // Selection count badge if any tiles selected
        if (context != null && context.session() != null && context.session().selection() != null) {
            var selected = context.session().selection().selectedCoordinates();
            if (!selected.isEmpty()) {
                ImGui.sameLine(0.0f, 12.0f);
                ImGui.textColored(StudioPalette.ACCENT, "(" + selected.size() + " selected)");
                ImGui.sameLine(0.0f, 4.0f);
                if (ImGui.smallButton("Clear##clr-sel-hdr")) {
                    context.session().selection().clear();
                }
            }
        }

        // Far right: Drawer Toggle chevron [v] or [^] - disabled when the active tool has
        // nothing to show, so there is nothing to expand into.
        float rightX = ImGui.getWindowWidth() - 36.0f;
        if (rightX > ImGui.getCursorPosX() && toolHasDrawer) {
            ImGui.sameLine(rightX);
            ImGui.pushFont(StudioFonts.icon(), 0.0f);
            String toggleIcon = drawerOpen ? StudioIcons.EXPAND_MORE : StudioIcons.EXPAND_LESS;
            if (ImGui.button(toggleIcon + "##tb-drawer-toggle", 28.0f, btnH)) {
                drawerOpen = !drawerOpen;
            }
            ImGui.popFont();
            if (ImGui.isItemHovered()) ImGui.setTooltip(drawerOpen ? "Collapse Shelf" : "Expand Shelf");
        }

        ImGui.popStyleVar(3);
        if (drawerOpen) {
            ImGui.separator();
        }
    }

    private void renderFallbackToolButtons(Consumer<String> activateTool, String activeToolId, float btnW, float btnH) {
        String[][] tools = {
                {"selection.box", StudioIcons.SELECT, "Tile Selection"},
                {"terrain.tile-painter", StudioIcons.BRUSH, "Tile Painter"},
                {"terrain.raise", StudioIcons.HEIGHT, "Height Sculptor"},
                {"terrain.smooth", StudioIcons.PATH, "Path Builder"},
                {"object.place", StudioIcons.OBJECT, "Object Placement"}
        };
        for (String[] t : tools) {
            String tid = t[0];
            String icon = t[1];
            String name = t[2];
            boolean isActive = tid.equals(activeToolId);

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.ACCENT);
                ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.PANEL_ELEVATED);
                ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT_MUTED);
            }

            ImGui.pushFont(StudioFonts.icon(), 0.0f);
            if (ImGui.button(icon + "##btm-fb-" + tid, btnW, btnH)) {
                if (isActive) {
                    drawerOpen = !drawerOpen;
                } else {
                    if (activateTool != null) activateTool.accept(tid);
                    drawerOpen = true;
                    drawerMode = DrawerMode.AUTO_TOOL;
                }
            }
            ImGui.popFont();
            ImGui.popStyleColor(2);
            if (ImGui.isItemHovered()) ImGui.setTooltip(name);
            ImGui.sameLine(0.0f, 4.0f);
        }
    }

    private void renderDrawerBody(StudioPanelManager panelManager,
                                  StudioPanelContext context,
                                  String activeToolId,
                                  float availH) {
        ImGui.beginChild("studio-bottom-drawer-content", 0.0f, availH, false);

        switch (drawerMode) {
            case AUTO_TOOL -> renderActiveToolShelf(panelManager, context, activeToolId);
            case HISTORY -> renderHistoryDrawer(context);
            case TASKS -> renderTasksDrawer(context);
            case NOTIFICATIONS -> renderNotificationsDrawer(context);
            case DIAGNOSTICS -> renderDiagnosticsDrawer(context);
            case CUSTOM_PANEL -> renderCustomPanelDrawer(panelManager, context);
        }

        ImGui.endChild();
    }

    private void renderActiveToolShelf(StudioPanelManager panelManager,
                                       StudioPanelContext context,
                                       String activeToolId) {
        if (context != null && context.studioPlugins() != null) {
            var toolView = context.studioPlugins().toolView(activeToolId).orElse(null);
            if (toolView != null && toolView.nativePlugin() != null) {
                try {
                    toolView.nativePlugin().renderContextDrawer(context);
                } catch (Throwable t) {
                    ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.DANGER);
                    ImGui.text(StudioIcons.BUG_REPORT + " Tool Drawer Error: " + t.getMessage());
                    ImGui.popStyleColor();
                }
                return;
            }
            if (toolView != null && toolView.hasContextDrawerContent()) {
                renderNeutralToolShelf(context, activeToolId, toolView.name());
                return;
            }
        }

        if (activeToolId == null) {
            ImGui.textDisabled("Select a tool from the Left Tool Rail to configure its parameters.");
            return;
        }

        if (activeToolId.equals("terrain.tile-painter")) {
            panelManager.panel(TilePainterPalette.ID).ifPresent(p -> p.render(context));
            return;
        }

        if (activeToolId.startsWith("terrain.raise") || activeToolId.startsWith("terrain.lower")) {
            panelManager.panel(HeightToolPanel.ID).ifPresent(p -> p.render(context));
            return;
        }

        if (activeToolId.startsWith("terrain.smooth") || activeToolId.startsWith("terrain.ramp")) {
            renderSplineRampShelf(context);
            return;
        }

        if (activeToolId.equals("selection.box")) {
            renderSelectionShelf(context);
            return;
        }

        if (activeToolId.startsWith("object.")) {
            renderObjectToolShelf(context);
            return;
        }

        ImGui.textDisabled("Tool '" + activeToolId + "' does not require drawer parameters.");
    }

    private void renderNeutralToolShelf(
            StudioPanelContext context,
            String activeToolId,
            String toolName) {
        if (context == null || context.pluginLifecycle() == null
                || context.pluginLifecycle().host() == null) {
            ImGui.textDisabled("Tool settings are unavailable.");
            return;
        }

        var host = context.pluginLifecycle().host();
        List<EditorSetting> settings =
                host.registry().settingsForTool(host.context(), activeToolId);

        ImGui.textColored(StudioPalette.ACCENT, toolName);
        ImGui.sameLine();
        ImGui.textDisabled(activeToolId);
        ImGui.separator();

        if (settings.isEmpty()) {
            ImGui.textDisabled("This tool owns the Context Drawer but has no controls yet.");
            return;
        }

        for (EditorSetting setting : settings) {
            renderNeutralSetting(setting);
        }
    }

    private void renderNeutralSetting(EditorSetting setting) {
        ImGui.pushID("tool-setting-" + setting.id());
        ImGui.alignTextToFramePadding();
        ImGui.text(setting.label());
        ImGui.sameLine(180.0f);
        ImGui.setNextItemWidth(Math.max(140.0f, ImGui.getContentRegionAvailX() - 12.0f));

        Object value = setting.value();
        switch (setting.type()) {
            case BOOLEAN -> {
                ImBoolean current = new ImBoolean(Boolean.TRUE.equals(value));
                if (ImGui.checkbox("##value", current)) {
                    setting.setValue(current.get());
                }
            }
            case INTEGER -> {
                int[] current = {value instanceof Number number ? number.intValue() : 0};
                if (ImGui.sliderInt("##value", current,
                        (int) setting.minimum(), (int) setting.maximum())) {
                    setting.setValue(current[0]);
                }
            }
            case DECIMAL -> {
                float[] current = {value instanceof Number number ? number.floatValue() : 0.0f};
                if (ImGui.sliderFloat("##value", current,
                        (float) setting.minimum(), (float) setting.maximum(), "%.2f",
                        ImGuiSliderFlags.None)) {
                    setting.setValue((double) current[0]);
                }
            }
            case ENUM -> {
                List<String> options = setting.options();
                String selectedValue = String.valueOf(value);
                int selectedIndex = Math.max(0, options.indexOf(selectedValue));
                ImInt selected = new ImInt(selectedIndex);
                if (ImGui.combo("##value", selected, options.toArray(String[]::new))) {
                    setting.setValue(options.get(selected.get()));
                }
            }
        }

        ImGui.popID();
    }

    private void renderSplineRampShelf(StudioPanelContext context) {
        ImGui.textColored(StudioPalette.ACCENT, "Spline Path & Incline Ramp Builder");
        ImGui.sameLine();
        ImGui.textDisabled("Click tiles sequentially to plot points. Double-click or press Enter to generate terrain gradient.");
        ImGui.separator();

        if (ImGui.button("Build Flat Road##b-road", 120.0f, 24.0f)) {
            // Future spline road interpolation
        }
        ImGui.sameLine();
        if (ImGui.button("Build Incline Ramp##b-ramp", 130.0f, 24.0f)) {
            // Future ramp interpolation
        }
        ImGui.sameLine();
        if (ImGui.button("Clear Plotted Points##b-clear", 130.0f, 24.0f)) {
            // Clear path
        }
    }

    private void renderSelectionShelf(StudioPanelContext context) {
        int selCount = context.session() != null ? context.session().selection().selectedCoordinates().size() : 0;
        ImGui.textColored(StudioPalette.ACCENT, "Selection Inspector: " + selCount + " tiles selected.");
        ImGui.sameLine();
        if (ImGui.button("Clear Selection##clr-sel-btn")) {
            if (context.session() != null) context.session().selection().clear();
        }
        ImGui.sameLine();
        if (ImGui.button("Fill Overlay on Selection##fill-ovr")) {
            // Fill overlay on selected tiles
        }
        ImGui.sameLine();
        if (ImGui.button("Fill Underlay on Selection##fill-und")) {
            // Fill underlay on selected tiles
        }
    }

    private void renderObjectToolShelf(StudioPanelContext context) {
        ImGui.textColored(StudioPalette.ACCENT, "Object Placement Controls");
        ImGui.sameLine();
        ImGui.textDisabled("Click viewport to spawn or manipulate objects. Use Outliner or Object Viewer for full definitions.");
    }

    private void renderHistoryDrawer(StudioPanelContext context) {
        var session = context.session();
        if (session == null) {
            ImGui.textDisabled("No active session.");
            return;
        }

        boolean canUndo = session.history().canUndo();
        boolean canRedo = session.history().canRedo();
        int undoCount = session.history().cursor();
        int redoCount = session.history().size() - undoCount;

        if (ImGui.button("Undo##hist-undo", 80.0f, 22.0f) && canUndo) session.undo();
        ImGui.sameLine();
        if (ImGui.button("Redo##hist-redo", 80.0f, 22.0f) && canRedo) session.redo();
        ImGui.sameLine();
        ImGui.textDisabled("History: " + undoCount + " undoable | " + redoCount + " redoable");
    }

    private void renderTasksDrawer(StudioPanelContext context) {
        ImGui.textColored(StudioPalette.ACCENT, "Background Tasks & Scene Cache Operations");
        ImGui.separator();
        ImGui.textDisabled("All background scene and asset threads are currently idle.");
    }

    private void renderNotificationsDrawer(StudioPanelContext context) {
        ImGui.textColored(StudioPalette.ACCENT, "System Notifications & Warnings");
        ImGui.separator();
        ImGui.text("Scene loaded successfully from local OSRS cache.");
    }

    private void renderDiagnosticsDrawer(StudioPanelContext context) {
        ImGui.textColored(StudioPalette.ACCENT, "OpenGL & Frame Timing Diagnostics");
        ImGui.separator();
        ImGui.text("Native Scene Renderer: Direct FBO Color Attachment");
        ImGui.text("FPS: " + String.format("%.1f", ImGui.getIO().getFramerate()) + " | Frame Time: " + String.format("%.2f ms", 1000.0f / Math.max(1.0f, ImGui.getIO().getFramerate())));
    }

    private void renderCustomPanelDrawer(StudioPanelManager panelManager, StudioPanelContext context) {
        List<StudioPanel> bottomPanels = panelManager.panelsForRegion(DockRegion.BOTTOM);
        if (bottomPanels.isEmpty()) {
            ImGui.textDisabled("No custom panels docked in the bottom drawer.");
            return;
        }

        String activeBottomId = panelManager.activeBottomPanelId();
        for (StudioPanel p : bottomPanels) {
            boolean isSel = p.id().equals(activeBottomId);
            if (ImGui.radioButton(p.title() + "##rad-bot-" + p.id(), isSel)) {
                panelManager.setActiveBottomPanelId(p.id());
            }
            ImGui.sameLine();
        }
        ImGui.newLine();
        ImGui.separator();

        panelManager.panel(activeBottomId).ifPresent(p -> p.render(context));
    }
}
