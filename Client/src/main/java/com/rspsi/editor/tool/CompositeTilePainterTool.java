package com.rspsi.editor.tool;

import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTerrainHeightCommand;
import com.rspsi.editor.SetTileFlagsCommand;
import com.rspsi.editor.SetTileMaterialCommand;
import com.rspsi.editor.brush.BrushAwareTool;
import com.rspsi.editor.brush.BrushEngine;
import com.rspsi.editor.brush.BrushMask;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.brush.builtin.SquareBrush;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.terrain.TerrainVertexLattice;
import com.rspsi.editor.tool.state.TilePainterState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composite Tile Painter backed by one authoritative state object and the
 * common BrushEngine. Height painting is projected through the shared vertex
 * lattice so adjacent tile corners remain synchronized.
 */
public final class CompositeTilePainterTool implements EditorTool, BrushAwareTool {
    private final TilePainterState state;
    private final BrushEngine brushEngine;
    private EditorBrush brush;

    private ToolContext context;
    private final Set<TileCoordinate> visited = new LinkedHashSet<>();
    private final Set<TileCoordinate> targetLocals = new LinkedHashSet<>();
    private BrushMask lastMask;

    public CompositeTilePainterTool() {
        this(new TilePainterState(), new BrushEngine());
    }

    public CompositeTilePainterTool(TilePainterState state, BrushEngine brushEngine) {
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.brushEngine = java.util.Objects.requireNonNull(brushEngine, "brushEngine");
        EditorBrush initial;
        try {
            initial = brushEngine.brush(state.brushId());
        } catch (IllegalArgumentException ignored) {
            initial = new SquareBrush();
            brushEngine.register(initial);
            state.setBrushId(initial.id());
        }
        this.brush = initial;
    }

    public TilePainterState state() { return state; }

    public boolean applyUnderlay() { return state.applyUnderlay(); }
    public void setApplyUnderlay(boolean apply) { state.setApplyUnderlay(apply); }
    public int underlayId() { return state.underlayId(); }
    public void setUnderlayId(int id) { state.setUnderlayId(id); }

    public boolean applyOverlay() { return state.applyOverlay(); }
    public void setApplyOverlay(boolean apply) { state.setApplyOverlay(apply); }
    public int overlayId() { return state.overlayId(); }
    public void setOverlayId(int id) { state.setOverlayId(id); }

    public boolean applyShape() { return state.applyShape(); }
    public void setApplyShape(boolean apply) { state.setApplyShape(apply); }
    public int shape() { return state.shape(); }
    public void setShape(int shape) { state.setShape(shape); }

    public boolean applyRotation() { return state.applyRotation(); }
    public void setApplyRotation(boolean apply) { state.setApplyRotation(apply); }
    public int rotation() { return state.rotation(); }
    public void setRotation(int rot) { state.setRotation(rot); }

    public boolean applyFlags() { return state.applyFlags(); }
    public void setApplyFlags(boolean apply) { state.setApplyFlags(apply); }
    public int flags() { return state.flags(); }
    public void setFlags(int flags) { state.setFlags(flags); }

    public boolean applyHeight() { return state.applyHeight(); }
    public void setApplyHeight(boolean apply) { state.setApplyHeight(apply); }
    public int height() { return state.height(); }
    public void setHeight(int h) { state.setHeight(h); }

    @Override public EditorBrush brush() { return brush; }

    @Override
    public void setBrush(EditorBrush brush) {
        this.brush = java.util.Objects.requireNonNull(brush, "brush");
        brushEngine.register(brush);
        state.setBrushId(brush.id());
    }

    @Override public int brushRadius() { return state.brushRadius(); }
    @Override public void setBrushRadius(int radius) { state.setBrushRadius(radius); }

