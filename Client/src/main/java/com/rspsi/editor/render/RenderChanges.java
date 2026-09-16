package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Small invalidation set sent to a renderer after an editor operation. */
public record RenderChanges(Set<TileCoordinate> dirtyTiles) {
    public RenderChanges {
        dirtyTiles = Collections.unmodifiableSet(new LinkedHashSet<>(
                dirtyTiles == null ? Set.of() : dirtyTiles));
    }

    public static RenderChanges none() {
        return new RenderChanges(Set.of());
    }
}
