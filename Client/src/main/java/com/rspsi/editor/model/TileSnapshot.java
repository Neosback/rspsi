package com.rspsi.editor.model;

import java.util.List;

/** Complete editable state for one tile, independent of rendering objects. */
public record TileSnapshot(
        int southWestHeight,
        int southEastHeight,
        int northEastHeight,
        int northWestHeight,
        int underlayId,
        int overlayId,
        int overlayShape,
        int overlayRotation,
        int flags,
        List<WorldObject> objects
) {
    public TileSnapshot {
        if (overlayShape < 0 || overlayRotation < 0 || overlayRotation > 3) {
            throw new IllegalArgumentException("Invalid overlay shape or rotation");
        }
        objects = List.copyOf(objects == null ? List.of() : objects);
    }
}
