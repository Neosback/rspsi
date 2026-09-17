package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic render/collision flag mutation. */
public final class ChangeTileFlagsCommand extends TileEditCommand {
    public ChangeTileFlagsCommand(TileCoordinate coordinate, TileSnapshot before,
                                  TileSnapshot after, String description) {
        super(coordinate, before, after, description);
    }
}
