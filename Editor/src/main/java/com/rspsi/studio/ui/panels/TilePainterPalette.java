package com.rspsi.studio.ui.panels;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-tab composite Tile Painter console matching the user's specification:
 * Each section (Underlay, Overlay, Shape, Rotation, Flags, Height) has its own tab,
 * and each tab has an "Apply" checkbox allowing selective tile attribute painting.
 */
public final class TilePainterPalette implements StudioPanel {
    public static final String ID = "studio.tile-palette";
    public static TilePainterPalette INSTANCE;

    private int activeTab = 1; // Default to Overlay tab

    // State for composite tile attributes
    private final ImBoolean applyUnderlay = new ImBoolean(false);
    private int underlayId = 0;

    private final ImBoolean applyOverlay = new ImBoolean(true);
    private int overlayId = 1;

    private final ImBoolean applyShape = new ImBoolean(false);
    private int shape = 0;

    private final ImBoolean applyRotation = new ImBoolean(false);
    private int rotation = 0;

    private final ImBoolean applyFlags = new ImBoolean(false);
    private int flags = 0;

    private final ImBoolean applyHeight = new ImBoolean(false);
    private final ImInt heightValue = new ImInt(0);

    private static final String[] SHAPE_NAMES = {
            "0: Full", "1: Diagonal", "2: Left 1/2", "3: Right 1/2",
            "4: Corner TL", "5: Corner TR", "6: Corner BR", "7: Corner BL",
            "8: Inv TL", "9: Inv TR", "10: Inv BR", "11: Inv BL", "12: Island"
    };
    private static final String[] ROTATION_NAMES = {"0° (North)", "90° (East)", "180° (South)", "270° (West)"};

    public TilePainterPalette() {
        INSTANCE = this;
    }

    public void sampleTile(EditorSession session, TileCoordinate coord) {
        if (session == null || coord == null) return;
        var snap = session.world().tile(coord).snapshot();
        this.underlayId = snap.underlayId();
        this.overlayId = snap.overlayId();
        this.shape = snap.overlayShape();
        this.rotation = snap.overlayRotation();
        this.flags = snap.flags();
        this.heightValue.set(snap.southWestHeight());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Tile Painter";
    }

