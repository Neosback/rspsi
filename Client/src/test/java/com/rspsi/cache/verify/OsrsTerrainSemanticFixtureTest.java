package com.rspsi.cache.verify;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsTerrainSemanticFixtureTest {
    @Test
    void comparesEveryCanonicalTerrainField() {
        int[] heights = new int[64 * 64 * 4];
        int[] underlays = new int[heights.length];
        int[] overlays = new int[heights.length];
        int[] shapes = new int[heights.length];
        int[] rotations = new int[heights.length];
        int[] flags = new int[heights.length];
        int index = (2 * 64 + 11) * 64 + 13;
        heights[index] = -128;
        underlays[index] = 4;
        overlays[index] = 9;
        shapes[index] = 7;
        rotations[index] = 3;
        flags[index] = 6;

        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(2, 11, 13).restore(new TileSnapshot(
                -128, 0, 0, 0, 4, 9, 7, 3, 6, List.of()));

        OsrsTerrainSemanticFixture fixture = new OsrsTerrainSemanticFixture(
                1, 64, 64, 4, heights, underlays, overlays, shapes, rotations, flags);
        OsrsTerrainSemanticFixture.Comparison match = fixture.compare(document);

        assertTrue(match.matches());
        assertEquals(0, match.differenceCount());
    }

    @Test
    void reportsBoundedFieldSamplesWhenReferenceDiffers() {
        int[] values = new int[64 * 64 * 4];
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                -8, 0, 0, 0, 1, 2, 3, 1, 4, List.of()));

        OsrsTerrainSemanticFixture fixture = new OsrsTerrainSemanticFixture(
                1, 64, 64, 4, values, values, values, values, values, values);
        OsrsTerrainSemanticFixture.Comparison comparison = fixture.compare(document);

        assertFalse(comparison.matches());
        assertEquals(6, comparison.differenceCount());
        assertEquals(6, comparison.samples().size());
    }

    @Test
    void locationFixtureComparesInteriorReferencePlacements() {
        WorldObject object = new WorldObject(1234, 10, 2, 1, 12, 15);
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 12, 15).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(object)));

        OsrsLocationSemanticFixture fixture = new OsrsLocationSemanticFixture(1, List.of(object));

        assertTrue(fixture.compare(document).matches());
    }

    @Test
    void sceneGeometryFixtureUsesCanonicalMeshCoordinatesAndTopology() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(0, 4, 5).restore(new TileSnapshot(
                -128, -96, -64, -112, 3, 7, 8, 2, 0, List.of()));
        TerrainMesh mesh = new TerrainMeshBuilder().build(document.tile(0, 4, 5).snapshot());

        OsrsSceneGeometryFixture.TileGeometry tile = new OsrsSceneGeometryFixture.TileGeometry(
                0, 4, 5, mesh.vertices(), mesh.faces());

        assertTrue(new OsrsSceneGeometryFixture(1, List.of(tile)).compare(document).matches());
    }
}
