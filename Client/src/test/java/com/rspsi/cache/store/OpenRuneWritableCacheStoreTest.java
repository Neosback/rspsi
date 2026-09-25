package com.rspsi.cache.store;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.workspace.OsrsStudioProject;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.project.ProjectMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Optional integration coverage for the canonical writable OpenRune FileStore output path.
 *
 * <p>No cache is bundled with the repository. Set {@code RSPSI_OSRS_WRITABLE_CACHE}
 * to a disposable copied cache to run these persistence/parity checks.</p>
 */
class OpenRuneWritableCacheStoreTest {

    @Test
    void persistsModernTerrainThroughOpenRuneWritableAdapterWhenCacheIsProvided() throws IOException {
        String configuredPath = System.getenv("RSPSI_OSRS_WRITABLE_CACHE");
        Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
                "set RSPSI_OSRS_WRITABLE_CACHE to run the external-cache integration test");

        Path source = Path.of(configuredPath);
        Assumptions.assumeTrue(Files.isDirectory(source), "configured cache path is not a directory");
        Path output = Files.createTempDirectory("rspsi-openrune-writable-output-");
        try {
            copyDirectory(source, output);
            int regionX = envInt("RSPSI_OSRS_REGION_X", 16);
            int regionY = envInt("RSPSI_OSRS_REGION_Y", 33);
            int revision = envInt("RSPSI_OSRS_REVISION", 240);
            int expected;

            try (CacheStore store = CacheStoreFactory.openRuneWritable(output)) {
                assertEquals(new CacheStoreCapabilities(true, true, true, CacheWriteMode.DIRECT),
                        store.capabilities());
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

            try (CacheStore reopenedStore = CacheStoreFactory.openRune(output)) {
                OsrsMapService maps = new OsrsMapService(reopenedStore, revision);
                WorldRegion reopened = maps.loadRegion(regionX, regionY).orElseThrow();
                assertEquals(expected, reopened.document().tile(0, 1, 1).snapshot().underlayId());
            }
        } finally {
            deleteDirectory(output);
        }
    }

    @Test
    void projectCompositionExecutesAndReopensCommandThroughOpenRuneOutput() throws IOException {
        String configuredPath = System.getenv("RSPSI_OSRS_WRITABLE_CACHE");
        Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
                "set RSPSI_OSRS_WRITABLE_CACHE to run the external-cache integration test");

