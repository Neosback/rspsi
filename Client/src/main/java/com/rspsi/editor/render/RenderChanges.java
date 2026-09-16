package com.rspsi.editor.render;

import com.rspsi.editor.model.DirtyRegion;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
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

    /**
     * Expands renderer-affecting dirty chunks into the tile coordinates needed
     * for a derived scene rebuild. A dirty chunk has no plane of its own, so
     * the expansion intentionally covers every document plane.
     */
    public static RenderChanges fromDirtyRegions(Set<DirtyRegion> dirtyRegions,
                                                  WorldDocument document) {
        Objects.requireNonNull(dirtyRegions, "dirtyRegions");
        Objects.requireNonNull(document, "document");
        Set<TileCoordinate> tiles = new LinkedHashSet<>();
        for (DirtyRegion region : dirtyRegions) {
            Objects.requireNonNull(region, "dirtyRegions cannot contain null");
            if (!region.render()) continue;
            int startX = region.chunkX() * 8;
            int startY = region.chunkY() * 8;
            if (startX >= document.width() || startY >= document.length()) {
                throw new IllegalArgumentException("Dirty chunk is outside the document: "
                        + region.chunkX() + "," + region.chunkY());
            }
            int endX = Math.min(startX + 8, document.width());
            int endY = Math.min(startY + 8, document.length());
            for (int plane = 0; plane < document.planes(); plane++) {
                for (int x = startX; x < endX; x++) {
                    for (int y = startY; y < endY; y++) {
                        tiles.add(new TileCoordinate(plane, x, y));
                    }
                }
            }
        }
        return new RenderChanges(tiles);
    }
}
