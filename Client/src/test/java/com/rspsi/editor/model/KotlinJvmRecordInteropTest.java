package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KotlinJvmRecordInteropTest {

    @Test
    void migratedModelTypesRemainRealJvmRecords() {
        assertTrue(WorldTileSource.class.isRecord());
        assertTrue(TerrainTilePatch.class.isRecord());
    }

    @Test
    void worldTileSourcePreservesRecordAccessorsAndValueSemantics() {
        TileSnapshot snapshot = snapshot();
        TerrainHeightSource source = TerrainHeightSource.explicitSource(12);

        WorldTileSource left = new WorldTileSource(snapshot, source);
        WorldTileSource right = new WorldTileSource(snapshot, source);

        assertSame(snapshot, left.snapshot());
        assertSame(source, left.heightSource());
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertTrue(left.toString().startsWith("WorldTileSource["));
        assertThrows(NullPointerException.class, () -> new WorldTileSource(null, source));
        assertThrows(NullPointerException.class, () -> new WorldTileSource(snapshot, null));
    }

    @Test
    void terrainPatchPreservesCoordinatesValidationAndRecordShape() {
        TileSnapshot snapshot = snapshot();
        TerrainTilePatch patch = new TerrainTilePatch(2, 10, 20, snapshot);

        assertEquals(2, patch.plane());
        assertEquals(10, patch.x());
        assertEquals(20, patch.y());
        assertSame(snapshot, patch.snapshot());
        assertTrue(patch.toString().startsWith("TerrainTilePatch["));

        assertThrows(IllegalArgumentException.class,
                () -> new TerrainTilePatch(-1, 0, 0, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainTilePatch(0, -1, 0, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainTilePatch(0, 0, -1, snapshot));
        assertThrows(NullPointerException.class,
                () -> new TerrainTilePatch(0, 0, 0, null));
    }

    private static TileSnapshot snapshot() {
        return new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0, 0,
                List.of());
    }
}
