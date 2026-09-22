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
            var world = context.session().world();
            Set<WorldObject> objects = new LinkedHashSet<>();
            for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
                for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                    world.tile(new LocalTile(localStart.plane(), x, y)).snapshot()
                            .objects().forEach(objects::add);
                }
            }
            context.session().selection().selectObjects(objects);
        }
        // Keep the world-space marquee visible until the next stroke.
    }

    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("target", "Select",
                PropertyDescriptor.ValueType.ENUM, 0, 1));
    }

    /** Amber, distinct from the default cyan tile-select outline - an object pick is not a tile pick. */
    private static final int OBJECT_OUTLINE_COLOR = 0xF59E0BFF;

    /**
     * A representative scenery height, not this object's real one - this
     * tool only knows a {@link WorldObject}'s id/type/rotation/position, not
     * its model bounds (that needs the cache's {@code DefinitionProvider},
     * which a low-level engine tool does not hold). A flat tile outline for
     * an object pick still reads as "you selected the ground", so a boxy
     * volume in roughly the right size is a real improvement even without
     * the object's exact silhouette.
     */
    private static final float OBJECT_BOX_HEIGHT = 190.0f;

    @Override public void renderOverlay(OverlayDraw draw) {
        if (start == null) return;
        boolean objects = target == Target.OBJECTS;
        if (mode == Mode.SINGLE || current == null) {
            mark(draw, start, objects);
            return;
        }
        TileBounds bounds = bounds(start, current);
        int plane = start.plane();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            mark(draw, new WorldTile(plane, x, bounds.minY()), objects);
            if (bounds.maxY() != bounds.minY()) {
                mark(draw, new WorldTile(plane, x, bounds.maxY()), objects);
            }
        }
        for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
            mark(draw, new WorldTile(plane, bounds.minX(), y), objects);
            if (bounds.maxX() != bounds.minX()) {
                mark(draw, new WorldTile(plane, bounds.maxX(), y), objects);
            }
        }
    }

    private static void mark(OverlayDraw draw, WorldTile tile, boolean objects) {
        if (!objects) {
            draw.tileOutline(tile);
            return;
        }
        float x0 = tile.x() * 128.0f;
        float z0 = tile.y() * 128.0f;
        draw.box(x0, -OBJECT_BOX_HEIGHT, z0, x0 + 128.0f, 0.0f, z0 + 128.0f, OBJECT_OUTLINE_COLOR, false);
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
