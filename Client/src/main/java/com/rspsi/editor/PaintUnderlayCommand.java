package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

/** Explicit atomic underlay mutation retained independently of UI/tool code. */
public final class PaintUnderlayCommand extends TileEditCommand {
    public PaintUnderlayCommand(TileCoordinate coordinate, TileSnapshot before,
                                TileSnapshot after, String description) {
        super(coordinate, before, after, description);
    }
}
