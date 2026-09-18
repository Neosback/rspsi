package com.rspsi.editor.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.List;

/**
 * A bounded OSRS world window made up of canonical 64x64 regions.
 *
 * <p>Missing regions are retained as absence rather than fabricated terrain;
 * scene builders can therefore distinguish a real empty region from a
 * loading-line hole.</p>
 */
public final class WorldRegionWindow {
    private final int minRegionX;
    private final int minRegionY;
    private final int regionWidth;
    private final int regionHeight;
    private final Map<Integer, WorldRegion> regions;

    public WorldRegionWindow(int minRegionX, int minRegionY, int regionWidth, int regionHeight,
                             Map<Integer, WorldRegion> regions) {
        if (minRegionX < 0 || minRegionY < 0 || minRegionX + regionWidth > 256
                || minRegionY + regionHeight > 256 || regionWidth <= 0 || regionHeight <= 0) {
            throw new IllegalArgumentException("Invalid OSRS region window");
        }
        this.minRegionX = minRegionX;
        this.minRegionY = minRegionY;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        Map<Integer, WorldRegion> copy = new LinkedHashMap<>();
        for (WorldRegion region : Objects.requireNonNull(regions, "regions").values()) {
            if (region.regionX() < minRegionX || region.regionX() >= minRegionX + regionWidth
                    || region.regionY() < minRegionY || region.regionY() >= minRegionY + regionHeight) {
                throw new IllegalArgumentException("Region lies outside the requested window: " + region.regionId());
            }
            if (copy.put(region.regionId(), region) != null) {
                throw new IllegalArgumentException("Duplicate region " + region.regionId());
            }
        }
        this.regions = Map.copyOf(copy);
    }

    public int minRegionX() { return minRegionX; }
    public int minRegionY() { return minRegionY; }
    public int regionWidth() { return regionWidth; }
    public int regionHeight() { return regionHeight; }
    public int expectedRegionCount() { return regionWidth * regionHeight; }
    public int loadedRegionCount() { return regions.size(); }
    public boolean complete() { return loadedRegionCount() == expectedRegionCount(); }
    public Map<Integer, WorldRegion> regions() { return regions; }

    /** Returns a deep neutral copy suitable for derived scene preparation. */
    public WorldRegionWindow copy() {
        Map<Integer, WorldRegion> copy = new LinkedHashMap<>();
        for (WorldRegion region : regions.values()) {
            copy.put(region.regionId(), new WorldRegion(region.regionX(), region.regionY(),
                    region.document().copy()));
        }
        return new WorldRegionWindow(minRegionX, minRegionY, regionWidth, regionHeight, copy);
    }

    public WorldWindow worldWindow() {
        return new WorldWindow(minRegionX * WorldRegion.REGION_SIZE,
                minRegionY * WorldRegion.REGION_SIZE,
                regionWidth * WorldRegion.REGION_SIZE,
                regionHeight * WorldRegion.REGION_SIZE);
    }

    /**
     * Materializes one world-addressed document for derived scene work.
     *
     * <p>Loaded regions are copied into their world-relative positions. A
     * missing region remains the document's neutral empty tile, which lets
     * callers retain a sparse window without fabricating cache data. This is
     * intentionally a derived document; it is never the editor's authored
     * source of truth.</p>
     */
    public WorldDocument materializeWorldDocument() {
        return materializePaddedWorldDocument(0);
    }

    /**
     * Materializes a derived document with an explicit neutral border around
     * the requested world window. Loaded neighboring regions are copied into
     * the border when they are part of this window; absent data remains an
     * empty tile instead of repeating the visible edge. This is used by
     * radius-five blending and height-normal derivation.
     */
    public WorldDocument materializePaddedWorldDocument(int border) {
        if (border < 0) throw new IllegalArgumentException("World context border cannot be negative");
        int planes = regions.values().stream()
                .mapToInt(region -> region.document().planes())
                .max()
                .orElse(WorldDocument.DEFAULT_PLANES);
        WorldWindow world = worldWindow();
        WorldDocument materialized = new WorldDocument(world.width() + border * 2,
                world.length() + border * 2, planes);
        for (WorldRegion region : regions.values()) {
            int offsetX = border + (region.regionX() - minRegionX) * WorldRegion.REGION_SIZE;
            int offsetY = border + (region.regionY() - minRegionY) * WorldRegion.REGION_SIZE;
            int copiedPlanes = Math.min(planes, region.document().planes());
            for (int plane = 0; plane < copiedPlanes; plane++) {
                for (int x = 0; x < WorldRegion.REGION_SIZE; x++) {
                    for (int y = 0; y < WorldRegion.REGION_SIZE; y++) {
                        Tile source = region.document().tile(plane, x, y);
                        Tile destination = materialized.tile(plane, offsetX + x, offsetY + y);
                        TileSnapshot snapshot = source.snapshot();
                        destination.restore(shiftObjects(snapshot, plane, offsetX, offsetY));
                        destination.heightSource(source.heightSource());
                    }
                }
            }
        }
        return materialized;
    }

