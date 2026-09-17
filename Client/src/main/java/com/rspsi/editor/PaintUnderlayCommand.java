package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic underlay mutation retained independently of UI/tool code. */
public final class PaintUnderlayCommand extends TileEditCommand {
    public PaintUnderlayCommand(TileCoordinate coordinate, TileSnapshot before,
                                TileSnapshot after, String description) {
        super(coordinate, before, after, description);
        requireUnchanged("south-west height", beforeSnapshot().southWestHeight(), afterSnapshot().southWestHeight());
        requireUnchanged("south-east height", beforeSnapshot().southEastHeight(), afterSnapshot().southEastHeight());
        requireUnchanged("north-east height", beforeSnapshot().northEastHeight(), afterSnapshot().northEastHeight());
        requireUnchanged("north-west height", beforeSnapshot().northWestHeight(), afterSnapshot().northWestHeight());
        requireUnchanged("overlay", beforeSnapshot().overlayId(), afterSnapshot().overlayId());
        requireUnchanged("overlay shape", beforeSnapshot().overlayShape(), afterSnapshot().overlayShape());
        requireUnchanged("overlay rotation", beforeSnapshot().overlayRotation(), afterSnapshot().overlayRotation());
        requireUnchanged("flags", beforeSnapshot().flags(), afterSnapshot().flags());
        requireUnchanged("objects", beforeSnapshot().objects(), afterSnapshot().objects());
    }
}
