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
    private int snapGridSize = 1;

    public int snapGridSize() { return snapGridSize; }
    public void setSnapGridSize(int snapGridSize) {
        if (snapGridSize < 1) throw new IllegalArgumentException("Snap grid size must be at least one tile");
        this.snapGridSize = snapGridSize;
    }

    @Override public String id() { return "move-object"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            java.util.Optional<WorldObject> picked = context.viewport().objectAt(event.x(), event.y());
            object = picked
                    .orElseGet(() -> context.session().world().tile(coordinate).snapshot().objects()
                            .stream().findFirst().orElse(null));
            if (object != null) {
                target = picked.isPresent()
                        ? new TileCoordinate(object.plane(), object.x(), object.y())
                        : coordinate;
            }
        });
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && object != null && event.button() == PointerButton.PRIMARY) {
            context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> target = snap(coordinate));
        }
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && object != null && target != null
                && (target.x() != object.x() || target.y() != object.y())
                && context.session().canEdit()) {
            context.session().execute(new MoveObjectCommand(object, target.x(), target.y()));
        }
        clear();
    }
    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("snapGridSize", "Snap grid", PropertyDescriptor.ValueType.INTEGER, 1, 64)); }
    @Override public void renderOverlay(OverlayDraw draw) {
        if (target != null) draw.tileOutline(target);
    }
    private void clear() { object = null; target = null; }
    private TileCoordinate snap(TileCoordinate coordinate) {
        return new TileCoordinate(coordinate.plane(),
                TileSnapper.snap(coordinate.x(), snapGridSize, context.session().world().width()),
                TileSnapper.snap(coordinate.y(), snapGridSize, context.session().world().length()));
    }
}
