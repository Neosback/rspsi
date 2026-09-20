package com.rspsi.editor.render.compiler;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.osrs.rules.terrain.FloorBlendRules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic revision tracker for 8x8 OSRS scene zones.
 *
 * <p>Each invalidation writes the current global revision into every
 * overlapping zone. A compiler can remember one baseline revision and query
 * only zones that changed after that point without walking individual dirty
 * tiles again.</p>
 */
public final class ZoneRevisionTracker {
    public static final int ZONE_SIZE = 8;
    public static final int UNDERLAY_RADIUS = FloorBlendRules.UNDERLAY_BLEND_RADIUS;
    public static final int HEIGHT_RADIUS = 2;
    public static final int LOCAL_RADIUS = 1;

    private final int width;
    private final int length;
    private final int planes;
    private final AtomicLong clock = new AtomicLong();
    private final Map<InvalidationGraph.ZoneCoordinate, Long> revisions = new LinkedHashMap<>();

    public ZoneRevisionTracker(int width, int length, int planes) {
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Zone tracker dimensions must be positive");
        }
        this.width = width;
        this.length = length;
        this.planes = planes;
    }

    public synchronized long revision(int plane, int zoneX, int zoneY) {
        validateZone(plane, zoneX, zoneY);
        return revisions.getOrDefault(
                new InvalidationGraph.ZoneCoordinate(plane, zoneX, zoneY), 0L);
    }

    /** Returns the current global revision for use as a compiler baseline. */
    public long baseline() {
        return clock.get();
    }

    /**
     * Invalidates every zone overlapping a radius-expanded tile bounds and
     * returns the revision assigned to that mutation.
     */
    public synchronized long markTerrainDirty(int plane, int tileX, int tileY, int radius) {
        validateTile(plane, tileX, tileY);
        if (radius < 0) throw new IllegalArgumentException("Invalidation radius cannot be negative");

        long revision = clock.incrementAndGet();
        int minX = Math.max(0, tileX - radius);
        int maxX = Math.min(width - 1, tileX + radius);
        int minY = Math.max(0, tileY - radius);
        int maxY = Math.min(length - 1, tileY + radius);
        for (int zoneX = minX >> 3; zoneX <= maxX >> 3; zoneX++) {
            for (int zoneY = minY >> 3; zoneY <= maxY >> 3; zoneY++) {
                revisions.put(new InvalidationGraph.ZoneCoordinate(plane, zoneX, zoneY), revision);
            }
        }
        return revision;
    }

    public synchronized long markDirty(Set<TileCoordinate> tiles,
                                       InvalidationGraph.InvalidationCause cause) {
        Objects.requireNonNull(tiles, "tiles");
        Objects.requireNonNull(cause, "cause");
        if (tiles.isEmpty()) return baseline();
        int radius = radius(cause);
        long newest = baseline();
        for (TileCoordinate tile : tiles) {
            newest = markTerrainDirty(tile.plane(), tile.x(), tile.y(), radius);
        }
        return newest;
    }

    public synchronized Set<InvalidationGraph.ZoneCoordinate> dirtyZones(long baseline) {
        if (baseline < 0) throw new IllegalArgumentException("Baseline revision cannot be negative");
        Set<InvalidationGraph.ZoneCoordinate> result = new LinkedHashSet<>();
        revisions.forEach((zone, revision) -> {
            if (revision > baseline) result.add(zone);
        });
        return Set.copyOf(result);
    }

    public synchronized Set<InvalidationGraph.ZoneCoordinate> allZones() {
        Set<InvalidationGraph.ZoneCoordinate> result = new LinkedHashSet<>();
        int maxZoneX = (width - 1) >> 3;
        int maxZoneY = (length - 1) >> 3;
        for (int plane = 0; plane < planes; plane++) {
            for (int zoneX = 0; zoneX <= maxZoneX; zoneX++) {
                for (int zoneY = 0; zoneY <= maxZoneY; zoneY++) {
                    result.add(new InvalidationGraph.ZoneCoordinate(plane, zoneX, zoneY));
                }
            }
        }
        return Set.copyOf(result);
    }

    public static int radius(InvalidationGraph.InvalidationCause cause) {
        return switch (Objects.requireNonNull(cause, "cause")) {
            case UNDERLAY_EDIT -> UNDERLAY_RADIUS;
            case HEIGHT_EDIT -> HEIGHT_RADIUS;
            case OVERLAY_EDIT, OBJECT_EDIT -> LOCAL_RADIUS;
        };
    }

    private void validateTile(int plane, int tileX, int tileY) {
        if (plane < 0 || plane >= planes || tileX < 0 || tileX >= width
                || tileY < 0 || tileY >= length) {
            throw new IndexOutOfBoundsException(
                    "Tile outside zone tracker: " + plane + "," + tileX + "," + tileY);
        }
    }

    private void validateZone(int plane, int zoneX, int zoneY) {
        int maxZoneX = (width - 1) >> 3;
        int maxZoneY = (length - 1) >> 3;
        if (plane < 0 || plane >= planes || zoneX < 0 || zoneX > maxZoneX
                || zoneY < 0 || zoneY > maxZoneY) {
            throw new IndexOutOfBoundsException(
                    "Zone outside tracker: " + plane + "," + zoneX + "," + zoneY);
        }
    }
}
