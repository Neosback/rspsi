package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.SetTerrainHeightCommand;
import com.rspsi.editor.SetTileMaterialCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.terrain.TerrainVertexLattice;
import com.rspsi.editor.tool.spline.SplineBrushStyle;
import com.rspsi.editor.tool.spline.SplinePath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Interactive spline path authoring tool.
 * Plots sequential control points, renders live Catmull-Rom curve and footprint previews,
 * and compiles the ribbon into atomic undoable tile material and height mutations.
 */
public final class SplinePathTool implements EditorTool {

    public static final String ID = "path.spline";

    private final SplinePath path = new SplinePath();
    private SplineBrushStyle style = SplineBrushStyle.SMOOTH;
    private int overlayId = 1;
    private int underlayId = 0;
    private boolean paintUnderlay = false;
    private int dragPointIndex = -1;
    private int selectedPointIndex = -1;
    private WorldTile hoveredTile;
    private ToolContext context;

    public SplinePath path() {
        return path;
    }

    public SplineBrushStyle style() {
        return style;
    }

    public void setStyle(SplineBrushStyle style) {
        this.style = Objects.requireNonNull(style, "style");
    }

    public int width() {
        return path.widthTiles();
    }

    public void setWidth(int width) {
        path.setWidthTiles(width);
    }

    public int overlayId() {
        return overlayId;
    }

    public void setOverlayId(int overlayId) {
        this.overlayId = Math.max(0, overlayId);
    }

    public int underlayId() {
        return underlayId;
    }

    public void setUnderlayId(int underlayId) {
        this.underlayId = Math.max(0, underlayId);
    }

    public boolean paintUnderlay() {
        return paintUnderlay;
    }

    public void setPaintUnderlay(boolean paintUnderlay) {
        this.paintUnderlay = paintUnderlay;
    }

    public int selectedPointIndex() {
        return selectedPointIndex;
    }

