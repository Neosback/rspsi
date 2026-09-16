package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Command-backed underlay brush used as the first complete neutral tool path. */
public final class PaintUnderlayTool implements EditorTool {
    private int underlayId;
    private ToolContext context;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();

    public PaintUnderlayTool(int underlayId) {
        setUnderlayId(underlayId);
    }

    public int underlayId() {
        return underlayId;
    }

    public void setUnderlayId(int underlayId) {
        if (underlayId < 0) {
            throw new IllegalArgumentException("Underlay ID cannot be negative");
        }
        this.underlayId = underlayId;
    }

    @Override
    public String id() {
        return "paint-underlay";
    }

    @Override
    public void activate(ToolContext context) {
        this.context = context;
        clearStroke();
    }

    @Override
    public void deactivate() {
        clearStroke();
        context = null;
    }

    @Override
    public void pointerDown(PointerEvent event) {
        if (event.button() != PointerButton.PRIMARY || context == null) {
            return;
        }
        clearStroke();
        addTileAt(event);
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        if (event.button() == PointerButton.PRIMARY && context != null) {
            addTileAt(event);
        }
    }

    @Override
    public void pointerUp(PointerEvent event) {
        if (context == null || stroke.isEmpty()) {
            clearStroke();
            return;
        }
        EditorSession session = context.session();
        session.execute(new CompositeEditCommand("Paint underlay", stroke));
        clearStroke();
    }

    @Override
    public ToolInspector inspector() {
        return () -> List.of(new PropertyDescriptor("underlayId", "Underlay", 
                PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE));
    }

    @Override
    public void renderOverlay(OverlayDraw draw) {
        for (TileCoordinate tile : visited) {
            draw.tileOutline(tile);
        }
    }

    private void addTileAt(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(coordinate -> {
            if (!visited.add(coordinate)) {
                return;
            }
            TileSnapshot before = context.session().world().tile(coordinate).snapshot();
            if (before.underlayId() == underlayId) {
                return;
            }
            TileSnapshot after = new TileSnapshot(before.southWestHeight(), before.southEastHeight(),
                    before.northEastHeight(), before.northWestHeight(), underlayId,
                    before.overlayId(), before.overlayShape(), before.overlayRotation(),
                    before.flags(), before.objects());
            stroke.add(new com.rspsi.editor.SetTileCommand(coordinate, before, after,
                    "Paint underlay at " + coordinate));
        });
    }

    private void clearStroke() {
        stroke.clear();
        visited.clear();
    }
}
