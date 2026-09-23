package com.rspsi.studio.plugin.builtin.tool;

import com.rspsi.editor.model.FloorId;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.tool.SplinePathTool;
import com.rspsi.editor.tool.spline.SplineBrushStyle;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.hud.ViewportHudManager;
import com.rspsi.studio.ui.panels.TilePainterPalette;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;

/**
 * Modernized Catmull-Rom Spline Path Builder tool plugin.
 * Provides real-time ribbon editing, multi-style autotiling edge selection,
 * width controls, and atomic undoable path generation.
 */
public final class PathToolPlugin implements StudioToolPlugin {

    public static final String ID = "studio.tool.path";
    public static final String ENGINE_TOOL_ID = SplinePathTool.ID;

    // Configurable defaults
    private final ImInt defaultPathWidth = new ImInt(2);
    private SplineBrushStyle defaultStyle = SplineBrushStyle.SMOOTH;
    private final ImInt defaultOverlayId = new ImInt(1);
    private final ImInt drawerOverlay = new ImInt(1);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Path Builder";
    }

    @Override
    public String description() {
        return "Plot sequential Catmull-Rom spline points to generate smooth cobblestone paths, dirt trails, and incline ramps.";
    }

    @Override
    public String version() {
        return "2.0.0";
    }

    @Override
    public String author() {
        return "OpenRune Team";
    }

    @Override
    public String icon() {
        return StudioIcons.PATH;
    }

    @Override
    public String toolId() {
        return ENGINE_TOOL_ID;
    }

    @Override
    public String shortcut() {
        return "P";
    }

    @Override
    public int railPriority() {
        return 40;
    }

    @Override
    public String category() {
        return "Paths";
    }

    @Override
    public java.util.Set<ToolSurface> surfaces() {
        // The docked Left Tool Rail is brush-only now (Tile Painter, Height
        // Sculptor) and the floating rail is a dedicated selection-mode
        // switcher (Single/Multi Select); the path builder lives only on
        // the bottom bar, which already covered it.
        return java.util.EnumSet.of(ToolSurface.BOTTOM_BAR);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  Path Builder Preferences");
        ImGui.separator();

        ImGui.sliderInt("Default Path Width (Tiles)", defaultPathWidth.getData(), 1, 8);

        ImGui.alignTextToFramePadding();
        ImGui.text("Default Overlay ID:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(80.0f);
        ImGui.inputInt("##def-ovr", defaultOverlayId);
        ImGui.sameLine();
        int col = TilePainterPalette.floorColor(context != null ? context.cache() : null, defaultOverlayId.get(), false, 0xFF4A4A4A);
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(sx, sy, sx + 22.0f, sy + 22.0f, col, 4.0f);
        draw.addRect(sx, sy, sx + 22.0f, sy + 22.0f, 0xFFFFFFFF, 4.0f, 0, 1.0f);
        ImGui.dummy(22.0f, 22.0f);

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset Path Defaults")) {
            defaultPathWidth.set(2);
            defaultStyle = SplineBrushStyle.SMOOTH;
            defaultOverlayId.set(1);
        }
    }

    @Override
    public void renderHUD(StudioPanelContext context) {
        if (!ENGINE_TOOL_ID.equals(context.activeToolId())) return;
        if (!(context.toolController().activeTool() instanceof SplinePathTool tool)) return;
        if (context.huds() == null) return;

        int count = tool.path().size();
        String text = "Spline Path  |  Points: " + count
                + "  |  W: " + tool.width()
                + "  |  " + tool.style().displayName()
                + (count >= 2 ? "  |  [Enter] Build  |  [Esc] Clear" : "  |  Click to plot nodes");

        float padX = 12.0f;
        float padY = 5.0f;
        float width = ImGui.calcTextSize(text).x + padX * 2.0f;
        float height = 24.0f;
        context.huds().register(ID, ViewportHudManager.Quadrant.BOTTOM_LEFT, 35);
        var placement = context.huds().place(ID, width, height);
        if (placement == null) return;

        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(placement.x(), placement.y(),
                placement.x() + width, placement.y() + height, 0xDF0F172A, 6.0f);
        draw.addRect(placement.x(), placement.y(),
                placement.x() + width, placement.y() + height, 0xDF38BDF8, 6.0f, 0, 1.0f);
        draw.addText(placement.x() + padX, placement.y() + padY, 0xFFE2E8F0, text);
    }

    @Override
    public void renderContextDrawer(StudioPanelContext context) {
        SplinePathTool tool = (context.toolController().activeTool() instanceof SplinePathTool t) ? t : null;

        ImGui.textColored(0xFF38BDF8, StudioIcons.PATH + "  Catmull-Rom Spline Path Builder");
        ImGui.sameLine(0.0f, 16.0f);
        ImGui.textDisabled("Left-click ground (or Shift+click) to drop nodes. Drag nodes to move. Right-click or Alt+click a node to delete.");
        ImGui.separator();

        int pointsCount = (tool != null) ? tool.path().size() : 0;
        int currentWidth = (tool != null) ? tool.width() : defaultPathWidth.get();
        SplineBrushStyle currentStyle = (tool != null) ? tool.style() : defaultStyle;
        int currentOverlay = (tool != null) ? tool.overlayId() : defaultOverlayId.get();

        // Row 1: Width, Overlay ID with Visual Swatch & Palette Popup, Plotted Nodes
        ImGui.alignTextToFramePadding();
        ImGui.text("Path Width:");
        ImGui.sameLine();
        if (ImGui.button(StudioIcons.REMOVE + "##w-dec", 24.0f, 22.0f)) {
            if (tool != null) tool.setWidth(Math.max(1, currentWidth - 1));
        }
        ImGui.sameLine(0.0f, 4.0f);
        ImGui.pushItemWidth(60.0f);
        int[] widthArr = {currentWidth};
        if (ImGui.sliderInt("##path-w", widthArr, 1, 8)) {
            if (tool != null) tool.setWidth(widthArr[0]);
        }
        ImGui.popItemWidth();
        ImGui.sameLine(0.0f, 4.0f);
        if (ImGui.button(StudioIcons.ADD + "##w-inc", 24.0f, 22.0f)) {
            if (tool != null) tool.setWidth(Math.min(16, currentWidth + 1));
        }

        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Overlay ID:");
        ImGui.sameLine();
        ImGui.pushItemWidth(55.0f);
        drawerOverlay.set(currentOverlay);
        if (ImGui.inputInt("##path-overlay", drawerOverlay, 1, 5, ImGuiInputTextFlags.CharsDecimal)) {
            int newOverlay = Math.max(0, drawerOverlay.get());
            if (tool != null) tool.setOverlayId(newOverlay);
            defaultOverlayId.set(newOverlay);
        }
        ImGui.popItemWidth();

        // Visual Overlay Swatch Box
        ImGui.sameLine(0.0f, 6.0f);
        int swatchColor = TilePainterPalette.floorColor(context.cache(), currentOverlay, false, 0xFF4A4A4A);
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(sx, sy, sx + 22.0f, sy + 22.0f, swatchColor, 4.0f);
        draw.addRect(sx, sy, sx + 22.0f, sy + 22.0f, 0xFF94A3B8, 4.0f, 0, 1.5f);
        if (ImGui.invisibleButton("##ovr-swatch-btn", 22.0f, 22.0f)) {
            ImGui.openPopup("path_overlay_palette_popup");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Overlay #" + FloorId.definitionId(currentOverlay) + " (Click to browse visual palette)");
        }

        ImGui.sameLine(0.0f, 6.0f);
        if (ImGui.button(StudioIcons.PALETTE + " Browse##path-browse-ovr", 88.0f, 22.0f)) {
            ImGui.openPopup("path_overlay_palette_popup");
        }

        // Palette popover with 128 overlay swatches
        if (ImGui.beginPopup("path_overlay_palette_popup")) {
            ImGui.textColored(0xFF38BDF8, StudioIcons.PALETTE + "  Select Cache Overlay Material");
            ImGui.separator();
            renderOverlayGridPopup(context.cache(), tool);
            ImGui.endPopup();
        }

        ImGui.sameLine(0.0f, 20.0f);
        ImGui.textColored(pointsCount >= 2 ? 0xFF34D399 : 0xFF94A3B8,
                "Nodes: " + pointsCount + (pointsCount < 2 ? " (min 2 required)" : ""));

        ImGui.spacing();

        // Row 2: Quick Road/Path Presets + Edge Styles
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("Presets:");
        ImGui.sameLine();
        renderPresetButton(tool, context.cache(), "Cobble", 10);
        ImGui.sameLine(0.0f, 4.0f);
        renderPresetButton(tool, context.cache(), "Dirt", 28);
        ImGui.sameLine(0.0f, 4.0f);
        renderPresetButton(tool, context.cache(), "Grass", 1);
        ImGui.sameLine(0.0f, 4.0f);
        renderPresetButton(tool, context.cache(), "Water", 12);
        ImGui.sameLine(0.0f, 4.0f);
        renderPresetButton(tool, context.cache(), "Wood", 4);
        ImGui.sameLine(0.0f, 4.0f);
        renderPresetButton(tool, context.cache(), "Snow", 35);

        ImGui.sameLine(0.0f, 24.0f);
        ImGui.text("Edge Style:");
        ImGui.sameLine();
        for (SplineBrushStyle styleOption : SplineBrushStyle.values()) {
            boolean active = (currentStyle == styleOption);
            if (active) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF0284C7);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF0369A1);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF075985);
            }

            if (ImGui.button(styleOption.displayName() + "##btn-style-" + styleOption.name())) {
                if (tool != null) tool.setStyle(styleOption);
            }

            if (ImGui.isItemHovered()) {
                String tip = switch (styleOption) {
                    case SMOOTH -> "Autotiled organic paths with smooth curved corners";
                    case SOLID -> "Blocky 100% full square tile footprint";
                    case WEDGE -> "45° diagonal angled corner wedges";
                    case RAMP -> "Smooth height elevation gradient from start to end node";
                };
                ImGui.setTooltip(tip);
            }

            if (active) {
                ImGui.popStyleColor(3);
            }
            ImGui.sameLine(0.0f, 6.0f);
        }

        ImGui.newLine();
        ImGui.spacing();

        // Row 3: Action Buttons
        boolean canBuild = (tool != null && pointsCount >= 2);
        if (!canBuild) {
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
        }

        ImGui.pushStyleColor(ImGuiCol.Button, 0xFF16A34A); // Emerald green for Build
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF15803D);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF166534);

        if (ImGui.button(StudioIcons.CHECK + "  Build Path [Enter]##btn-build", 160.0f, 26.0f)) {
            if (canBuild) {
                tool.buildPath();
            }
        }
        ImGui.popStyleColor(3);

        if (!canBuild) {
            ImGui.popStyleVar();
        }

        ImGui.sameLine(0.0f, 10.0f);
        if (ImGui.button(StudioIcons.CLOSE + "  Clear Points [Esc]##btn-clear", 150.0f, 26.0f)) {
            if (tool != null) tool.clear();
        }
    }

    private void renderPresetButton(SplinePathTool tool, LoadedOsrsCacheSession cache, String name, int overlayId) {
        int color = TilePainterPalette.floorColor(cache, overlayId, false, 0xFF4A4A4A);
        float sx = ImGui.getCursorScreenPos().x;
        float sy = ImGui.getCursorScreenPos().y;
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(sx + 4.0f, sy + 4.0f, sx + 16.0f, sy + 16.0f, color, 2.0f);
        draw.addRect(sx + 4.0f, sy + 4.0f, sx + 16.0f, sy + 16.0f, 0xFFFFFFFF, 2.0f, 0, 1.0f);

        if (ImGui.button("    " + name + "##preset-" + overlayId)) {
            if (tool != null) tool.setOverlayId(overlayId);
            defaultOverlayId.set(overlayId);
            drawerOverlay.set(overlayId);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Set overlay to #" + overlayId + " (" + name + ")");
        }
    }

    private void renderOverlayGridPopup(LoadedOsrsCacheSession cache, SplinePathTool tool) {
        float size = 22.0f;
        float spacing = 4.0f;
        int cols = 16;
        ImDrawList draw = ImGui.getWindowDrawList();

        for (int i = 1; i <= 128; i++) {
            if ((i - 1) > 0 && (i - 1) % cols != 0) ImGui.sameLine(0.0f, spacing);
            int color = TilePainterPalette.floorColor(cache, i, false, 0xFF4A4A4A);
            float sx = ImGui.getCursorScreenPos().x;
            float sy = ImGui.getCursorScreenPos().y;
            draw.addRectFilled(sx, sy, sx + size, sy + size, color, 3.0f);
            int selected = (tool != null) ? tool.overlayId() : defaultOverlayId.get();
            if (i == selected) {
                draw.addRect(sx - 1, sy - 1, sx + size + 1, sy + size + 1, 0xFF38BDF8, 3.0f, 0, 2.0f);
            } else {
                draw.addRect(sx, sy, sx + size, sy + size, 0xFF334155, 3.0f, 0, 1.0f);
            }
            if (ImGui.invisibleButton("ovr-pop-" + i, size, size)) {
                if (tool != null) tool.setOverlayId(i);
                defaultOverlayId.set(i);
                drawerOverlay.set(i);
                ImGui.closeCurrentPopup();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Overlay #" + FloorId.definitionId(i));
            }
        }
    }
}
