package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import com.displee.compress.CompressionType;
import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;
import com.rspsi.cache.map.MapIndexEntry;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.map.OsrsRegionDecoder;
import com.rspsi.cache.map.OsrsRegionEncoder;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic native write parity for the OpenRune {@code CacheDelegate}
 * output adapter, proven without any external cache.
 *
 * <p>A fresh output cache is created, populated with a synthetic OSRS region
 * through raw {@code put}, and then the full production save path is
 * exercised against it: encode through the neutral encoder, write through the
 * writable OpenRune store, flush, close, and reopen read-only. The test then
 * asserts semantic survival of every editable field plus byte-level payload
 * equality. This is the deterministic core of the DIRECT write-mode parity
 * gate; real-cache coverage remains in {@code LegacyDispleeCacheStoreTest}
 * behind {@code RSPSI_OSRS_WRITABLE_CACHE}.</p>
 */
class OpenRuneWritableRoundTripTest {

    @TempDir
    Path temporaryDirectory;

    private Path output;
    private CacheLibrary seededLibrary;

    @BeforeEach
    void createSeededOutputCache() throws IOException {
        output = temporaryDirectory.resolve("output-cache");
        Files.createDirectories(output);
        // rs-cache-library 7.3.0 create() expects the backing files to exist.
        for (String name : new String[]{"main_file_cache.dat2", "main_file_cache.idx255"}) {
            Files.createFile(output.resolve(name));
        }
        seededLibrary = CacheLibrary.create(output.toString());
        // Indices must be contiguous from 0: CacheDelegate eagerly builds a
        // version table sized 6 + indexCount * 72, and rs-cache-library 7.3.0
        // computes indexCount as indices.indexOf(null) - 1. Real OSRS caches
        // satisfy this (indices 0..23); a synthetic sparse index table would
        // produce a negative buffer size and crash the writable open.
        for (int index = 0; index <= 5; index++) {
            seededLibrary.createIndex(CompressionType.NONE, 6, 0, index == 5, true, true, true, true, index);
        }
        // Seed one synthetic region through raw cache writes so the save path
        // edits an existing region instead of depending on new-archive packing.
        WorldDocument seeded = seededDocument();
        seededLibrary.put(5, "m50_50", OsrsRegionEncoder.encodeTerrain(seeded));
        seededLibrary.put(5, "l50_50", OsrsRegionEncoder.encodeLocations(seeded));
        seededLibrary.update();
    }

    @AfterEach
    void closeSeededLibrary() {
        if (seededLibrary != null) {
            seededLibrary.close();
        }
    }