    @Override
    public String icon() {
        return StudioIcons.PALETTE;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.BOTTOM;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT);
    }

    @Override
    public int order() {
        return 5;
    }

    public boolean applyUnderlay() { return applyUnderlay.get(); }
    public boolean applyOverlay() { return applyOverlay.get(); }
    public boolean applyShape() { return applyShape.get(); }
    public boolean applyRotation() { return applyRotation.get(); }
    public boolean applyFlags() { return applyFlags.get(); }
    public boolean applyHeight() { return applyHeight.get(); }

    public int underlayId() { return underlayId; }
    public int overlayId() { return overlayId; }
    public int shape() { return shape; }
    public int rotation() { return rotation; }
    public int flags() { return flags; }
    public int height() { return heightValue.get(); }

    @Override
    public void render(StudioPanelContext context) {
        LoadedOsrsCacheSession cache = context.cache();
        EditorSession session = context.session();

        // Sync with active tool if it is CompositeTilePainterTool
        if (context.toolController() != null && context.toolController().activeTool() instanceof CompositeTilePainterTool tool) {
            tool.setApplyUnderlay(applyUnderlay.get());
            tool.setUnderlayId(underlayId);
            tool.setApplyOverlay(applyOverlay.get());
            tool.setOverlayId(overlayId);
            tool.setApplyShape(applyShape.get());
            tool.setShape(shape);
            tool.setApplyRotation(applyRotation.get());
            tool.setRotation(rotation);
            tool.setApplyFlags(applyFlags.get());
            tool.setFlags(flags);
            tool.setApplyHeight(applyHeight.get());
            tool.setHeight(heightValue.get());
            if (context.brushes() != null) {
                EditorBrush activeBrush = activeBrush(context);
                if (activeBrush != null) tool.setBrush(activeBrush);
                tool.setBrushRadius(context.brushes().brushRadius());
            }
        }

        // Header matching Displee
        ImGui.textColored(0xFFE2E8F0, "Tile Painter");
        ImGui.sameLine(0.0f, 20.0f);
        renderPresets();
        ImGui.separator();
        renderBrushStrip(context);
        ImGui.separator();

        float availW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();

        // 1. Left Preview Column (matching media_1789882734367.png)
        float previewW = 160.0f;
        ImGui.beginChild("tile-painter-preview-col", previewW, Math.max(100.0f, availH - 4.0f), false);
        renderPreviewBlock(cache, session);
        ImGui.endChild();

        ImGui.sameLine(0.0f, 12.0f);

        // 2. Main Area: Checkbox Tabs + Palette/Settings Content
        float contentW = availW - previewW - 16.0f;
        if (ImGui.beginChild("tile-painter-tab-content", contentW, Math.max(100.0f, availH - 4.0f), false)) {
            // Checkbox Tabs strip
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 3.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 0.0f);
            renderSectionTabs();
            ImGui.popStyleVar(2);
            ImGui.separator();

            switch (activeTab) {
                case 0 -> renderOverlayTab(cache);
                case 1 -> renderUnderlayTab(cache);
                case 2 -> renderShapeTab();
                case 3 -> renderHeightTab();
                case 4 -> renderFlagsTab();
                case 5 -> renderRotationTab();
            }
        }
        ImGui.endChild();
    }

    private void renderPreviewBlock(LoadedOsrsCacheSession cache, EditorSession session) {
        ImGui.textDisabled("Preview");

        // Square preview swatch box (64x64)
        float boxSize = 64.0f;
        float curX = ImGui.getCursorScreenPos().x;
        float curY = ImGui.getCursorScreenPos().y;
        imgui.ImDrawList dl = ImGui.getWindowDrawList();

        int underlayColor = 0xFF2A2A2A;
        if (cache != null && underlayId > 0) {
            var def = cache.bundle().definitions().underlay(underlayId);
            if (def.isPresent() && def.get().rgb() > 0) {
                underlayColor = 0xFF000000 | def.get().rgb();
            }
        }

        int overlayColor = 0xFF4A4A4A;
        if (cache != null && overlayId > 0) {
            var def = cache.bundle().definitions().overlay(overlayId);
            if (def.isPresent() && def.get().rgb() > 0) {
                overlayColor = 0xFF000000 | def.get().rgb();
            }
        }

        // Draw base underlay or background
        dl.addRectFilled(curX, curY, curX + boxSize, curY + boxSize, underlayColor, 4.0f);

        // Draw overlay shape if overlay is active
        if (overlayId > 0) {
            if (shape == 0) {
                dl.addRectFilled(curX, curY, curX + boxSize, curY + boxSize, overlayColor, 4.0f);
            } else {
                // Diagonal / shape representation
                dl.addTriangleFilled(curX, curY, curX + boxSize, curY, curX + boxSize, curY + boxSize, overlayColor);
            }
        }

        dl.addRect(curX, curY, curX + boxSize, curY + boxSize, 0xFF64748B, 4.0f, 0, 1.5f);
        ImGui.dummy(boxSize, boxSize);

        ImGui.spacing();
        ImGui.pushTextWrapPos(curX + boxSize + 40.0f);
        ImGui.textDisabled("Select an overlay or underlay");
        ImGui.popTextWrapPos();

        ImGui.spacing();
        Set<TileCoordinate> selected = session != null ? session.selection().selectedCoordinates() : Set.of();
        int selCount = selected.size();
        boolean canApply = selCount > 0 && session != null;

        if (!canApply) ImGui.beginDisabled();
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.18f, 0.55f, 0.35f, 1.0f));
        if (ImGui.button("Apply (" + selCount + ")##tp-apply-btn", 140.0f, 26.0f)) {
            applyCompositeToSelection(session, selected);
        }
        ImGui.popStyleColor();
        if (!canApply) ImGui.endDisabled();
        if (ImGui.isItemHovered() && selCount == 0) {
            ImGui.setTooltip("Select tiles first with the Selector to apply");
        }
    }

    private void renderBrushStrip(StudioPanelContext context) {
        if (context.brushes() == null) return;

        List<EditorBrush> compatible = compatibleBrushes(context);
        EditorBrush active = activeBrush(context);

        ImGui.textDisabled("Brush:");
        ImGui.sameLine();
        for (EditorBrush brush : compatible) {
            boolean selected = active != null && brush.id().equals(active.id());
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
            }
            if (ImGui.smallButton(brush.name() + "##tile-brush-" + brush.id())) {
                context.brushes().setActiveBrush("terrain.tile-painter", brush.id());
                active = brush;
            }
            if (selected) ImGui.popStyleColor();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(brush.description() + "\n" + String.join(", ", context.brushes().capabilityLabels(brush)));
            }
            ImGui.sameLine();
        }
        ImGui.newLine();

        ImInt radius = new ImInt(context.brushes().brushRadius());
        ImGui.setNextItemWidth(180.0f);
        if (ImGui.sliderInt("Radius##tile-brush-radius", radius.getData(), 0, 16)) {
            context.brushes().setBrushRadius(radius.get());
        }
        ImGui.sameLine();
        ImGui.textDisabled("Quick:");
        for (int preset : new int[]{0, 1, 2, 3, 5}) {
            ImGui.sameLine();
            if (ImGui.smallButton(preset + "##tile-brush-radius-" + preset)) {
                context.brushes().setBrushRadius(preset);
            }
        }
    }

    private List<EditorBrush> compatibleBrushes(StudioPanelContext context) {
        List<EditorBrush> result = new ArrayList<>();
        for (EditorBrush brush : context.brushes().enabledBrushes()) {
            boolean spatial = brush.capabilities().contains(BrushCapability.SPATIAL_FOOTPRINT);
            boolean paint = brush.capabilities().contains(BrushCapability.TILE_PAINT);
            boolean height = brush.capabilities().contains(BrushCapability.HEIGHT_MANIPULATION);
            if (spatial && (paint || (applyHeight.get() && height))) {
                result.add(brush);
            }
        }
        return List.copyOf(result);
    }

    private EditorBrush activeBrush(StudioPanelContext context) {
        List<EditorBrush> compatible = compatibleBrushes(context);
        if (compatible.isEmpty()) return null;
        EditorBrush active = context.brushes().activeBrush(
                "terrain.tile-painter", Set.of(BrushCapability.SPATIAL_FOOTPRINT));
        if (active != null && compatible.stream().anyMatch(brush -> brush.id().equals(active.id()))) {
            return active;
        }
        EditorBrush fallback = compatible.get(0);
        context.brushes().setActiveBrush("terrain.tile-painter", fallback.id());
        return fallback;
    }

    private void renderPresets() {
        ImGui.textDisabled("Presets:");
        ImGui.sameLine(0.0f, 4.0f);
        if (ImGui.smallButton(StudioIcons.ENVIRONMENT + " Grass##sw-grass")) {
            underlayId = 1;
            applyUnderlay.set(true);
        }
        ImGui.sameLine(0.0f, 3.0f);
        if (ImGui.smallButton(StudioIcons.GRID + " Cobble##sw-cobl")) {
            underlayId = 10;
            applyUnderlay.set(true);
        }
        ImGui.sameLine(0.0f, 3.0f);
        if (ImGui.smallButton(StudioIcons.WATER + " Water##sw-wtr")) {
            overlayId = 12;
            applyOverlay.set(true);
        }
        ImGui.sameLine(0.0f, 3.0f);
        if (ImGui.smallButton(StudioIcons.TERRAIN + " Sand##sw-snd")) {
            underlayId = 28;
            applyUnderlay.set(true);
        }
        ImGui.sameLine(0.0f, 3.0f);
        if (ImGui.smallButton("Snow##sw-snw")) {
            underlayId = 35;
            applyUnderlay.set(true);
        }
    }

    private void renderSectionTabs() {
        tabButton(0, StudioIcons.LAYERS + " Overlay", applyOverlay);
        ImGui.sameLine();
        tabButton(1, StudioIcons.TEXTURE + " Underlay", applyUnderlay);
        ImGui.sameLine();
        tabButton(2, StudioIcons.TILE + " Shape", applyShape);
        ImGui.sameLine();
        tabButton(3, StudioIcons.HEIGHT + " Height", applyHeight);
        ImGui.sameLine();
        tabButton(4, StudioIcons.FLAG + " Mask", applyFlags);
        ImGui.sameLine();
        tabButton(5, StudioIcons.REFRESH + " Rotation", applyRotation);
    }

    private void tabButton(int index, String label, ImBoolean checkState) {
        boolean isCurrent = activeTab == index;
        if (isCurrent) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
        }

        String mark = checkState.get() ? StudioIcons.CHECK + " " : "   ";
        if (ImGui.button(mark + label + "##tp-tab-" + index)) {
            activeTab = index;
        }

        if (ImGui.isItemClicked(1)) {
            // Right-click toggles checkbox state
            checkState.set(!checkState.get());
        }

        if (isCurrent) {
            ImGui.popStyleColor();
        }
    }

    private void applyCompositeToSelection(EditorSession session, Set<TileCoordinate> selected) {
        if (session == null || selected.isEmpty()) return;
        CompositeTilePainterTool helper = new CompositeTilePainterTool();
        helper.setApplyUnderlay(applyUnderlay.get());
        helper.setUnderlayId(underlayId);
        helper.setApplyOverlay(applyOverlay.get());
        helper.setOverlayId(overlayId);
        helper.setApplyShape(applyShape.get());
        helper.setShape(shape);
        helper.setApplyRotation(applyRotation.get());
        helper.setRotation(rotation);
        helper.setApplyFlags(applyFlags.get());
        helper.setFlags(flags);
        helper.setApplyHeight(applyHeight.get());
        helper.setHeight(heightValue.get());
        helper.applyToCoordinates(selected, session);
    }

    private void renderUnderlayTab(LoadedOsrsCacheSession cache) {
        ImGui.checkbox("Apply Underlay to painted/selected tiles##chk-und", applyUnderlay);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Underlay: #" + underlayId);
        ImGui.separator();

        float swatchSize = 22.0f;
        float spacing = 3.0f;
        float availW = ImGui.getContentRegionAvailX();
        int cols = Math.max(1, (int) (availW / (swatchSize + spacing)));
        imgui.ImDrawList draw = ImGui.getWindowDrawList();

        for (int i = 0; i < 128; i++) {
            if (i > 0 && i % cols != 0) ImGui.sameLine(0.0f, spacing);

            int color = 0xFF333333;
            if (cache != null) {
                var def = cache.bundle().definitions().underlay(i);
                if (def.isPresent() && def.get().rgb() > 0) {
                    color = 0xFF000000 | def.get().rgb();
                }
            }

            float sx = ImGui.getCursorScreenPos().x;
            float sy = ImGui.getCursorScreenPos().y;

            draw.addRectFilled(sx, sy, sx + swatchSize, sy + swatchSize, color);
            if (i == underlayId) {
                draw.addRect(sx - 1, sy - 1, sx + swatchSize + 1, sy + swatchSize + 1, 0xFFFFFFFF, 0.0f, 0, 2.0f);
            } else {
                draw.addRect(sx, sy, sx + swatchSize, sy + swatchSize, 0xFF222222);
            }

            if (ImGui.invisibleButton("und-" + i, swatchSize, swatchSize)) {
                underlayId = i;
                applyUnderlay.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Underlay #" + i);
            }
        }
    }

    private void renderOverlayTab(LoadedOsrsCacheSession cache) {
        ImGui.checkbox("Apply Overlay to painted/selected tiles##chk-ovr", applyOverlay);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Overlay: #" + overlayId);
        ImGui.separator();

        float swatchSize = 22.0f;
        float spacing = 3.0f;
        float availW = ImGui.getContentRegionAvailX();
        int cols = Math.max(1, (int) (availW / (swatchSize + spacing)));
        imgui.ImDrawList draw = ImGui.getWindowDrawList();

        for (int i = 0; i < 128; i++) {
            if (i > 0 && i % cols != 0) ImGui.sameLine(0.0f, spacing);

            int color = 0xFF4A4A4A;
            if (cache != null) {
                var def = cache.bundle().definitions().overlay(i);
                if (def.isPresent() && def.get().rgb() > 0) {
                    color = 0xFF000000 | def.get().rgb();
                }
            }

            float sx = ImGui.getCursorScreenPos().x;
            float sy = ImGui.getCursorScreenPos().y;

            draw.addRectFilled(sx, sy, sx + swatchSize, sy + swatchSize, color);
            if (i == overlayId) {
                draw.addRect(sx - 1, sy - 1, sx + swatchSize + 1, sy + swatchSize + 1, 0xFFFFFFFF, 0.0f, 0, 2.0f);
            } else {
                draw.addRect(sx, sy, sx + swatchSize, sy + swatchSize, 0xFF222222);
            }

            if (ImGui.invisibleButton("ovr-" + i, swatchSize, swatchSize)) {
                overlayId = i;
                applyOverlay.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Overlay #" + i);
            }
        }
    }

    private void renderShapeTab() {
        ImGui.checkbox("Apply Shape to painted/selected tiles##chk-shp", applyShape);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Shape: " + shape);
        ImGui.separator();

        for (int i = 0; i < SHAPE_NAMES.length; i++) {
            boolean isCur = shape == i;
            if (isCur) ImGui.pushStyleColor(ImGuiCol.Button, 0xFF3B82F6);
            if (ImGui.button(SHAPE_NAMES[i] + "##shp-" + i, 140.0f, 28.0f)) {
                shape = i;
                applyShape.set(true);
            }
            if (isCur) ImGui.popStyleColor();

            if ((i + 1) % 4 != 0 && i + 1 < SHAPE_NAMES.length) {
                ImGui.sameLine();
            }
        }
    }

    private void renderRotationTab() {
        ImGui.checkbox("Apply Rotation to painted/selected tiles##chk-rot", applyRotation);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Rotation: " + (rotation * 90) + "°");
        ImGui.separator();

        for (int i = 0; i < ROTATION_NAMES.length; i++) {
            boolean isCur = rotation == i;
            if (isCur) ImGui.pushStyleColor(ImGuiCol.Button, 0xFF3B82F6);
            if (ImGui.button(ROTATION_NAMES[i] + "##rot-" + i, 160.0f, 32.0f)) {
                rotation = i;
                applyRotation.set(true);
            }
            if (isCur) ImGui.popStyleColor();
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private void renderFlagsTab() {
        ImGui.checkbox("Apply Flags to painted/selected tiles##chk-flg", applyFlags);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Flags Mask: 0x" + Integer.toHexString(flags));
        ImGui.separator();

        boolean blocked = (flags & 0x01) != 0;
        if (ImGui.checkbox("Blocked Tile (0x01)##blk", blocked)) {
            flags ^= 0x01;
            applyFlags.set(true);
        }

        boolean bridge = (flags & 0x02) != 0;
        if (ImGui.checkbox("Bridge Tile (0x02)##brg", bridge)) {
            flags ^= 0x02;
            applyFlags.set(true);
        }

        boolean roof = (flags & 0x04) != 0;
        if (ImGui.checkbox("Under Roof / Force Lower (0x04)##rf", roof)) {
            flags ^= 0x04;
            applyFlags.set(true);
        }
    }

    private void renderHeightTab() {
        ImGui.checkbox("Apply Height to painted/selected tiles##chk-hgt", applyHeight);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Target Height: " + heightValue.get());
        ImGui.separator();

        ImGui.setNextItemWidth(260.0f);
        if (ImGui.sliderInt("Height Value##h-val", heightValue.getData(), -512, 512)) {
            applyHeight.set(true);
        }

        ImGui.sameLine();
        if (ImGui.button("Reset to 0##h-0")) {
            heightValue.set(0);
            applyHeight.set(true);
        }
    }
}
