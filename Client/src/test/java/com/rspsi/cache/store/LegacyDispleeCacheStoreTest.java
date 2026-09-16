package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldRegion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Optional integration coverage for the writable Displee output adapter.
 *
 * <p>No cache is bundled with the repository. Set
 * {@code RSPSI_OSRS_WRITABLE_CACHE} to a disposable copied cache to run this
 * test. The test copies that cache again before editing it and verifies the
 * changed terrain survives a close/reopen cycle.</p>
 */
class LegacyDispleeCacheStoreTest {

    @Test
    void persistsModernTerrainThroughFlushAndReopenWhenCacheIsProvided() throws IOException {
        String configuredPath = System.getenv("RSPSI_OSRS_WRITABLE_CACHE");
        Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
                "set RSPSI_OSRS_WRITABLE_CACHE to run the external-cache integration test");

        Path source = Path.of(configuredPath);
        Assumptions.assumeTrue(Files.isDirectory(source), "configured cache path is not a directory");
        Path output = Files.createTempDirectory("rspsi-displee-output-");
        try {
            copyDirectory(source, output);
            int regionX = envInt("RSPSI_OSRS_REGION_X", 16);
            int regionY = envInt("RSPSI_OSRS_REGION_Y", 33);
            int revision = envInt("RSPSI_OSRS_REVISION", 240);
            int expected;

            try (CacheStore store = CacheStoreFactory.openRuneWithDispleeOutput(source, output)) {
                OsrsMapService maps = new OsrsMapService(store, revision);
                WorldRegion region = maps.loadRegion(regionX, regionY).orElse(null);
                Assumptions.assumeTrue(region != null, "configured cache does not contain the selected region");
                var tile = region.document().tile(0, 1, 1);
                TileSnapshot before = tile.snapshot();
                expected = before.underlayId() >= 255 ? 1 : before.underlayId() + 1;
                tile.restore(new TileSnapshot(before.southWestHeight(), before.southEastHeight(),
                        before.northEastHeight(), before.northWestHeight(), expected,
                        before.overlayId(), before.overlayShape(), before.overlayRotation(),
                        before.flags(), before.objects()));
                maps.writeLandscape(regionX, regionY,
                        com.rspsi.cache.map.OsrsRegionEncoder.encodeTerrain(
                                region.document(), maps.newTerrainFormat()));
                maps.flush();
            }

            CacheLibrary reopenedLibrary = new CacheLibrary(output.toString(), false, null);
            try (LegacyDispleeCacheStore store = new LegacyDispleeCacheStore(reopenedLibrary)) {
                OsrsMapService maps = new OsrsMapService(store, revision);
                WorldRegion reopened = maps.loadRegion(regionX, regionY).orElseThrow();
                assertEquals(expected, reopened.document().tile(0, 1, 1).snapshot().underlayId());
            }

            // The output is written by the staged Displee adapter, but it must
            // remain consumable by the production OpenRune reader before it is
            // accepted as an OSRS output cache.
            try (CacheStore openRune = CacheStoreFactory.openRune(output)) {
                OsrsMapService maps = new OsrsMapService(openRune, revision);
                WorldRegion reopened = maps.loadRegion(regionX, regionY).orElseThrow();
                assertEquals(expected, reopened.document().tile(0, 1, 1).snapshot().underlayId());
            }
        } finally {
            deleteDirectory(output);
        }
    }

    private static int envInt(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(path -> {
                try {
                    Path target = destination.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(target);
                    else Files.copy(path, target);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
