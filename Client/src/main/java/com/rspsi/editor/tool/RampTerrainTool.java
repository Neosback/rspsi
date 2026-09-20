package com.rspsi.editor.tool;

import com.rspsi.editor.ChangeHeightCommand;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.List;

/** Creates a linear height ramp over a dragged rectangular world-space area. */
public final class RampTerrainTool implements EditorTool {
    public enum Axis { X, Y }

    private int startHeight;
    private int endHeight;
    private Axis axis = Axis.X;
    private ToolContext context;
    private WorldTile start;
    private WorldTile current;
    private final List<EditorCommand> stroke = new ArrayList<>();

    public RampTerrainTool(int startHeight, int endHeight) {
        this.startHeight = startHeight;
        this.endHeight = endHeight;
    }

    public int startHeight() { return startHeight; }
    public void setStartHeight(int startHeight) { this.startHeight = startHeight; }
    public int endHeight() { return endHeight; }
    public void setEndHeight(int endHeight) { this.endHeight = endHeight; }
    public Axis axis() { return axis; }
    public void setAxis(Axis axis) { this.axis = java.util.Objects.requireNonNull(axis, "axis"); }

    @Override public String id() { return "ramp-terrain"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        context.worldTileAt(event.x(), event.y())
                .filter(tile -> context.local(tile).isPresent())
                .ifPresent(tile -> { start = tile; current = tile; });
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && start != null && event.button() == PointerButton.PRIMARY) {
            context.worldTileAt(event.x(), event.y())
                    .filter(tile -> tile.plane() == start.plane())
                    .filter(tile -> context.local(tile).isPresent())
                    .ifPresent(tile -> current = tile);
        }
    }

    @Override public void pointerUp(PointerEvent event) {
        if (context != null && start != null && current != null
                && event.button() == PointerButton.PRIMARY) {
            buildStroke(bounds(start, current));
            if (!stroke.isEmpty() && context.session().canEdit()) {
                context.session().execute(new CompositeEditCommand("Ramp terrain", stroke));
            }
        }
        clear();
    }

    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("startHeight", "Start height", PropertyDescriptor.ValueType.INTEGER,
                    Integer.MIN_VALUE, Integer.MAX_VALUE),
            new PropertyDescriptor("endHeight", "End height", PropertyDescriptor.ValueType.INTEGER,
                    Integer.MIN_VALUE, Integer.MAX_VALUE),
            new PropertyDescriptor("axis", "Axis", PropertyDescriptor.ValueType.ENUM, 0, 1)); }

    @Override public void renderOverlay(OverlayDraw draw) {
        if (start == null || current == null) return;
        TileBounds bounds = bounds(start, current);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                draw.tileOutline(new WorldTile(start.plane(), x, y));
            }
        }
    }

    private void buildStroke(TileBounds worldBounds) {
        int startCoordinate = axis == Axis.X ? worldBounds.minX() : worldBounds.minY();
        int endCoordinate = axis == Axis.X ? worldBounds.maxX() + 1 : worldBounds.maxY() + 1;
        int span = endCoordinate - startCoordinate;
        for (int worldX = worldBounds.minX(); worldX <= worldBounds.maxX(); worldX++) {
            for (int worldY = worldBounds.minY(); worldY <= worldBounds.maxY(); worldY++) {
                WorldTile absolute = new WorldTile(start.plane(), worldX, worldY);
                LocalTile local = context.local(absolute).orElse(null);
                if (local == null) continue;
                TileSnapshot before = context.session().world().tile(local).snapshot();
                int sw = height(axis == Axis.X ? worldX : worldY, startCoordinate, span);
                int se = height(axis == Axis.X ? worldX + 1 : worldY, startCoordinate, span);
                int ne = height(axis == Axis.X ? worldX + 1 : worldY + 1, startCoordinate, span);
                int nw = height(axis == Axis.X ? worldX : worldY + 1, startCoordinate, span);
                TileSnapshot after = new TileSnapshot(sw, se, ne, nw,
                        before.underlayId(), before.overlayId(), before.overlayShape(),
                        before.overlayRotation(), before.flags(), before.objects(),
                        before.heightSource());
                if (!before.equals(after)) {
                    stroke.add(new ChangeHeightCommand(local.coordinate(), before, after,
                            "Ramp terrain at " + absolute));
                }
            }
        }
    }

    private int height(int coordinate, int startCoordinate, int span) {
        double progress = span == 0 ? 0.0 : (double) (coordinate - startCoordinate) / span;
        progress = Math.max(0.0, Math.min(1.0, progress));
        return (int) Math.round(startHeight + (endHeight - (double) startHeight) * progress);
    }

    private static TileBounds bounds(WorldTile first, WorldTile second) {
        return new TileBounds(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()));
    }

    private void clear() { start = null; current = null; stroke.clear(); }
}
