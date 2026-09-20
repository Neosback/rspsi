package com.rspsi.editor.tool;

import com.rspsi.editor.MoveObjectsCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Moves the current single- or multi-object selection by pointer drag. */
public final class MoveSelectionTool implements EditorTool {
    private ToolContext context;
    private Set<WorldObject> objects;
    private WorldTile anchor;
    private WorldTile target;
    private int snapGridSize = 1;

    public int snapGridSize() { return snapGridSize; }
    public void setSnapGridSize(int snapGridSize) {
        if (snapGridSize < 1) throw new IllegalArgumentException("Snap grid size must be at least one tile");
        this.snapGridSize = snapGridSize;
    }

    @Override public String id() { return "move-selection"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        objects = selectedObjects(context.session().selection().current());
        context.worldTileAt(event.x(), event.y())
                .filter(tile -> objects != null && contains(objects, tile))
                .ifPresent(tile -> { anchor = tile; target = tile; });
        if (anchor == null) objects = null;
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && objects != null && event.button() == PointerButton.PRIMARY) {
            context.worldTileAt(event.x(), event.y())
                    .filter(tile -> tile.plane() == anchor.plane())
                    .map(this::snap)
                    .ifPresent(tile -> target = tile);
        }
    }

    @Override public void pointerUp(PointerEvent event) {
        LocalTile anchorLocal = anchor == null ? null : context.local(anchor).orElse(null);
        LocalTile targetLocal = target == null ? null : context.local(target).orElse(null);
        if (context != null && objects != null && anchorLocal != null && targetLocal != null
                && event.button() == PointerButton.PRIMARY) {
            int deltaX = targetLocal.x() - anchorLocal.x();
            int deltaY = targetLocal.y() - anchorLocal.y();
            if ((deltaX != 0 || deltaY != 0) && context.session().canEdit()) {
                context.session().execute(new MoveObjectsCommand(objects, deltaX, deltaY));
                context.session().selection().selectObjects(objects.stream()
                        .map(object -> new WorldObject(object.id(), object.type(), object.rotation(), object.plane(),
                                object.x() + deltaX, object.y() + deltaY))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
            }
        }
        clear();
    }

    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("snapGridSize", "Snap grid", PropertyDescriptor.ValueType.INTEGER, 1, 64)); }
    @Override public void renderOverlay(OverlayDraw draw) {
        if (target != null) draw.tileOutline(target);
    }

    private WorldTile snap(WorldTile tile) {
        LocalTile local = context.local(tile).orElse(null);
        if (local == null) return tile;
        return context.world(new LocalTile(local.plane(),
                TileSnapper.snap(local.x(), snapGridSize, context.session().world().width()),
                TileSnapper.snap(local.y(), snapGridSize, context.session().world().length())));
    }

    private boolean contains(Set<WorldObject> values, WorldTile tile) {
        LocalTile local = context.local(tile).orElse(null);
        return local != null && values.stream().anyMatch(object -> object.plane() == local.plane()
                && object.x() == local.x() && object.y() == local.y());
    }

    private static Set<WorldObject> selectedObjects(Selection selection) {
        if (selection instanceof ObjectSelection object) return Set.of(object.object());
        if (selection instanceof ObjectSetSelection objects) return new LinkedHashSet<>(objects.objects());
        return null;
    }

    private void clear() { objects = null; anchor = null; target = null; }
}
