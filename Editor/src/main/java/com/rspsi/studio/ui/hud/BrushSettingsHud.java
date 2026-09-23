package com.rspsi.studio.ui.hud;

import com.rspsi.editor.model.FloorId;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.brush.BrushAwareTool;
import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.editor.tool.SplinePathTool;
import com.rspsi.editor.tool.spline.SplineBrushStyle;
import com.rspsi.studio.brush.StudioBrushManager;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.panels.HeightToolPanel;
import com.rspsi.studio.ui.panels.TilePainterPalette;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.List;
import java.util.Set;

/**
 * Modern draggable viewport HUD providing compact, unified brush settings
 * (shapes, radius, falloff) and contextual controls for whichever tool is currently active
 * (Tile Painter, Height Sculptor, Spline Path, and future brush-aware tools).
 */
public final class BrushSettingsHud implements StudioPlugin {

    public static final String ID = "studio.brush-settings-hud";

    public enum Corner {
        TOP_LEFT("Top-Left"),
        TOP_RIGHT("Top-Right"),
        BOTTOM_LEFT("Bottom-Left"),
        BOTTOM_RIGHT("Bottom-Right");

        private final String label;
        Corner(String label) { this.label = label; }
        public String label() { return label; }
    }

    // Configurable state
    private final ImInt defaultCorner = new ImInt(3); // 3 = Bottom-Right
    private final ImBoolean autoHide = new ImBoolean(true);
    private final ImFloat bgAlpha = new ImFloat(0.90f);
    private boolean minimized = false;
    private boolean pinned = false;
    private Corner snapRequest = null;

    // Transient editor inputs
    private final ImInt underlayIdInput = new ImInt(0);
    private final ImInt overlayIdInput = new ImInt(1);
    private final ImInt targetHeightInput = new ImInt(0);
    private final ImInt stepRateInput = new ImInt(32);

