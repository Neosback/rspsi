package com.rspsi.cache.map;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsSessionLoaderTest {
    @Test
    void createsCleanSaveCapableSessionAndPersistsThroughCoordinator() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        byte[] terrain = OsrsRegionEncoder.encodeTerrain(source, true);
        byte[] locations = OsrsRegionEncoder.encodeLocations(source);
        RecordingStore store = new RecordingStore(terrain, locations);
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"))));

        OsrsSessionLoader.LoadedRegion loaded = new OsrsSessionLoader(maps).load(50, 50);
        EditorSession session = loaded.session();
        assertTrue(session.canSave());
        assertFalse(session.isDirty());

        WorldDocument document = session.world();
        TileSnapshot before = document.tile(0, 1, 1).snapshot();
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1), before,
                new TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, List.of()), "paint"));
        assertTrue(session.isDirty());

        session.save();

        assertFalse(session.isDirty());
        assertTrue(store.values.containsKey("5:100:0"));
        assertTrue(store.values.containsKey("5:100:1"));
        assertTrue(store.flushes > 0);
    }

    @Test
    void loadsCanonicalRegionSessionsAndBatchSavesDirtyRegionsOnce() {
        WorldDocument westSource = new WorldDocument(64, 64, 4);
        WorldDocument eastSource = new WorldDocument(64, 64, 4);
        RecordingStore store = new RecordingStore(Map.of(
                "5:100:0", OsrsRegionEncoder.encodeTerrain(westSource, true),
                "5:100:1", OsrsRegionEncoder.encodeLocations(westSource),
                "5:101:0", OsrsRegionEncoder.encodeTerrain(eastSource, true),
                "5:101:1", OsrsRegionEncoder.encodeLocations(eastSource)));
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"),
                new MapIndexEntry(51, 50, 101, 101, "m51_50", "l51_50"))));

        OsrsSessionLoader.LoadedWindow loaded =
                new OsrsSessionLoader(maps).loadWindow(50, 50, 2, 1);
        WorldRegionSessionWindow sessions = loaded.sessions();

        assertTrue(loaded.window().complete());
        assertEquals(2, sessions.sessions().size());
        assertTrue(sessions.canSave());

        var west = sessions.resolve(
                new WorldTile(0, 50 * 64 + 63, 50 * 64 + 10)).orElseThrow();
        var east = sessions.resolve(
                new WorldTile(0, 51 * 64, 50 * 64 + 10)).orElseThrow();

        paint(west.session(), west.localTile().x(), west.localTile().y(), 7);
        paint(east.session(), east.localTile().x(), east.localTile().y(), 8);

        assertEquals(java.util.Set.of((50 << 8) | 50, (51 << 8) | 50),
                sessions.dirtyRegionIds());

        sessions.save();

        assertEquals(1, store.flushes);
        assertFalse(sessions.isDirty());

        WorldDocument savedWest = OsrsRegionDecoder.decodeRegion(
                store.values.get("5:100:0"), store.values.get("5:100:1"),
                50, 50, true).document();
        WorldDocument savedEast = OsrsRegionDecoder.decodeRegion(
                store.values.get("5:101:0"), store.values.get("5:101:1"),
                51, 50, true).document();
        assertEquals(7, savedWest.tile(0, 63, 10).snapshot().underlayId());
        assertEquals(8, savedEast.tile(0, 0, 10).snapshot().underlayId());
    }

    @Test
    void readOnlyWindowKeepsEveryLoadedRegionInspectOnly() {
        WorldDocument westSource = new WorldDocument(64, 64, 4);
        WorldDocument eastSource = new WorldDocument(64, 64, 4);
        RecordingStore store = new RecordingStore(Map.of(
                "5:100:0", OsrsRegionEncoder.encodeTerrain(westSource, true),
                "5:100:1", OsrsRegionEncoder.encodeLocations(westSource),
                "5:101:0", OsrsRegionEncoder.encodeTerrain(eastSource, true),
                "5:101:1", OsrsRegionEncoder.encodeLocations(eastSource)));
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"),
                new MapIndexEntry(51, 50, 101, 101, "m51_50", "l51_50"))));

        OsrsSessionLoader.LoadedWindow loaded =
                new OsrsSessionLoader(maps).loadWindowReadOnly(50, 50, 2, 1);

        assertFalse(loaded.sessions().canSave());
        assertTrue(loaded.sessions().sessions().values().stream()
                .noneMatch(EditorSession::canEdit));
    }

    private static void paint(EditorSession session, int x, int y, int underlay) {
        TileCoordinate coordinate = new TileCoordinate(0, x, y);
        TileSnapshot before = session.world().tile(coordinate).snapshot();
        TileSnapshot after = new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                underlay, before.overlayId(), before.overlayShape(),
                before.overlayRotation(), before.flags(), before.objects());
        session.execute(new SetTileCommand(coordinate, before, after, "paint"));
    }

    private static final class RecordingStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        private int flushes;

        private RecordingStore(byte[] terrain, byte[] locations) {
            this(Map.of("5:100:0", terrain, "5:100:1", locations));
        }

        private RecordingStore(Map<String, byte[]> initialValues) {
            initialValues.forEach((key, value) -> values.put(key, value.clone()));
        }

        @Override public byte[] read(int index, int archive, int file) {
            return values.get(index + ":" + archive + ":" + file);
        }

        @Override public void write(int index, int archive, int file, byte[] data) {
            values.put(index + ":" + archive + ":" + file, data.clone());
        }

        @Override public void flush() {
            flushes++;
        }

        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(true, true, true);
        }
    }
}
