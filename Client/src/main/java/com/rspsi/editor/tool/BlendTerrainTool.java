package com.rspsi.editor.tool;

import com.rspsi.editor.ChangeHeightCommand;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Neighbor-aware terrain blend brush that preserves sharp cliffs by excluding
 * samples whose delta exceeds a configurable edge threshold.
 */
public final class BlendTerrainTool implements EditorTool {
    private int strengthPercent;
    private int edgeThreshold;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();

    public BlendTerrainTool(int strengthPercent, int edgeThreshold) {
        setStrengthPercent(strengthPercent);
        setEdgeThreshold(edgeThreshold);
    }

    public int strengthPercent() { return strengthPercent; }
    public void setStrengthPercent(int strengthPercent) {
        if (strengthPercent < 0 || strengthPercent > 100) {
            throw new IllegalArgumentException("Blend strength must be between 0 and 100 percent");
        }
        this.strengthPercent = strengthPercent;
    }

    public int edgeThreshold() { return edgeThreshold; }
    public void setEdgeThreshold(int edgeThreshold) {
        if (edgeThreshold < 0) throw new IllegalArgumentException("Edge threshold must be non-negative");
        this.edgeThreshold = edgeThreshold;
    }

    @Override public String id() { return "blend-terrain"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) {
            clear();
            addTile(event);
        }
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) addTile(event);
    }

    @Override public void pointerUp(PointerEvent event) {
        if (context != null && !stroke.isEmpty()) {
            context.session().execute(new CompositeEditCommand("Blend terrain", stroke));
        }
        clear();
    }

    @Override public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("strengthPercent", "Strength",
                        PropertyDescriptor.ValueType.INTEGER, 0, 100),
                new PropertyDescriptor("edgeThreshold", "Cliff threshold",
                        PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE));
    }

    @Override public void renderOverlay(OverlayDraw draw) {
        visited.forEach(draw::tileOutline);
    }

    private void addTile(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(absolute -> {
            if (!visited.add(absolute)) return;
            var world = context.session().world();
            int localX = Math.floorMod(absolute.x(), Math.max(1, world.width()));
            int localY = Math.floorMod(absolute.y(), Math.max(1, world.length()));
            TileCoordinate coordinate = new TileCoordinate(absolute.plane(), localX, localY);
            TileSnapshot before = world.tile(coordinate).snapshot();

            int sw = blendCorner(coordinate, Corner.SOUTH_WEST, before.southWestHeight());
            int se = blendCorner(coordinate, Corner.SOUTH_EAST, before.southEastHeight());
            int ne = blendCorner(coordinate, Corner.NORTH_EAST, before.northEastHeight());
            int nw = blendCorner(coordinate, Corner.NORTH_WEST, before.northWestHeight());

            TileSnapshot after = new TileSnapshot(sw, se, ne, nw,
                    before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                    before.flags(), before.objects());

            if (!before.equals(after)) {
                stroke.add(new ChangeHeightCommand(coordinate, before, after, "Blend terrain at " + coordinate));
            }
        });
    }

    private int blendCorner(TileCoordinate coordinate, Corner corner, int current) {
        var world = context.session().world();
        int sum = current;
        int count = 1;
        int x = coordinate.x();
        int y = coordinate.y();

        int[][] neighbours = switch (corner) {
            case SOUTH_WEST -> new int[][]{{x - 1, y, 1}, {x, y - 1, 3}, {x - 1, y - 1, 2}};
            case SOUTH_EAST -> new int[][]{{x + 1, y, 0}, {x, y - 1, 2}, {x + 1, y - 1, 3}};
            case NORTH_EAST -> new int[][]{{x + 1, y, 3}, {x, y + 1, 1}, {x + 1, y + 1, 0}};
            case NORTH_WEST -> new int[][]{{x - 1, y, 2}, {x, y + 1, 0}, {x - 1, y + 1, 1}};
        };

        for (int[] neighbour : neighbours) {
            if (neighbour[0] < 0 || neighbour[0] >= world.width()
                    || neighbour[1] < 0 || neighbour[1] >= world.length()) continue;
            TileSnapshot snapshot = world.tile(coordinate.plane(), neighbour[0], neighbour[1]).snapshot();
            int sample = cornerHeight(snapshot, neighbour[2]);
            if (Math.abs(sample - current) > edgeThreshold) continue;
            sum += sample;
            count++;
        }

        int average = sum / count;
        return current + (average - current) * strengthPercent / 100;
    }

    private static int cornerHeight(TileSnapshot snapshot, int index) {
        return switch (index) {
            case 0 -> snapshot.southWestHeight();
            case 1 -> snapshot.southEastHeight();
            case 2 -> snapshot.northEastHeight();
            default -> snapshot.northWestHeight();
        };
    }

    private enum Corner { SOUTH_WEST, SOUTH_EAST, NORTH_EAST, NORTH_WEST }

    private void clear() {
        stroke.clear();
        visited.clear();
    }
}
