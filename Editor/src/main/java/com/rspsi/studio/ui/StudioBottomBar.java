package com.rspsi.studio.ui;

import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioPalette;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Primary bottom authoring rail plus the single active tool Context Drawer.
 *
 * <p>The bottom area is intentionally not a generic console/panel host. Tool-specific
 * libraries and controls (Tile Painter, Path Builder, Object Placement, generators, etc.)
 * are projected here from the active tool descriptor/native compatibility projection.</p>
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

    private boolean drawerOpen = true;
    private String lastDrawerToolId;
    private final DeclarativeToolUiRenderer declarativeToolUi = new DeclarativeToolUiRenderer();

    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    public void setDrawerOpen(boolean drawerOpen) {
        this.drawerOpen = drawerOpen;
    }

    public void toggleDrawer() {
        this.drawerOpen = !this.drawerOpen;
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
        // authoring drawer. The last real tool shelf stays available while a picker
        // is active over the viewport.
        String drawerToolId = activeToolHasDrawer ? activeToolId : lastDrawerToolId;
        boolean drawerHasContent = drawerToolId != null;

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

    private void renderDrawerBody(StudioPanelManager panelManager,
                                  StudioPanelContext context,
                                  String activeToolId,
                                  float availH) {
        ImGui.beginChild("studio-bottom-drawer-content", 0.0f, availH, false);

        renderActiveToolShelf(context, activeToolId);

        ImGui.endChild();
    }

    private void renderActiveToolShelf(StudioPanelContext context,
                                       String activeToolId) {
        if (context != null && context.studioPlugins() != null) {
            var toolView = context.studioPlugins().toolView(activeToolId).orElse(null);
            if (toolView != null) {
                try {
                    if (toolView.nativePlugin() != null) {
                        toolView.nativePlugin().renderContextDrawer(context);
                        return;
                    }
                    var drawer = toolView.contextDrawerNode();
                    if (drawer.isPresent()) {
                        declarativeToolUi.render(drawer.orElseThrow());
                        return;
                    }
                } catch (Throwable t) {
                    ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.DANGER);
                    ImGui.text(StudioIcons.BUG_REPORT + " Tool Drawer Error: " + t.getMessage());
                    ImGui.popStyleColor();
                    return;
                }
            }
        }

        if (activeToolId == null) {
            ImGui.textDisabled("Select an authoring tool from the bottom tool rail.");
            return;
        }

        ImGui.textDisabled("Tool '" + activeToolId
                + "' does not expose Context Drawer content.");
    }

}
