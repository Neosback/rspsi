package com.rspsi.cache.map;

import com.rspsi.cache.store.CacheStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable, cache-neutral map index. OSRS map archives are named {@code mX_Y}
 * and {@code lX_Y}; this type keeps that detail at the boundary instead of in
 * the world model.
 */
public final class MapIndexTable {
    public static final int DEFAULT_REGION_LIMIT = 256;

    private final Map<Integer, MapIndexEntry> entries = new LinkedHashMap<>();

    public static MapIndexTable discover(CacheStore store, int mapIndex) {
        Objects.requireNonNull(store, "store");
        MapIndexTable table = new MapIndexTable();
        for (int regionX = 0; regionX < DEFAULT_REGION_LIMIT; regionX++) {
            for (int regionY = 0; regionY < DEFAULT_REGION_LIMIT; regionY++) {
                String landscapeName = "m" + regionX + "_" + regionY;
                String objectName = "l" + regionX + "_" + regionY;
                int landscapeId = store.archiveId(mapIndex, landscapeName);
                int objectId = store.archiveId(mapIndex, objectName);
                if (landscapeId >= 0 || objectId >= 0) {
                    table.put(new MapIndexEntry(regionX, regionY, landscapeId, objectId,
                            landscapeName, objectName));
                }
            }
        }
        return table;
    }

    public static MapIndexTable of(Collection<MapIndexEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        MapIndexTable table = new MapIndexTable();
        entries.forEach(table::put);
        return table;
    }

    public void put(MapIndexEntry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.put(entry.key(), entry);
    }

    public MapIndexEntry region(int regionX, int regionY) {
        return entries.get(key(regionX, regionY));
    }

    public int archiveId(int regionX, int regionY, MapArchiveType type) {
        MapIndexEntry entry = region(regionX, regionY);
        if (entry == null) {
            return -1;
        }
        return type == MapArchiveType.LANDSCAPE
                ? entry.landscapeArchiveId()
                : entry.objectArchiveId();
    }

    public boolean present(int archiveId, MapArchiveType type) {
        return entries.values().stream().anyMatch(entry ->
                (type == MapArchiveType.LANDSCAPE
                        ? entry.landscapeArchiveId()
                        : entry.objectArchiveId()) == archiveId);
    }

    public List<MapIndexEntry> entries() {
        List<MapIndexEntry> snapshot = new ArrayList<>(entries.values());
        snapshot.sort(Comparator.comparingInt(MapIndexEntry::regionX)
                .thenComparingInt(MapIndexEntry::regionY));
        return List.copyOf(snapshot);
    }

    public int size() {
        return entries.size();
    }

    private static int key(int regionX, int regionY) {
        if (regionX < 0 || regionX > 255 || regionY < 0 || regionY > 255) {
            throw new IllegalArgumentException("OSRS region coordinates must be in [0, 255]");
        }
        return (regionX << 8) | regionY;
    }
}