        Path source = Path.of(configuredPath);
        Assumptions.assumeTrue(Files.isDirectory(source), "configured cache path is not a directory");
        Path output = Files.createTempDirectory("rspsi-openrune-project-output-");
        try {
            copyDirectory(source, output);
            int regionX = envInt("RSPSI_OSRS_REGION_X", 16);
            int regionY = envInt("RSPSI_OSRS_REGION_Y", 33);
            int revision = envInt("RSPSI_OSRS_REVISION", 240);
            ProjectMetadata project;
            try (OpenRuneCacheStore sourceStore = OpenRuneCacheStore.open(source)) {
                project = ProjectMetadata.forCache(sourceStore.metadata(revision).orElseThrow());
            }

            int expected;
            WorldObject rotatedObject;
            TileSnapshot expectedSemanticTile;
            int expectedVertexHeight;
            try (OsrsStudioProject studio = OsrsStudioProject.openWithOpenRuneOutput(
                    source, output, project)) {
                var opened = studio.openRegion(regionX, regionY);
                var session = opened.region().session();
                var tile = session.world().tile(0, 1, 1);
                TileSnapshot before = tile.snapshot();
                expected = before.underlayId() >= 255 ? 1 : before.underlayId() + 1;
                TileSnapshot after = new TileSnapshot(before.southWestHeight(), before.southEastHeight(),
                        before.northEastHeight(), before.northWestHeight(), expected,
                        before.overlayId(), before.overlayShape(), before.overlayRotation(),
                        before.flags(), before.objects());
                session.execute(new SetTileCommand(
                        tile.coordinate(), before, after, "integration underlay edit"));
                WorldObject firstObject = firstObject(session.world());
                rotatedObject = firstObject == null ? null
                        : new WorldObject(firstObject.id(), firstObject.type(),
                        (firstObject.rotation() + 1) & 3, firstObject.plane(),
                        firstObject.x(), firstObject.y());
                if (firstObject != null) {
                    session.execute(new RotateObjectCommand(firstObject,
                            rotatedObject.rotation(), "integration object rotation"));
                }

                TileCoordinate semanticCoordinate = new TileCoordinate(0, 2, 2);
                TileSnapshot semanticBefore = session.world().tile(semanticCoordinate).snapshot();
                int overlayId = semanticBefore.overlayId() == 0 ? 1 : semanticBefore.overlayId();
                int overlayShape = semanticBefore.overlayId() == 0
                        ? 2 : (semanticBefore.overlayShape() + 1) % 12;
                int overlayRotation = (semanticBefore.overlayRotation() + 1) & 3;
                int flags = (semanticBefore.flags() & 31) | 1;
                TileSnapshot semanticAfter = new TileSnapshot(
                        semanticBefore.southWestHeight(), semanticBefore.southEastHeight(),
                        semanticBefore.northEastHeight(), semanticBefore.northWestHeight(),
                        semanticBefore.underlayId(), overlayId, overlayShape, overlayRotation,
                        flags, semanticBefore.objects());
                session.execute(new SetTileCommand(semanticCoordinate, semanticBefore,
                        semanticAfter, "integration terrain semantics"));
                expectedSemanticTile = semanticAfter;

                int vertexX = 8;
                int vertexY = 8;
                int originalVertexHeight = session.world().tile(0, vertexX, vertexY)
                        .snapshot().southWestHeight();
                int heightDelta = originalVertexHeight <= -8 ? 8 : -8;
                expectedVertexHeight = originalVertexHeight + heightDelta;
                session.execute(new CompositeEditCommand("integration shared height edit", List.of(
                        adjustCorner(session, vertexX, vertexY, 0, heightDelta),
                        adjustCorner(session, vertexX - 1, vertexY, 1, heightDelta),
                        adjustCorner(session, vertexX - 1, vertexY - 1, 2, heightDelta),
                        adjustCorner(session, vertexX, vertexY - 1, 3, heightDelta))));
                session.save();
                assertEquals(expected, tile.snapshot().underlayId());
                assertEquals(expectedSemanticTile,
                        session.world().tile(semanticCoordinate).snapshot());
                assertEquals(expectedVertexHeight,
                        session.world().tile(0, vertexX, vertexY).snapshot().southWestHeight());
                assertEquals(session.history().position(), session.savedHistoryPosition());
            }

            // The output cache's content fingerprint changes after the save,
            // but the project remains authored against the unchanged source
            // cache and must reopen as editable.
            try (OsrsStudioProject reopenedProject = OsrsStudioProject.openWithOpenRuneOutput(
                    source, output, project)) {
                var reopened = reopenedProject.openRegion(regionX, regionY);
                assertFalse(reopened.readOnly());
                assertEquals(CacheWriteMode.DIRECT, reopened.writeMode());
                if (rotatedObject != null) {
                    assertEquals(rotatedObject,
                            reopened.region().session().world()
                                    .tile(rotatedObject.plane(), rotatedObject.x(), rotatedObject.y())
                                    .snapshot().objects().stream()
                                    .filter(object -> object.id() == rotatedObject.id()
                                            && object.type() == rotatedObject.type()
                                            && object.x() == rotatedObject.x()
                                            && object.y() == rotatedObject.y())
                                    .findFirst().orElseThrow());
                }
                assertEquals(expectedSemanticTile,
                        reopened.region().session().world().tile(0, 2, 2).snapshot());
                assertEquals(expectedVertexHeight,
                        reopened.region().session().world().tile(0, 8, 8)
                                .snapshot().southWestHeight());
            }

            try (CacheStore reopenedStore = CacheStoreFactory.openRune(output)) {
                OsrsMapService maps = new OsrsMapService(reopenedStore, revision);
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

    private static WorldObject firstObject(WorldDocument document) {
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    if (!document.tile(plane, x, y).snapshot().objects().isEmpty()) {
                        return document.tile(plane, x, y).snapshot().objects().get(0);
                    }
                }
            }
        }
        return null;
    }

    private static SetTileCommand adjustCorner(EditorSession session, int x, int y,
                                                int corner, int delta) {
        TileCoordinate coordinate = new TileCoordinate(0, x, y);
        TileSnapshot before = session.world().tile(coordinate).snapshot();
        int southWest = before.southWestHeight();
        int southEast = before.southEastHeight();
        int northEast = before.northEastHeight();
        int northWest = before.northWestHeight();
        switch (corner) {
            case 0 -> southWest += delta;
            case 1 -> southEast += delta;
            case 2 -> northEast += delta;
            case 3 -> northWest += delta;
            default -> throw new IllegalArgumentException("Unknown tile corner: " + corner);
        }
        TileSnapshot after = new TileSnapshot(southWest, southEast, northEast, northWest,
                before.underlayId(), before.overlayId(), before.overlayShape(),
                before.overlayRotation(), before.flags(), before.objects());
        return new SetTileCommand(coordinate, before, after, "integration corner height edit");
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
