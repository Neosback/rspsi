package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.ChangeHeightCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Vertex-aware raise/lower brush with optional radius and falloff. */
public final class ChangeHeightTool implements EditorTool {
    public enum Falloff { NONE, LINEAR, SMOOTH }

    private int delta;
    private int radius;
    private Falloff falloff = Falloff.NONE;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();
    private final Map<VertexKey, Integer> vertexDeltas = new LinkedHashMap<>();

    public ChangeHeightTool(int delta) { setDelta(delta); }
    public int delta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
    public int radius() { return radius; }
    public void setRadius(int radius) {
        if (radius < 0 || radius > 64) throw new IllegalArgumentException("Height brush radius must be 0 through 64");
        this.radius = radius;
    }
    public Falloff falloff() { return falloff; }
    public void setFalloff(Falloff falloff) { this.falloff = java.util.Objects.requireNonNull(falloff, "falloff"); }
    @Override public String id() { return "change-height"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) { clear(); addTile(event); }
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) addTile(event);
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && !vertexDeltas.isEmpty()) {
            buildStroke();
            if (!stroke.isEmpty()) context.session().execute(
                    new CompositeEditCommand(delta < 0 ? "Lower terrain" : "Raise terrain", stroke));
        }
        clear();
    }
    @Override public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("delta", "Height delta", PropertyDescriptor.ValueType.INTEGER,
                        Integer.MIN_VALUE, Integer.MAX_VALUE),
                new PropertyDescriptor("radius", "Radius", PropertyDescriptor.ValueType.INTEGER, 0, 64),
                new PropertyDescriptor("falloff", "Falloff", PropertyDescriptor.ValueType.ENUM, 0, 2));
    }
    @Override public void renderOverlay(OverlayDraw draw) { visited.forEach(draw::tileOutline); }
    private void addTile(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(absolute -> {
            // "visited" dedups by the absolute pick and drives the 3D overlay (which must draw
            // at the real world position); the vertex/height math below needs the region-local
            // equivalent since that's what WorldDocument and vertexDeltas are indexed by.
            if (!visited.add(absolute) || delta == 0) return;
            int localX = Math.floorMod(absolute.x(), Math.max(1, context.session().world().width()));
            int localY = Math.floorMod(absolute.y(), Math.max(1, context.session().world().length()));
            int minX = Math.max(0, localX - radius);
            int maxX = Math.min(context.session().world().width(), localX + 1 + radius);
            int minY = Math.max(0, localY - radius);
            int maxY = Math.min(context.session().world().length(), localY + 1 + radius);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    double distance = vertexDistance(x, y, localX, localY);
                    if (distance > radius && radius > 0) continue;
                    int amount = (int) Math.round(delta * weight(distance));
                    if (amount == 0) continue;
                    addVertexDelta(absolute.plane(), x, y, amount);
                }
            }
        });
    }

    private void buildStroke() {
        Set<TileCoordinate> affected = new LinkedHashSet<>();
        for (VertexKey vertex : vertexDeltas.keySet()) {
            addAffected(affected, vertex.plane(), vertex.x(), vertex.y());
            addAffected(affected, vertex.plane(), vertex.x() - 1, vertex.y());
            addAffected(affected, vertex.plane(), vertex.x(), vertex.y() - 1);
            addAffected(affected, vertex.plane(), vertex.x() - 1, vertex.y() - 1);
        }
        for (TileCoordinate coordinate : affected) {
            TileSnapshot before = context.session().world().tile(coordinate).snapshot();
            int sw = before.southWestHeight() + vertexDeltas.getOrDefault(
                    new VertexKey(coordinate.plane(), coordinate.x(), coordinate.y()), 0);
            int se = before.southEastHeight() + vertexDeltas.getOrDefault(
                    new VertexKey(coordinate.plane(), coordinate.x() + 1, coordinate.y()), 0);
            int ne = before.northEastHeight() + vertexDeltas.getOrDefault(
                    new VertexKey(coordinate.plane(), coordinate.x() + 1, coordinate.y() + 1), 0);
            int nw = before.northWestHeight() + vertexDeltas.getOrDefault(
                    new VertexKey(coordinate.plane(), coordinate.x(), coordinate.y() + 1), 0);
            TileSnapshot after = new TileSnapshot(sw, se, ne, nw, before.underlayId(), before.overlayId(),
                    before.overlayShape(), before.overlayRotation(), before.flags(), before.objects());
            if (!before.equals(after)) stroke.add(new ChangeHeightCommand(coordinate, before, after,
                    "Change height at " + coordinate));
        }
    }

    private void addAffected(Set<TileCoordinate> affected, int plane, int x, int y) {
        if (x >= 0 && x < context.session().world().width() && y >= 0
                && y < context.session().world().length()) {
            affected.add(new TileCoordinate(plane, x, y));
        }
    }

    private void addVertexDelta(int plane, int x, int y, int amount) {
        VertexKey key = new VertexKey(plane, x, y);
        vertexDeltas.merge(key, amount, Math::addExact);
    }

    private double weight(double distance) {
        if (radius == 0 || falloff == Falloff.NONE) return 1.0;
        double linear = Math.max(0.0, 1.0 - distance / (radius + 1.0));
        return falloff == Falloff.LINEAR ? linear : linear * linear * (3.0 - 2.0 * linear);
    }

    /** Chebyshev distance from a vertex to the brushed tile's 2x2 corner box. */
    private static double vertexDistance(int vertexX, int vertexY, int tileX, int tileY) {
        int dx = vertexX < tileX ? tileX - vertexX : Math.max(0, vertexX - (tileX + 1));
        int dy = vertexY < tileY ? tileY - vertexY : Math.max(0, vertexY - (tileY + 1));
        return Math.max(dx, dy);
    }

    private void clear() { stroke.clear(); visited.clear(); vertexDeltas.clear(); }

    private record VertexKey(int plane, int x, int y) { }
}
