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
        MapIndexTable table = discoverNamed(store, mapIndex);
        if (table.size() > 0 && hasCompleteNamedEntries(table)) {
            return table;
        }
        MapIndexTable numeric = new MapIndexTable();
        discoverNumericGroups(store, mapIndex, numeric);
        return numeric.size() > table.size() ? numeric : table;
    }

    private static boolean hasCompleteNamedEntries(MapIndexTable table) {
        return table.entries.values().stream().anyMatch(entry ->
                entry.landscapeArchiveId() >= 0 && entry.objectArchiveId() >= 0);
    }

    private static MapIndexTable discoverNamed(CacheStore store, int mapIndex) {
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

    /**
     * Discovers using a known revision profile. The explicit profile makes
     * revision drift visible to callers while retaining the autodetecting
     * overload for existing adapters and tooling.
     */
    public static MapIndexTable discover(CacheStore store, int mapIndex, OsrsRevisionProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return profile.mapGroupLayout() == OsrsRevisionProfile.MapGroupLayout.NUMERIC
                ? discoverNumeric(store, mapIndex)
                : discoverNamed(store, mapIndex);
    }

    private static MapIndexTable discoverNumeric(CacheStore store, int mapIndex) {
        MapIndexTable table = new MapIndexTable();
        discoverNumericGroups(store, mapIndex, table);
        return table;
    }

    /**
     * Revision 237+ OpenRune packing stores maps as numeric groups whose ID is
     * the packed 8-bit region coordinate, with terrain in file 0 and
     * locations in file 1. Numeric discovery is also used when name-hash
     * probing yields only partial matches; this avoids mistaking a hash
     * collision in a modern index for a complete named map index.
     */
    private static void discoverNumericGroups(CacheStore store, int mapIndex, MapIndexTable table) {
        for (int archiveId : store.archiveIds(mapIndex)) {
            if (archiveId < 0 || archiveId > 0xFFFF) continue;
            int regionX = archiveId >>> 8;
            int regionY = archiveId & 0xFF;
            String landscapeName = "m" + regionX + "_" + regionY;
            String objectName = "l" + regionX + "_" + regionY;
            table.put(new MapIndexEntry(regionX, regionY, archiveId, archiveId,
                    landscapeName, objectName));
        }
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