    @Override public String id() { return "tile-painter"; }

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
        if (context != null && event.button() == PointerButton.PRIMARY) {
            clearStroke();
            sample(event);
        }
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        if (context != null && event.button() == PointerButton.PRIMARY) sample(event);
    }

    @Override
    public void pointerUp(PointerEvent event) {
        if (context != null && !targetLocals.isEmpty() && context.session().canEdit()) {
            List<EditorCommand> commands = buildCommands(context.session(), targetLocals);
            if (!commands.isEmpty()) {
                context.session().execute(new CompositeEditCommand(
                        "Paint composite tiles (" + targetLocals.size() + " targets)", commands));
            }
        }
        clearStroke();
    }

    @Override
    public ToolInspector inspector() {
        return () -> List.of(
                new PropertyDescriptor("underlayId", "Underlay", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("overlayId", "Overlay", PropertyDescriptor.ValueType.INTEGER, 0, Integer.MAX_VALUE),
                new PropertyDescriptor("shape", "Shape", PropertyDescriptor.ValueType.INTEGER, 0, 11),
                new PropertyDescriptor("rotation", "Rotation", PropertyDescriptor.ValueType.INTEGER, 0, 3),
                new PropertyDescriptor("height", "Height", PropertyDescriptor.ValueType.INTEGER, -2048, 2048),
                new PropertyDescriptor("brushRadius", "Brush radius", PropertyDescriptor.ValueType.INTEGER, 0, 64));
    }

    @Override
    public void renderOverlay(OverlayDraw draw) {
        // This is the exact mask that the edit command consumes during the
        // current stroke, rather than a second independently-computed shape.
        if (lastMask != null) {
            lastMask.samples().forEach(sample -> draw.tileOutline(sample.absolute()));
        } else {
            visited.forEach(draw::tileOutline);
        }
    }

    public TileSnapshot transformTile(TileSnapshot before) {
        int und = state.applyUnderlay() ? state.underlayId() : before.underlayId();
        int ovr = state.applyOverlay() ? state.overlayId() : before.overlayId();
        int shp = state.applyShape() ? state.shape() : before.overlayShape();
        int rot = state.applyRotation() ? state.rotation() : before.overlayRotation();
        int flg = state.applyFlags() ? state.flags() : before.flags();
        int sw = state.applyHeight() ? state.height() : before.southWestHeight();
        int se = state.applyHeight() ? state.height() : before.southEastHeight();
        int ne = state.applyHeight() ? state.height() : before.northEastHeight();
        int nw = state.applyHeight() ? state.height() : before.northWestHeight();
        return new TileSnapshot(sw, se, ne, nw, und, ovr, shp, rot, flg,
                before.objects(), before.heightSource());
    }

    /** Applies current state to a selection as one atomic history entry. */
    public void applyToCoordinates(Collection<TileCoordinate> coordinates, EditorSession session) {
        if (coordinates == null || coordinates.isEmpty() || session == null || !session.canEdit()) return;
        Set<TileCoordinate> locals = new LinkedHashSet<>();
        for (TileCoordinate coordinate : coordinates) {
            TileCoordinate local = toLocal(coordinate, session.world());
            if (local != null) locals.add(local);
        }
        List<EditorCommand> commands = buildCommands(session, locals);
        if (!commands.isEmpty()) {
            session.execute(new CompositeEditCommand(
                    "Apply tile properties to selection (" + locals.size() + " tiles)", commands));
        }
    }

    private void sample(PointerEvent event) {
        context.viewport().tileAt(event.x(), event.y()).ifPresent(center -> {
            lastMask = brushEngine.sample(brush, state.brushRadius(), center, context.session().world());
            for (var sample : lastMask.samples()) {
                visited.add(sample.absolute());
                targetLocals.add(sample.local());
            }
        });
    }

    private List<EditorCommand> buildCommands(EditorSession session, Collection<TileCoordinate> targets) {
        if (targets == null || targets.isEmpty()) return List.of();

        List<EditorCommand> commands = new ArrayList<>();
        WorldDocument original = session.world();
        WorldDocument predicted = original.copy();
        Set<TileCoordinate> heightAffected = new LinkedHashSet<>();

        // Height is applied first to a copy through the canonical shared
        // lattice. Commands then reproduce those synchronized snapshots.
        if (state.applyHeight()) {
            TerrainVertexLattice lattice = new TerrainVertexLattice(predicted);
            for (TileCoordinate coordinate : targets) {
                int plane = coordinate.plane();
                int x = coordinate.x();
                int y = coordinate.y();
                heightAffected.addAll(lattice.setHeight(plane, x, y, state.height()));
                heightAffected.addAll(lattice.setHeight(plane, x + 1, y, state.height()));
                heightAffected.addAll(lattice.setHeight(plane, x + 1, y + 1, state.height()));
                heightAffected.addAll(lattice.setHeight(plane, x, y + 1, state.height()));
            }
            for (TileCoordinate coordinate : heightAffected) {
                TileSnapshot before = original.tile(coordinate).snapshot();
                TileSnapshot after = predicted.tile(coordinate).snapshot();
                if (!before.equals(after)) {
                    commands.add(new SetTerrainHeightCommand(
                            coordinate, before, after, before.heightSource(), after.heightSource(),
                            "Paint terrain height at " + coordinate));
                }
            }
        }

        // Material and flags build from the predicted post-height state so
        // sequential application never reverts height edits.
        for (TileCoordinate coordinate : targets) {
            TileSnapshot base = predicted.tile(coordinate).snapshot();
            TileSnapshot materialAfter = materialSnapshot(base);
            if (!sameMaterial(base, materialAfter)) {
                commands.add(new SetTileMaterialCommand(
                        coordinate, base, materialAfter, "Paint tile material at " + coordinate));
                predicted.tile(coordinate).restore(materialAfter, base.heightSource());
                base = predicted.tile(coordinate).snapshot();
            }

            TileSnapshot flagsAfter = flagsSnapshot(base);
            if (base.flags() != flagsAfter.flags()) {
                commands.add(new SetTileFlagsCommand(
                        coordinate, base, flagsAfter, "Paint tile flags at " + coordinate));
                predicted.tile(coordinate).restore(flagsAfter, base.heightSource());
            }
        }
        return List.copyOf(commands);
    }

    private TileSnapshot materialSnapshot(TileSnapshot before) {
        return new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                state.applyUnderlay() ? state.underlayId() : before.underlayId(),
                state.applyOverlay() ? state.overlayId() : before.overlayId(),
                state.applyShape() ? state.shape() : before.overlayShape(),
                state.applyRotation() ? state.rotation() : before.overlayRotation(),
                before.flags(), before.objects(), before.heightSource());
    }

    private TileSnapshot flagsSnapshot(TileSnapshot before) {
        return new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                state.applyFlags() ? state.flags() : before.flags(),
                before.objects(), before.heightSource());
    }

    private static boolean sameMaterial(TileSnapshot first, TileSnapshot second) {
        return first.underlayId() == second.underlayId()
                && first.overlayId() == second.overlayId()
                && first.overlayShape() == second.overlayShape()
                && first.overlayRotation() == second.overlayRotation();
    }

    private static TileCoordinate toLocal(TileCoordinate coordinate, WorldDocument world) {
        if (coordinate == null) return null;
        if (world.contains(coordinate)) return coordinate;
        if (coordinate.x() < 0 || coordinate.y() < 0 || coordinate.plane() < 0) return null;
        WorldTileAddress address = WorldTileAddress.of(coordinate.x(), coordinate.y(), coordinate.plane());
        int localX = address.regionLocalX();
        int localY = address.regionLocalY();
        return world.contains(coordinate.plane(), localX, localY)
                ? new TileCoordinate(coordinate.plane(), localX, localY)
                : null;
    }

    private void clearStroke() {
        visited.clear();
        targetLocals.clear();
        lastMask = null;
    }
}
