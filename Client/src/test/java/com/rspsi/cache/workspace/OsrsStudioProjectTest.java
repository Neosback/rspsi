package com.rspsi.cache.workspace;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.map.MapIndexEntry;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.map.OsrsRegionEncoder;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.project.ProjectMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsStudioProjectTest {
    @Test
    void composesNeutralMapSessionDefinitionsAndAssetServices() {
        OsrsCacheMetadata identity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(identity);
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"))), true);
        ProjectMetadata project = ProjectMetadata.forCache(identity);

        try (OsrsStudioProject studio = new OsrsStudioProject(store, maps,
                emptyDefinitions(), project)) {
            var opened = studio.openRegion(50, 50);
            assertFalse(opened.readOnly());
            assertTrue(opened.region().session().canSave());
            assertTrue(studio.assets().search("").isEmpty());
            assertTrue(studio.capabilities().writable());
        }

        assertTrue(store.closed);
    }

    @Test
    void closedProjectRejectsNewRegionSessions() {
        OsrsCacheMetadata identity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(identity);
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"))), true);
        OsrsStudioProject studio = new OsrsStudioProject(store, maps,
                emptyDefinitions(), ProjectMetadata.forCache(identity));

        studio.close();
        assertThrows(IllegalStateException.class, () -> studio.openRegion(50, 50));
    }

    private static DefinitionProvider emptyDefinitions() {
        return new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }

    private static final class RecordingStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        private final OsrsCacheMetadata identity;
        private boolean closed;

        private RecordingStore(OsrsCacheMetadata identity) {
            this.identity = identity;
            WorldDocument source = new WorldDocument(64, 64, 4);
            values.put("5:100:0", OsrsRegionEncoder.encodeTerrain(source, true));
            values.put("5:100:1", OsrsRegionEncoder.encodeLocations(source));
        }

        @Override public byte[] read(int index, int archive, int file) {
            return values.get(index + ":" + archive + ":" + file);
        }
        @Override public void write(int index, int archive, int file, byte[] data) {
            values.put(index + ":" + archive + ":" + file, data.clone());
        }
        @Override public void flush() { }
        @Override public void close() { closed = true; }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(true, true, true);
        }
        @Override public Optional<OsrsCacheMetadata> metadata(int revision) {
            return Optional.of(identity);
        }
    }
}