    private static TileSnapshot shiftObjects(TileSnapshot source, int plane, int offsetX, int offsetY) {
        List<WorldObject> objects = new ArrayList<>(source.objects().size());
        for (WorldObject object : source.objects()) {
            objects.add(new WorldObject(object.id(), object.type(), object.rotation(), plane,
                    object.x() + offsetX, object.y() + offsetY));
        }
        return new TileSnapshot(source.southWestHeight(), source.southEastHeight(),
                source.northEastHeight(), source.northWestHeight(), source.underlayId(),
                source.overlayId(), source.overlayShape(), source.overlayRotation(),
                source.flags(), objects);
    }

    public Optional<WorldRegion> region(int regionX, int regionY) {
        return Optional.ofNullable(regions.get((regionX << 8) | regionY));
    }

    public boolean containsWorldTile(int worldX, int worldY) {
        WorldWindow window = worldWindow();
        return worldX >= window.originX() && worldX < window.originX() + window.width()
                && worldY >= window.originY() && worldY < window.originY() + window.length();
    }

    /** Resolves a loaded canonical tile without inventing data for missing regions. */
    public Optional<TileSnapshot> tile(int plane, int worldX, int worldY) {
        return tileSource(plane, worldX, worldY).map(WorldTileSource::snapshot);
    }

    /** Resolves a tile while retaining cache height-opcode provenance. */
    public Optional<WorldTileSource> tileSource(int plane, int worldX, int worldY) {
        if (plane < 0 || !containsWorldTile(worldX, worldY)) return Optional.empty();
        int regionX = worldX >> 6;
        int regionY = worldY >> 6;
        WorldRegion region = regions.get((regionX << 8) | regionY);
        if (region == null || plane >= region.document().planes()) return Optional.empty();
        Tile tile = region.document().tile(plane, worldX & 63, worldY & 63);
        return Optional.of(new WorldTileSource(tile.snapshot(), tile.heightSource()));
    }

    public Set<Integer> missingRegionIds() {
        Set<Integer> missing = new TreeSet<>();
        for (int x = minRegionX; x < minRegionX + regionWidth; x++) {
            for (int y = minRegionY; y < minRegionY + regionHeight; y++) {
                int id = (x << 8) | y;
                if (!regions.containsKey(id)) missing.add(id);
            }
        }
        return Set.copyOf(missing);
    }

    /**
     * Compares shared corner heights between adjacent loaded regions. Missing
     * regions are skipped because their boundary cannot be verified yet.
     */
    public List<RegionBoundaryMismatch> boundaryMismatches() {
        List<RegionBoundaryMismatch> mismatches = new ArrayList<>();
        for (WorldRegion region : regions.values()) {
            WorldRegion east = regions.get(((region.regionX() + 1) << 8) | region.regionY());
            if (east != null) {
                compareEast(region, east, mismatches);
            }
            WorldRegion north = regions.get((region.regionX() << 8) | (region.regionY() + 1));
            if (north != null) {
                compareNorth(region, north, mismatches);
            }
        }
        return List.copyOf(mismatches);
    }

    /**
     * Materializes shared border vertices from neighboring region origins.
     *
     * <p>A terrain archive stores the origin height for each tile; the final
     * row and column of a standalone 64x64 document are therefore provisional
     * until neighboring regions are present. Scene construction must call this
     * once a context window has loaded so shared geometry uses one vertex. The
     * return value is the number of tile snapshots updated.</p>
     */
    public int stitchSharedEdges() {
        int updates = 0;
        for (WorldRegion region : regions.values()) {
            WorldRegion east = regions.get(((region.regionX() + 1) << 8) | region.regionY());
            if (east != null) {
                WorldRegion northEast = regions.get(((region.regionX() + 1) << 8)
                        | (region.regionY() + 1));
                updates += stitchEast(region, east, northEast);
            }
            WorldRegion north = regions.get((region.regionX() << 8) | (region.regionY() + 1));
            if (north != null) {
                WorldRegion northEast = regions.get(((region.regionX() + 1) << 8)
                        | (region.regionY() + 1));
                updates += stitchNorth(region, north, northEast);
            }
        }
        return updates;
    }

