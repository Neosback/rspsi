package com.rspsi.editor.model;

/**
 * Neutral OSRS coordinate breakdown used by inspectors and debug overlays.
 * Region and chunk values are derived from world coordinates, not cache APIs.
 */
public record WorldTileAddress(
        int worldX,
        int worldY,
        int plane,
        int regionId,
        int regionX,
        int regionY,
        int regionLocalX,
        int regionLocalY,
        int chunkX,
        int chunkY,
        int chunkLocalX,
        int chunkLocalY
) {
    public WorldTileAddress {
        if (worldX < 0 || worldY < 0 || plane < 0) {
            throw new IllegalArgumentException("OSRS tile coordinates cannot be negative");
        }
        if (regionId != ((regionX << 8) | regionY)
                || regionLocalX != (worldX & 63)
                || regionLocalY != (worldY & 63)
                || chunkLocalX != (worldX & 7)
                || chunkLocalY != (worldY & 7)) {
            throw new IllegalArgumentException("Inconsistent OSRS tile address");
        }
    }

    public static WorldTileAddress of(int worldX, int worldY, int plane) {
        if (worldX < 0 || worldY < 0 || plane < 0) {
            throw new IllegalArgumentException("OSRS tile coordinates cannot be negative");
        }
        int regionX = worldX >> 6;
        int regionY = worldY >> 6;
        return new WorldTileAddress(
                worldX,
                worldY,
                plane,
                (regionX << 8) | regionY,
                regionX,
                regionY,
                worldX & 63,
                worldY & 63,
                worldX >> 3,
                worldY >> 3,
                worldX & 7,
                worldY & 7
        );
    }
}
