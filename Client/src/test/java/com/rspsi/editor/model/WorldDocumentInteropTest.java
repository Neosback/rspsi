package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class WorldDocumentInteropTest {

    @Test
    void remainsOpenJavaClassWithStaticDefaultPlanesAndConstructors() throws Exception {
        assertTrue(Modifier.isPublic(WorldDocument.class.getModifiers()));
        assertFalse(Modifier.isFinal(WorldDocument.class.getModifiers()));

        assertTrue(Modifier.isPublic(
                WorldDocument.class.getConstructor(int.class, int.class).getModifiers()));
        assertTrue(Modifier.isPublic(
                WorldDocument.class.getConstructor(int.class, int.class, int.class).getModifiers()));

        var field = WorldDocument.class.getDeclaredField("DEFAULT_PLANES");
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(4, field.getInt(null));
    }

    @Test
    void deprecatedCoordinateOverloadsRetainJavaDeprecationMetadata() throws Exception {
        assertTrue(WorldDocument.class
                .getMethod("contains", TileCoordinate.class)
                .isAnnotationPresent(Deprecated.class));
        assertTrue(WorldDocument.class
                .getMethod("tileOpt", TileCoordinate.class)
                .isAnnotationPresent(Deprecated.class));
        assertTrue(WorldDocument.class
                .getMethod("tile", TileCoordinate.class)
                .isAnnotationPresent(Deprecated.class));
    }

    @Test
    void preservesCoordinateNullAndBoundsBehavior() {
        WorldDocument document = new WorldDocument(2, 3, 2);

        assertFalse(document.contains((LocalTile) null));
        assertFalse(document.contains((TileCoordinate) null));
        assertEquals(java.util.Optional.empty(), document.tileOpt((LocalTile) null));
        assertEquals(java.util.Optional.empty(), document.tileOpt((TileCoordinate) null));

        NullPointerException localFailure = assertThrows(
                NullPointerException.class,
                () -> document.tile((LocalTile) null));
        assertEquals("coordinate", localFailure.getMessage());

        NullPointerException legacyFailure = assertThrows(
                NullPointerException.class,
                () -> document.tile((TileCoordinate) null));
        assertEquals("coordinate", legacyFailure.getMessage());

        assertThrows(IndexOutOfBoundsException.class, () -> document.tile(0, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> document.tile(0, 2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> document.tile(0, 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> document.tile(2, 0, 0));
    }

    @Test
    void copyRemainsIndependentAndPreservesHeightProvenance() {
        WorldDocument source = new WorldDocument(2, 2, 2);
        Tile original = source.tile(1, 1, 1);
        original.restore(new TileSnapshot(
                10, 20, 30, 40,
                1, 2, 3, 1,
                OsrsTileFlags.BRIDGE,
                List.of(new WorldObject(42, 10, 0, 1, 1, 1))));
        original.heightSource(TerrainHeightSource.explicitSource(7));

        WorldDocument copy = source.copy();
        Tile copied = copy.tile(1, 1, 1);

        assertNotSame(source, copy);
        assertNotSame(original, copied);
        assertEquals(original.snapshot(), copied.snapshot());
        assertEquals(original.heightSource(), copied.heightSource());

        copied.restore(new TileSnapshot(
                1, 1, 1, 1,
                0, 0, 0, 0, 0,
                List.of()));
        assertEquals(10, original.snapshot().southWestHeight());
    }

    @Test
    void bridgeLinksRemainUnmodifiableAndOrderedByCoordinatesThenPlane() {
        WorldDocument document = new WorldDocument(2, 2, 3);
        document.tile(1, 0, 1).restore(new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0,
                OsrsTileFlags.BRIDGE,
                List.of()));

        List<BridgeLink> links = document.bridgeLinks();

        assertEquals(List.of(
                new BridgeLink(
                        new TileCoordinate(1, 0, 1),
                        new TileCoordinate(0, 0, 1)),
                new BridgeLink(
                        new TileCoordinate(2, 0, 1),
                        new TileCoordinate(1, 0, 1))),
                links);
        assertThrows(UnsupportedOperationException.class,
                () -> links.add(links.get(0)));
    }
}
