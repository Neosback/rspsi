package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Replaces one tile's canonical state; useful as the first adapter for old TileChange edits. */
public final class SetTileCommand extends TileEditCommand {
    public SetTileCommand(TileCoordinate coordinate, TileSnapshot before, TileSnapshot after, String description) {
        super(coordinate, before, after, description);
    }
}
