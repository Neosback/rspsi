package com.rspsi.editor.render;

import java.util.Objects;

/** Shared-lattice corner normals for one terrain tile. */
public record TerrainNormalTile(
        TerrainNormal southWest,
        TerrainNormal southEast,
        TerrainNormal northEast,
        TerrainNormal northWest
) {
    public TerrainNormalTile {
        southWest = Objects.requireNonNull(southWest, "southWest");
        southEast = Objects.requireNonNull(southEast, "southEast");
        northEast = Objects.requireNonNull(northEast, "northEast");
        northWest = Objects.requireNonNull(northWest, "northWest");
    }

    public static TerrainNormalTile flat() {
        return new TerrainNormalTile(
                TerrainNormal.FLAT, TerrainNormal.FLAT,
                TerrainNormal.FLAT, TerrainNormal.FLAT);
    }

    /**
     * Bilinearly interpolates the shared corner-normal field and renormalizes
     * to the client 256-scale convention used by model normals.
     */
    public TerrainNormal at(int x, int y) {
        if (x < 0 || x > 128 || y < 0 || y > 128) {
            throw new IllegalArgumentException("Terrain normal sample must be inside a tile");
        }
        if (x == 0 && y == 0) return southWest;
        if (x == 128 && y == 0) return southEast;
        if (x == 128 && y == 128) return northEast;
        if (x == 0 && y == 128) return northWest;

        int inverseX = 128 - x;
        int inverseY = 128 - y;
        int nx = weighted(southWest.x(), southEast.x(), northEast.x(), northWest.x(),
                x, y, inverseX, inverseY);
        int ny = weighted(southWest.y(), southEast.y(), northEast.y(), northWest.y(),
                x, y, inverseX, inverseY);
        int nz = weighted(southWest.z(), southEast.z(), northEast.z(), northWest.z(),
                x, y, inverseX, inverseY);
        long lengthSquared = (long) nx * nx + (long) ny * ny + (long) nz * nz;
        if (lengthSquared == 0L) return TerrainNormal.FLAT;
        int length = Math.max(1, (int) Math.sqrt(lengthSquared));
        return new TerrainNormal(nx * 256 / length, ny * 256 / length,
                nz * 256 / length, 1);
    }

    private static int weighted(int sw, int se, int ne, int nw,
                                int x, int y, int inverseX, int inverseY) {
        long value = (long) sw * inverseX * inverseY
                + (long) se * x * inverseY
                + (long) ne * x * y
                + (long) nw * inverseX * y;
        return (int) ((value >= 0 ? value + 8192 : value - 8192) / 16384);
    }
}
