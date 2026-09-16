package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** UI-neutral decoded view of one collision tile for inspectors and overlays. */
public record CollisionTileSnapshot(
        TileCoordinate coordinate,
        int rawFlags,
        Set<CollisionDirection> movementBlocked,
        Set<CollisionDirection> projectileBlocked,
        boolean floorBlocked,
        boolean objectBlocked,
        boolean roof
) {
    public CollisionTileSnapshot {
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        movementBlocked = immutableDirections(movementBlocked);
        projectileBlocked = immutableDirections(projectileBlocked);
    }

    public static CollisionTileSnapshot from(CollisionMap map, TileCoordinate coordinate) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(coordinate, "coordinate");
        int flags = map.flags(coordinate);
        EnumSet<CollisionDirection> movement = EnumSet.noneOf(CollisionDirection.class);
        EnumSet<CollisionDirection> projectile = EnumSet.noneOf(CollisionDirection.class);
        for (CollisionDirection direction : CollisionDirection.values()) {
            if ((flags & direction.movementMask()) != 0) movement.add(direction);
            if ((flags & direction.projectileMask()) != 0) projectile.add(direction);
        }
        return new CollisionTileSnapshot(coordinate, flags, movement, projectile,
                (flags & (CollisionFlag.BLOCK_WALK | CollisionFlag.GROUND_DECOR)) != 0,
                (flags & CollisionFlag.LOC) != 0,
                (flags & CollisionFlag.ROOF) != 0);
    }

    private static Set<CollisionDirection> immutableDirections(Set<CollisionDirection> directions) {
        return Set.copyOf(directions == null ? Set.of() : directions);
    }
}
