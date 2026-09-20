package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.SetTerrainHeightCommand;
import com.rspsi.editor.brush.BrushAwareTool;
import com.rspsi.editor.brush.BrushEngine;
import com.rspsi.editor.brush.BrushMask;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.brush.builtin.SquareBrush;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.terrain.TerrainVertexLattice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared-lattice raise/lower brush. BrushEngine supplies the exact footprint
 * used by both the visible stroke preview and height mutation.
 */
public final class ChangeHeightTool implements EditorTool, BrushAwareTool {
    public enum Falloff { NONE, LINEAR, SMOOTH }

    private int delta;
    private int radius;
    private Falloff falloff = Falloff.NONE;
    private EditorBrush brush = new SquareBrush();
    private final BrushEngine brushEngine = new BrushEngine();

    private ToolContext context;
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();
    private final Map<VertexKey, Integer> vertexDeltas = new LinkedHashMap<>();
    private BrushMask lastMask;
    private TileCoordinate lastCenter;

    public ChangeHeightTool(int delta) { setDelta(delta); }

    public int delta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }

    public int radius() { return radius; }
    public void setRadius(int radius) { setBrushRadius(radius); }

    public Falloff falloff() { return falloff; }
    public void setFalloff(Falloff falloff) {
        this.falloff = java.util.Objects.requireNonNull(falloff, "falloff");
    }

    @Override public EditorBrush brush() { return brush; }

    @Override
    public void setBrush(EditorBrush brush) {
        this.brush = java.util.Objects.requireNonNull(brush, "brush");
        brushEngine.register(brush);
    }

    @Override public int brushRadius() { return radius; }

    @Override
    public void setBrushRadius(int radius) {
        if (radius < 0 || radius > 64) {
            throw new IllegalArgumentException("Height brush radius must be 0 through 64");
        }
        this.radius = radius;
    }

    @Override public String id() { return "change-height"; }

    @Override
    public void activate(ToolContext context) {
        this.context = context;
        clear();
    }

    @Override
    public void deactivate() {
        clear();
        context = null;
    }

    @Override
    public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) {
            clear();
            addStamp(event);
        }
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) addStamp(event);
    }

    @Override
    public void pointerUp(PointerEvent event) {
        if (context != null && !vertexDeltas.isEmpty() && context.session().canEdit()) {
            List<EditorCommand> commands = buildStroke();
            if (!commands.isEmpty()) {
                context.session().execute(new CompositeEditCommand(
                        delta < 0 ? "Lower terrain" : "Raise terrain", commands));
            }
        }
        clear();
    }

    @Override
    public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("delta", "Height delta", PropertyDescriptor.ValueType.INTEGER,
                        Integer.MIN_VALUE, Integer.MAX_VALUE),
                new PropertyDescriptor("radius", "Radius", PropertyDescriptor.ValueType.INTEGER, 0, 64),
                new PropertyDescriptor("falloff", "Falloff", PropertyDescriptor.ValueType.ENUM, 0, 2));
    }

    @Override
    public void renderOverlay(OverlayDraw draw) {
        if (lastMask != null) {
            lastMask.samples().forEach(sample -> draw.tileOutline(sample.absolute()));
        } else {
            visited.forEach(draw::tileOutline);
        }
    }

    private void addStamp(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(center -> {
            if (delta == 0) return;
            List<TileCoordinate> centers = lastCenter == null
                    ? List.of(center)
                    : brushEngine.interpolateStroke(lastCenter, center, 1.0);
            int effectiveDelta = event.alt() ? -delta : delta;

            for (TileCoordinate stampCenter : centers) {
                if (!visited.add(stampCenter)) continue;
                lastMask = brushEngine.sample(brush, radius, stampCenter, context.session().world());
                Map<VertexKey, Integer> stamp = new LinkedHashMap<>();

                for (var sample : lastMask.samples()) {
                    int dx = sample.absolute().x() - stampCenter.x();
                    int dy = sample.absolute().y() - stampCenter.y();
                    // The authored tile radius describes included tiles. Falloff reaches
                    // zero one grid step beyond that footprint, so the outer ring still
                    // receives a partial influence instead of collapsing to zero.
                    double distance = radius == 0 ? 0.0
                            : Math.max(Math.abs(dx), Math.abs(dy)) / (double) (radius + 1);
                    double weight = sample.weight() * falloffWeight(distance);
                    int amount = (int) Math.round(effectiveDelta * weight);
                    if (amount == 0) continue;

                    TileCoordinate local = sample.local();
                    mergeStrongest(stamp, new VertexKey(local.plane(), local.x(), local.y()), amount);
                    mergeStrongest(stamp, new VertexKey(local.plane(), local.x() + 1, local.y()), amount);
                    mergeStrongest(stamp, new VertexKey(local.plane(), local.x() + 1, local.y() + 1), amount);
                    mergeStrongest(stamp, new VertexKey(local.plane(), local.x(), local.y() + 1), amount);
                }

                stamp.forEach((key, amount) -> vertexDeltas.merge(key, amount, Math::addExact));
            }
            lastCenter = center;
        });
    }

    private List<EditorCommand> buildStroke() {
        var original = context.session().world();
        var predicted = original.copy();
        TerrainVertexLattice source = new TerrainVertexLattice(original);
        TerrainVertexLattice target = new TerrainVertexLattice(predicted);
        Set<TileCoordinate> affected = new LinkedHashSet<>();

        for (var entry : vertexDeltas.entrySet()) {
            VertexKey vertex = entry.getKey();
            int after = source.height(vertex.plane(), vertex.x(), vertex.y()) + entry.getValue();
            affected.addAll(target.setHeight(vertex.plane(), vertex.x(), vertex.y(), after));
        }

        List<EditorCommand> commands = new ArrayList<>();
        for (TileCoordinate coordinate : affected) {
            TileSnapshot before = original.tile(coordinate).snapshot();
            TileSnapshot after = predicted.tile(coordinate).snapshot();
            if (!before.equals(after)) {
                commands.add(new SetTerrainHeightCommand(
                        coordinate, before, after, before.heightSource(), after.heightSource(),
                        "Change height at " + coordinate));
            }
        }
        return List.copyOf(commands);
    }

    private double falloffWeight(double normalizedDistance) {
        return switch (falloff) {
            case NONE -> 1.0;
            case LINEAR -> BrushEngine.applyFalloff(BrushEngine.Falloff.LINEAR, normalizedDistance);
            case SMOOTH -> BrushEngine.applyFalloff(BrushEngine.Falloff.SMOOTH, normalizedDistance);
        };
    }

    private static void mergeStrongest(Map<VertexKey, Integer> values, VertexKey key, int amount) {
        values.merge(key, amount, (first, second) ->
                Math.abs(second) > Math.abs(first) ? second : first);
    }

    private void clear() {
        visited.clear();
        vertexDeltas.clear();
        lastMask = null;
        lastCenter = null;
    }

    private record VertexKey(int plane, int x, int y) { }
}
