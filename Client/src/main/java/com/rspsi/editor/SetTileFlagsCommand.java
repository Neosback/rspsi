package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

import java.util.Objects;
import java.util.Set;

/** Undoable tile-flag mutation that preserves materials, geometry, objects and height provenance. */
public final class SetTileFlagsCommand implements EditorCommand {
    private final TileCoordinate coordinate;
    private final TileSnapshot before;
    private final TileSnapshot after;
    private final String description;

    public SetTileFlagsCommand(TileCoordinate coordinate, TileSnapshot before,
                               TileSnapshot after, String description) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.description = Objects.requireNonNull(description, "description");
        if (!sameExceptFlags(before, after)) {
            throw new IllegalArgumentException("Flag command can only change tile flags");
        }
    }

    @Override public void apply(EditorSession session) {
        session.world().tile(coordinate).restore(after, before.heightSource());
    }

    @Override public void undo(EditorSession session) {
        session.world().tile(coordinate).restore(before, before.heightSource());
    }

    @Override public String description() { return description; }
    @Override public Set<TileCoordinate> changedTiles() { return Set.of(coordinate); }

    private static boolean sameExceptFlags(TileSnapshot a, TileSnapshot b) {
        return a.southWestHeight() == b.southWestHeight()
                && a.southEastHeight() == b.southEastHeight()
                && a.northEastHeight() == b.northEastHeight()
                && a.northWestHeight() == b.northWestHeight()
                && a.underlayId() == b.underlayId()
                && a.overlayId() == b.overlayId()
                && a.overlayShape() == b.overlayShape()
                && a.overlayRotation() == b.overlayRotation()
                && a.objects().equals(b.objects());
    }
}
