package com.rspsi.editor.tool;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selects tiles or objects whose tile centers fall inside a world-space pointer lasso. */
public final class LassoSelectTool implements EditorTool {
    public enum Target { TILES, OBJECTS }

    private Target target = Target.TILES;
    private ToolContext context;
    private final List<WorldTile> points = new ArrayList<>();
    private int plane = -1;

    public Target target() { return target; }
    public void setTarget(Target target) { this.target = java.util.Objects.requireNonNull(target, "target"); }

    @Override public String id() { return "lasso-select"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        addPoint(event);
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && !points.isEmpty() && event.button() == PointerButton.PRIMARY) addPoint(event);
    }

    @Override public void pointerUp(PointerEvent event) {
        if (context == null || points.size() < 3 || event.button() != PointerButton.PRIMARY) {
            clear();
            return;
        }
        Set<LocalTile> selectedLocals = localsInsideLasso();
        if (target == Target.TILES) {
            Set<TileCoordinate> selected = new LinkedHashSet<>();
            selectedLocals.forEach(tile -> selected.add(tile.coordinate()));
            context.session().selection().selectTiles(selected);
        } else {
            Set<WorldObject> objects = new LinkedHashSet<>();
            selectedLocals.forEach(tile ->
                    context.session().world().tile(tile).snapshot().objects().forEach(objects::add));
            context.session().selection().selectObjects(objects);
        }
        clear();
    }

    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("target", "Select",
                PropertyDescriptor.ValueType.ENUM, 0, 1));
    }

    @Override public void renderOverlay(OverlayDraw draw) {
        localsInsideLasso().stream().map(context::world).forEach(draw::tileOutline);
    }

    private void addPoint(PointerEvent event) {
        context.worldTileAt(event.x(), event.y())
                .filter(tile -> plane < 0 || tile.plane() == plane)
                .filter(tile -> context.local(tile).isPresent())
                .ifPresent(tile -> {
                    if (plane < 0) plane = tile.plane();
                    if (points.isEmpty() || !points.get(points.size() - 1).equals(tile)) points.add(tile);
                });
    }

    private Set<LocalTile> localsInsideLasso() {
        if (context == null || points.size() < 3 || plane < 0) return Set.of();
        Set<LocalTile> selected = new LinkedHashSet<>();
        var world = context.session().world();
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.length(); y++) {
                LocalTile local = new LocalTile(plane, x, y);
                WorldTile absolute = context.world(local);
                if (contains(absolute.x() + 0.5, absolute.y() + 0.5)) selected.add(local);
            }
        }
        return Set.copyOf(selected);
    }

    private boolean contains(double x, double y) {
        boolean inside = false;
        for (int index = 0, previous = points.size() - 1; index < points.size(); previous = index++) {
            double currentX = points.get(index).x() + 0.5;
            double currentY = points.get(index).y() + 0.5;
            double previousX = points.get(previous).x() + 0.5;
            double previousY = points.get(previous).y() + 0.5;
            boolean crosses = (currentY > y) != (previousY > y)
                    && x < (previousX - currentX) * (y - currentY)
                    / (previousY - currentY) + currentX;
            if (crosses) inside = !inside;
        }
        return inside;
    }

    private void clear() {
        points.clear();
        plane = -1;
    }
}
