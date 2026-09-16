package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

import java.util.Objects;

/** Replaces one tile's canonical state; useful as the first adapter for old TileChange edits. */
public final class SetTileCommand implements EditCommand {
    private final TileCoordinate coordinate;
    private final TileSnapshot before;
    private final TileSnapshot after;
    private final String description;

    public SetTileCommand(TileCoordinate coordinate, TileSnapshot before, TileSnapshot after, String description) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public void apply(EditorSession session) {
        session.world().tile(coordinate).restore(after);
    }

    @Override
    public void undo(EditorSession session) {
        session.world().tile(coordinate).restore(before);
    }

    @Override
    public String description() {
        return description;
    }
}
