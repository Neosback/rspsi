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

/** Quantizes brushed terrain heights into configurable stepped terraces. */
public final class TerraceTerrainTool implements EditorTool {
    private int step;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();

    public TerraceTerrainTool(int step) {
        setStep(step);
    }

    public int step() { return step; }
    public void setStep(int step) {
        if (step < 2 || step > 96) {
            throw new IllegalArgumentException("Terrace step must be between 2 and 96");
        }
        this.step = step;
    }

    @Override public String id() { return "terrace-terrain"; }
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
            context.session().execute(new CompositeEditCommand("Terrace terrain", stroke));
        }
        clear();
    }

    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("step", "Terrace step",
                PropertyDescriptor.ValueType.INTEGER, 2, 96));
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

            TileSnapshot after = new TileSnapshot(
                    quantize(before.southWestHeight()),
                    quantize(before.southEastHeight()),
                    quantize(before.northEastHeight()),
                    quantize(before.northWestHeight()),
                    before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                    before.flags(), before.objects());

            if (!before.equals(after)) {
                stroke.add(new ChangeHeightCommand(coordinate, before, after, "Terrace terrain at " + coordinate));
            }
        });
    }

    private int quantize(int value) {
        return Math.round((float) value / step) * step;
    }

    private void clear() {
        stroke.clear();
        visited.clear();
    }
}
