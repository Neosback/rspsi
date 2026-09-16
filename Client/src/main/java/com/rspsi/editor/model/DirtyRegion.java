package com.rspsi.editor.model;

import java.util.Objects;

/** Derived-data invalidation flags for one 8x8 editor chunk. */
public record DirtyRegion(
        int chunkX,
        int chunkY,
        boolean terrain,
        boolean objects,
        boolean collision,
        boolean minimap,
        boolean render
) {
    public DirtyRegion {
        if (chunkX < 0 || chunkY < 0) {
            throw new IllegalArgumentException("Chunk coordinates cannot be negative");
        }
    }

    public static DirtyRegion forTile(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return new DirtyRegion(coordinate.x() / 8, coordinate.y() / 8,
                true, true, true, true, true);
    }

    public DirtyRegion merge(DirtyRegion other) {
        Objects.requireNonNull(other, "other");
        if (chunkX != other.chunkX || chunkY != other.chunkY) {
            throw new IllegalArgumentException("Can only merge the same dirty chunk");
        }
        return new DirtyRegion(chunkX, chunkY,
                terrain || other.terrain,
                objects || other.objects,
                collision || other.collision,
                minimap || other.minimap,
                render || other.render);
    }
}
