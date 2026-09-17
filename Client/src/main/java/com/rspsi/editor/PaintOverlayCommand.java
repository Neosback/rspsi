package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic overlay/shape/rotation mutation retained independently of UI code. */
public final class PaintOverlayCommand extends TileEditCommand {
    public PaintOverlayCommand(TileCoordinate coordinate, TileSnapshot before,
                               TileSnapshot after, String description) {
        super(coordinate, before, after, description);
        requireUnchanged("south-west height", beforeSnapshot().southWestHeight(), afterSnapshot().southWestHeight());
        requireUnchanged("south-east height", beforeSnapshot().southEastHeight(), afterSnapshot().southEastHeight());
        requireUnchanged("north-east height", beforeSnapshot().northEastHeight(), afterSnapshot().northEastHeight());
        requireUnchanged("north-west height", beforeSnapshot().northWestHeight(), afterSnapshot().northWestHeight());
        requireUnchanged("underlay", beforeSnapshot().underlayId(), afterSnapshot().underlayId());
        requireUnchanged("flags", beforeSnapshot().flags(), afterSnapshot().flags());
        requireUnchanged("objects", beforeSnapshot().objects(), afterSnapshot().objects());
    }
}
