package com.rspsi.editor.tool;

import com.rspsi.editor.RotateObjectsCommand;
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

/** Rotates the current object selection clockwise on click. */
public final class RotateSelectionTool implements EditorTool {
    private ToolContext context;
    private int quarterTurns = 1;

    public int quarterTurns() { return quarterTurns; }
    public void setQuarterTurns(int quarterTurns) { this.quarterTurns = Math.floorMod(quarterTurns, 4); }

    @Override public String id() { return "rotate-selection"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        Set<WorldObject> objects = selectedObjects(context.session().selection().current());
        if (objects == null) return;
        context.viewport().tileAt(event.x(), event.y())
                .filter(tile -> contains(objects, tile))
                .ifPresent(ignored -> {
                    if (context.session().canEdit()) {
                        context.session().execute(new RotateObjectsCommand(objects, quarterTurns));
                        context.session().selection().selectObjects(rotated(objects, quarterTurns));
                    }
                });
    }

    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("quarterTurns", "Quarter turns", PropertyDescriptor.ValueType.INTEGER, 0, 3)); }
    @Override public void renderOverlay(OverlayDraw draw) { }

    private static Set<WorldObject> selectedObjects(Selection selection) {
        if (selection instanceof ObjectSelection object) return Set.of(object.object());
        if (selection instanceof ObjectSetSelection objects) return new LinkedHashSet<>(objects.objects());
        return null;
    }

    private static boolean contains(Set<WorldObject> objects, TileCoordinate tile) {
        return objects.stream().anyMatch(object -> object.plane() == tile.plane()
                && object.x() == tile.x() && object.y() == tile.y());
    }

    private static Set<WorldObject> rotated(Set<WorldObject> objects, int turns) {
        return objects.stream().map(object -> new WorldObject(object.id(), object.type(),
                (object.rotation() + turns) & 3, object.plane(), object.x(), object.y()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