    private static final String[] SHAPE_NAMES = {
            "0: Full", "1: Diagonal", "2: Left 1/2", "3: Right 1/2",
            "4: Corner TL", "5: Corner TR", "6: Corner BR", "7: Corner BL",
            "8: Inv TL", "9: Inv TR", "10: Inv BR", "11: Inv BL"
    };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Brush Settings HUD";
    }

    @Override
    public String description() {
        return "Compact draggable viewport HUD for brush shapes, radius, and contextual tool controls.";
    }

    @Override
    public String icon() {
        return StudioIcons.BRUSH;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public void setMinimized(boolean minimized) {
        this.minimized = minimized;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void requestSnap(Corner corner) {
        this.snapRequest = corner;
    }

    @Override
    public void renderFloating(StudioPanelContext context) {
        if (!shouldDisplay(context)) return;

        // Apply corner snap if requested, or default position on first launch
        applyWindowPosition(context);

        // Styling
        int alphaByte = (int) (Math.max(0.2f, Math.min(1.0f, bgAlpha.get())) * 255.0f);
        int bgColor = (alphaByte << 24) | 0x0E1015;
        int borderColor = (alphaByte << 24) | 0x272C38;

        ImGui.pushStyleColor(ImGuiCol.WindowBg, bgColor);
        ImGui.pushStyleColor(ImGuiCol.Border, borderColor);
        ImGui.pushStyleColor(ImGuiCol.TitleBg, (alphaByte << 24) | 0x14161D);
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, (alphaByte << 24) | 0x6366F1);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 10.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 8.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6.0f, 6.0f);

        int flags = ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoScrollbar;

        String windowTitle = minimized
                ? "Brush##hud_min"
                : StudioIcons.BRUSH + "  Brush Settings##hud_main";

        try {
            if (ImGui.begin(windowTitle, flags)) {
                if (minimized) {
                    renderMinimizedPill(context);
                } else {
                    renderExpandedContent(context);
                }
            }
        } finally {
            ImGui.end();
            ImGui.popStyleVar(3);
            ImGui.popStyleColor(4);
        }
    }

    private boolean shouldDisplay(StudioPanelContext context) {
        if (context == null) return false;
        if (pinned) return true;
        if (!autoHide.get()) return true;

        String activeToolId = context.activeToolId();
        if (activeToolId == null) return false;

        if ("terrain.tile-painter".equals(activeToolId)
                || SplinePathTool.ID.equals(activeToolId)
                || activeToolId.startsWith("terrain.")
                || activeToolId.startsWith("path.")) {
            return true;
        }

        if (context.toolController() != null) {
            var active = context.toolController().activeTool();
            if (active instanceof BrushAwareTool || active instanceof SplinePathTool) {
                return true;
            }
        }
        return false;
    }

    private void applyWindowPosition(StudioPanelContext context) {
        float vpX = context.huds() != null ? context.huds().viewportX() : 0.0f;
        float vpY = context.huds() != null ? context.huds().viewportY() : 0.0f;
        float vpW = context.huds() != null ? context.huds().viewportWidth() : ImGui.getMainViewport().getSizeX();
        float vpH = context.huds() != null ? context.huds().viewportHeight() : ImGui.getMainViewport().getSizeY();
        float margin = 16.0f;

        // Default initial placement in designated corner
        Corner defCorner = Corner.values()[Math.max(0, Math.min(Corner.values().length - 1, defaultCorner.get()))];
        float initX = switch (defCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> vpX + margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> vpX + vpW - 250.0f - margin;
        };
        float initY = switch (defCorner) {
            case TOP_LEFT, TOP_RIGHT -> vpY + margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> vpY + vpH - 240.0f - margin;
        };
        ImGui.setNextWindowPos(initX, initY, ImGuiCond.FirstUseEver);

        if (snapRequest != null) {
            float winW = ImGui.getWindowSizeX() > 0.0f ? ImGui.getWindowSizeX() : 240.0f;
            float winH = ImGui.getWindowSizeY() > 0.0f ? ImGui.getWindowSizeY() : 200.0f;
            float targetX = switch (snapRequest) {
                case TOP_LEFT, BOTTOM_LEFT -> vpX + margin;
                case TOP_RIGHT, BOTTOM_RIGHT -> vpX + vpW - winW - margin;
            };
            float targetY = switch (snapRequest) {
                case TOP_LEFT, TOP_RIGHT -> vpY + margin;
                case BOTTOM_LEFT, BOTTOM_RIGHT -> vpY + vpH - winH - margin;
            };
            ImGui.setNextWindowPos(targetX, targetY, ImGuiCond.Always);
            snapRequest = null;
        }
    }

    /**
     * Renders an ultra-compact single-line pill taking almost zero viewport space.
     */
    private void renderMinimizedPill(StudioPanelContext context) {
        StudioBrushManager brushes = context.brushes();
        BrushAwareTool brushTool = (context.toolController() != null
                && context.toolController().activeTool() instanceof BrushAwareTool bat) ? bat : null;

        int radius = brushTool != null ? brushTool.brushRadius() : (brushes != null ? brushes.brushRadius() : 0);
        String shapeName = resolveActiveBrushName(context, brushTool, brushes);

        ImGui.alignTextToFramePadding();
        ImGui.textColored(0xFF38BDF8, StudioIcons.BRUSH + " " + shapeName + " R:" + radius);

        ImGui.sameLine(0.0f, 6.0f);
        if (ImGui.button(StudioIcons.REMOVE + "##min-dec", 20.0f, 20.0f)) {
            int newRadius = Math.max(0, radius - 1);
            if (brushTool != null) brushTool.setBrushRadius(newRadius);
            if (brushes != null) brushes.setBrushRadius(newRadius);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Decrease radius");

        ImGui.sameLine(0.0f, 2.0f);
        if (ImGui.button(StudioIcons.ADD + "##min-inc", 20.0f, 20.0f)) {
            int newRadius = Math.min(32, radius + 1);
            if (brushTool != null) brushTool.setBrushRadius(newRadius);
            if (brushes != null) brushes.setBrushRadius(newRadius);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Increase radius");

        ImGui.sameLine(0.0f, 6.0f);
        if (ImGui.button(StudioIcons.EXPAND_LESS + "##expand", 22.0f, 20.0f)) {
            minimized = false;
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Expand Brush Settings");
    }

    /**
     * Full expanded interactive brush HUD.
     */
    private void renderExpandedContent(StudioPanelContext context) {
        StudioBrushManager brushes = context.brushes();
        BrushAwareTool brushTool = (context.toolController() != null
                && context.toolController().activeTool() instanceof BrushAwareTool bat) ? bat : null;
        SplinePathTool pathTool = (context.toolController() != null
                && context.toolController().activeTool() instanceof SplinePathTool spt) ? spt : null;

        // Top Header Actions: Corner Snapping, Pin, Minimize
        renderHeaderControls();
        ImGui.separator();

        // 1. Universal Brush Shape Selector
        renderShapeSelector(context, brushTool, brushes);

        // 2. Universal Radius Controls
        renderRadiusControls(brushTool, brushes, pathTool);

        // 3. Contextual Section (adapts to active tool)
        renderContextualSection(context, brushTool, pathTool);
    }

    private void renderHeaderControls() {
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("Snap:");
        ImGui.sameLine(0.0f, 4.0f);

        if (ImGui.smallButton("TL##snap-tl")) snapRequest = Corner.TOP_LEFT;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Snap to Top-Left corner");

        ImGui.sameLine(0.0f, 2.0f);
        if (ImGui.smallButton("TR##snap-tr")) snapRequest = Corner.TOP_RIGHT;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Snap to Top-Right corner");

        ImGui.sameLine(0.0f, 2.0f);
        if (ImGui.smallButton("BL##snap-bl")) snapRequest = Corner.BOTTOM_LEFT;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Snap to Bottom-Left corner");

        ImGui.sameLine(0.0f, 2.0f);
        if (ImGui.smallButton("BR##snap-br")) snapRequest = Corner.BOTTOM_RIGHT;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Snap to Bottom-Right corner");

        ImGui.sameLine(0.0f, 10.0f);
        if (pinned) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0xFF38BDF8);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0xFF64748B);
        }
        if (ImGui.smallButton(StudioIcons.PIN + "##pin-btn")) {
            pinned = !pinned;
        }
        ImGui.popStyleColor();
        if (ImGui.isItemHovered()) ImGui.setTooltip(pinned ? "HUD pinned (click to unpin)" : "Pin HUD (keep visible on all tools)");

        ImGui.sameLine(0.0f, 4.0f);
        if (ImGui.smallButton(StudioIcons.EXPAND_MORE + "##min-btn")) {
            minimized = true;
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Minimize to compact pill");
    }

    private void renderShapeSelector(StudioPanelContext context, BrushAwareTool brushTool, StudioBrushManager brushes) {
        String activeShapeId = resolveActiveBrushId(context, brushTool, brushes);

        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("Shape:");
        ImGui.sameLine(0.0f, 6.0f);

        renderShapeButton(context, brushTool, brushes, StudioIcons.SHAPE_SQUARE + " Sq", "brush.square", activeShapeId, "Square footprint");
        ImGui.sameLine(0.0f, 3.0f);
        renderShapeButton(context, brushTool, brushes, StudioIcons.SHAPE_CIRCLE + " Circ", "brush.circle", activeShapeId, "Circle footprint");
        ImGui.sameLine(0.0f, 3.0f);
        renderShapeButton(context, brushTool, brushes, StudioIcons.SHAPE_DIAMOND + " Dia", "brush.diamond", activeShapeId, "Diamond footprint");
        ImGui.sameLine(0.0f, 3.0f);
        renderShapeButton(context, brushTool, brushes, StudioIcons.SHAPE_FALLOFF + " Soft", "brush.gaussian", activeShapeId, "Gaussian falloff footprint");

        // More brushes popup button
        ImGui.sameLine(0.0f, 4.0f);
        if (ImGui.button(StudioIcons.MORE_VERT + "##more-shapes", 22.0f, 22.0f)) {
            ImGui.openPopup("hud_more_brushes_popup");
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("More brushes (Checker, Slope, Terrace)");

        if (ImGui.beginPopup("hud_more_brushes_popup")) {
            ImGui.textColored(0xFF38BDF8, StudioIcons.BRUSH + "  Additional Brushes");
            ImGui.separator();
            if (brushes != null) {
                for (EditorBrush brush : brushes.enabledBrushes()) {
                    boolean isCur = brush.id().equals(activeShapeId);
                    if (ImGui.selectable(brush.name() + (isCur ? " (Active)" : ""), isCur)) {
                        applyBrush(context, brushTool, brushes, brush.id());
                    }
                }
            }
            ImGui.endPopup();
        }
    }

    private void renderShapeButton(StudioPanelContext context, BrushAwareTool brushTool,
                                   StudioBrushManager brushes, String label, String brushId,
                                   String activeShapeId, String tooltip) {
        boolean active = brushId.equals(activeShapeId);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0xFF0284C7);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF0369A1);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF075985);
        }
        if (ImGui.button(label + "##shp-" + brushId, 54.0f, 22.0f)) {
            applyBrush(context, brushTool, brushes, brushId);
        }
        if (active) {
            ImGui.popStyleColor(3);
        }
        if (ImGui.isItemHovered() && tooltip != null) {
            ImGui.setTooltip(tooltip);
        }
    }

    private void applyBrush(StudioPanelContext context, BrushAwareTool brushTool,
                            StudioBrushManager brushes, String brushId) {
        if (brushes != null) {
            String toolId = context.activeToolId() != null ? context.activeToolId() : "general";
            brushes.setActiveBrush(toolId, brushId);
            EditorBrush brush = brushes.allBrushes().stream()
                    .filter(b -> b.id().equals(brushId))
                    .findFirst().orElse(null);
            if (brush != null && brushTool != null) {
                brushTool.setBrush(brush);
            }
        }
    }

    private void renderRadiusControls(BrushAwareTool brushTool, StudioBrushManager brushes, SplinePathTool pathTool) {
        int currentRadius = brushTool != null
                ? brushTool.brushRadius()
                : (pathTool != null ? pathTool.width() : (brushes != null ? brushes.brushRadius() : 0));

        ImGui.alignTextToFramePadding();
        ImGui.text(pathTool != null ? "Width: " : "Radius:");
        ImGui.sameLine(0.0f, 6.0f);

        // [-] Stepper button
        if (ImGui.button(StudioIcons.REMOVE + "##rad-dec", 22.0f, 22.0f)) {
            int newRadius = Math.max(pathTool != null ? 1 : 0, currentRadius - 1);
            setRadius(brushTool, brushes, pathTool, newRadius);
        }

        ImGui.sameLine(0.0f, 4.0f);
        ImGui.pushItemWidth(100.0f);
        int[] radArr = {currentRadius};
        if (ImGui.sliderInt("##rad-slider", radArr, pathTool != null ? 1 : 0, 16)) {
            setRadius(brushTool, brushes, pathTool, radArr[0]);
        }
        ImGui.popItemWidth();

        ImGui.sameLine(0.0f, 4.0f);
        // [+] Stepper button
        if (ImGui.button(StudioIcons.ADD + "##rad-inc", 22.0f, 22.0f)) {
            int newRadius = Math.min(32, currentRadius + 1);
            setRadius(brushTool, brushes, pathTool, newRadius);
        }
    }

    private void setRadius(BrushAwareTool brushTool, StudioBrushManager brushes, SplinePathTool pathTool, int val) {
        if (brushTool != null) brushTool.setBrushRadius(val);
        if (brushes != null) brushes.setBrushRadius(val);
        if (pathTool != null) pathTool.setWidth(Math.max(1, val));
    }

    private void renderContextualSection(StudioPanelContext context, BrushAwareTool brushTool, SplinePathTool pathTool) {
        String toolId = context.activeToolId();
        if (toolId == null) return;

        ImGui.separator();

        if (brushTool instanceof CompositeTilePainterTool cpt) {
            renderTilePainterContext(context, cpt);
        } else if ("terrain.tile-painter".equals(toolId)) {
            renderTilePainterContext(context, null);
        } else if (brushTool instanceof ChangeHeightTool || (toolId != null && toolId.startsWith("terrain."))) {
            renderHeightSculptorContext(context);
        } else if (SplinePathTool.ID.equals(toolId) || pathTool != null) {
            renderPathBuilderContext(context, pathTool);
        } else {
            ImGui.textDisabled("Active: " + toolId);
        }
    }

    /**
     * Contextual Tile Painter swatches, overlay shape, and 90° rotation controls.
     */
    private void renderTilePainterContext(StudioPanelContext context, CompositeTilePainterTool painter) {
        LoadedOsrsCacheSession cache = context.cache();
        TilePainterPalette palette = TilePainterPalette.INSTANCE;

        int underlayId = painter != null ? painter.underlayId() : (palette != null ? palette.state().underlayId() : 0);
        int overlayId = painter != null ? painter.overlayId() : (palette != null ? palette.state().overlayId() : 1);
        int shape = painter != null ? painter.shape() : (palette != null ? palette.state().shape() : 0);
        int rotation = painter != null ? painter.rotation() : (palette != null ? palette.state().rotation() : 0);

        // Row 1: Underlay & Overlay with visual color swatch boxes
        ImGui.alignTextToFramePadding();
        ImGui.text("U:");
        ImGui.sameLine(0.0f, 4.0f);
        ImGui.pushItemWidth(38.0f);
        underlayIdInput.set(underlayId);
        if (ImGui.inputInt("##hud-und", underlayIdInput, 0, 0, ImGuiInputTextFlags.CharsDecimal)) {
            int newId = Math.max(0, underlayIdInput.get());
            if (painter != null) painter.setUnderlayId(newId);
            if (palette != null) palette.state().setUnderlayId(newId);
        }
        ImGui.popItemWidth();

        ImGui.sameLine(0.0f, 4.0f);
        renderSwatchBox(cache, underlayId, true, "hud_underlay_palette");

        ImGui.sameLine(0.0f, 10.0f);
        ImGui.text("O:");
        ImGui.sameLine(0.0f, 4.0f);
        ImGui.pushItemWidth(38.0f);
        overlayIdInput.set(overlayId);
        if (ImGui.inputInt("##hud-ovr", overlayIdInput, 0, 0, ImGuiInputTextFlags.CharsDecimal)) {
            int newId = Math.max(0, overlayIdInput.get());
            if (painter != null) painter.setOverlayId(newId);
            if (palette != null) palette.state().setOverlayId(newId);
        }
        ImGui.popItemWidth();

        ImGui.sameLine(0.0f, 4.0f);
        renderSwatchBox(cache, overlayId, false, "hud_overlay_palette");

        // Palette popups
        if (ImGui.beginPopup("hud_underlay_palette")) {
            ImGui.textColored(0xFF38BDF8, StudioIcons.PALETTE + "  Select Underlay Material");
            ImGui.separator();
            renderGridPopup(cache, painter, palette, true);
            ImGui.endPopup();
        }

        if (ImGui.beginPopup("hud_overlay_palette")) {
            ImGui.textColored(0xFF38BDF8, StudioIcons.PALETTE + "  Select Overlay Material");
            ImGui.separator();
            renderGridPopup(cache, painter, palette, false);
            ImGui.endPopup();
        }

        // Row 2: Overlay Shape & Rotation
        ImGui.alignTextToFramePadding();
        ImGui.text("Shape:");
        ImGui.sameLine(0.0f, 4.0f);
        ImGui.pushItemWidth(86.0f);
        String shapeLabel = (shape >= 0 && shape < SHAPE_NAMES.length) ? SHAPE_NAMES[shape] : String.valueOf(shape);
        if (ImGui.beginCombo("##hud-shp-combo", shapeLabel)) {
            for (int i = 0; i < SHAPE_NAMES.length; i++) {
                boolean isCur = (shape == i);
                if (ImGui.selectable(SHAPE_NAMES[i], isCur)) {
                    if (painter != null) painter.setShape(i);
                    if (palette != null) palette.state().setShape(i);
                }
            }
            ImGui.endCombo();
        }
        ImGui.popItemWidth();

        ImGui.sameLine(0.0f, 8.0f);
        ImGui.text("Rot: " + (rotation * 90) + "°");
        ImGui.sameLine(0.0f, 4.0f);
        if (ImGui.button(StudioIcons.ROTATE_LEFT + "##rot-ccw", 24.0f, 22.0f)) {
            int nextRot = (rotation + 3) % 4;
            if (painter != null) painter.setRotation(nextRot);
            if (palette != null) palette.state().setRotation(nextRot);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Rotate counter-clockwise (90°)");

        ImGui.sameLine(0.0f, 2.0f);
        if (ImGui.button(StudioIcons.ROTATE_RIGHT + "##rot-cw", 24.0f, 22.0f)) {
            int nextRot = (rotation + 1) % 4;
            if (painter != null) painter.setRotation(nextRot);
            if (palette != null) palette.state().setRotation(nextRot);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Rotate clockwise (90°)");
    }

    private void renderSwatchBox(LoadedOsrsCacheSession cache, int id, boolean underlay, String popupId) {
        int color = TilePainterPalette.floorColor(cache, id, underlay, underlay ? 0xFF2A2A2A : 0xFF4A4A4A);
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(sx, sy, sx + 22.0f, sy + 22.0f, color, 4.0f);
        draw.addRect(sx, sy, sx + 22.0f, sy + 22.0f, 0xFF94A3B8, 4.0f, 0, 1.0f);
        if (ImGui.invisibleButton("##swatch-" + popupId, 22.0f, 22.0f)) {
            ImGui.openPopup(popupId);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip((underlay ? "Underlay #" : "Overlay #") + FloorId.definitionId(id) + " (Click to browse visual swatches)");
        }
    }

    private void renderGridPopup(LoadedOsrsCacheSession cache, CompositeTilePainterTool painter,
                                TilePainterPalette palette, boolean underlay) {
        float size = 20.0f;
        float spacing = 3.0f;
        int cols = 16;
        ImDrawList draw = ImGui.getWindowDrawList();

        for (int i = 1; i <= 128; i++) {
            if ((i - 1) > 0 && (i - 1) % cols != 0) ImGui.sameLine(0.0f, spacing);
            int color = TilePainterPalette.floorColor(cache, i, underlay, underlay ? 0xFF2A2A2A : 0xFF4A4A4A);
            float sx = ImGui.getCursorScreenPosX();
            float sy = ImGui.getCursorScreenPosY();
            draw.addRectFilled(sx, sy, sx + size, sy + size, color, 2.0f);
            draw.addRect(sx, sy, sx + size, sy + size, 0xFF334155, 2.0f, 0, 1.0f);

            if (ImGui.invisibleButton("pop-sw-" + (underlay ? "u-" : "o-") + i, size, size)) {
                if (underlay) {
                    if (painter != null) painter.setUnderlayId(i);
                    if (palette != null) palette.state().setUnderlayId(i);
                    underlayIdInput.set(i);
                } else {
                    if (painter != null) painter.setOverlayId(i);
                    if (palette != null) palette.state().setOverlayId(i);
                    overlayIdInput.set(i);
                }
                ImGui.closeCurrentPopup();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip((underlay ? "Underlay #" : "Overlay #") + FloorId.definitionId(i));
            }
        }
    }

    /**
     * Contextual Height Sculptor mode selector pills and step rate slider.
     */
    private void renderHeightSculptorContext(StudioPanelContext context) {
        HeightToolPanel panel = HeightToolPanel.INSTANCE;
        HeightToolPanel.HeightMode currentMode = panel != null ? panel.mode() : HeightToolPanel.HeightMode.RAISE;

        // Row 1: Mode Pills
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("Mode:");
        ImGui.sameLine(0.0f, 4.0f);

        renderHeightModeButton(context, HeightToolPanel.HeightMode.RAISE, StudioIcons.RAISE + " Raise", currentMode);
        ImGui.sameLine(0.0f, 2.0f);
        renderHeightModeButton(context, HeightToolPanel.HeightMode.LOWER, StudioIcons.LOWER + " Lower", currentMode);
        ImGui.sameLine(0.0f, 2.0f);
        renderHeightModeButton(context, HeightToolPanel.HeightMode.FLATTEN, StudioIcons.FLATTEN + " Flat", currentMode);
        ImGui.sameLine(0.0f, 2.0f);
        renderHeightModeButton(context, HeightToolPanel.HeightMode.SMOOTH, StudioIcons.SMOOTH + " Smooth", currentMode);
        ImGui.sameLine(0.0f, 2.0f);
        renderHeightModeButton(context, HeightToolPanel.HeightMode.BLEND, StudioIcons.BLEND + " Blend", currentMode);
        ImGui.sameLine(0.0f, 2.0f);
        renderHeightModeButton(context, HeightToolPanel.HeightMode.TERRACE, StudioIcons.TERRACE + " Step", currentMode);

        // Row 2: Step Rate or Target Height
        if (currentMode == HeightToolPanel.HeightMode.FLATTEN || currentMode == HeightToolPanel.HeightMode.SET_VALUE) {
            ImGui.alignTextToFramePadding();
            ImGui.text("Target Height:");
            ImGui.sameLine(0.0f, 4.0f);
            ImGui.pushItemWidth(90.0f);
            int curTarget = panel != null ? panel.targetHeight() : 0;
            targetHeightInput.set(curTarget);
            if (ImGui.inputInt("##hud-th", targetHeightInput, 16, 64)) {
                // target updated
            }
            ImGui.popItemWidth();
        } else {
            ImGui.alignTextToFramePadding();
            ImGui.text("Step Rate:");
            ImGui.sameLine(0.0f, 4.0f);
            int step = panel != null ? panel.stepRate() : 32;
            stepRateInput.set(step);
            ImGui.pushItemWidth(90.0f);
            if (ImGui.sliderInt("##hud-step-rate", stepRateInput.getData(), 4, 128)) {
                // step rate updated
            }
            ImGui.popItemWidth();

            ImGui.sameLine(0.0f, 6.0f);
            ImGui.textDisabled("Quick:");
            ImGui.sameLine(0.0f, 2.0f);
            if (ImGui.smallButton("8##qs8")) stepRateInput.set(8);
            ImGui.sameLine(0.0f, 2.0f);
            if (ImGui.smallButton("16##qs16")) stepRateInput.set(16);
            ImGui.sameLine(0.0f, 2.0f);
            if (ImGui.smallButton("32##qs32")) stepRateInput.set(32);
            ImGui.sameLine(0.0f, 2.0f);
            if (ImGui.smallButton("64##qs64")) stepRateInput.set(64);
        }
    }

    private void renderHeightModeButton(StudioPanelContext context, HeightToolPanel.HeightMode mode,
                                       String label, HeightToolPanel.HeightMode current) {
        boolean active = (mode == current);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0xFF0284C7);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF0369A1);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF075985);
        }
        if (ImGui.button(label + "##hm-btn-" + mode.name())) {
            if (context.activateTool() != null) {
                context.activateTool().accept(mode.engineToolId());
            }
        }
        if (active) {
            ImGui.popStyleColor(3);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Activate " + mode.label() + " tool");
        }
    }

    /**
     * Contextual Spline Path Builder controls (styles, node count, build/clear).
     */
    private void renderPathBuilderContext(StudioPanelContext context, SplinePathTool pathTool) {
        int nodeCount = pathTool != null ? pathTool.path().size() : 0;
        SplineBrushStyle currentStyle = pathTool != null ? pathTool.style() : SplineBrushStyle.SMOOTH;

        // Row 1: Style Pills
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("Style:");
        ImGui.sameLine(0.0f, 4.0f);

        for (SplineBrushStyle styleOption : SplineBrushStyle.values()) {
            boolean active = (currentStyle == styleOption);
            if (active) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF0284C7);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF0369A1);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF075985);
            }
            if (ImGui.button(styleOption.displayName() + "##hud-spl-" + styleOption.name())) {
                if (pathTool != null) pathTool.setStyle(styleOption);
            }
            if (active) {
                ImGui.popStyleColor(3);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(styleOption.displayName() + " path ribbon style");
            }
            ImGui.sameLine(0.0f, 3.0f);
        }

        ImGui.newLine();

        // Row 2: Node counter & Build / Clear actions
        ImGui.alignTextToFramePadding();
        ImGui.textColored(nodeCount >= 2 ? 0xFF34D399 : 0xFF94A3B8, "Nodes: " + nodeCount);

        ImGui.sameLine(0.0f, 10.0f);
        boolean canBuild = (pathTool != null && nodeCount >= 2);
        if (!canBuild) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0xFF16A34A);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF15803D);

        if (ImGui.button(StudioIcons.CHECK + " Build [Enter]##hud-build-path", 96.0f, 22.0f)) {
            if (canBuild) pathTool.buildPath();
        }
        ImGui.popStyleColor(2);
        if (!canBuild) ImGui.popStyleVar();

        ImGui.sameLine(0.0f, 4.0f);
        if (ImGui.button(StudioIcons.CLOSE + " Clear##hud-clr-path", 64.0f, 22.0f)) {
            if (pathTool != null) pathTool.clear();
        }
    }

    private String resolveActiveBrushId(StudioPanelContext context, BrushAwareTool brushTool, StudioBrushManager brushes) {
        if (brushTool != null && brushTool.brush() != null) {
            return brushTool.brush().id();
        }
        if (brushes != null) {
            String toolId = context.activeToolId() != null ? context.activeToolId() : "general";
            EditorBrush active = brushes.activeBrush(toolId, Set.of(BrushCapability.SPATIAL_FOOTPRINT));
            if (active != null) return active.id();
        }
        return "brush.square";
    }

    private String resolveActiveBrushName(StudioPanelContext context, BrushAwareTool brushTool, StudioBrushManager brushes) {
        if (brushTool != null && brushTool.brush() != null) {
            return brushTool.brush().name();
        }
        if (brushes != null) {
            String toolId = context.activeToolId() != null ? context.activeToolId() : "general";
            EditorBrush active = brushes.activeBrush(toolId, Set.of(BrushCapability.SPATIAL_FOOTPRINT));
            if (active != null) return active.name();
        }
        return "Square";
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  Brush Settings HUD Preferences");
        ImGui.separator();

        String[] cornerNames = { "Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right" };
        ImGui.combo("Default Corner Placement##hud-def-corner", defaultCorner, cornerNames);
        ImGui.sliderFloat("HUD Background Opacity##hud-opacity", bgAlpha.getData(), 0.2f, 1.0f, "%.2f");
        autoHide.set(StudioWidgets.toggleSwitch("hud-autohide", autoHide.get(), "Auto-Hide on Non-Brush Tools"));

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset HUD Position & Defaults##hud-pos-reset")) {
            defaultCorner.set(3);
            bgAlpha.set(0.90f);
            autoHide.set(true);
            minimized = false;
            pinned = false;
            snapRequest = Corner.BOTTOM_RIGHT;
        }
    }
}
