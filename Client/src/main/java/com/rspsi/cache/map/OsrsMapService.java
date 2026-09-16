package com.rspsi.cache.map;

import com.rspsi.cache.store.CacheStore;

import java.util.Objects;

/**
 * OSRS map service backed by the neutral cache store. The map index remains
 * discoverable and testable without importing OpenRune or Displee types.
 */
public final class OsrsMapService implements MapService {
    public static final int OSRS_MAP_INDEX = 5;

    private final CacheStore store;
    private final int mapIndex;
    private final MapIndexTable index;

    public OsrsMapService(CacheStore store) {
        this(store, OSRS_MAP_INDEX, MapIndexTable.discover(store, OSRS_MAP_INDEX));
    }

    public OsrsMapService(CacheStore store, int mapIndex, MapIndexTable index) {
        this.store = Objects.requireNonNull(store, "store");
        this.mapIndex = mapIndex;
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public MapIndexTable index() {
        return index;
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
        int archiveId = index.archiveId(regionX, regionY, type);
        if (archiveId < 0) {
            return null;
        }
        // OSRS map archives use file 0 for terrain and file 1 for locations.
        // Keeping this distinction here prevents callers from having to know
        // the cache-file layout.
        int file = type == MapArchiveType.LANDSCAPE ? 0 : 1;
        return store.read(mapIndex, archiveId, file);
    }

    private void write(int regionX, int regionY, MapArchiveType type, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (!store.capabilities().writable()) {
            throw new UnsupportedOperationException("Cache backend is read-only");
        }
        int archiveId = index.archiveId(regionX, regionY, type);
        if (archiveId < 0) {
            throw new IllegalArgumentException("Region is not present in the map index: " + regionX + "," + regionY);
        }
        int file = type == MapArchiveType.LANDSCAPE ? 0 : 1;
        store.write(mapIndex, archiveId, file, data);
    }
}
