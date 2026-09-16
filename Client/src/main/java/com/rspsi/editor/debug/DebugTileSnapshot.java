package com.rspsi.editor.debug;

import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileInspectorSnapshot;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;
import java.util.Optional;

/**
 * One tile's inspection payload for DevTools-style overlays and inspectors.
 * A missing collision snapshot means collision was not requested or the
 * supplied collision map does not cover this tile.
 */
public record DebugTileSnapshot(
        TileCoordinate coordinate,
        WorldTileAddress address,
        TileInspectorSnapshot terrain,
        Optional<CollisionTileSnapshot> collision,
        int effectivePlane
) {
    public DebugTileSnapshot {
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        address = Objects.requireNonNull(address, "address");
        terrain = Objects.requireNonNull(terrain, "terrain");
        collision = Objects.requireNonNull(collision, "collision");
        if (effectivePlane < 0) {
            throw new IllegalArgumentException("Effective plane cannot be negative");
        }
    }
}
