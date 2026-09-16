package com.rspsi.editor.tool;

import com.rspsi.editor.DuplicateObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Picks an object and places a copy at the release tile. */
public final class DuplicateObjectTool implements EditorTool {
    private ToolContext context;
    private WorldObject object;
    private TileCoordinate target;

    @Override public String id() { return "duplicate-object"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            List<WorldObject> objects = context.session().world().tile(coordinate).snapshot().objects();
            if (!objects.isEmpty()) object = objects.get(0);
        });
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && object != null && event.button() == PointerButton.PRIMARY) {
            context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> target = coordinate);
        }
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && object != null && target != null
                && target.plane() == object.plane()) {
            context.session().execute(new DuplicateObjectCommand(object, target.x(), target.y()));
        }
        clear();
    }
    @Override public ToolInspector inspector() { return () -> List.of(); }
    @Override public void renderOverlay(OverlayDraw draw) {
        if (target != null) draw.tileOutline(target);
    }
    private void clear() { object = null; target = null; }
}
