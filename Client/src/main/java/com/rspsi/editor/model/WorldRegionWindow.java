package com.rspsi.editor.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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

    public WorldWindow worldWindow() {
        return new WorldWindow(minRegionX * WorldRegion.REGION_SIZE,
                minRegionY * WorldRegion.REGION_SIZE,
                regionWidth * WorldRegion.REGION_SIZE,
                regionHeight * WorldRegion.REGION_SIZE);
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
        if (plane < 0 || !containsWorldTile(worldX, worldY)) return Optional.empty();
        int regionX = worldX >> 6;
        int regionY = worldY >> 6;
        WorldRegion region = regions.get((regionX << 8) | regionY);
        if (region == null || plane >= region.document().planes()) return Optional.empty();
        return Optional.of(region.document().tile(plane, worldX & 63, worldY & 63).snapshot());
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
}
