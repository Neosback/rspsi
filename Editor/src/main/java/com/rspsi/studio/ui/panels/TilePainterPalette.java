package com.rspsi.studio.ui.panels;

import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.editor.model.FloorId;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.editor.tool.state.TilePainterState;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Composite Tile Painter console bound to one authoritative TilePainterState.
 * The active tool shares this same state object rather than receiving copied
 * settings every frame.
 *
 * <p>Brush shape/radius controls live exclusively in {@link
 * com.rspsi.studio.ui.hud.BrushSettingsHud} now - this panel and that HUD
 * used to render two independent copies of the same brush strip bound to
 * the same underlying state, which was confusing busywork, not two
 * different things to configure.</p>
 */
public final class TilePainterPalette implements StudioPanel {
    public static final String ID = "studio.tile-palette";
    public static TilePainterPalette INSTANCE;

    private final TilePainterState state = new TilePainterState();
    private final TerrainMeshBuilder meshBuilder = new TerrainMeshBuilder();
    private int activeTab = 1;

    private static final String[] SHAPE_NAMES = {
            "Full", "Diagonal", "Left 1/2", "Right 1/2",
            "Corner TL", "Corner TR", "Corner BR", "Corner BL",
            "Inverse TL", "Inverse TR", "Inverse BR", "Inverse BL"
    };
    private static final String[] ROTATION_NAMES = {
            "0° (North)", "90° (East)", "180° (South)", "270° (West)"
    };

    public TilePainterPalette() {
        INSTANCE = this;
    }

    public TilePainterState state() { return state; }

    public void sampleTile(EditorSession session, TileCoordinate coord) {
        if (session == null || coord == null || !session.world().contains(coord)) return;
        TileSnapshot snap = session.world().tile(coord).snapshot();
        state.setUnderlayId(snap.underlayId());
        state.setOverlayId(snap.overlayId());
        state.setShape(snap.overlayShape());
        state.setRotation(snap.overlayRotation());
        state.setFlags(snap.flags());
        state.setHeight(snap.southWestHeight());
    }

