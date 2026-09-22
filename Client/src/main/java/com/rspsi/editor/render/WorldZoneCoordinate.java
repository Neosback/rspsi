package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;

/** Canonical absolute 8x8 world-zone identity shared by CPU and GPU incremental paths. */
public record WorldZoneCoordinate(int plane, int zoneX, int zoneY)
        implements Comparable<WorldZoneCoordinate> {
    public WorldZoneCoordinate {
        if (plane < 0 || zoneX < 0 || zoneY < 0) {
            throw new IllegalArgumentException("World zone coordinates cannot be negative");
        }
    }

    public static WorldZoneCoordinate from(WorldTileAddress tile) {
        Objects.requireNonNull(tile, "tile");
        return new WorldZoneCoordinate(tile.plane(), tile.worldX() >> 3, tile.worldY() >> 3);
    }

    public boolean contains(WorldTileAddress tile) {
        Objects.requireNonNull(tile, "tile");
        return tile.plane() == plane && (tile.worldX() >> 3) == zoneX && (tile.worldY() >> 3) == zoneY;
    }

    public long key() {
        return ((long) plane << 32) | ((long) (zoneX & 0xFFFF) << 16) | (zoneY & 0xFFFFL);
    }

    @Override
    public int compareTo(WorldZoneCoordinate other) {
        int byPlane = Integer.compare(plane, other.plane);
        if (byPlane != 0) return byPlane;
        int byX = Integer.compare(zoneX, other.zoneX);
        return byX != 0 ? byX : Integer.compare(zoneY, other.zoneY);
    }
}
