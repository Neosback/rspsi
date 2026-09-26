package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class TileCompatibilityTest {

    @Test
    void remainsPublicFinalWithPackagePrivateConstructor() throws Exception {
        assertTrue(Modifier.isPublic(Tile.class.getModifiers()));
        assertTrue(Modifier.isFinal(Tile.class.getModifiers()));

        var constructor = Tile.class.getDeclaredConstructor(TileCoordinate.class);
        int modifiers = constructor.getModifiers();
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
        assertFalse(Modifier.isPrivate(modifiers));
    }

    @Test
    void packagePrivateConstructorKeepsHistoricalInitialization() {
        TileCoordinate coordinate = new TileCoordinate(1, 2, 3);
        Tile tile = new Tile(coordinate);

        assertSame(coordinate, tile.coordinate());
        assertEquals(TerrainHeightSource.unknown(), tile.heightSource());

        TileSnapshot state = tile.snapshot();
        assertEquals(0, state.southWestHeight());
        assertEquals(0, state.southEastHeight());
        assertEquals(0, state.northEastHeight());
        assertEquals(0, state.northWestHeight());
        assertEquals(0, state.underlayId());
        assertEquals(0, state.overlayId());
        assertEquals(0, state.flags());
        assertEquals(List.of(), state.objects());
        assertEquals(TerrainHeightSource.unknown(), state.heightSource());
    }

    @Test
    void constructorStillDoesNotIntroduceCoordinateNullCheck() {
        Tile tile = new Tile(null);
        assertNull(tile.coordinate());
    }

    @Test
    void restoreWithUnknownSnapshotInheritsCurrentProvenance() {
        Tile tile = new Tile(new TileCoordinate(0, 0, 0));
        TerrainHeightSource known = TerrainHeightSource.explicitSource(7);
        tile.heightSource(known);

        TileSnapshot incoming = new TileSnapshot(
                10, 20, 30, 40,
                1, 2, 3, 1, 4,
                List.of());

        tile.restore(incoming);

        assertEquals(known, tile.heightSource());
        assertEquals(known, tile.snapshot().heightSource());
        assertEquals(10, tile.snapshot().southWestHeight());
    }

    @Test
    void restoreWithKnownSnapshotProvenanceWins() {
        Tile tile = new Tile(new TileCoordinate(0, 0, 0));
        tile.heightSource(TerrainHeightSource.explicitSource(7));

        TerrainHeightSource authored = TerrainHeightSource.authoredSource();
        TileSnapshot incoming = new TileSnapshot(
                10, 20, 30, 40,
                1, 2, 3, 1, 4,
                List.of(),
                authored);

        tile.restore(incoming);

        assertEquals(authored, tile.heightSource());
        assertEquals(authored, tile.snapshot().heightSource());
    }

    @Test
    void singleArgumentRestorePreservesStateNullFailureWithoutMutation() {
        Tile tile = new Tile(new TileCoordinate(0, 0, 0));
        TerrainHeightSource originalSource = TerrainHeightSource.explicitSource(8);
        tile.heightSource(originalSource);
        TileSnapshot originalState = tile.snapshot();

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> tile.restore((TileSnapshot) null));

        assertEquals("state", failure.getMessage());
        assertSame(originalState, tile.snapshot());
        assertEquals(originalSource, tile.heightSource());
    }

    @Test
    void explicitRestorePreservesValidationOrderAndHistoricalPartialMutation() {
        Tile tile = new Tile(new TileCoordinate(0, 0, 0));
        TileSnapshot originalState = tile.snapshot();
        TerrainHeightSource replacement = TerrainHeightSource.authoredSource();

        NullPointerException stateFailure = assertThrows(
                NullPointerException.class,
                () -> tile.restore(null, replacement));

        assertEquals("state", stateFailure.getMessage());
        assertEquals(replacement, tile.heightSource());
        assertSame(originalState, tile.snapshot());

        TerrainHeightSource beforeNullSource = tile.heightSource();
        TileSnapshot valid = new TileSnapshot(
                1, 2, 3, 4,
                0, 0, 0, 0, 0,
                List.of());

        NullPointerException sourceFailure = assertThrows(
                NullPointerException.class,
                () -> tile.restore(valid, null));

        assertEquals("source", sourceFailure.getMessage());
        assertEquals(beforeNullSource, tile.heightSource());
        assertSame(originalState, tile.snapshot());
    }

    @Test
    void heightSourceSetterKeepsSnapshotAndFieldInSync() {
        Tile tile = new Tile(new TileCoordinate(0, 0, 0));
        TerrainHeightSource source = TerrainHeightSource.generatedSource();

        tile.heightSource(source);

        assertEquals(source, tile.heightSource());
        assertEquals(source, tile.snapshot().heightSource());

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> tile.heightSource(null));
        assertEquals("heightSource", failure.getMessage());
        assertEquals(source, tile.heightSource());
        assertEquals(source, tile.snapshot().heightSource());
    }

    @Test
    void objectsReturnsMutableDefensiveCopy() {
        WorldObject object = new WorldObject(42, 10, 0, 0, 1, 1);
        Tile tile = new Tile(new TileCoordinate(0, 0, 0));
        tile.restore(new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0, 0,
                List.of(object)));

        List<WorldObject> copy = tile.objects();
        assertEquals(List.of(object), copy);

        copy.clear();
        assertTrue(copy.isEmpty());
        assertEquals(List.of(object), tile.snapshot().objects());
    }
}