    public WorldTile hoveredTile() {
        return hoveredTile;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void activate(ToolContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void deactivate() {
        dragPointIndex = -1;
        hoveredTile = null;
    }

    @Override
    public void pointerMove(PointerEvent event) {
        if (context == null) {
            hoveredTile = null;
            return;
        }
        hoveredTile = context.worldTileAt(event.x(), event.y()).orElse(null);
    }

    @Override
    public void pointerDown(PointerEvent event) {
        if (context == null) return;

        Optional<WorldTile> hit = context.worldTileAt(event.x(), event.y());
        if (hit.isEmpty()) return;

        WorldTile tile = hit.get();
        hoveredTile = tile;
        int nearIdx = path.findPointNear(tile.x(), tile.y(), tile.plane(), 1);

        if (event.button() == PointerButton.SECONDARY || (event.button() == PointerButton.PRIMARY && event.alt())) {
            // Right-click or Alt-click on existing node deletes it
            if (nearIdx >= 0) {
                path.removePoint(nearIdx);
                selectedPointIndex = Math.min(selectedPointIndex, path.size() - 1);
                dragPointIndex = -1;
            }
            return;
        }

        if (event.button() == PointerButton.PRIMARY) {
            // If Shift is pressed, or if not clicking directly on an existing node, add a new node
            if (nearIdx >= 0 && !event.shift()) {
                // Select and begin dragging existing control point
                selectedPointIndex = nearIdx;
                dragPointIndex = nearIdx;
            } else {
                // Add new point at clicked location
                path.addPoint(tile.x(), tile.y(), tile.plane());
                selectedPointIndex = path.size() - 1;
                dragPointIndex = selectedPointIndex;
            }
        }
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        if (context == null || dragPointIndex < 0 || dragPointIndex >= path.size()) return;

        Optional<WorldTile> hit = context.worldTileAt(event.x(), event.y());
        if (hit.isEmpty()) return;

        WorldTile tile = hit.get();
        hoveredTile = tile;
        path.movePoint(dragPointIndex, tile.x(), tile.y());
    }

    @Override
    public void pointerUp(PointerEvent event) {
        dragPointIndex = -1;
    }

    private float sampleTileHeight(int plane, int x, int y) {
        if (context == null || context.session() == null) return 0.0f;
        Optional<LocalTile> local = context.local(new WorldTile(plane, x, y));
        if (local.isEmpty() || !context.session().world().contains(local.get())) return 0.0f;
        TileSnapshot s = context.session().world().tile(local.get().coordinate()).snapshot();
        return (s.southWestHeight() + s.southEastHeight() + s.northEastHeight() + s.northWestHeight()) / 4.0f;
    }

    @Override
    public void renderOverlay(OverlayDraw draw) {
        if (draw == null) return;

        // 1. Terrain hover reticle following pointer
        if (hoveredTile != null) {
            draw.tileOutline(hoveredTile, 0x38BDF8EE);
            draw.tileFilled(hoveredTile, 0x38BDF822);

            if (path.isEmpty()) {
                float hx = hoveredTile.x() * 128.0f + 64.0f;
                float hz = hoveredTile.y() * 128.0f + 64.0f;
                float hy = sampleTileHeight(hoveredTile.plane(), hoveredTile.x(), hoveredTile.y()) - 10.0f;
                draw.circle(hx, hy, hz, 36.0f, 0x34D399FF, 2.5f);
                draw.worldLabel("Click to place Start (P1)", hx, hy - 20.0f, hz, 0xFFFFFFFF, 0x059669EE);
            }
        }

        if (path.isEmpty()) return;

        List<SplinePath.Point> points = path.points();
        int pointCount = points.size();

        // 2. Draw placed control point nodes and labels conforming to real 3D terrain elevation
        for (int i = 0; i < pointCount; i++) {
            SplinePath.Point p = points.get(i);
            float worldX = p.x * 128.0f + 64.0f;
            float worldZ = p.y * 128.0f + 64.0f;
            float worldY = sampleTileHeight(p.plane, p.x, p.y) - 6.0f;
            boolean selected = (i == selectedPointIndex);

            int ringColor = selected ? 0xFBBF24FF : 0x38BDF8FF; // Amber if selected, cyan if normal
            draw.circle(worldX, worldY, worldZ, 40.0f, ringColor, selected ? 3.5f : 2.5f);
            draw.worldLabel("P" + (i + 1), worldX, worldY, worldZ, 0xFFFFFFFF, selected ? 0xD97706EE : 0x0284C7EE);
        }

        // 3. Rubber-band connector line from the last node to the cursor, plus preview node
        if (hoveredTile != null && pointCount >= 1 && dragPointIndex < 0) {
            SplinePath.Point lastP = points.get(pointCount - 1);
            if (lastP.plane == hoveredTile.plane() && (lastP.x != hoveredTile.x() || lastP.y != hoveredTile.y())) {
                float x1 = lastP.x * 128.0f + 64.0f;
                float z1 = lastP.y * 128.0f + 64.0f;
                float y1 = sampleTileHeight(lastP.plane, lastP.x, lastP.y) - 6.0f;

                float x2 = hoveredTile.x() * 128.0f + 64.0f;
                float z2 = hoveredTile.y() * 128.0f + 64.0f;
                float y2 = sampleTileHeight(hoveredTile.plane(), hoveredTile.x(), hoveredTile.y()) - 6.0f;

                draw.line(x1, y1, z1, x2, y2, z2, 0x38BDF8CC, 2.5f);
                draw.circle(x2, y2, z2, 28.0f, 0x38BDF8AA, 2.0f);
                draw.worldLabel("P" + (pointCount + 1), x2, y2, z2, 0xFFE2E8F0, 0x0F172ACC);
            }
        }

        int plane = points.get(0).plane;

        // If only 1 node placed so far, preview the 2-point ribbon to the hovered cursor
        if (pointCount == 1 && hoveredTile != null && dragPointIndex < 0
                && (points.get(0).x != hoveredTile.x() || points.get(0).y != hoveredTile.y())) {
            SplinePath previewPath = new SplinePath();
            previewPath.setWidthTiles(path.widthTiles());
            previewPath.addPoint(points.get(0).x, points.get(0).y, plane);
            previewPath.addPoint(hoveredTile.x(), hoveredTile.y(), plane);
            Set<Long> previewFootprint = previewPath.rasterize(0.4f);
            for (long packed : previewFootprint) {
                int tx = SplinePath.unpackX(packed);
                int ty = SplinePath.unpackY(packed);
                WorldTile tile = new WorldTile(plane, tx, ty);
                int mask = SplinePath.neighbourMask(previewFootprint, tx, ty);
                int shape = style.shape(mask);
                if (shape > 0) {
                    draw.tileFilled(tile, 0x34D39944);
                    draw.tileOutline(tile, 0x34D399AA);
                } else {
                    draw.tileFilled(tile, 0x38BDF844);
                    draw.tileOutline(tile, 0x38BDF888);
                }
            }
            return;
        }

        if (pointCount < 2) return;

        // 4. Draw Catmull-Rom interpolated curve line hugging terrain slope
        float[] curve = path.evaluate(0.35f);
        int curvePoints = curve.length / 2;
        for (int i = 0; i < curvePoints - 1; i++) {
            float x1 = curve[i * 2] * 128.0f + 64.0f;
            float z1 = curve[i * 2 + 1] * 128.0f + 64.0f;
            float y1 = sampleTileHeight(plane, Math.round(curve[i * 2]), Math.round(curve[i * 2 + 1])) - 4.0f;

            float x2 = curve[(i + 1) * 2] * 128.0f + 64.0f;
            float z2 = curve[(i + 1) * 2 + 1] * 128.0f + 64.0f;
            float y2 = sampleTileHeight(plane, Math.round(curve[(i + 1) * 2]), Math.round(curve[(i + 1) * 2 + 1])) - 4.0f;

            draw.line(x1, y1, z1, x2, y2, z2, 0xFBBF24FF, 3.5f);
        }

        // 5. Draw rasterized tile footprint ribbon preview with autotiled edge highlights
        Set<Long> footprint = path.rasterize(0.4f);
        for (long packed : footprint) {
            int tx = SplinePath.unpackX(packed);
            int ty = SplinePath.unpackY(packed);
            WorldTile tile = new WorldTile(plane, tx, ty);
            int mask = SplinePath.neighbourMask(footprint, tx, ty);
            int shape = style.shape(mask);
            if (shape > 0) {
                draw.tileFilled(tile, 0x34D39955);
                draw.tileOutline(tile, 0x34D399CC);
            } else {
                draw.tileFilled(tile, 0x38BDF855);
                draw.tileOutline(tile, 0x38BDF8AA);
            }
        }
    }

    /**
     * Builds the spline path, compiling autotiled materials and heights into one undoable command.
     * @return true if changes were committed to the session
     */
    public boolean buildPath() {
        if (context == null || path.size() < 2) {
            return false;
        }

        Set<Long> footprint = path.rasterize(0.4f);
        if (footprint.isEmpty()) {
            return false;
        }

        int plane = path.points().get(0).plane;
        WorldDocument world = context.session().world();
        WorldDocument predicted = world.copy();
        List<EditorCommand> commands = new ArrayList<>();

        // Handle Height Ramping along the curve if RAMP style is active
        if (style == SplineBrushStyle.RAMP && path.size() >= 2) {
            SplinePath.Point startP = path.points().get(0);
            SplinePath.Point endP = path.points().get(path.size() - 1);

            Optional<LocalTile> startLocal = context.local(new WorldTile(plane, startP.x, startP.y));
            Optional<LocalTile> endLocal = context.local(new WorldTile(plane, endP.x, endP.y));

            int startHeight = startLocal.map(t -> {
                TileSnapshot s = world.tile(t.coordinate()).snapshot();
                return (s.southWestHeight() + s.southEastHeight() + s.northEastHeight() + s.northWestHeight()) / 4;
            }).orElse(0);

            int endHeight = endLocal.map(t -> {
                TileSnapshot s = world.tile(t.coordinate()).snapshot();
                return (s.southWestHeight() + s.southEastHeight() + s.northEastHeight() + s.northWestHeight()) / 4;
            }).orElse(startHeight + 128);

            TerrainVertexLattice lattice = new TerrainVertexLattice(predicted);
            Set<TileCoordinate> heightChanged = new LinkedHashSet<>();

            // Measure total arc length for distance ratio
            float totalDist = (float) Math.hypot(endP.x - startP.x, endP.y - startP.y);
            if (totalDist < 1.0f) totalDist = 1.0f;

            for (long packed : footprint) {
                int wx = SplinePath.unpackX(packed);
                int wy = SplinePath.unpackY(packed);
                Optional<LocalTile> localOpt = context.local(new WorldTile(plane, wx, wy));
                if (localOpt.isEmpty()) continue;

                LocalTile local = localOpt.get();
                float distFromStart = (float) Math.hypot(wx - startP.x, wy - startP.y);
                float progress = Math.max(0.0f, Math.min(1.0f, distFromStart / totalDist));
                int targetH = Math.round(startHeight + (endHeight - startHeight) * progress);

                heightChanged.addAll(lattice.setHeight(local.plane(), local.x(), local.y(), targetH));
                heightChanged.addAll(lattice.setHeight(local.plane(), local.x() + 1, local.y(), targetH));
                heightChanged.addAll(lattice.setHeight(local.plane(), local.x() + 1, local.y() + 1, targetH));
                heightChanged.addAll(lattice.setHeight(local.plane(), local.x(), local.y() + 1, targetH));
            }

            for (TileCoordinate coord : heightChanged) {
                TileSnapshot before = world.tile(coord).snapshot();
                TileSnapshot after = predicted.tile(coord).snapshot();
                if (!before.equals(after)) {
                    commands.add(new SetTerrainHeightCommand(
                            coord, before, after, before.heightSource(), after.heightSource(),
                            "Ramp path height at " + coord));
                }
            }
        }

        // Apply autotiled material shapes and rotations
        for (long packed : footprint) {
            int wx = SplinePath.unpackX(packed);
            int wy = SplinePath.unpackY(packed);

            Optional<LocalTile> localOpt = context.local(new WorldTile(plane, wx, wy));
            if (localOpt.isEmpty()) continue;

            LocalTile local = localOpt.get();
            TileCoordinate coord = local.coordinate();

            int mask = SplinePath.neighbourMask(footprint, wx, wy);
            int targetShape = style.shape(mask);
            int targetRotation = style.rotation(mask);

            TileSnapshot base = predicted.tile(coord).snapshot();
            int underlay = paintUnderlay ? underlayId : base.underlayId();
            int overlay = (overlayId > 0) ? overlayId : base.overlayId();

            TileSnapshot materialAfter = new TileSnapshot(
                    base.southWestHeight(), base.southEastHeight(),
                    base.northEastHeight(), base.northWestHeight(),
                    underlay, overlay, targetShape, targetRotation,
                    base.flags(), base.objects(), base.heightSource());

            if (!sameMaterial(base, materialAfter)) {
                commands.add(new SetTileMaterialCommand(
                        coord, base, materialAfter, "Paint path material at " + coord));
                predicted.tile(coord).restore(materialAfter, base.heightSource());
            }
        }

        if (commands.isEmpty()) {
            return false;
        }

        CompositeEditCommand composite = new CompositeEditCommand(
                "Build Spline Path (" + style.displayName() + ", " + commands.size() + " tiles)",
                commands);
        context.session().execute(composite);
        clear();
        return true;
    }

    public void clear() {
        path.clear();
        dragPointIndex = -1;
        selectedPointIndex = -1;
    }

    private static boolean sameMaterial(TileSnapshot first, TileSnapshot second) {
        return first.underlayId() == second.underlayId()
                && first.overlayId() == second.overlayId()
                && first.overlayShape() == second.overlayShape()
                && first.overlayRotation() == second.overlayRotation();
    }
}