    @Override public String id() { return ID; }
    @Override public String title() { return "Tile Painter"; }
    @Override public String icon() { return StudioIcons.PALETTE; }
    @Override public DockRegion preferredRegion() { return DockRegion.BOTTOM; }
    @Override public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT);
    }
    @Override public int order() { return 5; }

    public boolean applyUnderlay() { return state.applyUnderlay(); }
    public boolean applyOverlay() { return state.applyOverlay(); }
    public boolean applyShape() { return state.applyShape(); }
    public boolean applyRotation() { return state.applyRotation(); }
    public boolean applyFlags() { return state.applyFlags(); }
    public boolean applyHeight() { return state.applyHeight(); }
    public int underlayId() { return state.underlayId(); }
    public int overlayId() { return state.overlayId(); }
    public int shape() { return state.shape(); }
    public int rotation() { return state.rotation(); }
    public int flags() { return state.flags(); }
    public int height() { return state.height(); }

    @Override
    public void render(StudioPanelContext context) {
        LoadedOsrsCacheSession cache = context.cache();
        EditorSession session = context.session();

        if (context.toolController() != null
                && context.toolController().activeTool() instanceof CompositeTilePainterTool tool) {
            if (context.brushes() != null) {
                EditorBrush active = activeBrush(context);
                if (active != null && !active.id().equals(tool.brush().id())) tool.setBrush(active);
                if (tool.state() != state) tool.bindState(state);
                if (state.brushRadius() != context.brushes().brushRadius()) {
                    state.setBrushRadius(context.brushes().brushRadius());
                }
            }
        }

        // The tab strip is intentionally outside every scrolling child. Tool identity and
        // channel-enable state remain visible while the selected channel body scrolls.
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 7.0f, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 5.0f, 0.0f);
        renderSectionTabs();
        ImGui.popStyleVar(2);
        ImGui.separator();

        float availW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();
        float previewW = Math.min(170.0f, Math.max(140.0f, availW * 0.18f));

        ImGui.beginChild("tile-painter-preview-col", previewW, Math.max(100.0f, availH - 2.0f), false);
        renderPreviewBlock(cache, session, context);
        ImGui.endChild();

        ImGui.sameLine(0.0f, 12.0f);

        float contentW = Math.max(220.0f, availW - previewW - 16.0f);
        if (ImGui.beginChild("tile-painter-tab-content", contentW,
                Math.max(100.0f, availH - 2.0f), false)) {
            ImGui.setScrollX(0.0f);
            switch (activeTab) {
                case 0 -> renderOverlayTab(cache);
                case 1 -> renderUnderlayTab(cache);
                case 2 -> renderShapeTab(cache);
                case 3 -> renderHeightTab();
                case 4 -> renderFlagsTab();
                case 5 -> renderRotationTab();
                default -> { }
            }
        }
        ImGui.endChild();
    }

    /** Exact topology preview generated by the same TerrainMeshBuilder as scene terrain. */
    private void renderPreviewBlock(LoadedOsrsCacheSession cache, EditorSession session, StudioPanelContext context) {
        ImGui.textDisabled("Preview");

        float box = 96.0f;
        float x = ImGui.getCursorScreenPos().x;
        float y = ImGui.getCursorScreenPos().y;
        imgui.ImDrawList draw = ImGui.getWindowDrawList();

        int underlayColor = floorColor(cache, state.underlayId(), true, 0xFF2A2A2A);
        int overlayColor = floorColor(cache, state.overlayId(), false, 0xFF4A4A4A);

        TileSnapshot preview = new TileSnapshot(
                state.height(), state.height(), state.height(), state.height(),
                state.underlayId(), state.overlayId(), state.shape(), state.rotation(),
                state.flags(), List.of());
        TerrainMesh mesh = meshBuilder.build(preview);

        draw.addRectFilled(x, y, x + box, y + box, StudioPalette.draw(underlayColor), 3.0f);
        for (var face : mesh.faces()) {
            if (face.material() == 1 && state.overlayId() <= 0) continue;
            int color = face.material() == 1 ? overlayColor : underlayColor;
            var a = mesh.vertices().get(face.a());
            var b = mesh.vertices().get(face.b());
            var c = mesh.vertices().get(face.c());
            draw.addTriangleFilled(px(x, box, a.x()), py(y, box, a.y()),
                    px(x, box, b.x()), py(y, box, b.y()),
                    px(x, box, c.x()), py(y, box, c.y()), StudioPalette.draw(color));
        }

        draw.addRect(x, y, x + box, y + box, StudioPalette.draw(StudioPalette.BORDER_STRONG),
                3.0f, 0, 1.5f);
        int compassSize = 18;
        var compassText = ImGui.calcTextSize(StudioIcons.EXPLORE);
        draw.addText(StudioFonts.icon(), compassSize,
                x + (box - compassText.x) * 0.5f, y - 19.0f,
                StudioPalette.draw(StudioPalette.TEXT), StudioIcons.EXPLORE);
        ImGui.dummy(box, box);

        ImGui.textDisabled("Shape " + state.shape() + "  |  Rotation " + state.rotation() * 90 + "°");

        Set<TileCoordinate> selected = session != null ? session.selection().selectedCoordinates() : Set.of();
        int count = selected.size();
        if (count == 0) ImGui.beginDisabled();
        if (ImGui.button("Apply (" + count + ")##tp-apply-btn", 140.0f, 26.0f)) {
            applyCompositeToSelection(session, selected, context);
        }
        if (count == 0) ImGui.endDisabled();
    }

    private static float px(float x, float size, int vertexX) {
        return x + vertexX / 128.0f * size;
    }

    private static float py(float y, float size, int vertexY) {
        return y + size - vertexY / 128.0f * size;
    }

    /** Display colour for an encoded (map-stored) floor id; see {@link FloorId}. */
    public static int floorColor(LoadedOsrsCacheSession cache, int encodedId,
                                  boolean underlay, int fallback) {
        if (cache == null || encodedId <= 0) return fallback;
        int definitionId = FloorId.definitionId(encodedId);
        var def = underlay
                ? cache.bundle().definitions().underlay(definitionId)
                : cache.bundle().definitions().overlay(definitionId);
        return def.isPresent() && def.get().rgb() >= 0
                ? 0xFF000000 | def.get().rgb() : fallback;
    }

    private List<EditorBrush> compatibleBrushes(StudioPanelContext context) {
        List<EditorBrush> result = new ArrayList<>();
        for (EditorBrush brush : context.brushes().enabledBrushes()) {
            boolean spatial = brush.capabilities().contains(BrushCapability.SPATIAL_FOOTPRINT);
            boolean paint = brush.capabilities().contains(BrushCapability.TILE_PAINT);
            boolean height = brush.capabilities().contains(BrushCapability.HEIGHT_MANIPULATION);
            if (spatial && (paint || (state.applyHeight() && height))) result.add(brush);
        }
        return List.copyOf(result);
    }

    private EditorBrush activeBrush(StudioPanelContext context) {
        List<EditorBrush> compatible = compatibleBrushes(context);
        if (compatible.isEmpty()) return null;
        EditorBrush active = context.brushes().activeBrush(
                "terrain.tile-painter", Set.of(BrushCapability.SPATIAL_FOOTPRINT));
        if (active != null && compatible.stream().anyMatch(b -> b.id().equals(active.id()))) {
            if (!state.brushId().equals(active.id())) state.setBrushId(active.id());
            return active;
        }
        EditorBrush fallback = compatible.get(0);
        context.brushes().setActiveBrush("terrain.tile-painter", fallback.id());
        state.setBrushId(fallback.id());
        return fallback;
    }

    private static String definitionLabel(int encodedId) {
        return encodedId <= 0 ? "none" : "#" + FloorId.definitionId(encodedId);
    }

    private void renderSectionTabs() {
        if (!ImGui.beginTable("##tile-painter-tabs", 6, ImGuiTableFlags.SizingStretchSame)) {
            return;
        }
        sectionTab(0, "Overlay", state.applyOverlay(), state::setApplyOverlay);
        sectionTab(1, "Underlay", state.applyUnderlay(), state::setApplyUnderlay);
        sectionTab(2, "Shape", state.applyShape(), state::setApplyShape);
        sectionTab(3, "Height", state.applyHeight(), state::setApplyHeight);
        sectionTab(4, "Mask", state.applyFlags(), state::setApplyFlags);
        sectionTab(5, "Rotation", state.applyRotation(), state::setApplyRotation);
        ImGui.endTable();
    }

    private void sectionTab(int index, String label, boolean enabled, Consumer<Boolean> setter) {
        ImGui.tableNextColumn();
        boolean current = activeTab == index;
        float checkWidth = ImGui.getFrameHeight() + 6.0f;
        float labelWidth = Math.max(44.0f, ImGui.getContentRegionAvailX() - checkWidth);

        ImGui.pushStyleColor(ImGuiCol.Button,
                current ? StudioPalette.ACCENT : StudioPalette.PANEL_ELEVATED);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,
                current ? StudioPalette.ACCENT_HOVER : StudioPalette.FIELD_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,
                current ? StudioPalette.ACCENT_ACTIVE : StudioPalette.ACCENT_SOFT);
        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
        if (ImGui.button(label + "##tp-tab-" + index, labelWidth, 0.0f)) {
            activeTab = index;
        }
        ImGui.popStyleColor(4);

        ImGui.sameLine(0.0f, 3.0f);
        ImBoolean checked = new ImBoolean(enabled);
        if (ImGui.checkbox("##tp-enabled-" + index, checked)) {
            setter.accept(checked.get());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(checked.get() ? "Included when painting" : "Not included when painting");
        }
    }

    private void applyCompositeToSelection(EditorSession session, Set<TileCoordinate> selected,
                                           StudioPanelContext context) {
        if (session == null || selected.isEmpty()) return;
        CompositeTilePainterTool helper = new CompositeTilePainterTool();
        if (context != null && context.brushes() != null) {
            EditorBrush active = activeBrush(context);
            if (active != null) helper.setBrush(active);
        }
        helper.bindState(state);
        helper.applyToCoordinates(selected, session);
    }

    private void renderUnderlayTab(LoadedOsrsCacheSession cache) {
        StudioWidgets.heading("Underlay", "Selected " + definitionLabel(state.underlayId()));
        renderFloorGrid(cache, true);
    }

    private void renderOverlayTab(LoadedOsrsCacheSession cache) {
        StudioWidgets.heading("Overlay", "Selected " + definitionLabel(state.overlayId()));
        renderFloorGrid(cache, false);
    }

    private void renderFloorGrid(LoadedOsrsCacheSession cache, boolean underlay) {
        float size = 22.0f;
        float spacing = 3.0f;
        int cols = Math.max(1, (int) (ImGui.getContentRegionAvailX() / (size + spacing)));
        imgui.ImDrawList draw = ImGui.getWindowDrawList();

        // Swatch i is floor definition i; painter state stores the encoded map value.
        for (int i = 0; i < 128; i++) {
            if (i > 0 && i % cols != 0) ImGui.sameLine(0.0f, spacing);
            int encoded = FloorId.encode(i);
            int color = floorColor(cache, encoded, underlay, underlay ? 0xFF333333 : 0xFF4A4A4A);
            float sx = ImGui.getCursorScreenPos().x;
            float sy = ImGui.getCursorScreenPos().y;
            draw.addRectFilled(sx, sy, sx + size, sy + size, StudioPalette.draw(color));
            int selected = underlay ? state.underlayId() : state.overlayId();
            boolean isSelected = encoded == selected;
            draw.addRect(sx - (isSelected ? 1 : 0), sy - (isSelected ? 1 : 0),
                    sx + size + (isSelected ? 1 : 0), sy + size + (isSelected ? 1 : 0),
                    StudioPalette.draw(isSelected ? StudioPalette.TEXT : StudioPalette.BORDER),
                    0.0f, 0, isSelected ? 2.0f : 1.0f);
            if (ImGui.invisibleButton((underlay ? "und-" : "ovr-") + i, size, size)) {
                if (underlay) {
                    state.setUnderlayId(encoded); state.setApplyUnderlay(true);
                } else {
                    state.setOverlayId(encoded); state.setApplyOverlay(true);
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip((underlay ? "Underlay #" : "Overlay #") + i);
        }
    }

    private void renderShapeTab(LoadedOsrsCacheSession cache) {
        StudioWidgets.heading("Tile shape",
                "Preview uses the same terrain mesh topology as the scene renderer.");

        float cardW = 94.0f;
        float cardH = 86.0f;
        float gap = 8.0f;
        int cols = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + gap) / (cardW + gap)));
        for (int i = 0; i < SHAPE_NAMES.length; i++) {
            if (i > 0 && i % cols != 0) ImGui.sameLine(0.0f, gap);
            renderShapeCard(cache, i, cardW, cardH);
        }
    }

    private void renderShapeCard(LoadedOsrsCacheSession cache, int shape, float width, float height) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean selected = state.shape() == shape;
        if (ImGui.invisibleButton("##shape-card-" + shape, width, height)) {
            state.setShape(shape);
            state.setApplyShape(true);
        }

        var draw = ImGui.getWindowDrawList();
        int bg = StudioPalette.draw(selected ? StudioPalette.ACCENT_SOFT : StudioPalette.FIELD_BG);
        int border = StudioPalette.draw(selected ? StudioPalette.ACCENT : StudioPalette.BORDER);
        draw.addRectFilled(x, y, x + width, y + height, bg, 5.0f);
        draw.addRect(x, y, x + width, y + height, border, 5.0f, 0, selected ? 2.0f : 1.0f);

        float size = 52.0f;
        float px = x + (width - size) * 0.5f;
        float py = y + 6.0f;
        int underlayColor = floorColor(cache, state.underlayId(), true, 0xFF334155);
        int overlayColor = floorColor(cache, state.overlayId(), false, 0xFF9A6B32);
        TileSnapshot preview = new TileSnapshot(
                0, 0, 0, 0,
                state.underlayId(), state.overlayId(), shape, state.rotation(),
                0, List.of());
        TerrainMesh mesh = meshBuilder.build(preview);
        draw.addRectFilled(px, py, px + size, py + size, StudioPalette.draw(underlayColor), 2.0f);
        for (var face : mesh.faces()) {
            int color = face.material() == 1 ? overlayColor : underlayColor;
            var a = mesh.vertices().get(face.a());
            var b = mesh.vertices().get(face.b());
            var cc = mesh.vertices().get(face.c());
            draw.addTriangleFilled(
                    px(px, size, a.x()), py(py, size, a.y()),
                    px(px, size, b.x()), py(py, size, b.y()),
                    px(px, size, cc.x()), py(py, size, cc.y()), StudioPalette.draw(color));
        }
        draw.addRect(px, py, px + size, py + size,
                StudioPalette.draw(StudioPalette.BORDER_STRONG), 2.0f);

        String caption = shape + " · " + SHAPE_NAMES[shape];
        int captionSize = 12;
        draw.addText(StudioFonts.ui(), captionSize, x + 6.0f, y + 64.0f,
                StudioPalette.draw(StudioPalette.TEXT), caption);
    }

    private void renderRotationTab() {
        StudioWidgets.heading("Rotation", "Selected " + state.rotation() * 90 + "°");
        float gap = 8.0f;
        float width = Math.max(120.0f, (ImGui.getContentRegionAvailX() - gap) * 0.5f);
        for (int i = 0; i < ROTATION_NAMES.length; i++) {
            if (i % 2 == 1) ImGui.sameLine(0.0f, gap);
            boolean current = state.rotation() == i;
            ImGui.pushStyleColor(ImGuiCol.Button,
                    current ? StudioPalette.ACCENT : StudioPalette.PANEL_ELEVATED);
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
            if (ImGui.button(ROTATION_NAMES[i] + "##rot-" + i, width, 32.0f)) {
                state.setRotation(i);
                state.setApplyRotation(true);
            }
            ImGui.popStyleColor(2);
        }
    }

    private void renderFlagsTab() {
        StudioWidgets.heading("Tile mask", "Flags 0x" + Integer.toHexString(state.flags()).toUpperCase());
        if (ImGui.beginTable("##tile-mask-grid", 2, ImGuiTableFlags.SizingStretchSame)) {
            maskCheckbox("Blocked tile", OsrsTileFlags.BLOCK_MAP_SQUARE);
            maskCheckbox("Bridge tile", OsrsTileFlags.BRIDGE);
            maskCheckbox("Remove roofs", OsrsTileFlags.REMOVE_ROOFS);
            maskCheckbox("Minimap bridge", OsrsTileFlags.MINIMAP_BRIDGE);
            ImGui.endTable();
        }
    }

    private void maskCheckbox(String label, int bit) {
        ImGui.tableNextColumn();
        boolean current = (state.flags() & bit) != 0;
        ImBoolean value = new ImBoolean(current);
        if (ImGui.checkbox(label + "##mask-" + bit, value)) {
            state.setFlags(value.get() ? state.flags() | bit : state.flags() & ~bit);
            state.setApplyFlags(true);
        }
    }

    private void renderHeightTab() {
        StudioWidgets.heading("Height", "Target " + state.height());
        ImInt height = new ImInt(state.height());
        ImGui.setNextItemWidth(Math.max(180.0f, ImGui.getContentRegionAvailX() - 110.0f));
        if (ImGui.sliderInt("##h-val", height.getData(), -2048, 2048)) {
            state.setHeight(height.get());
            state.setApplyHeight(true);
        }
        ImGui.sameLine();
        if (ImGui.button("Reset##h-0", 90.0f, 0.0f)) {
            state.setHeight(0);
            state.setApplyHeight(true);
        }
    }
}
