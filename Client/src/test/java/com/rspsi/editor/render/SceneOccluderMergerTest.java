package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneOccluderMergerTest {
    @Test
    void mergesContiguousTypeOneWallPlanesAndRetainsWorldBounds() {
        SceneOccluder lower = new SceneOccluder(1, 20, 20, 30, 30,
                0, 0, 3200, 3200, 3840, 3968, 100, 900);
        SceneOccluder upper = new SceneOccluder(1, 20, 20, 31, 31,
                0, 0, 3200, 3200, 3968, 4096, 100, 900);

        List<SceneOccluder> result = SceneOccluderMerger.merge(List.of(upper, lower));

        assertEquals(1, result.size());
        SceneOccluder merged = result.get(0);
        assertEquals(30, merged.minTileY());
        assertEquals(31, merged.maxTileY());
        assertEquals(3840, merged.minWorldY());
        assertEquals(4096, merged.maxWorldY());
        assertEquals(3200, merged.minWorldX());
        assertEquals(3200, merged.maxWorldX());
    }

    @Test
    void doesNotMergeAcrossDifferentHeightsOrGaps() {
        SceneOccluder first = new SceneOccluder(2, 20, 20, 30, 30,
                0, 0, 3200, 3328, 3840, 3840, 100, 900);
        SceneOccluder differentHeight = new SceneOccluder(2, 21, 21, 30, 30,
                0, 0, 3328, 3456, 3840, 3840, 101, 900);
        SceneOccluder gap = new SceneOccluder(2, 23, 23, 30, 30,
                0, 0, 3584, 3712, 3840, 3840, 100, 900);

        assertEquals(3, SceneOccluderMerger.merge(List.of(first, differentHeight, gap)).size());
    }
}
