package com.rspsi.editor.tool;

import com.rspsi.editor.DuplicateObjectsCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Duplicates the current object selection by a snapped pointer offset. */
public final class DuplicateSelectionTool implements EditorTool {
    private ToolContext context;
    private Set<WorldObject> objects;
    private TileCoordinate anchor;
    private TileCoordinate target;
    private int snapGridSize = 1;

    public int snapGridSize() { return snapGridSize; }
    public void setSnapGridSize(int snapGridSize) {
        if (snapGridSize < 1) throw new IllegalArgumentException("Snap grid size must be at least one tile");
        this.snapGridSize = snapGridSize;
    }

    @Override public String id() { return "duplicate-selection"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        objects = selectedObjects(context.session().selection().current());
        if (objects == null) return;
        context.viewport().tileAt(event.x(), event.y())
                .filter(tile -> tile.plane() == selectedPlane(objects) && contains(objects, tile))
                .ifPresent(tile -> { anchor = tile; target = tile; });
        if (anchor == null) objects = null;
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && objects != null && event.button() == PointerButton.PRIMARY) {
            context.viewport().tileAt(event.x(), event.y())
                    .filter(tile -> tile.plane() == anchor.plane())
                    .ifPresent(tile -> target = snap(tile));
        }
    }

    @Override public void pointerUp(PointerEvent event) {
        if (context != null && objects != null && anchor != null && target != null
                && event.button() == PointerButton.PRIMARY) {
            int deltaX = target.x() - anchor.x();
            int deltaY = target.y() - anchor.y();
            if ((deltaX != 0 || deltaY != 0) && context.session().canEdit()) {
                DuplicateObjectsCommand command = new DuplicateObjectsCommand(objects, deltaX, deltaY);
                context.session().execute(command);
                context.session().selection().selectObjects(objects.stream()
                        .map(object -> new WorldObject(object.id(), object.type(), object.rotation(), object.plane(),
                                object.x() + deltaX, object.y() + deltaY))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
            }
        }
        clear();
    }

    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("snapGridSize", "Snap grid", PropertyDescriptor.ValueType.INTEGER, 1, 64)); }

    @Override public void renderOverlay(OverlayDraw draw) {
        if (target != null) draw.tileOutline(target);
    }

    private TileCoordinate snap(TileCoordinate tile) {
        return new TileCoordinate(tile.plane(),
                TileSnapper.snap(tile.x(), snapGridSize, context.session().world().width()),
                TileSnapper.snap(tile.y(), snapGridSize, context.session().world().length()));
    }

    private static Set<WorldObject> selectedObjects(Selection selection) {
        if (selection instanceof ObjectSelection object) return Set.of(object.object());
        if (selection instanceof ObjectSetSelection objects) return new LinkedHashSet<>(objects.objects());
        return null;
    }

    private static int selectedPlane(Set<WorldObject> objects) {
        return objects.iterator().next().plane();
    }

    private static boolean contains(Set<WorldObject> objects, TileCoordinate tile) {
        return objects.stream().anyMatch(object -> object.plane() == tile.plane()
                && object.x() == tile.x() && object.y() == tile.y());
    }

    private void clear() { objects = null; anchor = null; target = null; }
}
