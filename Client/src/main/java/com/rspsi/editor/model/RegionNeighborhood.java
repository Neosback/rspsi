package com.rspsi.editor.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loaded 3x3 OSRS region neighborhood centered on one editable region.
 *
 * <p>World-coordinate lookups never clamp or fabricate neighboring data.
 * Missing adjacent regions remain absent so blending, stitching and
 * diagnostics can distinguish an unloaded seam from authored terrain.</p>
 */
public final class RegionNeighborhood {
    private final int centerRegionX;
    private final int centerRegionY;
    private final Map<Integer, WorldRegion> regions;

    public RegionNeighborhood(WorldRegion center, Map<Integer, WorldRegion> loadedRegions) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(loadedRegions, "loadedRegions");
        this.centerRegionX = center.regionX();
        this.centerRegionY = center.regionY();

        Map<Integer, WorldRegion> copy = new LinkedHashMap<>();
        for (WorldRegion region : loadedRegions.values()) {
            if (Math.abs(region.regionX() - centerRegionX) > 1
                    || Math.abs(region.regionY() - centerRegionY) > 1) {
                continue;
            }
            copy.put(region.regionId(), region);
        }
        copy.put(center.regionId(), center);
        this.regions = Map.copyOf(copy);
    }

    public static RegionNeighborhood from(WorldRegionWindow window, int centerRegionX, int centerRegionY) {
        Objects.requireNonNull(window, "window");
        WorldRegion center = window.region(centerRegionX, centerRegionY)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Center region is not loaded: " + centerRegionX + "," + centerRegionY));
        return new RegionNeighborhood(center, window.regions());
    }

    public int centerRegionX() { return centerRegionX; }
    public int centerRegionY() { return centerRegionY; }

    public WorldRegion center() {
        return region(centerRegionX, centerRegionY).orElseThrow();
    }

    public Map<Integer, WorldRegion> regions() { return regions; }

    public Optional<WorldRegion> region(int regionX, int regionY) {
        return Optional.ofNullable(regions.get((regionX << 8) | regionY));
    }

    public Optional<WorldRegion> regionAtWorldTile(int worldX, int worldY) {
        if (worldX < 0 || worldY < 0) return Optional.empty();
        return region(worldX >> 6, worldY >> 6);
    }

    public Optional<TileSnapshot> tileAt(int plane, int worldX, int worldY) {
        return tileSource(plane, worldX, worldY).map(WorldTileSource::snapshot);
    }

    public Optional<WorldTileSource> tileSource(int plane, int worldX, int worldY) {
        if (plane < 0 || worldX < 0 || worldY < 0) return Optional.empty();
        WorldRegion region = regionAtWorldTile(worldX, worldY).orElse(null);
        if (region == null || plane >= region.document().planes()) return Optional.empty();
        Tile tile = region.document().tile(plane, worldX & 63, worldY & 63);
        return Optional.of(new WorldTileSource(tile.snapshot(), tile.heightSource()));
    }

    public Optional<Tile> mutableTileAt(int plane, int worldX, int worldY) {
        if (plane < 0 || worldX < 0 || worldY < 0) return Optional.empty();
        WorldRegion region = regionAtWorldTile(worldX, worldY).orElse(null);
        if (region == null || plane >= region.document().planes()) return Optional.empty();
        return Optional.of(region.document().tile(plane, worldX & 63, worldY & 63));
    }

    /**
     * Resolves authored-plane bridge demotion from the same world tile on
     * plane 1, matching WorldDocument.effectivePlane without region clamping.
     */
    public int effectivePlane(int authoredPlane, int worldX, int worldY) {
        if (authoredPlane < 0) throw new IllegalArgumentException("Authored plane cannot be negative");
        boolean bridged = tileAt(1, worldX, worldY)
                .map(TileSnapshot::flags)
                .map(OsrsTileFlags::hasBridge)
                .orElse(false);
        return bridged ? authoredPlane - 1 : authoredPlane;
    }

    public int centerOriginX() { return centerRegionX * WorldRegion.REGION_SIZE; }
    public int centerOriginY() { return centerRegionY * WorldRegion.REGION_SIZE; }

    public boolean isCenterWorldTile(int worldX, int worldY) {
        return worldX >= centerOriginX()
                && worldX < centerOriginX() + WorldRegion.REGION_SIZE
                && worldY >= centerOriginY()
                && worldY < centerOriginY() + WorldRegion.REGION_SIZE;
    }
}
