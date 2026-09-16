package com.rspsi.editor.tool;

import com.rspsi.editor.ReplaceObjectsCommand;
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

/** Replaces the definition ID of the current object selection on click. */
public final class ReplaceSelectionTool implements EditorTool {
    private int replacementId;
    private ToolContext context;

    public ReplaceSelectionTool(int replacementId) { setReplacementId(replacementId); }
    public int replacementId() { return replacementId; }
    public void setReplacementId(int replacementId) {
        if (replacementId < 0) throw new IllegalArgumentException("Replacement object ID cannot be negative");
        this.replacementId = replacementId;
    }

    @Override public String id() { return "replace-selection"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        Set<WorldObject> objects = selectedObjects(context.session().selection().current());
        if (objects == null) return;
        context.viewport().tileAt(event.x(), event.y())
                .filter(tile -> contains(objects, tile))
                .ifPresent(ignored -> {
                    ReplaceObjectsCommand command = new ReplaceObjectsCommand(objects, replacementId);
                    context.session().execute(command);
                    context.session().selection().selectObjects(command.replacementObjects());
                });
    }

    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("replacementId", "Replacement", PropertyDescriptor.ValueType.INTEGER,
                    0, Integer.MAX_VALUE)); }
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
}
