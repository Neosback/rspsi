package com.rspsi.studio.ui.panels;

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
            "0: Full", "1: Diagonal", "2: Left 1/2", "3: Right 1/2",
            "4: Corner TL", "5: Corner TR", "6: Corner BR", "7: Corner BL",
            "8: Inv TL", "9: Inv TR", "10: Inv BR", "11: Inv BL"
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

        ImGui.textColored(0xFFE2E8F0, "Tile Painter");
        ImGui.sameLine(0.0f, 20.0f);
        renderPresets();
        ImGui.separator();

        float availW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();
        float previewW = 160.0f;

        ImGui.beginChild("tile-painter-preview-col", previewW, Math.max(100.0f, availH - 4.0f), false);
        renderPreviewBlock(cache, session, context);
        ImGui.endChild();

        ImGui.sameLine(0.0f, 12.0f);

        float contentW = availW - previewW - 16.0f;
        if (ImGui.beginChild("tile-painter-tab-content", contentW, Math.max(100.0f, availH - 4.0f), false)) {
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

        draw.addRectFilled(x, y, x + box, y + box, underlayColor, 3.0f);
        for (var face : mesh.faces()) {
            if (face.material() == 1 && state.overlayId() <= 0) continue;
            int color = face.material() == 1 ? overlayColor : underlayColor;
            var a = mesh.vertices().get(face.a());
            var b = mesh.vertices().get(face.b());
            var c = mesh.vertices().get(face.c());
            draw.addTriangleFilled(px(x, box, a.x()), py(y, box, a.y()),
                    px(x, box, b.x()), py(y, box, b.y()),
                    px(x, box, c.x()), py(y, box, c.y()), color);
        }

        draw.addRect(x, y, x + box, y + box, 0xFF64748B, 3.0f, 0, 1.5f);
        draw.addText(x + box / 2.0f - 10.0f, y - 15.0f, 0xFFE2E8F0, "N ↑");
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

    // Preset ids were picked from swatches that showed definition N for
    // encoded value N; they keep those definition ids but are unverified
    // against real floors (e.g. overlay 12 is an untextured red floor).
    private void renderPresets() {
        ImGui.textDisabled("Presets:");
        ImGui.sameLine();
        if (ImGui.smallButton(StudioIcons.ENVIRONMENT + " Grass##sw-grass")) {
            state.setUnderlayId(FloorId.encode(1)); state.setApplyUnderlay(true);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(StudioIcons.GRID + " Cobble##sw-cobl")) {
            state.setUnderlayId(FloorId.encode(10)); state.setApplyUnderlay(true);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(StudioIcons.WATER + " Water##sw-wtr")) {
            state.setOverlayId(FloorId.encode(12)); state.setApplyOverlay(true);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(StudioIcons.TERRAIN + " Sand##sw-snd")) {
            state.setUnderlayId(FloorId.encode(28)); state.setApplyUnderlay(true);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Snow##sw-snw")) {
            state.setUnderlayId(FloorId.encode(35)); state.setApplyUnderlay(true);
        }
    }

    private void renderSectionTabs() {
        tabButton(0, StudioIcons.LAYERS + " Overlay", state.applyOverlay(), state::setApplyOverlay);
        ImGui.sameLine();
        tabButton(1, StudioIcons.TEXTURE + " Underlay", state.applyUnderlay(), state::setApplyUnderlay);
        ImGui.sameLine();
        tabButton(2, StudioIcons.TILE + " Shape", state.applyShape(), state::setApplyShape);
        ImGui.sameLine();
        tabButton(3, StudioIcons.HEIGHT + " Height", state.applyHeight(), state::setApplyHeight);
        ImGui.sameLine();
        tabButton(4, StudioIcons.FLAG + " Mask", state.applyFlags(), state::setApplyFlags);
        ImGui.sameLine();
        tabButton(5, StudioIcons.REFRESH + " Rotation", state.applyRotation(), state::setApplyRotation);
    }

    private void tabButton(int index, String label, boolean enabled, Consumer<Boolean> setter) {
        boolean current = activeTab == index;
        if (current) ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
        if (ImGui.button((enabled ? StudioIcons.CHECK + " " : "   ") + label + "##tp-tab-" + index)) {
            activeTab = index;
        }
        if (ImGui.isItemClicked(1)) setter.accept(!enabled);
        if (current) ImGui.popStyleColor();
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
        checkbox("Apply Underlay to painted/selected tiles##chk-und",
                state.applyUnderlay(), state::setApplyUnderlay);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Underlay: " + definitionLabel(state.underlayId()));
        ImGui.separator();
        renderFloorGrid(cache, true);
    }

    private void renderOverlayTab(LoadedOsrsCacheSession cache) {
        checkbox("Apply Overlay to painted/selected tiles##chk-ovr",
                state.applyOverlay(), state::setApplyOverlay);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Overlay: " + definitionLabel(state.overlayId()));
        ImGui.separator();
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
            draw.addRectFilled(sx, sy, sx + size, sy + size, color);
            int selected = underlay ? state.underlayId() : state.overlayId();
            boolean isSelected = encoded == selected;
            draw.addRect(sx - (isSelected ? 1 : 0), sy - (isSelected ? 1 : 0),
                    sx + size + (isSelected ? 1 : 0), sy + size + (isSelected ? 1 : 0),
                    isSelected ? 0xFFFFFFFF : 0xFF222222, 0.0f, 0, isSelected ? 2.0f : 1.0f);
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

    private void renderShapeTab() {
        checkbox("Apply Shape to painted/selected tiles##chk-shp", state.applyShape(), state::setApplyShape);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Shape: " + state.shape());
        ImGui.separator();

        for (int i = 0; i < SHAPE_NAMES.length; i++) {
            boolean current = state.shape() == i;
            if (current) ImGui.pushStyleColor(ImGuiCol.Button, 0xFF3B82F6);
            if (ImGui.button(SHAPE_NAMES[i] + "##shp-" + i, 140.0f, 28.0f)) {
                state.setShape(i); state.setApplyShape(true);
            }
            if (current) ImGui.popStyleColor();
            if ((i + 1) % 4 != 0 && i + 1 < SHAPE_NAMES.length) ImGui.sameLine();
        }
    }

    private void renderRotationTab() {
        checkbox("Apply Rotation to painted/selected tiles##chk-rot",
                state.applyRotation(), state::setApplyRotation);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Selected Rotation: " + state.rotation() * 90 + "°");
        ImGui.separator();

        for (int i = 0; i < ROTATION_NAMES.length; i++) {
            boolean current = state.rotation() == i;
            if (current) ImGui.pushStyleColor(ImGuiCol.Button, 0xFF3B82F6);
            if (ImGui.button(ROTATION_NAMES[i] + "##rot-" + i, 160.0f, 32.0f)) {
                state.setRotation(i); state.setApplyRotation(true);
            }
            if (current) ImGui.popStyleColor();
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private void renderFlagsTab() {
        checkbox("Apply Flags to painted/selected tiles##chk-flg", state.applyFlags(), state::setApplyFlags);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Flags Mask: 0x" + Integer.toHexString(state.flags()));
        ImGui.separator();

        flagCheckbox("Blocked Tile (0x01)##blk", OsrsTileFlags.BLOCK_MAP_SQUARE);
        flagCheckbox("Bridge Tile (0x02)##brg", OsrsTileFlags.BRIDGE);
        flagCheckbox("Remove Roofs (0x04)##rf", OsrsTileFlags.REMOVE_ROOFS);
        flagCheckbox("Minimap Bridge (0x08)##mmb", OsrsTileFlags.MINIMAP_BRIDGE);
    }

    private void flagCheckbox(String label, int bit) {
        boolean current = (state.flags() & bit) != 0;
        boolean updated = StudioWidgets.toggleSwitch(label, current, label);
        if (updated != current) {
            state.setFlags(updated ? state.flags() | bit : state.flags() & ~bit);
            state.setApplyFlags(true);
        }
    }

    private void renderHeightTab() {
        checkbox("Apply Height to painted/selected tiles##chk-hgt", state.applyHeight(), state::setApplyHeight);
        ImGui.sameLine(0.0f, 20.0f);
        ImGui.text("Target Height: " + state.height());
        ImGui.separator();

        ImInt height = new ImInt(state.height());
        ImGui.setNextItemWidth(260.0f);
        if (ImGui.sliderInt("Height Value##h-val", height.getData(), -2048, 2048)) {
            state.setHeight(height.get());
            state.setApplyHeight(true);
        }
        ImGui.sameLine();
        if (ImGui.button("Reset to 0##h-0")) {
            state.setHeight(0);
            state.setApplyHeight(true);
        }
    }

    private static void checkbox(String label, boolean current, Consumer<Boolean> setter) {
        boolean updated = StudioWidgets.toggleSwitch(label, current, label);
        if (updated != current) {
            setter.accept(updated);
        }
    }
}
