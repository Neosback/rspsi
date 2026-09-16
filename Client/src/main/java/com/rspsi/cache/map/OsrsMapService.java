package com.rspsi.cache.map;

import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;

import java.util.Objects;
import java.util.Optional;

/**
 * OSRS map service backed by the neutral cache store. The map index remains
 * discoverable and testable without importing OpenRune or Displee types.
 */
public final class OsrsMapService implements MapService {
    public static final int OSRS_MAP_INDEX = 5;

    private final CacheStore store;
    private final int mapIndex;
    private final MapIndexTable index;
    private final boolean newTerrainFormat;

    public OsrsMapService(CacheStore store) {
        this(store, OSRS_MAP_INDEX, MapIndexTable.discover(store, OSRS_MAP_INDEX), true);
    }

    /** Creates a map service using the layout selected by an OSRS revision. */
    public OsrsMapService(CacheStore store, int revision) {
        this(store, OsrsRevisionProfile.forRevision(revision));
    }

    private OsrsMapService(CacheStore store, OsrsRevisionProfile profile) {
        this(store, OSRS_MAP_INDEX, MapIndexTable.discover(store, OSRS_MAP_INDEX, profile),
                profile.newTerrainFormat());
    }

    /** Loads one canonical region; a missing location archive is treated as empty. */
    public Optional<WorldRegion> loadRegion(int regionX, int regionY) {
        byte[] landscape = readLandscape(regionX, regionY);
        if (landscape == null) return Optional.empty();
        byte[] locations = readLocations(regionX, regionY);
        return Optional.of(OsrsRegionDecoder.decodeRegion(landscape, locations, regionX, regionY,
                newTerrainFormat));
    }

    /** Loads a bounded region window while preserving missing-region holes. */
    public WorldRegionWindow loadWindow(int minRegionX, int minRegionY,
                                        int regionWidth, int regionHeight) {
        java.util.Map<Integer, WorldRegion> regions = new java.util.LinkedHashMap<>();
        for (int regionX = minRegionX; regionX < minRegionX + regionWidth; regionX++) {
            for (int regionY = minRegionY; regionY < minRegionY + regionHeight; regionY++) {
                loadRegion(regionX, regionY).ifPresent(region -> regions.put(region.regionId(), region));
            }
        }
        return new WorldRegionWindow(minRegionX, minRegionY, regionWidth, regionHeight, regions);
    }

    public OsrsMapService(CacheStore store, int mapIndex, MapIndexTable index) {
        this(store, mapIndex, index, true);
    }

    /** Creates a map service with an explicit terrain representation policy. */
    public OsrsMapService(CacheStore store, int mapIndex, MapIndexTable index, boolean newTerrainFormat) {
        this.store = Objects.requireNonNull(store, "store");
        this.mapIndex = mapIndex;
        this.index = Objects.requireNonNull(index, "index");
        this.newTerrainFormat = newTerrainFormat;
    }

    @Override
    public MapIndexTable index() {
        return index;
    }

    @Override
    public boolean newTerrainFormat() {
        return newTerrainFormat;
    }

    @Override
    public byte[] readLandscape(int regionX, int regionY) {
        return read(regionX, regionY, MapArchiveType.LANDSCAPE);
    }

    @Override
    public byte[] readLocations(int regionX, int regionY) {
        return read(regionX, regionY, MapArchiveType.OBJECT);
    }

    @Override
    public void writeLandscape(int regionX, int regionY, byte[] data) {
        write(regionX, regionY, MapArchiveType.LANDSCAPE, data);
    }

    @Override
    public void writeLocations(int regionX, int regionY, byte[] data) {
        write(regionX, regionY, MapArchiveType.OBJECT, data);
    }

    @Override
    public void flush() {
        store.flush();
    }

    private byte[] read(int regionX, int regionY, MapArchiveType type) {
        MapIndexEntry entry = index.region(regionX, regionY);
        if (entry == null) {
            return null;
        }
        int archiveId = index.archiveId(regionX, regionY, type);
        if (archiveId < 0) {
            return null;
        }
        int file = payloadFile(entry, type);
        return store.read(mapIndex, archiveId, file);
    }

    private void write(int regionX, int regionY, MapArchiveType type, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (!store.capabilities().writable()) {
            throw new UnsupportedOperationException("Cache backend is read-only");
        }
        MapIndexEntry entry = index.region(regionX, regionY);
        if (entry == null) {
            throw new IllegalArgumentException("Region is not present in the map index: " + regionX + "," + regionY);
        }
        int archiveId = index.archiveId(regionX, regionY, type);
        if (archiveId < 0) {
            throw new IllegalArgumentException("Region is not present in the map index: " + regionX + "," + regionY);
        }
        int file = payloadFile(entry, type);
        store.write(mapIndex, archiveId, file, data);
    }

    /**
     * Named map indexes keep terrain and locations in separate groups, each
     * with payload file 0. Modern packed indexes use one numeric group with
     * terrain in file 0 and locations in file 1. The index shape is enough to
     * distinguish these layouts without leaking a cache-library type into the
     * world model.
     */
    private static int payloadFile(MapIndexEntry entry, MapArchiveType type) {
        if (entry.landscapeArchiveId() != entry.objectArchiveId()) {
            return 0;
        }
        return type == MapArchiveType.LANDSCAPE ? 0 : 1;
    }
}
