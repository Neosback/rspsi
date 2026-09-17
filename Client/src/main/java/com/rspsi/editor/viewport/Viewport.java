package com.rspsi.editor.viewport;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;

import java.util.Optional;

/** Viewport services exposed to tools without exposing a UI or renderer. */
public interface Viewport {
    Optional<TileCoordinate> tileAt(float x, float y);

    /** Optional object picking capability; legacy viewports may not provide it yet. */
    default Optional<WorldObject> objectAt(float x, float y) {
        return Optional.empty();
    }
}
