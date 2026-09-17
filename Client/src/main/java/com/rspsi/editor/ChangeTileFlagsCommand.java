package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic render/collision flag mutation. */
public final class ChangeTileFlagsCommand extends TileEditCommand {
    public ChangeTileFlagsCommand(TileCoordinate coordinate, TileSnapshot before,
                                  TileSnapshot after, String description) {
        super(coordinate, before, after, description);
        requireUnchanged("south-west height", beforeSnapshot().southWestHeight(), afterSnapshot().southWestHeight());
        requireUnchanged("south-east height", beforeSnapshot().southEastHeight(), afterSnapshot().southEastHeight());
        requireUnchanged("north-east height", beforeSnapshot().northEastHeight(), afterSnapshot().northEastHeight());
        requireUnchanged("north-west height", beforeSnapshot().northWestHeight(), afterSnapshot().northWestHeight());
        requireUnchanged("underlay", beforeSnapshot().underlayId(), afterSnapshot().underlayId());
        requireUnchanged("overlay", beforeSnapshot().overlayId(), afterSnapshot().overlayId());
        requireUnchanged("overlay shape", beforeSnapshot().overlayShape(), afterSnapshot().overlayShape());
        requireUnchanged("overlay rotation", beforeSnapshot().overlayRotation(), afterSnapshot().overlayRotation());
        requireUnchanged("objects", beforeSnapshot().objects(), afterSnapshot().objects());
    }
}
