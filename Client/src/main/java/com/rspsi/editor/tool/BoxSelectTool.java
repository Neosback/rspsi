package com.rspsi.editor.tool;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
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
    private WorldTile start;
    private WorldTile current;
    private float downX;
    private float downY;

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
        downX = event.x();
        downY = event.y();
        context.worldTileAt(event.x(), event.y()).ifPresent(tile -> {
            start = tile;
            current = tile;
        });
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (mode == Mode.SINGLE) return;
        if (context != null && start != null && event.button() == PointerButton.PRIMARY) {
            context.worldTileAt(event.x(), event.y())
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
        LocalTile localStart = context.local(start).orElse(null);
        LocalTile localCurrent = context.local(current).orElse(null);
        if (localStart == null || localCurrent == null) {
            clear();
            return;
        }

        TileBounds localBounds = mode == Mode.SINGLE
                ? new TileBounds(localStart.x(), localStart.y(), localStart.x(), localStart.y())
                : bounds(localStart, localCurrent);
        if (target == Target.TILES) {
            context.session().selection().selectArea(localStart.plane(), localBounds);
        } else {
            Set<WorldObject> objects = new LinkedHashSet<>();
            // A single click resolves to the exact object the click ray hit - not
            // every object present on that tile. A tile can carry a wall, a wall
            // decoration, and a ground object all at once; without this, clicking
            // any one of them selected all three. Falls back to the tile-scan below
            // only when the viewport can't do a precise pick (or the ray genuinely
            // missed every object), matching Viewport.objectAt's own documented
            // "legacy viewport" fallback contract.
            if (mode == Mode.SINGLE) {
                context.hitAt(downX, downY)
                        .flatMap(com.rspsi.editor.viewport.SurfaceHit::object)
                        .ifPresent(objects::add);
            }
            if (objects.isEmpty()) {
                var world = context.session().world();
                for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
                    for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                        world.tile(new LocalTile(localStart.plane(), x, y)).snapshot()
                                .objects().forEach(objects::add);
                    }
                }
            }
            context.session().selection().selectObjects(objects);
            if (objects.isEmpty()) {
                // Nothing was actually under the click - clear the marquee too,
                // so no stray outline is left behind pointing at empty ground.
                clear();
                return;
            }
        }
        // Keep the world-space marquee visible until the next stroke.
    }

    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("target", "Select",
                PropertyDescriptor.ValueType.ENUM, 0, 1));
    }

    /**
     * Draws the tile marquee only when the target is {@link Target#TILES}.
     * An object pick is not a tile pick: the "what did I select" highlight
     * for {@link Target#OBJECTS} is owned entirely by the Studio-level
     * selection overlay, which re-reads the live selection model every
     * frame and draws a real hull around each selected object's own
     * geometry instead of a generic box. That also keeps this tool from
     * ever needing to remember - and possibly forget to clear - a "last
     * picked" outline of its own.
     */
    @Override public void renderOverlay(OverlayDraw draw) {
        if (start == null || target != Target.TILES) return;
        if (mode == Mode.SINGLE || current == null) {
            draw.tileFilled(start);
            draw.tileOutline(start);
            return;
        }
        TileBounds bounds = bounds(start, current);
        int plane = start.plane();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                draw.tileFilled(new WorldTile(plane, x, y));
            }
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            draw.tileOutline(new WorldTile(plane, x, bounds.minY()));
            if (bounds.maxY() != bounds.minY()) {
                draw.tileOutline(new WorldTile(plane, x, bounds.maxY()));
            }
        }
        for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
            draw.tileOutline(new WorldTile(plane, bounds.minX(), y));
            if (bounds.maxX() != bounds.minX()) {
                draw.tileOutline(new WorldTile(plane, bounds.maxX(), y));
            }
        }
    }

    private static TileBounds bounds(WorldTile first, WorldTile second) {
        return new TileBounds(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()));
    }

    private static TileBounds bounds(LocalTile first, LocalTile second) {
        return new TileBounds(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()));
    }

    private void clear() { start = null; current = null; }
}
