package com.rspsi.editor.tool;

import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Rotates the first object owned by the clicked tile clockwise. */
public final class RotateObjectTool implements EditorTool {
    private ToolContext context;
    private int quarterTurns = 1;

    public int quarterTurns() { return quarterTurns; }
    public void setQuarterTurns(int quarterTurns) { this.quarterTurns = Math.floorMod(quarterTurns, 4); }

    @Override public String id() { return "rotate-object"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        context.viewport().objectAt(event.x(), event.y())
                .or(() -> context.worldTileAt(event.x(), event.y()).flatMap(this::firstObject))
                .ifPresent(this::rotate);
    }

    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("quarterTurns", "Quarter turns", PropertyDescriptor.ValueType.INTEGER, 0, 3)); }
    @Override public void renderOverlay(OverlayDraw draw) { }

    private java.util.Optional<WorldObject> firstObject(WorldTile worldTile) {
        return context.local(worldTile)
                .flatMap(context.session().world()::tileOpt)
                .flatMap(tile -> tile.snapshot().objects().stream().findFirst());
    }

    private void rotate(WorldObject object) {
        if (quarterTurns != 0 && context.session().canEdit()) {
            context.session().execute(new RotateObjectCommand(
                    object, (object.rotation() + quarterTurns) & 3));
        }
    }
}
