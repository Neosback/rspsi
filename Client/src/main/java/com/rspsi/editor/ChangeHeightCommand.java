package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic corner-height mutation used by all terrain height tools. */
public final class ChangeHeightCommand extends TileEditCommand {
    public ChangeHeightCommand(TileCoordinate coordinate, TileSnapshot before,
                               TileSnapshot after, String description) {
        super(coordinate, before, after, description);
    }
}
