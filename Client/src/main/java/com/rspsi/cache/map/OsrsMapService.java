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
    public byte[] readObjects(int regionX, int regionY) {
        return read(regionX, regionY, MapArchiveType.OBJECT);
    }

    private byte[] read(int regionX, int regionY, MapArchiveType type) {
        int archiveId = index.archiveId(regionX, regionY, type);
        return archiveId < 0 ? null : store.read(mapIndex, archiveId, 0);
    }
}
