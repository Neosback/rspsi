package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Command-backed overlay brush with explicit shape and rotation settings. */
public final class PaintOverlayTool implements EditorTool {
    private int overlayId;
    private int shape;
    private int rotation;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();

    public PaintOverlayTool(int overlayId) { setOverlayId(overlayId); }
    public int overlayId() { return overlayId; }
    public int shape() { return shape; }
    public int rotation() { return rotation; }
    public void setOverlayId(int overlayId) {
        if (overlayId < 0) throw new IllegalArgumentException("Overlay ID cannot be negative");
        this.overlayId = overlayId;
    }
    public void setShape(int shape) {
        if (shape < 0 || shape > 11) throw new IllegalArgumentException("Overlay shape must be between 0 and 11");
        this.shape = shape;
    }
    public void setRotation(int rotation) {
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Overlay rotation must be between 0 and 3");
        this.rotation = rotation;
    }
    @Override public String id() { return "paint-overlay"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) { clear(); addTile(event); }
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) addTile(event);
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && !stroke.isEmpty()) context.session().execute(
                new CompositeEditCommand("Paint overlay", stroke));
        clear();
    }
    @Override public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("overlayId", "Overlay", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("shape", "Shape", PropertyDescriptor.ValueType.INTEGER, 0, 11),
                new PropertyDescriptor("rotation", "Rotation", PropertyDescriptor.ValueType.INTEGER, 0, 3));
    }
    @Override public void renderOverlay(OverlayDraw draw) { visited.forEach(draw::tileOutline); }
    private void addTile(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            if (!visited.add(coordinate)) return;
            TileSnapshot before = context.session().world().tile(coordinate).snapshot();
            if (before.overlayId() == overlayId && before.overlayShape() == shape && before.overlayRotation() == rotation) return;
            TileSnapshot after = new TileSnapshot(before.southWestHeight(), before.southEastHeight(),
                    before.northEastHeight(), before.northWestHeight(), before.underlayId(), overlayId,
                    shape, rotation, before.flags(), before.objects());
            stroke.add(new SetTileCommand(coordinate, before, after, "Paint overlay at " + coordinate));
        });
    }
    private void clear() { stroke.clear(); visited.clear(); }
}
