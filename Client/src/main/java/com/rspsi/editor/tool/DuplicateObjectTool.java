package com.rspsi.editor.tool;

import com.rspsi.editor.DuplicateObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Picks an object and places a copy at the release tile. */
public final class DuplicateObjectTool implements EditorTool {
    private ToolContext context;
    private WorldObject object;
    private WorldTile target;
    private int snapGridSize = 1;

    public int snapGridSize() { return snapGridSize; }
    public void setSnapGridSize(int snapGridSize) {
        if (snapGridSize < 1) throw new IllegalArgumentException("Snap grid size must be at least one tile");
        this.snapGridSize = snapGridSize;
    }

    @Override public String id() { return "duplicate-object"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        clear();
        context.worldTileAt(event.x(), event.y()).ifPresent(worldTile -> {
            LocalTile local = context.local(worldTile).orElse(null);
            if (local == null) return;
            object = context.viewport().objectAt(event.x(), event.y())
                    .orElseGet(() -> context.session().world().tile(local).snapshot().objects()
                            .stream().findFirst().orElse(null));
            target = worldTile;
        });
    }

    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && object != null && event.button() == PointerButton.PRIMARY) {
            context.worldTileAt(event.x(), event.y()).map(this::snap).ifPresent(tile -> target = tile);
        }
    }

    @Override public void pointerUp(PointerEvent event) {
        LocalTile targetLocal = target == null ? null : context.local(target).orElse(null);
        if (context != null && object != null && targetLocal != null
                && targetLocal.plane() == object.plane() && context.session().canEdit()) {
            context.session().execute(new DuplicateObjectCommand(
                    object, targetLocal.x(), targetLocal.y()));
        }
        clear();
    }

    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("snapGridSize", "Snap grid", PropertyDescriptor.ValueType.INTEGER, 1, 64)); }
    @Override public void renderOverlay(OverlayDraw draw) {
        if (target != null) draw.tileOutline(target);
    }

    private WorldTile snap(WorldTile absolute) {
        LocalTile local = context.local(absolute).orElse(null);
        if (local == null) return absolute;
        return context.world(new LocalTile(local.plane(),
                TileSnapper.snap(local.x(), snapGridSize, context.session().world().width()),
                TileSnapper.snap(local.y(), snapGridSize, context.session().world().length())));
    }

    private void clear() { object = null; target = null; }
}
