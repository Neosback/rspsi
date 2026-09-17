package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.ChangeHeightCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.List;

/** Creates a linear height ramp over a dragged rectangular area. */
public final class RampTerrainTool implements EditorTool {
    public enum Axis { X, Y }

    private int startHeight;
    private int endHeight;
    private Axis axis = Axis.X;
    private ToolContext context;
    private TileCoordinate start;
    private TileCoordinate current;
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
        context.viewport().tileAt(event.x(), event.y()).ifPresent(tile -> {
            start = tile;
            current = tile;
        });
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && start != null && event.button() == PointerButton.PRIMARY) {
            context.viewport().tileAt(event.x(), event.y())
                    .filter(tile -> tile.plane() == start.plane())
                    .ifPresent(tile -> current = tile);
        }
    }

    @Override public void pointerUp(PointerEvent event) {
        if (context != null && start != null && current != null
                && event.button() == PointerButton.PRIMARY) {
            buildStroke(bounds(start, current));
            if (!stroke.isEmpty()) context.session().execute(
                    new CompositeEditCommand("Ramp terrain", stroke));
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
                draw.tileOutline(new TileCoordinate(start.plane(), x, y));
            }
        }
    }

    private void buildStroke(TileBounds bounds) {
        int startCoordinate = axis == Axis.X ? bounds.minX() : bounds.minY();
        int endCoordinate = axis == Axis.X ? bounds.maxX() + 1 : bounds.maxY() + 1;
        int span = endCoordinate - startCoordinate;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                TileCoordinate coordinate = new TileCoordinate(start.plane(), x, y);
                TileSnapshot before = context.session().world().tile(coordinate).snapshot();
                int sw = height(axis == Axis.X ? x : y, startCoordinate, span);
                int se = height(axis == Axis.X ? x + 1 : y, startCoordinate, span);
                int ne = height(axis == Axis.X ? x + 1 : y + 1, startCoordinate, span);
                int nw = height(axis == Axis.X ? x : y + 1, startCoordinate, span);
                TileSnapshot after = new TileSnapshot(sw, se, ne, nw, before.underlayId(), before.overlayId(),
                        before.overlayShape(), before.overlayRotation(), before.flags(), before.objects());
                if (!before.equals(after)) stroke.add(new ChangeHeightCommand(coordinate, before, after,
                        "Ramp terrain at " + coordinate));
            }
        }
    }

    private int height(int coordinate, int startCoordinate, int span) {
        double progress = span == 0 ? 0.0 : (double) (coordinate - startCoordinate) / span;
        progress = Math.max(0.0, Math.min(1.0, progress));
        return (int) Math.round(startHeight + (endHeight - (double) startHeight) * progress);
    }

    private static TileBounds bounds(TileCoordinate first, TileCoordinate second) {
        return new TileBounds(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()));
    }

    private void clear() { start = null; current = null; stroke.clear(); }
}
