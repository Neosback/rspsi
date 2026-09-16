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

/** Simple raise/lower brush; smoothing and falloff are later tool settings. */
public final class ChangeHeightTool implements EditorTool {
    private int delta;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();

    public ChangeHeightTool(int delta) { setDelta(delta); }
    public int delta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
    @Override public String id() { return "change-height"; }
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
                new CompositeEditCommand(delta < 0 ? "Lower terrain" : "Raise terrain", stroke));
        clear();
    }
    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("delta", "Height delta", PropertyDescriptor.ValueType.INTEGER,
                Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
    @Override public void renderOverlay(OverlayDraw draw) { visited.forEach(draw::tileOutline); }
    private void addTile(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            if (!visited.add(coordinate) || delta == 0) return;
            TileSnapshot before = context.session().world().tile(coordinate).snapshot();
            TileSnapshot after = new TileSnapshot(before.southWestHeight() + delta, before.southEastHeight() + delta,
                    before.northEastHeight() + delta, before.northWestHeight() + delta, before.underlayId(),
                    before.overlayId(), before.overlayShape(), before.overlayRotation(), before.flags(), before.objects());
            stroke.add(new SetTileCommand(coordinate, before, after, "Change height at " + coordinate));
        });
    }
    private void clear() { stroke.clear(); visited.clear(); }
}
