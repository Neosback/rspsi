package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

import java.util.Objects;
import java.util.Set;

/** Shared immutable implementation for atomic terrain-state commands. */
abstract class TileEditCommand implements EditCommand {
    private final TileCoordinate coordinate;
    private final TileSnapshot before;
    private final TileSnapshot after;
    private final String description;

    TileEditCommand(TileCoordinate coordinate, TileSnapshot before, TileSnapshot after,
                    String description) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public final void apply(EditorSession session) {
        session.world().tile(coordinate).restore(after);
    }

    @Override
    public final void undo(EditorSession session) {
        session.world().tile(coordinate).restore(before);
    }

    @Override
    public final String description() {
        return description;
    }

    @Override
    public final Set<TileCoordinate> changedTiles() {
        return Set.of(coordinate);
    }

    protected final TileSnapshot beforeSnapshot() {
        return before;
    }

    protected final TileSnapshot afterSnapshot() {
        return after;
    }

    protected final void requireUnchanged(String field, Object beforeValue, Object afterValue) {
        if (!Objects.equals(beforeValue, afterValue)) {
            throw new IllegalArgumentException(description + " cannot change " + field);
        }
    }
}
