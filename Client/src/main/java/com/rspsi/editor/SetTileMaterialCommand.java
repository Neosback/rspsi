package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

import java.util.Objects;
import java.util.Set;

/** Undoable underlay/overlay/shape/rotation mutation that preserves height provenance. */
public final class SetTileMaterialCommand implements EditorCommand {
    private final TileCoordinate coordinate;
    private final TileSnapshot before;
    private final TileSnapshot after;
    private final String description;

    public SetTileMaterialCommand(TileCoordinate coordinate, TileSnapshot before,
                                  TileSnapshot after, String description) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.description = Objects.requireNonNull(description, "description");
        requireEqual(before.southWestHeight(), after.southWestHeight(), "south-west height");
        requireEqual(before.southEastHeight(), after.southEastHeight(), "south-east height");
        requireEqual(before.northEastHeight(), after.northEastHeight(), "north-east height");
        requireEqual(before.northWestHeight(), after.northWestHeight(), "north-west height");
        requireEqual(before.flags(), after.flags(), "flags");
        if (!before.objects().equals(after.objects())) {
            throw new IllegalArgumentException("Material command cannot change objects");
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

    private static void requireEqual(int before, int after, String field) {
        if (before != after) throw new IllegalArgumentException("Material command cannot change " + field);
    }
}
