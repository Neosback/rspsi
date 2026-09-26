package com.rspsi.editor.tool;

import com.rspsi.editor.model.TileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileGeometryHelpersTest {

    @Test
    void tileSnapperRoundsToNearestGridOriginAndClampsToDocument() {
        assertEquals(4, TileSnapper.snap(5, 4, 64));
        assertEquals(8, TileSnapper.snap(6, 4, 64));
        assertEquals(63, TileSnapper.snap(63, 8, 64));
        assertEquals(12, TileSnapper.snap(12, 1, 64));
        assertEquals(63, TileSnapper.snap(99, 1, 64));
    }

    @Test
    void tileSnapperPreservesValidationContract() {
        assertThrows(IllegalArgumentException.class, () -> TileSnapper.snap(-1, 1, 64));
        assertThrows(IllegalArgumentException.class, () -> TileSnapper.snap(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> TileSnapper.snap(0, 0, 64));
    }

    @Test
    void terrainSamplerReturnsExactCornersAndBilinearMidpoint() {
        TileSnapshot tile = tile(0, 100, 300, 200);

        assertEquals(0, TerrainHeightSampler.sample(tile, 0.0, 0.0));
        assertEquals(100, TerrainHeightSampler.sample(tile, 1.0, 0.0));
        assertEquals(200, TerrainHeightSampler.sample(tile, 0.0, 1.0));
        assertEquals(300, TerrainHeightSampler.sample(tile, 1.0, 1.0));
        assertEquals(150, TerrainHeightSampler.sample(tile, 0.5, 0.5));
    }

    @Test
    void terrainSamplerKeepsJavaMathRoundBehavior() {
        TileSnapshot tile = tile(0, 1, 1, 0);

        assertEquals(1, TerrainHeightSampler.sample(tile, 0.5, 0.0));
        assertEquals(1, TerrainHeightSampler.sample(tile, 0.5, 1.0));
    }

    @Test
    void terrainSamplerPreservesNullAndRangeValidation() {
        TileSnapshot tile = tile(0, 0, 0, 0);

        assertThrows(NullPointerException.class,
                () -> TerrainHeightSampler.sample(null, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightSampler.sample(tile, -0.01, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightSampler.sample(tile, 1.01, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightSampler.sample(tile, 0.5, -0.01));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightSampler.sample(tile, 0.5, 1.01));
    }

    private static TileSnapshot tile(int sw, int se, int ne, int nw) {
        return new TileSnapshot(sw, se, ne, nw, 0, 0, 0, 0, 0, List.of());
    }
}
