package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.brush.BrushAwareTool;
import com.rspsi.editor.brush.BrushSampling;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.brush.builtin.SquareBrush;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.OverlayDraw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composite tile painter tool allowing selective application of
 * underlay, overlay, shape, rotation, flags, and height to brushed or selected tiles.
 */
public final class CompositeTilePainterTool implements EditorTool, BrushAwareTool {
    private boolean applyUnderlay = false;
    private int underlayId = 0;

    private boolean applyOverlay = true;
    private int overlayId = 1;

    private boolean applyShape = false;
    private int shape = 0;

    private boolean applyRotation = false;
    private int rotation = 0;

    private boolean applyFlags = false;
    private int flags = 0;

    private boolean applyHeight = false;
    private int height = 0;

    private ToolContext context;
    private EditorBrush brush = new SquareBrush();
    private int brushRadius = 0;
    private final List<EditorCommand> stroke = new ArrayList<>();
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();
    private final Set<TileCoordinate> targetLocals = new LinkedHashSet<>();

    public CompositeTilePainterTool() {
    }

    public boolean applyUnderlay() { return applyUnderlay; }
    public void setApplyUnderlay(boolean apply) { this.applyUnderlay = apply; }
    public int underlayId() { return underlayId; }
    public void setUnderlayId(int id) { this.underlayId = Math.max(0, id); }

    public boolean applyOverlay() { return applyOverlay; }
    public void setApplyOverlay(boolean apply) { this.applyOverlay = apply; }
    public int overlayId() { return overlayId; }
    public void setOverlayId(int id) { this.overlayId = Math.max(0, id); }

    public boolean applyShape() { return applyShape; }
    public void setApplyShape(boolean apply) { this.applyShape = apply; }
    public int shape() { return shape; }
    public void setShape(int shape) { this.shape = Math.max(0, Math.min(12, shape)); }

    public boolean applyRotation() { return applyRotation; }
    public void setApplyRotation(boolean apply) { this.applyRotation = apply; }
    public int rotation() { return rotation; }
    public void setRotation(int rot) { this.rotation = Math.max(0, Math.min(3, rot)); }

    public boolean applyFlags() { return applyFlags; }
    public void setApplyFlags(boolean apply) { this.applyFlags = apply; }
    public int flags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }

    public boolean applyHeight() { return applyHeight; }
    public void setApplyHeight(boolean apply) { this.applyHeight = apply; }
    public int height() { return height; }
    public void setHeight(int h) { this.height = h; }

    @Override
    public EditorBrush brush() { return brush; }
    @Override
    public void setBrush(EditorBrush brush) {
        this.brush = java.util.Objects.requireNonNull(brush, "brush");
    }
    @Override
    public int brushRadius() { return brushRadius; }
    @Override
    public void setBrushRadius(int brushRadius) {
        if (brushRadius < 0 || brushRadius > 64) {
            throw new IllegalArgumentException("Brush radius must be 0 through 64");
        }
        this.brushRadius = brushRadius;
    }

    @Override
    public String id() {
        return "tile-painter";
    }

    @Override
    public void activate(ToolContext context) {
        this.context = context;
        clear();
    }

    @Override
    public void deactivate() {
        clear();
        this.context = null;
    }

    @Override
    public void pointerDown(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) {
            clear();
            addTile(event);
        }
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) {
            addTile(event);
        }
    }

    @Override
    public void pointerUp(PointerEvent event) {
        if (context != null && !targetLocals.isEmpty()) {
            buildStroke();
            if (!stroke.isEmpty() && context.session().canEdit()) {
                context.session().execute(new CompositeEditCommand("Paint composite tiles", stroke));
            }
        }
        clear();
    }

    @Override
    public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("underlayId", "Underlay", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("overlayId", "Overlay", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("shape", "Shape", PropertyDescriptor.ValueType.INTEGER, 0, 12),
                new PropertyDescriptor("rotation", "Rotation", PropertyDescriptor.ValueType.INTEGER, 0, 3),
                new PropertyDescriptor("height", "Height", PropertyDescriptor.ValueType.INTEGER, -2048, 2048)
        );
    }

    @Override
    public void renderOverlay(OverlayDraw draw) {
        visited.forEach(draw::tileOutline);
    }

    public TileSnapshot transformTile(TileSnapshot before) {
        int swH = applyHeight ? height : before.southWestHeight();
        int seH = applyHeight ? height : before.southEastHeight();
        int neH = applyHeight ? height : before.northEastHeight();
        int nwH = applyHeight ? height : before.northWestHeight();
        int und = applyUnderlay ? underlayId : before.underlayId();
        int ovr = applyOverlay ? overlayId : before.overlayId();
        int shp = applyShape ? shape : before.overlayShape();
        int rot = applyRotation ? rotation : before.overlayRotation();
        int flg = applyFlags ? flags : before.flags();
        return new TileSnapshot(swH, seH, neH, nwH, und, ovr, shp, rot, flg, before.objects());
    }

    /**
     * Applies the currently enabled properties to all given tile coordinates in one command.
     */
    public void applyToCoordinates(Collection<TileCoordinate> coordinates, com.rspsi.editor.EditorSession session) {
        if (coordinates == null || coordinates.isEmpty() || session == null || !session.canEdit()) return;
        List<EditorCommand> commands = new ArrayList<>();
        for (TileCoordinate coord : coordinates) {
            TileCoordinate local = toLocal(coord, session.world());
            TileSnapshot before = session.world().tile(local).snapshot();
            TileSnapshot after = transformTile(before);
            if (!before.equals(after)) {
                commands.add(new SetTileCommand(local, before, after, "Paint composite tile at " + local));
            }
        }
        if (!commands.isEmpty()) {
            session.execute(new CompositeEditCommand("Apply tile properties to selection (" + commands.size() + " tiles)", commands));
        }
    }

    private void addTile(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(center -> {
            for (BrushSampling.Sample sample : BrushSampling.sample(
                    brush, brushRadius, center, context.session().world())) {
                visited.add(sample.absolute());
                targetLocals.add(sample.local());
            }
        });
    }

    private void buildStroke() {
        stroke.clear();
        for (TileCoordinate local : targetLocals) {
            TileSnapshot before = context.session().world().tile(local).snapshot();
            TileSnapshot after = transformTile(before);
            if (!before.equals(after)) {
                stroke.add(new SetTileCommand(local, before, after, "Paint composite tile at " + local));
            }
        }
    }

    /**
     * Viewport picks return absolute OSRS world tile coordinates, but {@code WorldDocument} is
     * indexed by region-local coordinates - every tool that turns a pick into a document lookup
     * must convert here first, or it throws {@code IndexOutOfBoundsException} the moment someone
     * clicks/paints outside the tiny 0..width-1 range.
     */
    private static TileCoordinate toLocal(TileCoordinate absolute, com.rspsi.editor.model.WorldDocument world) {
        int localX = Math.floorMod(absolute.x(), Math.max(1, world.width()));
        int localY = Math.floorMod(absolute.y(), Math.max(1, world.length()));
        return new TileCoordinate(absolute.plane(), localX, localY);
    }

    private void clear() {
        stroke.clear();
        visited.clear();
        targetLocals.clear();
    }
}
