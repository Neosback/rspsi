package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic corner-height mutation used by all terrain height tools. */
public final class ChangeHeightCommand extends TileEditCommand {
    public ChangeHeightCommand(TileCoordinate coordinate, TileSnapshot before,
                               TileSnapshot after, String description) {
        super(coordinate, before, after, description);
        requireUnchanged("underlay", beforeSnapshot().underlayId(), afterSnapshot().underlayId());
        requireUnchanged("overlay", beforeSnapshot().overlayId(), afterSnapshot().overlayId());
        requireUnchanged("overlay shape", beforeSnapshot().overlayShape(), afterSnapshot().overlayShape());
        requireUnchanged("overlay rotation", beforeSnapshot().overlayRotation(), afterSnapshot().overlayRotation());
        requireUnchanged("flags", beforeSnapshot().flags(), afterSnapshot().flags());
        requireUnchanged("objects", beforeSnapshot().objects(), afterSnapshot().objects());
    }
}
