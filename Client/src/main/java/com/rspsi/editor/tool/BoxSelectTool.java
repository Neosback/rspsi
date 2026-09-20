package com.rspsi.editor.tool;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.OverlayDraw;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selects a rectangular tile area or the objects owned by that area. */
public final class BoxSelectTool implements EditorTool {
    public enum Target { TILES, OBJECTS }
    public enum Mode { SINGLE, MULTI }

    private Target target = Target.TILES;
    private Mode mode = Mode.MULTI;
    private ToolContext context;
    private TileCoordinate start;
    private TileCoordinate current;

    public Target target() { return target; }
    public void setTarget(Target target) { this.target = java.util.Objects.requireNonNull(target, "target"); }
    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = java.util.Objects.requireNonNull(mode, "mode"); }

    @Override public String id() { return "box-select"; }
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
        if (mode == Mode.SINGLE) return;
        if (context != null && start != null && event.button() == PointerButton.PRIMARY) {
            context.viewport().tileAt(event.x(), event.y())
                    .filter(tile -> tile.plane() == start.plane())
                    .ifPresent(tile -> current = tile);
        }
    }

    @Override public void pointerUp(PointerEvent event) {
        if (context == null || start == null || current == null
                || event.button() != PointerButton.PRIMARY) {
            clear();
            return;
        }
        TileBounds bounds = mode == Mode.SINGLE
                ? new TileBounds(start.x(), start.y(), start.x(), start.y())
                : bounds(start, current);
        if (target == Target.TILES) {
            context.session().selection().selectArea(start.plane(), bounds);
        } else {
            var world = context.session().world();
            Set<WorldObject> objects = new LinkedHashSet<>();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    // bounds are absolute world tile coordinates; WorldDocument is indexed
                    // region-locally, so wrap before touching it (avoids IndexOutOfBoundsException
                    // the moment a marquee is drawn on a real map).
                    int localX = Math.floorMod(x, Math.max(1, world.width()));
                    int localY = Math.floorMod(y, Math.max(1, world.length()));
                    world.tile(start.plane(), localX, localY).snapshot()
                            .objects().forEach(objects::add);
                }
            }
            context.session().selection().selectObjects(objects);
        }
        // Deliberately not clearing start/current here: renderOverlay keeps showing the
        // selection outline until the next pointerDown (which clears it first) or an explicit
        // selection clear, instead of vanishing the instant the mouse is released.
    }

    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("target", "Select", PropertyDescriptor.ValueType.ENUM, 0, 1));
    }

    @Override public void renderOverlay(OverlayDraw draw) {
        if (start == null) return;
        if (mode == Mode.SINGLE || current == null) {
            draw.tileOutline(start);
            return;
        }
        // Outline only the marquee's border tiles, not every tile inside it - a large drag
        // (e.g. 80x80) would otherwise submit thousands of outline draws per frame.
        TileBounds bounds = bounds(start, current);
        int plane = start.plane();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            draw.tileOutline(new TileCoordinate(plane, x, bounds.minY()));
            if (bounds.maxY() != bounds.minY()) {
                draw.tileOutline(new TileCoordinate(plane, x, bounds.maxY()));
            }
        }
        for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
            draw.tileOutline(new TileCoordinate(plane, bounds.minX(), y));
            if (bounds.maxX() != bounds.minX()) {
                draw.tileOutline(new TileCoordinate(plane, bounds.maxX(), y));
            }
        }
    }

    private static TileBounds bounds(TileCoordinate first, TileCoordinate second) {
        return new TileBounds(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()));
    }

    private void clear() { start = null; current = null; }
}
