package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

/** Minimal drawing vocabulary for tool/debug overlays. */
public interface OverlayDraw {
    void tileOutline(TileCoordinate tile);

    default void label(String text, float x, float y) {
        // Optional for frontends that do not render labels in the viewport.
    }
}
