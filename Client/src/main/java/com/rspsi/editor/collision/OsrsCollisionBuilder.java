package com.rspsi.editor.collision;

import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.Objects;

/** Builds the terrain/bridge portion of canonical OSRS collision. */
public final class OsrsCollisionBuilder {
    public static final int BLOCK_MAP_SQUARE = 0x1;
    public static final int LINK_BELOW = 0x2;
    public static final int REMOVE_ROOFS = 0x4;

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
        return collision;
    }

    /** Resolves the authored plane using the LINK_BELOW flag on plane 1. */
    public static int resolvedPlane(WorldDocument document, int authoredPlane, int x, int y) {
        Objects.requireNonNull(document, "document");
        if (authoredPlane < 0 || authoredPlane >= document.planes()) {
            throw new IndexOutOfBoundsException("Invalid authored plane: " + authoredPlane);
        }
        boolean bridged = document.planes() > BRIDGE_FLAG_PLANE
                && (document.tile(BRIDGE_FLAG_PLANE, x, y).snapshot().flags() & LINK_BELOW) != 0;
        return bridged ? authoredPlane - 1 : authoredPlane;
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

    private static void addObject(CollisionMap collision, WorldDocument document,
                                  WorldObject object, ObjectCollisionView definition) {
        int plane = resolvedPlane(document, object.plane(), object.x(), object.y());
        if (plane < 0 || plane >= document.planes() || definition.blockWalk() == 0) {
            return;
        }
        int width = definition.width();
        int length = definition.length();
        if (object.rotation() == 1 || object.rotation() == 3) {
            int swap = width;
            width = length;
            length = swap;
        }
        int shape = object.type();
        if (shape == 22) {
            if (definition.blockWalk() == 1) {
                addAt(collision, plane, object.x(), object.y(), CollisionFlag.GROUND_DECOR);
            }
            return;
        }
        int projectileMask = definition.blockProjectile() ? CollisionFlag.LOC_PROJECTILE : 0;
        if (shape == 10 || shape == 11 || shape >= 12 || shape == 9) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    addAt(collision, plane, object.x() + x, object.y() + y,
                            CollisionFlag.LOC | projectileMask);
                }
            }
        } else if (shape >= 0 && shape <= 3) {
            addWall(collision, plane, object.x(), object.y(), object.rotation(), shape,
                    projectileMask);
        }
    }

    private static void addWall(CollisionMap collision, int plane, int x, int y,
                                int rotation, int shape, int projectileMask) {
        if (shape == 0) {
            switch (rotation) {
                case 0 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_WEST, projectileMask,
                        x - 1, y, CollisionFlag.WALL_EAST);
                case 1 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_NORTH, projectileMask,
                        x, y + 1, CollisionFlag.WALL_SOUTH);
                case 2 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_EAST, projectileMask,
                        x + 1, y, CollisionFlag.WALL_WEST);
                default -> wallPart(collision, plane, x, y, CollisionFlag.WALL_SOUTH, projectileMask,
                        x, y - 1, CollisionFlag.WALL_NORTH);
            }
        } else if (shape == 1 || shape == 3) {
            switch (rotation) {
                case 0 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_NORTH_WEST, projectileMask,
                        x - 1, y + 1, CollisionFlag.WALL_SOUTH_EAST);
                case 1 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_NORTH_EAST, projectileMask,
                        x + 1, y + 1, CollisionFlag.WALL_SOUTH_WEST);
                case 2 -> wallPart(collision, plane, x, y, CollisionFlag.WALL_SOUTH_EAST, projectileMask,
                        x + 1, y - 1, CollisionFlag.WALL_NORTH_WEST);
                default -> wallPart(collision, plane, x, y, CollisionFlag.WALL_SOUTH_WEST, projectileMask,
                        x - 1, y - 1, CollisionFlag.WALL_NORTH_EAST);
            }
        } else {
            switch (rotation) {
                case 0 -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_WEST | CollisionFlag.WALL_NORTH, projectileMask);
                    addWallPart(collision, plane, x - 1, y, CollisionFlag.WALL_EAST, projectileMask);
                    addWallPart(collision, plane, x, y + 1, CollisionFlag.WALL_SOUTH, projectileMask);
                }
                case 1 -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_NORTH | CollisionFlag.WALL_EAST, projectileMask);
                    addWallPart(collision, plane, x, y + 1, CollisionFlag.WALL_SOUTH, projectileMask);
                    addWallPart(collision, plane, x + 1, y, CollisionFlag.WALL_WEST, projectileMask);
                }
                case 2 -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_EAST | CollisionFlag.WALL_SOUTH, projectileMask);
                    addWallPart(collision, plane, x + 1, y, CollisionFlag.WALL_WEST, projectileMask);
                    addWallPart(collision, plane, x, y - 1, CollisionFlag.WALL_NORTH, projectileMask);
                }
                default -> {
                    addWallPart(collision, plane, x, y,
                            CollisionFlag.WALL_SOUTH | CollisionFlag.WALL_WEST, projectileMask);
                    addWallPart(collision, plane, x, y - 1, CollisionFlag.WALL_NORTH, projectileMask);
                    addWallPart(collision, plane, x - 1, y, CollisionFlag.WALL_EAST, projectileMask);
                }
            }
        }
    }

    private static void wallPart(CollisionMap collision, int plane, int x, int y, int primary,
                                 int projectileMask, int neighbourX, int neighbourY, int neighbour) {
        addWallPart(collision, plane, x, y, primary, projectileMask);
        addWallPart(collision, plane, neighbourX, neighbourY, neighbour, projectileMask);
    }

    private static void addWallPart(CollisionMap collision, int plane, int x, int y,
                                    int movementMask, int projectileMask) {
        addAt(collision, plane, x, y, movementMask | projectileFor(movementMask, projectileMask));
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

    private static void addAt(CollisionMap collision, int plane, int x, int y, int mask) {
        if (x >= 0 && x < collision.width() && y >= 0 && y < collision.length()) {
            collision.add(new TileCoordinate(plane, x, y), mask);
        }
    }
}
