package com.rspsi.editor.model;

import java.util.Objects;

/** Derived-data invalidation flags for one 8x8 editor chunk. */
public record DirtyRegion(
        int plane,
        int chunkX,
        int chunkY,
        boolean terrain,
        boolean objects,
        boolean collision,
        boolean minimap,
        boolean render
) {
    public DirtyRegion {
        if (plane < -1 || chunkX < 0 || chunkY < 0) {
            throw new IllegalArgumentException("Plane must be -1 or non-negative and chunks cannot be negative");
        }
    }

    /**
     * Compatibility constructor for callers that invalidate the same chunk
     * across every document plane. Canonical session invalidations use the
     * plane-aware factory below.
     */
    public DirtyRegion(int chunkX, int chunkY, boolean terrain, boolean objects,
                       boolean collision, boolean minimap, boolean render) {
        this(-1, chunkX, chunkY, terrain, objects, collision, minimap, render);
    }

    public static DirtyRegion forTile(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return new DirtyRegion(coordinate.plane(), coordinate.x() / 8, coordinate.y() / 8,
                true, true, true, true, true);
    }

    public DirtyRegion merge(DirtyRegion other) {
        Objects.requireNonNull(other, "other");
        if (plane != other.plane || chunkX != other.chunkX || chunkY != other.chunkY) {
            throw new IllegalArgumentException("Can only merge the same plane and dirty chunk");
        }
        return new DirtyRegion(plane, chunkX, chunkY,
                terrain || other.terrain,
                objects || other.objects,
                collision || other.collision,
                minimap || other.minimap,
                render || other.render);
    }
}
