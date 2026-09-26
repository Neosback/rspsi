package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileSnapshotCompatibilityTest {

    @Test
    void remainsJvmRecordWithHistoricalComponentSurface() {
        assertTrue(TileSnapshot.class.isRecord());

        var components = TileSnapshot.class.getRecordComponents();
        assertEquals(11, components.length);
        assertEquals("southWestHeight", components[0].getName());
        assertEquals("objects", components[9].getName());
        assertEquals("heightSource", components[10].getName());
    }

    @Test
    void canonicalConstructorStillNormalizesNullableComponents() {
        TileSnapshot snapshot = new TileSnapshot(
                1, 2, 3, 4,
                5, 6, 7, 3, 8,
                null,
                null);

        assertEquals(List.of(), snapshot.objects());
        assertEquals(TerrainHeightSource.unknown(), snapshot.heightSource());
    }

    @Test
    void objectsRemainDefensivelyCopiedAndUnmodifiable() {
        List<WorldObject> mutable = new ArrayList<>();
        mutable.add(new WorldObject(42, 10, 0, 0, 1, 1));

        TileSnapshot snapshot = new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0, 0,
                mutable,
                TerrainHeightSource.unknown());

        mutable.clear();
        assertEquals(1, snapshot.objects().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.objects().add(new WorldObject(43, 10, 0, 0, 1, 1)));
    }

    @Test
    void provenanceRemainsExcludedFromEqualityAndHashCode() {
        TileSnapshot unknown = new TileSnapshot(
                1, 2, 3, 4,
                5, 6, 7, 2, 8,
                List.of(),
                TerrainHeightSource.unknown());
        TileSnapshot authored = unknown.withHeightSource(
                TerrainHeightSource.authoredSource());

        assertNotEquals(unknown.heightSource(), authored.heightSource());
        assertEquals(unknown, authored);
        assertEquals(unknown.hashCode(), authored.hashCode());
    }

    @Test
    void authoredChangesStillParticipateInEquality() {
        TileSnapshot first = new TileSnapshot(
                1, 2, 3, 4,
                5, 6, 7, 2, 8,
                List.of(),
                TerrainHeightSource.unknown());
        TileSnapshot changed = new TileSnapshot(
                99, 2, 3, 4,
                5, 6, 7, 2, 8,
                List.of(),
                TerrainHeightSource.unknown());

        assertNotEquals(first, changed);
    }

    @Test
    void overlayValidationMessageIsPreserved() {
        IllegalArgumentException shape = assertThrows(
                IllegalArgumentException.class,
                () -> new TileSnapshot(
                        0, 0, 0, 0,
                        0, 0, 12, 0, 0,
                        List.of(),
                        TerrainHeightSource.unknown()));
        assertEquals(
                "Overlay shape must be 0..11 and rotation 0..3",
                shape.getMessage());

        IllegalArgumentException rotation = assertThrows(
                IllegalArgumentException.class,
                () -> new TileSnapshot(
                        0, 0, 0, 0,
                        0, 0, 0, 4, 0,
                        List.of(),
                        TerrainHeightSource.unknown()));
        assertEquals(
                "Overlay shape must be 0..11 and rotation 0..3",
                rotation.getMessage());
    }
}
