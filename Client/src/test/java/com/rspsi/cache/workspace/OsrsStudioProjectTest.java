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
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.project.ProjectLayout;
import com.rspsi.project.ProjectMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsStudioProjectTest {
    @TempDir
    Path temporaryDirectory;

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
            assertTrue(studio.openWindowAround(50, 50, 0).complete());
            assertTrue(studio.assets().search("").isEmpty());
            assertTrue(studio.capabilities().writable());
            assertTrue(studio.capabilities().mapPacking());
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
        assertThrows(IllegalStateException.class, () -> studio.openWindow(50, 50, 1, 1));
    }

    @Test
    void attachesAutosaveOnlyToMatchingProjectMetadata() throws Exception {
        OsrsCacheMetadata identity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(identity);
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"))), true);
        ProjectMetadata project = ProjectMetadata.forCache(identity);
        ProjectLayout layout = new ProjectLayout(temporaryDirectory.resolve("project"));
        layout.initialize(project);

        try (OsrsStudioProject studio = new OsrsStudioProject(store, maps,
                emptyDefinitions(), project)) {
            var opened = studio.openRegion(50, 50);
            try (var autosave = studio.attachAutosave(layout, opened.region().session())) {
                var document = opened.region().session().world();
                TileSnapshot before = document.tile(0, 0, 0).snapshot();
                TileSnapshot after = new TileSnapshot(8, 8, 8, 8, 1, 0,
                        0, 0, 0, List.of());
                opened.region().session().execute(new SetTileCommand(
                        new TileCoordinate(0, 0, 0), before, after, "autosave"));
                assertTrue(autosave.snapshotMatchesProject());
            }
        }

        ProjectMetadata mismatched = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, 2, "cache-b"));
        RecordingStore secondStore = new RecordingStore(identity);
        OsrsMapService secondMaps = new OsrsMapService(secondStore, 5,
                MapIndexTable.of(List.of(new MapIndexEntry(50, 50, 100, 100,
                        "m50_50", "l50_50"))), true);
        try (OsrsStudioProject studio = new OsrsStudioProject(secondStore, secondMaps,
                emptyDefinitions(), mismatched)) {
            assertThrows(java.io.IOException.class, () -> studio.attachAutosave(layout,
                    studio.openRegion(50, 50).region().session()));
        }
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
