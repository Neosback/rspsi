package com.rspsi.editor.viewport;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Optional;

/** Viewport services exposed to tools without exposing a UI or renderer. */
public interface Viewport {
    Optional<TileCoordinate> tileAt(float x, float y);
}
