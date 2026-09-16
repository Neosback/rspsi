package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A small, deterministic neighbour-aware terrain smoothing brush. */
public final class SmoothTerrainTool implements EditorTool {
    private int strengthPercent;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();

    public SmoothTerrainTool(int strengthPercent) { setStrengthPercent(strengthPercent); }
    public int strengthPercent() { return strengthPercent; }
    public void setStrengthPercent(int strengthPercent) {
        if (strengthPercent < 0 || strengthPercent > 100) {
            throw new IllegalArgumentException("Smooth strength must be between 0 and 100 percent");
        }
        this.strengthPercent = strengthPercent;
    }
    @Override public String id() { return "smooth-terrain"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) { clear(); addTile(event); }
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) addTile(event);
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && !stroke.isEmpty()) context.session().execute(
                new CompositeEditCommand("Smooth terrain", stroke));
        clear();
    }
    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("strengthPercent", "Strength",
                PropertyDescriptor.ValueType.INTEGER, 0, 100));
    }
    @Override public void renderOverlay(OverlayDraw draw) { visited.forEach(draw::tileOutline); }

    private void addTile(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            if (!visited.add(coordinate)) return;
            TileSnapshot before = context.session().world().tile(coordinate).snapshot();
            int[] smoothed = {
                    smoothCorner(coordinate, Corner.SOUTH_WEST, before.southWestHeight()),
                    smoothCorner(coordinate, Corner.SOUTH_EAST, before.southEastHeight()),
                    smoothCorner(coordinate, Corner.NORTH_EAST, before.northEastHeight()),
                    smoothCorner(coordinate, Corner.NORTH_WEST, before.northWestHeight())};
            TileSnapshot after = new TileSnapshot(smoothed[0], smoothed[1], smoothed[2], smoothed[3],
                    before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                    before.flags(), before.objects());
            if (!before.equals(after)) stroke.add(new SetTileCommand(coordinate, before, after,
                    "Smooth terrain at " + coordinate));
        });
    }

    private int smoothCorner(TileCoordinate coordinate, Corner corner, int current) {
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
            sum += cornerHeight(snapshot, neighbour[2]);
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
    private void clear() { stroke.clear(); visited.clear(); }
}
