package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic overlay/shape/rotation mutation retained independently of UI code. */
public final class PaintOverlayCommand extends TileEditCommand {
    public PaintOverlayCommand(TileCoordinate coordinate, TileSnapshot before,
                               TileSnapshot after, String description) {
        super(coordinate, before, after, description);
    }
}
