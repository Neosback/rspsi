package com.rspsi.editor.tool;

import com.rspsi.editor.DeleteObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Deletes the first object owned by the clicked tile. */
public final class DeleteObjectTool implements EditorTool {
    private ToolContext context;
    @Override public String id() { return "delete-object"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        context.viewport().objectAt(event.x(), event.y())
                .or(() -> context.worldTileAt(event.x(), event.y()).flatMap(this::firstObject))
                .ifPresent(this::delete);
    }

    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() { return () -> List.of(); }
    @Override public void renderOverlay(OverlayDraw draw) { }

    private java.util.Optional<WorldObject> firstObject(WorldTile worldTile) {
        return context.local(worldTile)
                .flatMap(context.session().world()::tileOpt)
                .flatMap(tile -> tile.snapshot().objects().stream().findFirst());
    }

    private void delete(WorldObject object) {
        if (context.session().canEdit()) context.session().execute(new DeleteObjectCommand(object));
    }
}
