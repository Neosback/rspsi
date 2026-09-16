package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstanceChunkGridTest {
    @Test
    void preservesHolesAndMapsRepeatedSourceChunks() {
        InstanceChunkTemplate first = new InstanceChunkTemplate(0, 0, 0, 0, 100, 200, 0);
        InstanceChunkTemplate second = new InstanceChunkTemplate(0, 1, 0, 0, 100, 200, 1);
        int[][][] packed = {{{first.encode()}, {second.encode()}, {-1}}};

        InstanceChunkGrid grid = InstanceChunkGrid.decode(packed, 3200, 3216);

        TileCoordinate source = new TileCoordinate(0, 800, 1600);
        assertEquals(List.of(new TileCoordinate(0, 3200, 3216),
                        new TileCoordinate(0, 3208, 3223)),
                grid.sourceToScene(source));
        assertEquals(2, grid.transforms().size());
        assertTrue(grid.sceneToSource(new TileCoordinate(0, 3216, 3216)).isEmpty());
    }

    @Test
    void resolvesSceneTilesThroughRotationAndSourcePlane() {
        InstanceChunkTemplate template = new InstanceChunkTemplate(2, 0, 0, 3, 12, 15, 3);
        InstanceChunkGrid grid = InstanceChunkGrid.decode(new int[][][]{{{-1}}, {{-1}}, {{template.encode()}}},
                0, 0);

        assertEquals(new TileCoordinate(3, 96, 120),
                grid.sceneToSource(new TileCoordinate(2, 7, 0)).orElseThrow());
        assertTrue(grid.sceneToSource(new TileCoordinate(1, 0, 0)).isEmpty());
    }
}
