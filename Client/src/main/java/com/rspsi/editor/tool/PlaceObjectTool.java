package com.rspsi.editor.tool;

import com.rspsi.editor.PlaceObjectCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.OverlayDraw;

import java.util.List;

/** Places a configured object through the canonical object command. */
public final class PlaceObjectTool implements EditorTool {
    private int id;
    private int type;
    private int rotation;
    private ToolContext context;

    public PlaceObjectTool(int id, int type, int rotation) {
        setId(id); setType(type); setRotation(rotation);
    }
    public void setId(int id) { if (id < 0) throw new IllegalArgumentException("Object ID cannot be negative"); this.id = id; }
    public void setType(int type) { if (type < 0) throw new IllegalArgumentException("Object type cannot be negative"); this.type = type; }
    public void setRotation(int rotation) { if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Object rotation must be between 0 and 3"); this.rotation = rotation; }
    public int idValue() { return id; }
    public int type() { return type; }
    public int rotation() { return rotation; }
    @Override public String id() { return "place-object"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        var world = context.session().world();
        context.viewport().tileAt(event.x(), event.y()).ifPresent(tile -> {
            // Viewport picks are absolute world coordinates; WorldObject/WorldDocument are
            // region-local, matching how objects already loaded from the cache are stored.
            int localX = Math.floorMod(tile.x(), Math.max(1, world.width()));
            int localY = Math.floorMod(tile.y(), Math.max(1, world.length()));
            context.session().execute(new PlaceObjectCommand(
                    new WorldObject(id, type, rotation, tile.plane(), localX, localY)));
        });
    }
    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("id", "Object", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("type", "Type", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("rotation", "Rotation", PropertyDescriptor.ValueType.INTEGER, 0, 3));
    }
    @Override public void renderOverlay(OverlayDraw draw) { }
}
