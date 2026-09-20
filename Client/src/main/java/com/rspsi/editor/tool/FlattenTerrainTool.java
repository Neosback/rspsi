package com.rspsi.editor.tool;

import com.rspsi.editor.ChangeHeightCommand;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Sets all four corners of each brushed tile to one height. */
public final class FlattenTerrainTool implements EditorTool {
    private int targetHeight;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<WorldTile> visited = new LinkedHashSet<>();

    public FlattenTerrainTool(int targetHeight) { this.targetHeight = targetHeight; }
    public int targetHeight() { return targetHeight; }
    public void setTargetHeight(int targetHeight) { this.targetHeight = targetHeight; }
    @Override public String id() { return "flatten-terrain"; }
    @Override public void activate(ToolContext context) { this.context = context; clear(); }
    @Override public void deactivate() { clear(); context = null; }
    @Override public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) { clear(); addTile(event); }
    }
    @Override public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) addTile(event);
    }
    @Override public void pointerUp(PointerEvent event) {
        if (context != null && !stroke.isEmpty() && context.session().canEdit()) {
            context.session().execute(new CompositeEditCommand("Flatten terrain", stroke));
        }
        clear();
    }
    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("targetHeight", "Target height",
                PropertyDescriptor.ValueType.INTEGER, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
    @Override public void renderOverlay(OverlayDraw draw) { visited.forEach(draw::tileOutline); }

    private void addTile(PointerEvent event) {
        context.worldTileAt(event.x(), event.y()).ifPresent(worldTile -> {
            if (!visited.add(worldTile)) return;
            LocalTile local = context.local(worldTile).orElse(null);
            if (local == null) return;
            TileSnapshot before = context.session().world().tile(local).snapshot();
            TileSnapshot after = new TileSnapshot(
                    targetHeight, targetHeight, targetHeight, targetHeight,
                    before.underlayId(), before.overlayId(), before.overlayShape(),
                    before.overlayRotation(), before.flags(), before.objects(),
                    before.heightSource());
            if (!before.equals(after)) {
                stroke.add(new ChangeHeightCommand(local.coordinate(), before, after,
                        "Flatten terrain at " + worldTile));
            }
        });
    }

    private void clear() { stroke.clear(); visited.clear(); }
}
