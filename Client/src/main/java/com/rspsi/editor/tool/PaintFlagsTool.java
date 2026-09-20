package com.rspsi.editor.tool;

import com.rspsi.editor.ChangeTileFlagsCommand;
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

/** Command-backed tile-flag brush. */
public final class PaintFlagsTool implements EditorTool {
    private int flags;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<WorldTile> visited = new LinkedHashSet<>();

    public PaintFlagsTool(int flags) { setFlags(flags); }
    public int flags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    @Override public String id() { return "paint-flags"; }
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
            context.session().execute(new CompositeEditCommand("Paint tile flags", stroke));
        }
        clear();
    }
    @Override public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("flags", "Flags",
                PropertyDescriptor.ValueType.INTEGER, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
    @Override public void renderOverlay(OverlayDraw draw) { visited.forEach(draw::tileOutline); }

    private void addTile(PointerEvent event) {
        context.worldTileAt(event.x(), event.y()).ifPresent(worldTile -> {
            if (!visited.add(worldTile)) return;
            LocalTile local = context.local(worldTile).orElse(null);
            if (local == null) return;
            TileSnapshot before = context.session().world().tile(local).snapshot();
            if (before.flags() == flags) return;
            TileSnapshot after = new TileSnapshot(
                    before.southWestHeight(), before.southEastHeight(),
                    before.northEastHeight(), before.northWestHeight(),
                    before.underlayId(), before.overlayId(), before.overlayShape(),
                    before.overlayRotation(), flags, before.objects(), before.heightSource());
            stroke.add(new ChangeTileFlagsCommand(local.coordinate(), before, after,
                    "Paint flags at " + worldTile));
        });
    }

    private void clear() { stroke.clear(); visited.clear(); }
}
