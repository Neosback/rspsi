package com.rspsi.studio.ui;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.ModelPacketBuilder;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.ModelVertex;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.studio.ViewportOverlayDraw;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.theme.StudioIcons;
import imgui.ImDrawList;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Draws the real silhouette of whatever object(s) are currently selected.
 *
 * <p>This re-reads the live {@link Selection} fresh every frame instead of
 * remembering a tool's own click state (the pattern RuneLite's overlays use:
 * {@code TileIndicatorsOverlay} re-queries {@code getSelectedSceneTile()}
 * every frame and simply draws nothing when it comes back {@code null}). A
 * click-driven tool like {@code BoxSelectTool} previously drew its own
 * fixed-height box and kept it on screen until the next stroke, which is
 * exactly what let the overlay linger after clicking empty ground - nothing
 * here can go stale, because there's no stroke state to forget to clear.</p>
 *
 * <p>The outline itself is a screen-space convex hull over the object's own
 * projected model vertices (ported from RuneLite's {@code Model.getConvexHull()}
 * approach), not a generic bounding box - it hugs the real shape. RuneLite's
 * own "highlight" plugins (object indicators, NPC highlight) turned out to
 * use exactly this same trick - an alpha-blended fill over the 2D hull/tile
 * polygon, not a real tint of the model's own triangles, which its renderer
 * has no hook for either - so the configurable {@link SelectionOverlayStyle#fillAlpha()}
 * here plays the same role RuneLite's translucent hull fill does.</p>
 */
public final class SelectionOverlayPlugin implements StudioPlugin {

    public static final String ID = "studio.overlay.selection";

    private final SelectionOverlayStyle style = SelectionOverlayStyle.shared();

    private Selection lastSelection;
    private final Map<WorldObject, List<float[]>> hullVertexCache = new HashMap<>();

    @Override public String id() { return ID; }
    @Override public String name() { return "Selection Overlay"; }
    @Override public String description() {
        return "Highlights the current tile/object selection with a real silhouette hull instead of a generic box.";
    }
    @Override public String icon() { return StudioIcons.OBJECT; }
    @Override public boolean isConfigurable() { return true; }

    @Override
    public void renderOverlay(ImDrawList drawList, StudioPanelContext context) {
        if (!style.objectHullEnabled()) return;
        if (context == null || context.session() == null || context.cache() == null || context.viewport() == null) {
            return;
        }
        ViewportOverlayDraw draw = context.viewport().createOverlayDraw();

        Selection current = context.session().selection().current();
        Set<WorldObject> objects = switch (current) {
            case ObjectSelection single -> Set.of(single.object());
            case ObjectSetSelection set -> set.objects();
            case null, default -> Set.of();
        };

        if (objects.isEmpty()) {
            lastSelection = current;
            hullVertexCache.clear();
            return;
        }

        if (!Objects.equals(current, lastSelection)) {
            hullVertexCache.keySet().retainAll(objects);
            lastSelection = current;
        }

        DefinitionProvider definitions = context.cache().bundle().definitions();
        for (WorldObject object : objects) {
            List<float[]> worldVertices = hullVertexCache.computeIfAbsent(object,
                    o -> computeWorldVertices(definitions, o, context));
            if (worldVertices.size() < 3) continue;

            ObjectCategory category = object.category();
            int outline = style.outlineColor(category);
            draw.modelHull(worldVertices, style.fillColor(category), true);
            if (style.paintedEdge()) {
                // A soft, wider under-stroke behind the crisp one reads as a
                // "painted" edge rather than a thin wireframe line.
                draw.modelHullOutline(worldVertices, (outline & 0xFFFFFF00) | 0x50, style.outlineThickness() * 2.2f);
            }
            draw.modelHullOutline(worldVertices, outline, style.outlineThickness());

            if (style.showObjectInfo()) {
                renderObjectInfo(draw, definitions, object, worldVertices, outline);
            }
        }
    }

    private static void renderObjectInfo(ViewportOverlayDraw draw, DefinitionProvider definitions,
                                         WorldObject object, List<float[]> worldVertices, int color) {
        float topY = Float.MAX_VALUE;
        float sumX = 0.0f;
        float sumZ = 0.0f;
        for (float[] p : worldVertices) {
            topY = Math.min(topY, p[1]);
            sumX += p[0];
            sumZ += p[2];
        }
        float centerX = sumX / worldVertices.size();
        float centerZ = sumZ / worldVertices.size();
        String name = definitions.object(object.id())
                .map(def -> def.hasDisplayName() ? "#" + object.id() + " " + def.displayName() : def.displayName())
                .orElse("Object #" + object.id() + " (no definition)");
        String label = name + " (" + object.category().displayName() + ")";
        draw.worldLabel(label, centerX, topY - 24.0f, centerZ, 0xFFFFFFFF, (color & 0xFFFFFF00) | 0xD0);
    }

    private static List<float[]> computeWorldVertices(DefinitionProvider definitions, WorldObject object,
                                                       StudioPanelContext context) {
        try {
            var modelOpt = new ModelPacketBuilder(definitions).build(object, context.session().world());
            // Some objects (invisible markers, collision-only volumes) legitimately
            // have no renderable model - same as RuneLite's own getConvexHull(),
            // there's simply nothing to hull around, not an error.
            if (modelOpt.isEmpty()) return List.of();
            ModelRenderPacket model = modelOpt.get();
            // ModelPacketBuilder keeps vertices relative to the anchor tile, and that
            // anchor is itself in LOCAL document space (matching the WorldDocument it
            // was built against) - not the same absolute OSRS world-tile space the
            // camera and viewport projection operate in (see ToolContext's own "the
            // viewport speaks absolute WorldTile; the document speaks LocalTile"
            // doc comment). Skipping this conversion put every projected vertex
            // hundreds of thousands of units away from the camera, so every single
            // one came back "behind/outside" and nothing ever drew.
            WorldTile absoluteAnchor = context.session().coordinates()
                    .toWorld(new LocalTile(model.anchor().plane(), model.anchor().x(), model.anchor().y()));
            float anchorWorldX = absoluteAnchor.x() * 128.0f;
            float anchorWorldZ = absoluteAnchor.y() * 128.0f;
            float baseHeight = model.placementHeight();
            List<float[]> points = new ArrayList<>(model.vertices().size());
            for (ModelVertex vertex : model.vertices()) {
                points.add(new float[]{anchorWorldX + vertex.x(), baseHeight + vertex.y(), anchorWorldZ + vertex.z()});
            }
            return points;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), StudioIcons.OBJECT + "  Selection Overlay");
        ImGui.separator();

        style.setObjectHullEnabled(ImGui.checkbox("Highlight selected objects##sel-ov-enabled", style.objectHullEnabled()));
        style.setShowObjectInfo(ImGui.checkbox("Show object info (id / name / category)##sel-ov-info", style.showObjectInfo()));
        style.setPaintedEdge(ImGui.checkbox("Painted edge (soft outer glow)##sel-ov-painted", style.paintedEdge()));

        float[] thickness = {style.outlineThickness()};
        if (ImGui.sliderFloat("Outline thickness##sel-ov-thickness", thickness, 0.5f, 6.0f, "%.1f")) {
            style.setOutlineThickness(thickness[0]);
        }

        float[] fillPct = {style.fillAlpha() / 255.0f * 100.0f};
        if (ImGui.sliderFloat("Fill opacity##sel-ov-fill", fillPct, 0.0f, 100.0f, "%.0f%%")) {
            style.setFillAlpha(Math.round(fillPct[0] / 100.0f * 255.0f));
        }

        ImGui.spacing();
        ImGui.text("Outline color by object type");
        for (ObjectCategory category : ObjectCategory.values()) {
            float[] col = toFloat4(style.outlineColor(category));
            if (ImGui.colorEdit4(category.displayName() + "##sel-ov-color-" + category.name(), col)) {
                style.setOutlineColor(category, fromFloat4(col));
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Tile selection");
        float[] tileCol = toFloat4(style.tileOutlineColor());
        if (ImGui.colorEdit4("Tile outline color##sel-ov-tile-color", tileCol)) {
            style.setTileOutlineColor(fromFloat4(tileCol));
        }
        float[] tileFillPct = {style.tileFillAlpha() / 255.0f * 100.0f};
        if (ImGui.sliderFloat("Tile fill opacity##sel-ov-tile-fill", tileFillPct, 0.0f, 100.0f, "%.0f%%")) {
            style.setTileFillAlpha(Math.round(tileFillPct[0] / 100.0f * 255.0f));
        }

        ImGui.spacing();
        if (ImGui.button(StudioIcons.REFRESH + "  Reset to Defaults##sel-ov-reset")) {
            style.resetToDefaults();
        }
    }

    private static float[] toFloat4(int rgba) {
        return new float[]{
                ((rgba >>> 24) & 0xFF) / 255.0f,
                ((rgba >>> 16) & 0xFF) / 255.0f,
                ((rgba >>> 8) & 0xFF) / 255.0f,
                (rgba & 0xFF) / 255.0f
        };
    }

    private static int fromFloat4(float[] col) {
        int r = Math.round(col[0] * 255.0f) & 0xFF;
        int g = Math.round(col[1] * 255.0f) & 0xFF;
        int b = Math.round(col[2] * 255.0f) & 0xFF;
        int a = Math.round(col[3] * 255.0f) & 0xFF;
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    /**
     * Modal tool for picking exactly one placed object at a time. Nested here rather than
     * living as its own top-level plugin file: this button exists purely to feed a selection
     * for {@link SelectionOverlayPlugin} to highlight, and the Object Viewer panel to preview -
     * it has no independent identity worth a separate file. Lives on the floating tool rail
     * alongside tile selection.
     *
     * <p>Studio-level presentation id only - like Single/Multi tile select, it drives the same
     * engine {@code selection.box} tool (already able to target objects instead of tiles),
     * distinguished by mode and target in {@code MapEditorView.activateTool}.</p>
     */
    public static final class SingleObjectSelectToolPlugin implements com.rspsi.studio.plugin.StudioToolPlugin {

        public static final String ID = "studio.tool.select.object.single";
        public static final String ENGINE_TOOL_ID = "selection.object.single";

        @Override public String id() { return ID; }
        @Override public String name() { return "Select Object"; }
        @Override public String description() {
            return "Click a placed object to select exactly one for the Object Viewer's preview.";
        }
        @Override public String icon() { return StudioIcons.OBJECT; }
        @Override public String toolId() { return ENGINE_TOOL_ID; }
        @Override public String shortcut() { return ""; }
        @Override public int railPriority() { return 12; }
        @Override public String category() { return "Selection"; }
        @Override public Set<ToolSurface> surfaces() { return java.util.EnumSet.of(ToolSurface.FLOATING_TOOLBAR); }
        @Override public boolean hasContextDrawerContent() { return false; }
        @Override public void renderContextDrawer(StudioPanelContext context) {
            // No drawer content - selection results show in the Object Viewer panel instead.
        }
    }

    /**
     * Modal tool for dragging a rectangular marquee to select every placed object it covers.
     * Nested alongside {@link SingleObjectSelectToolPlugin} for the same reason - it only
     * exists to feed the selection this file's overlay then highlights.
     */
    public static final class MultiObjectSelectToolPlugin implements com.rspsi.studio.plugin.StudioToolPlugin {

        public static final String ID = "studio.tool.select.object.multi";
        public static final String ENGINE_TOOL_ID = "selection.object.multi";

        @Override public String id() { return ID; }
        @Override public String name() { return "Multi Select Objects"; }
        @Override public String description() {
            return "Click and drag to marquee-select every placed object in a rectangular region.";
        }
        @Override public String icon() { return StudioIcons.AREA; }
        @Override public String toolId() { return ENGINE_TOOL_ID; }
        @Override public String shortcut() { return ""; }
        @Override public int railPriority() { return 13; }
        @Override public String category() { return "Selection"; }
        @Override public Set<ToolSurface> surfaces() { return java.util.EnumSet.of(ToolSurface.FLOATING_TOOLBAR); }
        @Override public boolean hasContextDrawerContent() { return false; }
        @Override public void renderContextDrawer(StudioPanelContext context) {
            // No drawer content - selection results show in the Object Viewer panel instead.
        }
    }
}
