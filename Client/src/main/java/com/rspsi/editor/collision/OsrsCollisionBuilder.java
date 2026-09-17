package com.rspsi.editor.collision;

import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.OsrsTileFlags;

import java.util.Objects;

/** Builds the terrain/bridge portion of canonical OSRS collision. */
public final class OsrsCollisionBuilder {
    /** @deprecated use {@link OsrsTileFlags#BLOCK_MAP_SQUARE}. */
    @Deprecated public static final int BLOCK_MAP_SQUARE = OsrsTileFlags.BLOCK_MAP_SQUARE;
    /** @deprecated use {@link OsrsTileFlags#BRIDGE}. */
    @Deprecated public static final int LINK_BELOW = OsrsTileFlags.BRIDGE;
    /** @deprecated use {@link OsrsTileFlags#REMOVE_ROOFS}. */
    @Deprecated public static final int REMOVE_ROOFS = OsrsTileFlags.REMOVE_ROOFS;

    private static final int BRIDGE_FLAG_PLANE = 1;

    private OsrsCollisionBuilder() {
    }

    /**
     * Applies map-square blocking and roof flags, resolving the OSRS bridge
     * relationship before writing collision. Object collision is intentionally
     * a separate definition-backed step and is not guessed here.
     */
    public static CollisionMap fromTerrain(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        CollisionMap collision = new CollisionMap(document.width(), document.length(), document.planes());
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    int mask = terrainMask(tile.flags());
                    if (mask == 0) {
                        continue;
                    }
                    int resolvedPlane = resolvedPlane(document, plane, x, y);
                    if (resolvedPlane >= 0 && resolvedPlane < document.planes()) {
                        collision.add(new com.rspsi.editor.model.TileCoordinate(resolvedPlane, x, y), mask);
                    }
                }
            }
        }
        return collision;
    }

    /** Adds definition-backed location collision to terrain/bridge collision. */
    public static CollisionMap fromTerrainAndObjects(WorldDocument document, DefinitionProvider definitions) {
        Objects.requireNonNull(definitions, "definitions");
        CollisionMap collision = fromTerrain(document);
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    for (WorldObject object : document.tile(plane, x, y).snapshot().objects()) {
                        definitions.objectCollision(object.id())
                                .ifPresent(definition -> addObject(collision, document, object, definition));
                    }
                }
            }
        }
        applyBridgeBoundaryWalls(document, collision);
        return collision;
    }

    /**
     * Compatibility delegate for callers that still use the collision
     * builder as their bridge-plane lookup. New code should use
     * {@link WorldDocument#effectivePlane(int, int, int)} directly.
     */
    public static int resolvedPlane(WorldDocument document, int authoredPlane, int x, int y) {
        return Objects.requireNonNull(document, "document").effectivePlane(authoredPlane, x, y);
    }

    private static int terrainMask(int flags) {
        int mask = 0;
        if ((flags & BLOCK_MAP_SQUARE) != 0) {
            mask |= CollisionFlag.BLOCK_WALK;
        }
        if ((flags & REMOVE_ROOFS) != 0) {
            mask |= CollisionFlag.ROOF;
        }
        return mask;
    }

    /**
     * Relinks the wall boundary beside a bridge onto the effective lower
     * plane. This is the second bridge-collision pass used by the OSRS scene
     * builder: the bridge column is rendered on the lower plane, but an
     * adjacent non-bridge wall still has to block movement across that edge.
     *
     * <p>All three directional flag families are copied together so the
     * editor's movement, projectile, and routefinding views stay aligned.</p>
     */
    private static void applyBridgeBoundaryWalls(WorldDocument document, CollisionMap collision) {
        int wallWest = CollisionFlag.WALL_WEST
                | CollisionFlag.WALL_WEST_PROJECTILE
                | CollisionFlag.WALL_WEST_ROUTE_BLOCKER;
        int wallEast = CollisionFlag.WALL_EAST
                | CollisionFlag.WALL_EAST_PROJECTILE
                | CollisionFlag.WALL_EAST_ROUTE_BLOCKER;
        int wallSouth = CollisionFlag.WALL_SOUTH
                | CollisionFlag.WALL_SOUTH_PROJECTILE
                | CollisionFlag.WALL_SOUTH_ROUTE_BLOCKER;
        int wallNorth = CollisionFlag.WALL_NORTH
                | CollisionFlag.WALL_NORTH_PROJECTILE
                | CollisionFlag.WALL_NORTH_ROUTE_BLOCKER;

        if (document.planes() <= BRIDGE_FLAG_PLANE) {
            return;
        }
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                if (!OsrsTileFlags.hasBridge(document.tile(BRIDGE_FLAG_PLANE, x, y)
                        .snapshot().flags())) {
                    continue;
                }

                // A west neighbor exposes its east edge to the bridge tile.
                if (x > 0 && !hasBridge(document, x - 1, y)) {
                    copyDirectionalFlags(collision, 1, 0, x - 1, y, wallEast);
                }
                // An east neighbor exposes its west edge to the bridge tile.
                if (x + 1 < document.width() && !hasBridge(document, x + 1, y)) {
                    copyDirectionalFlags(collision, 1, 0, x + 1, y, wallWest);
                }
                // A south neighbor exposes its north edge to the bridge tile.
                if (y > 0 && !hasBridge(document, x, y - 1)) {
                    copyDirectionalFlags(collision, 1, 0, x, y - 1, wallNorth);
                }
                // A north neighbor exposes its south edge to the bridge tile.
                if (y + 1 < document.length() && !hasBridge(document, x, y + 1)) {
                    copyDirectionalFlags(collision, 1, 0, x, y + 1, wallSouth);
                }
            }
        }
    }

    private static boolean hasBridge(WorldDocument document, int x, int y) {
        return OsrsTileFlags.hasBridge(document.tile(BRIDGE_FLAG_PLANE, x, y)
                .snapshot().flags());
    }

    private static void copyDirectionalFlags(CollisionMap collision, int sourcePlane,
                                              int destinationPlane, int x, int y, int mask) {
        int copied = collision.flags(sourcePlane, x, y) & mask;
        if (copied != 0) {
            collision.add(new TileCoordinate(destinationPlane, x, y), copied);
        }
    }

    private static void addObject(CollisionMap collision, WorldDocument document,
                                  WorldObject object, ObjectCollisionView definition) {
        // Match the client/TSPS scene loader: id 0 is an empty location slot,
        // not a collision-bearing object.
        if (object.id() <= 0) {
            return;
        }
        int plane = resolvedPlane(document, object.plane(), object.x(), object.y());
        if (plane < 0 || plane >= document.planes() || definition.clipType() == 0
                || definition.blockWalk() == 0) {
            return;
        }
        int width = definition.width();
        int length = definition.length();
        if (object.rotation() == 1 || object.rotation() == 3) {
            int swap = width;
            width = length;
            length = swap;
        }
        OsrsLocShape shape = OsrsLocShape.fromId(object.type()).orElse(null);
        if (shape == null) {
            return;
        }
        if (shape.category() == ObjectCategory.GROUND_DECOR) {
            if (definition.blockWalk() == 1) {
                addAt(collision, plane, object.x(), object.y(), CollisionFlag.GROUND_DECOR);
            }
            return;
        }
        int projectileMask = definition.blockProjectile() ? CollisionFlag.LOC_PROJECTILE : 0;
        int routeMask = definition.breakRouteFinding() ? CollisionFlag.LOC_ROUTE_BLOCKER : 0;
        if (shape.category() == ObjectCategory.GROUND) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    addAt(collision, plane, object.x() + x, object.y() + y,
                            CollisionFlag.LOC | projectileMask | routeMask);
                }
            }
        } else if (shape.category() == ObjectCategory.WALL) {
            addWall(collision, plane, object.x(), object.y(), object.rotation(), shape.id(),
                    projectileMask, routeMask);
        }
    }

    private static void addWall(CollisionMap collision, int plane, int x, int y,
                                int rotation, int shape, int projectileMask, int routeMask) {
        if (shape == 0) {
            switch (rotation) {
                case 0 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_WEST, projectileMask,
                        routeMask, x - 1, y, CollisionFlag.WALL_EAST);
                case 1 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_NORTH, projectileMask,
                        routeMask, x, y + 1, CollisionFlag.WALL_SOUTH);
                case 2 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_EAST, projectileMask,
                        routeMask, x + 1, y, CollisionFlag.WALL_WEST);
                default -> wallPart(collision, plane, x, y, CollisionFlag.WALL_SOUTH, projectileMask,
                        routeMask, x, y - 1, CollisionFlag.WALL_NORTH);
            }
        } else if (shape == 1 || shape == 3) {
            switch (rotation) {
                case 0 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_NORTH_WEST, projectileMask,
                        routeMask, x - 1, y + 1, CollisionFlag.WALL_SOUTH_EAST);
                case 1 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_NORTH_EAST, projectileMask,
                        routeMask, x + 1, y + 1, CollisionFlag.WALL_SOUTH_WEST);
                case 2 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_SOUTH_EAST, projectileMask,
                        routeMask, x + 1, y - 1, CollisionFlag.WALL_NORTH_WEST);
                default -> wallPart(collision, plane, x, y, CollisionFlag.WALL_SOUTH_WEST, projectileMask,
                        routeMask, x - 1, y - 1, CollisionFlag.WALL_NORTH_EAST);
            }
        } else {
            switch (rotation) {
                case 0 -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_WEST | CollisionFlag.WALL_NORTH, projectileMask, routeMask);
                    addWallPart(collision, plane, x - 1, y, CollisionFlag.WALL_EAST, projectileMask, routeMask);
                    addWallPart(collision, plane, x, y + 1, CollisionFlag.WALL_SOUTH, projectileMask, routeMask);
                }
                case 1 -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_NORTH | CollisionFlag.WALL_EAST, projectileMask, routeMask);
                    addWallPart(collision, plane, x, y + 1, CollisionFlag.WALL_SOUTH, projectileMask, routeMask);
                    addWallPart(collision, plane, x + 1, y, CollisionFlag.WALL_WEST, projectileMask, routeMask);
                }
                case 2 -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_EAST | CollisionFlag.WALL_SOUTH, projectileMask, routeMask);
                    addWallPart(collision, plane, x + 1, y, CollisionFlag.WALL_WEST, projectileMask, routeMask);
                    addWallPart(collision, plane, x, y - 1, CollisionFlag.WALL_NORTH, projectileMask, routeMask);
                }
                default -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_SOUTH | CollisionFlag.WALL_WEST, projectileMask, routeMask);
                    addWallPart(collision, plane, x, y - 1, CollisionFlag.WALL_NORTH, projectileMask, routeMask);
                    addWallPart(collision, plane, x - 1, y, CollisionFlag.WALL_EAST, projectileMask, routeMask);
                }
            }
        }
    }

    private static void wallPart(CollisionMap collision, int plane, int x, int y, int primary,
                                 int projectileMask, int routeMask, int neighbourX, int neighbourY, int neighbour) {
        addWallPart(collision, plane, x, y, primary, projectileMask, routeMask);
        addWallPart(collision, plane, neighbourX, neighbourY, neighbour, projectileMask, routeMask);
    }

    private static void addWallPart(CollisionMap collision, int plane, int x, int y,
                                    int movementMask, int projectileMask, int routeMask) {
        addAt(collision, plane, x, y, movementMask
                | projectileFor(movementMask, projectileMask)
                | routeFor(movementMask, routeMask));
    }

    private static int projectileFor(int movementMask, int enabled) {
        if (enabled == 0) return 0;
        int result = 0;
        if ((movementMask & CollisionFlag.WALL_WEST) != 0) result |= CollisionFlag.WALL_WEST_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_NORTH) != 0) result |= CollisionFlag.WALL_NORTH_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_EAST) != 0) result |= CollisionFlag.WALL_EAST_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_SOUTH) != 0) result |= CollisionFlag.WALL_SOUTH_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_NORTH_WEST) != 0) result |= CollisionFlag.WALL_NORTH_WEST_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_NORTH_EAST) != 0) result |= CollisionFlag.WALL_NORTH_EAST_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_SOUTH_EAST) != 0) result |= CollisionFlag.WALL_SOUTH_EAST_PROJECTILE;
        if ((movementMask & CollisionFlag.WALL_SOUTH_WEST) != 0) result |= CollisionFlag.WALL_SOUTH_WEST_PROJECTILE;
        return result;
    }

    private static int routeFor(int movementMask, int enabled) {
        if (enabled == 0) return 0;
        int result = 0;
        if ((movementMask & CollisionFlag.WALL_WEST) != 0) result |= CollisionFlag.WALL_WEST_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_NORTH) != 0) result |= CollisionFlag.WALL_NORTH_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_EAST) != 0) result |= CollisionFlag.WALL_EAST_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_SOUTH) != 0) result |= CollisionFlag.WALL_SOUTH_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_NORTH_WEST) != 0) result |= CollisionFlag.WALL_NORTH_WEST_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_NORTH_EAST) != 0) result |= CollisionFlag.WALL_NORTH_EAST_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_SOUTH_EAST) != 0) result |= CollisionFlag.WALL_SOUTH_EAST_ROUTE_BLOCKER;
        if ((movementMask & CollisionFlag.WALL_SOUTH_WEST) != 0) result |= CollisionFlag.WALL_SOUTH_WEST_ROUTE_BLOCKER;
        return result;
    }

    private static void addAt(CollisionMap collision, int plane, int x, int y, int mask) {
        if (x >= 0 && x < collision.width() && y >= 0 && y < collision.length()) {
            collision.add(new TileCoordinate(plane, x, y), mask);
        }
    }
}
