package com.rspsi.editor.render.compiler;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.osrs.rules.terrain.FloorBlendRules;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Maps authored tile, terrain, and object modifications to affected 8x8 scene zones.
 *
 * <p>Accurately models neighborhood spillover rules (such as the 5-tile underlay blend radius
 * and 1-tile terrain lighting normal calculations) so only genuinely dirty zones are recompiled.</p>
 */
public final class InvalidationGraph {

    public record ZoneCoordinate(int plane, int zoneX, int zoneY) {}

    public enum InvalidationCause {
        HEIGHT_EDIT,
        UNDERLAY_EDIT,
        OVERLAY_EDIT,
        OBJECT_EDIT
    }

    private InvalidationGraph() {}

    /** Maps a tile coordinate to its containing 8x8 zone coordinate. */
    public static ZoneCoordinate toZone(TileCoordinate tile) {
        Objects.requireNonNull(tile, "tile");
        return new ZoneCoordinate(tile.plane(), tile.x() >> 3, tile.y() >> 3);
    }

    /**
     * Determines all 8x8 zones invalidated by a set of modified tiles and the cause of mutation.
     */
    public static Set<ZoneCoordinate> computeInvalidatedZones(
            Set<TileCoordinate> dirtyTiles,
            InvalidationCause cause,
            int worldWidth,
            int worldLength
    ) {
        Objects.requireNonNull(dirtyTiles, "dirtyTiles");
        if (dirtyTiles.isEmpty()) return Set.of();

        int radius = switch (cause) {
            case UNDERLAY_EDIT -> FloorBlendRules.UNDERLAY_BLEND_RADIUS; // 5 tiles
            case HEIGHT_EDIT -> 2; // Lighting normal + contour radius
            case OVERLAY_EDIT, OBJECT_EDIT -> 1;
        };

        Set<ZoneCoordinate> invalidated = new HashSet<>();
        int maxZoneX = (worldWidth - 1) >> 3;
        int maxZoneY = (worldLength - 1) >> 3;

        for (TileCoordinate tile : dirtyTiles) {
            int minX = Math.max(0, tile.x() - radius);
            int maxX = Math.min(worldWidth - 1, tile.x() + radius);
            int minY = Math.max(0, tile.y() - radius);
            int maxY = Math.min(worldLength - 1, tile.y() + radius);

            int startZoneX = Math.max(0, minX >> 3);
            int endZoneX = Math.min(maxZoneX, maxX >> 3);
            int startZoneY = Math.max(0, minY >> 3);
            int endZoneY = Math.min(maxZoneY, maxY >> 3);

            for (int zx = startZoneX; zx <= endZoneX; zx++) {
                for (int zy = startZoneY; zy <= endZoneY; zy++) {
                    invalidated.add(new ZoneCoordinate(tile.plane(), zx, zy));
                }
            }
        }

        return Collections.unmodifiableSet(invalidated);
    }
}
