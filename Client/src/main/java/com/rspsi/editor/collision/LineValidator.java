package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

/**
 * Neutral edge-aware line validation adapted from OpenRune-Server's
 * {@code LineValidator}. It intentionally accepts the editor's canonical
 * {@link CollisionMap} rather than server collision types.
 */
public final class LineValidator {
    private static final int SCALE = 16;
    private static final long HALF_TILE = 1L << (SCALE - 1);

    private static final int WALK_BLOCKED_NORTH = CollisionFlag.WALL_NORTH
            | CollisionFlag.LOC | CollisionFlag.GROUND_DECOR | CollisionFlag.BLOCK_WALK;
    private static final int WALK_BLOCKED_EAST = CollisionFlag.WALL_EAST
            | CollisionFlag.LOC | CollisionFlag.GROUND_DECOR | CollisionFlag.BLOCK_WALK;
    private static final int WALK_BLOCKED_SOUTH = CollisionFlag.WALL_SOUTH
            | CollisionFlag.LOC | CollisionFlag.GROUND_DECOR | CollisionFlag.BLOCK_WALK;
    private static final int WALK_BLOCKED_WEST = CollisionFlag.WALL_WEST
            | CollisionFlag.LOC | CollisionFlag.GROUND_DECOR | CollisionFlag.BLOCK_WALK;

    private static final int SIGHT_BLOCKED_NORTH = CollisionFlag.LOC_PROJECTILE
            | CollisionFlag.WALL_NORTH_PROJECTILE;
    private static final int SIGHT_BLOCKED_EAST = CollisionFlag.LOC_PROJECTILE
            | CollisionFlag.WALL_EAST_PROJECTILE;
    private static final int SIGHT_BLOCKED_SOUTH = CollisionFlag.LOC_PROJECTILE
            | CollisionFlag.WALL_SOUTH_PROJECTILE;
    private static final int SIGHT_BLOCKED_WEST = CollisionFlag.LOC_PROJECTILE
            | CollisionFlag.WALL_WEST_PROJECTILE;

    private LineValidator() {
    }

    public static boolean hasLineOfSight(CollisionMap map, TileCoordinate start,
                                         TileCoordinate target) {
        return hasLineOfSight(map, start, 1, 1, target, 1, 1);
    }

    /** Checks line of sight between rectangular source and target footprints. */
    public static boolean hasLineOfSight(CollisionMap map, TileCoordinate start,
                                         int sourceWidth, int sourceLength,
                                         TileCoordinate target, int targetWidth,
                                         int targetLength) {
        return rayCast(map, start, sourceWidth, sourceLength, target, targetWidth,
                targetLength, SIGHT_BLOCKED_WEST, SIGHT_BLOCKED_EAST,
                SIGHT_BLOCKED_SOUTH, SIGHT_BLOCKED_NORTH, true);
    }

    public static boolean hasLineOfWalk(CollisionMap map, TileCoordinate start,
                                        TileCoordinate target) {
        return hasLineOfWalk(map, start, 1, 1, target, 1, 1);
    }

    /** Checks an edge-aware walk line using OpenRune's line-of-walk masks. */
    public static boolean hasLineOfWalk(CollisionMap map, TileCoordinate start,
                                        int sourceWidth, int sourceLength,
                                        TileCoordinate target, int targetWidth,
                                        int targetLength) {
        return rayCast(map, start, sourceWidth, sourceLength, target, targetWidth,
                targetLength, WALK_BLOCKED_WEST, WALK_BLOCKED_EAST,
                WALK_BLOCKED_SOUTH, WALK_BLOCKED_NORTH, false);
    }

    private static boolean rayCast(CollisionMap map, TileCoordinate source,
                                   int sourceWidth, int sourceLength,
                                   TileCoordinate target, int targetWidth,
                                   int targetLength, int flagWest, int flagEast,
                                   int flagSouth, int flagNorth, boolean lineOfSight) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (sourceWidth <= 0 || sourceLength <= 0 || targetWidth <= 0 || targetLength <= 0) {
            throw new IllegalArgumentException("Line endpoints must have positive footprints");
        }
        if (source.plane() != target.plane()
                || !contains(map, source, sourceWidth, sourceLength)
                || !contains(map, target, targetWidth, targetLength)) {
            return false;
        }

        int startX = endpoint(source.x(), target.x(), sourceWidth);
        int startY = endpoint(source.y(), target.y(), sourceLength);
        int endX = endpoint(target.x(), source.x(), targetWidth);
        int endY = endpoint(target.y(), source.y(), targetLength);
        if (startX == endX && startY == endY) return true;

        int plane = source.plane();
        if (lineOfSight && flagged(map, plane, startX, startY, CollisionFlag.LOC)) {
            return false;
        }

        int deltaX = endX - startX;
        int deltaY = endY - startY;
        boolean east = deltaX >= 0;
        boolean north = deltaY >= 0;
        int xFlags = east ? flagWest : flagEast;
        int yFlags = north ? flagSouth : flagNorth;

        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            int offsetX = east ? 1 : -1;
            int offsetY = north ? 0 : -1;
            long scaledY = scaleUp(startY) + HALF_TILE + offsetY;
            long tangent = scaleUp(deltaY) / Math.abs((long) deltaX);
            int currentX = startX;
            while (currentX != endX) {
                currentX += offsetX;
                int currentY = scaleDown(scaledY);
                if (lineOfSight && currentX == endX && currentY == endY) {
                    xFlags &= ~CollisionFlag.LOC_PROJECTILE;
                }
                if (flagged(map, plane, currentX, currentY, xFlags)) return false;

                scaledY += tangent;
                int nextY = scaleDown(scaledY);
                if (lineOfSight && currentX == endX && nextY == endY) {
                    yFlags &= ~CollisionFlag.LOC_PROJECTILE;
                }
                if (nextY != currentY && flagged(map, plane, currentX, nextY, yFlags)) {
                    return false;
                }
            }
        } else {
            int offsetX = east ? 0 : -1;
            int offsetY = north ? 1 : -1;
            long scaledX = scaleUp(startX) + HALF_TILE + offsetX;
            long tangent = scaleUp(deltaX) / Math.abs((long) deltaY);
            int currentY = startY;
            while (currentY != endY) {
                currentY += offsetY;
                int currentX = scaleDown(scaledX);
                if (lineOfSight && currentX == endX && currentY == endY) {
                    yFlags &= ~CollisionFlag.LOC_PROJECTILE;
                }
                if (flagged(map, plane, currentX, currentY, yFlags)) return false;

                scaledX += tangent;
                int nextX = scaleDown(scaledX);
                if (lineOfSight && nextX == endX && currentY == endY) {
                    xFlags &= ~CollisionFlag.LOC_PROJECTILE;
                }
                if (nextX != currentX && flagged(map, plane, nextX, currentY, xFlags)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean contains(CollisionMap map, TileCoordinate origin,
                                    int width, int length) {
        return map.contains(origin)
                && map.contains(new TileCoordinate(origin.plane(), origin.x() + width - 1,
                origin.y() + length - 1));
    }

    private static boolean flagged(CollisionMap map, int plane, int x, int y, int mask) {
        return map.contains(new TileCoordinate(plane, x, y))
                && (map.flags(plane, x, y) & mask) != 0;
    }

    private static int endpoint(int first, int other, int size) {
        if (first >= other) return first;
        if (first + size - 1 <= other) return first + size - 1;
        return other;
    }

    private static long scaleUp(int tiles) {
        return ((long) tiles) << SCALE;
    }

    private static int scaleDown(long scaled) {
        return (int) (scaled >>> SCALE);
    }
}