    private static int stitchEast(WorldRegion west, WorldRegion east, WorldRegion northEast) {
        int updates = 0;
        for (int plane = 0; plane < Math.min(west.document().planes(), east.document().planes()); plane++) {
            for (int y = 0; y < WorldRegion.REGION_SIZE; y++) {
                TileSnapshot right = east.document().tile(plane, 0, y).snapshot();
                int northWestHeight = y < WorldRegion.REGION_SIZE - 1
                        ? east.document().tile(plane, 0, y + 1).snapshot().southWestHeight()
                        : northEast == null
                        ? right.northWestHeight()
                        : northEast.document().tile(plane, 0, 0).snapshot().southWestHeight();
                TileSnapshot left = west.document().tile(plane, 63, y).snapshot();
                TileSnapshot updated = new TileSnapshot(left.southWestHeight(),
                        right.southWestHeight(), northWestHeight, left.northWestHeight(),
                        left.underlayId(), left.overlayId(), left.overlayShape(),
                        left.overlayRotation(), left.flags(), left.objects());
                if (!updated.equals(left)) {
                    west.document().tile(plane, 63, y).restore(updated);
                    updates++;
                }
            }
        }
        return updates;
    }

    private static int stitchNorth(WorldRegion south, WorldRegion north, WorldRegion northEast) {
        int updates = 0;
        for (int plane = 0; plane < Math.min(south.document().planes(), north.document().planes()); plane++) {
            for (int x = 0; x < WorldRegion.REGION_SIZE; x++) {
                TileSnapshot upper = north.document().tile(plane, x, 0).snapshot();
                int northEastHeight = x < WorldRegion.REGION_SIZE - 1
                        ? north.document().tile(plane, x + 1, 0).snapshot().southWestHeight()
                        : northEast == null
                        ? upper.southEastHeight()
                        : northEast.document().tile(plane, 0, 0).snapshot().southWestHeight();
                TileSnapshot lower = south.document().tile(plane, x, 63).snapshot();
                TileSnapshot updated = new TileSnapshot(lower.southWestHeight(),
                        lower.southEastHeight(), northEastHeight, upper.southWestHeight(),
                        lower.underlayId(), lower.overlayId(), lower.overlayShape(),
                        lower.overlayRotation(), lower.flags(), lower.objects());
                if (!updated.equals(lower)) {
                    south.document().tile(plane, x, 63).restore(updated);
                    updates++;
                }
            }
        }
        return updates;
    }

    private static void compareEast(WorldRegion west, WorldRegion east,
                                    List<RegionBoundaryMismatch> mismatches) {
        for (int plane = 0; plane < Math.min(west.document().planes(), east.document().planes()); plane++) {
            for (int along = 0; along < WorldRegion.REGION_SIZE; along++) {
                TileSnapshot left = west.document().tile(plane, 63, along).snapshot();
                TileSnapshot right = east.document().tile(plane, 0, along).snapshot();
                compare(west, RegionBoundaryDirection.EAST, plane, along, false,
                        left.southEastHeight(), right.southWestHeight(), mismatches);
                compare(west, RegionBoundaryDirection.EAST, plane, along, true,
                        left.northEastHeight(), right.northWestHeight(), mismatches);
            }
        }
    }

    private static void compareNorth(WorldRegion south, WorldRegion north,
                                     List<RegionBoundaryMismatch> mismatches) {
        for (int plane = 0; plane < Math.min(south.document().planes(), north.document().planes()); plane++) {
            for (int along = 0; along < WorldRegion.REGION_SIZE; along++) {
                TileSnapshot lower = south.document().tile(plane, along, 63).snapshot();
                TileSnapshot upper = north.document().tile(plane, along, 0).snapshot();
                compare(south, RegionBoundaryDirection.NORTH, plane, along, false,
                        lower.northWestHeight(), upper.southWestHeight(), mismatches);
                compare(south, RegionBoundaryDirection.NORTH, plane, along, true,
                        lower.northEastHeight(), upper.southEastHeight(), mismatches);
            }
        }
    }

    private static void compare(WorldRegion origin, RegionBoundaryDirection direction, int plane,
                                int along, boolean upperCorner, int expected, int actual,
                                List<RegionBoundaryMismatch> mismatches) {
        if (expected != actual) {
            mismatches.add(new RegionBoundaryMismatch(direction, plane, origin.regionX(),
                    origin.regionY(), along, upperCorner, expected, actual));
        }
    }
}