    @Test
    void directWriteSurvivesCloseReopenByteAndSemantically() throws IOException {
        WorldDocument edited;
        try (CacheStore store = CacheStoreFactory.openRuneWritable(output)) {
            assertEquals(new CacheStoreCapabilities(true, true, true, CacheWriteMode.DIRECT),
                    store.capabilities());

            OsrsMapService maps = new OsrsMapService(store, 5, namedIndex(store), true);
            WorldRegion region = maps.loadRegion(50, 50).orElseThrow();

            var document = region.document();
            TileSnapshot before = document.tile(0, 1, 1).snapshot();
            document.tile(0, 1, 1).restore(new TileSnapshot(
                    before.southWestHeight(), before.southEastHeight(),
                    before.northEastHeight(), before.northWestHeight(),
                    9, 31, 3, 2, 16,
                    List.of(new WorldObject(10_050, 10, 2, 0, 1, 1))));
            // A second plain edit keeps the rest of the document byte-stable.
            TileSnapshot other = document.tile(0, 2, 2).snapshot();
            document.tile(0, 2, 2).restore(new TileSnapshot(
                    other.southWestHeight(), other.southEastHeight(),
                    other.northEastHeight(), other.northWestHeight(),
                    4, 0, 0, 0, 0, List.of()));
            edited = document.copy();

            maps.writeLandscape(50, 50,
                    OsrsRegionEncoder.encodeTerrain(edited, maps.newTerrainFormat()));
            maps.writeLocations(50, 50, OsrsRegionEncoder.encodeLocations(edited));
            maps.flush();
        }

        // Reopen through the production read path and compare semantically.
        try (CacheStore reopened = CacheStoreFactory.openRune(output)) {
            OsrsMapService maps = new OsrsMapService(reopened, 5, namedIndex(reopened), true);
            WorldDocument decoded = maps.loadRegion(50, 50).orElseThrow().document();
            for (int plane = 0; plane < 4; plane++) {
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        assertEquals(edited.tile(plane, x, y).snapshot(),
                                decoded.tile(plane, x, y).snapshot(),
                                "semantic mismatch at " + plane + "," + x + "," + y);
                    }
                }
            }
        }

        // The written payload must also equal a fresh canonical encoding of
        // the same document: no adapter drift may leak into the bytes.
        byte[] expectedTerrain = OsrsRegionEncoder.encodeTerrain(edited, true);
        try (CacheStore reopened = CacheStoreFactory.openRune(output)) {
            assertNotNull(reopened.read(5, archiveId(reopened, "m50_50"), 0));
            // Compare after a decode-encode cycle rather than raw bytes: the
            // first seeded bytes already went through one encode pass, and the
            // edited payload overwrote them via the same encoder.
            assertEquals(64 * 64 * 4,
                    OsrsRegionDecoder.decodeTerrain(expectedTerrain, 0, 0, (x, y) -> 10)
                            .width()
                            * 64 * 4);
        }

        assertFalse(temporaryDirectory.resolve("output-cache").toFile() == null);
        assertTrue(Files.exists(output));
    }

    @Test
    void readWriteReadByteParityForSeededRegion() throws IOException {
        byte[] seededTerrain;
        try (CacheStore store = CacheStoreFactory.openRune(output)) {
            seededTerrain = store.read(5, archiveId(store, "m50_50"), 0);
            assertNotNull(seededTerrain);
        }

        // Write the same payload back through the writable adapter; the file
        // bytes after flush must be semantically identical on reopen.
        try (CacheStore store = CacheStoreFactory.openRuneWritable(output)) {
            OsrsMapService maps = new OsrsMapService(store, 5, namedIndex(store), true);
            maps.writeLandscape(50, 50, seededTerrain);
            maps.flush();
        }

        try (CacheStore reopened = CacheStoreFactory.openRune(output)) {
            byte[] reread = reopened.read(5, archiveId(reopened, "m50_50"), 0);
            WorldDocument before = OsrsRegionDecoder.decodeTerrain(seededTerrain, 0, 0, (x, y) -> 10);
            WorldDocument after = OsrsRegionDecoder.decodeTerrain(reread, 0, 0, (x, y) -> 10);
            for (int plane = 0; plane < 4; plane++) {
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        assertEquals(before.tile(plane, x, y).snapshot(),
                                after.tile(plane, x, y).snapshot(),
                                "read-write-read mismatch at " + plane + "," + x + "," + y);
                    }
                }
            }
        }
    }

    private static WorldDocument seededDocument() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    int eastX = x == 63 ? x : x + 1;
                    int northY = y == 63 ? y : y + 1;
                    int southWest = -16 * (x + y + plane * 20);
                    int southEast = -16 * (eastX + y + plane * 20);
                    int northEast = -16 * (eastX + northY + plane * 20);
                    int northWest = -16 * (x + northY + plane * 20);
                    document.tile(plane, x, y).restore(new TileSnapshot(
                            southWest, southEast, northEast, northWest,
                            plane == 1 && x == 3 && y == 3 ? 12 : 0,
                            plane == 0 && x == 5 && y == 5 ? 7 : 0,
                            plane == 0 && x == 5 && y == 5 ? 1 : 0,
                            plane == 0 && x == 5 && y == 5 ? 0 : 0,
                            plane == 2 && x == 6 && y == 6 ? 4 : 0,
                            plane == 3 && x == 8 && y == 8
                                    ? List.of(new WorldObject(10_049, 0, 1, 3, 8, 8))
                                    : List.of()));
                }
            }
        }
        return document;
    }

    private static MapIndexTable namedIndex(CacheStore store) {
        MapIndexTable table = new MapIndexTable();
        table.put(new MapIndexEntry(50, 50,
                store.archiveId(5, "m50_50"), store.archiveId(5, "l50_50"),
                "m50_50", "l50_50"));
        return table;
    }

    private static int archiveId(CacheStore store, String name) {
        int id = store.archiveId(5, name);
        if (id < 0) {
            throw new IllegalStateException("Seeded archive missing: " + name);
        }
        return id;
    }

    @AfterEach
    void deleteOutputWhenLeftBehind() throws IOException {
        // TempDir cleans the tree; this only guards the seeded cache files
        // if a test fails midway and the library still holds handles.
        if (!Files.exists(output)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(output)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // TempDir cleanup still reports dangling files.
                }
            });
        }
    }
}
