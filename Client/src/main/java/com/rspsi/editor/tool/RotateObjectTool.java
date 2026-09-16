package com.rspsi.editor.tool;

import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Rotates the first object owned by the clicked tile clockwise. */
public final class RotateObjectTool implements EditorTool {
    private ToolContext context;
    @Override public String id() { return "rotate-object"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        context.viewport().tileAt(event.x(), event.y()).ifPresent(this::rotateAt);
    }
    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() { return () -> List.of(); }
    @Override public void renderOverlay(OverlayDraw draw) { }
    private void rotateAt(TileCoordinate coordinate) {
        context.session().world().tile(coordinate).snapshot().objects().stream().findFirst()
                .ifPresent(object -> context.session().execute(new RotateObjectCommand(object, (object.rotation() + 1) & 3)));
    }
}
