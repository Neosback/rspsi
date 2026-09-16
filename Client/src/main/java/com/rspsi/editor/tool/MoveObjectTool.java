package com.rspsi.editor.tool;

import com.rspsi.editor.MoveObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Picks the first object on pointer-down and moves it on pointer-up. */
public final class MoveObjectTool implements EditorTool {
    private ToolContext context;
    private WorldObject object;
    private TileCoordinate target;

    @Override public String id() { return "move-object"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            List<com.rspsi.editor.model.WorldObject> objects = context.session().world()
                    .tile(coordinate).snapshot().objects();
            if (!objects.isEmpty()) {
                object = objects.get(0);
                target = coordinate;
            }
        });
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && object != null && event.button() == PointerButton.PRIMARY) {
            context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> target = coordinate);
        }
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && object != null && target != null
                && (target.x() != object.x() || target.y() != object.y())) {
            context.session().execute(new MoveObjectCommand(object, target.x(), target.y()));
        }
        clear();
    }
    @Override public ToolInspector inspector() { return () -> List.of(); }
    @Override public void renderOverlay(OverlayDraw draw) {
        if (target != null) draw.tileOutline(target);
    }
    private void clear() { object = null; target = null; }
}
