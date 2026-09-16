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

    @Test
    void materializesRotatedTerrainAndObjectsIntoCanonicalWorldTiles() {
        int[][] destinationTiles = {{0, 0}, {0, 7}, {7, 7}, {7, 0}};
        int[][] destinationCorners = {
                {10, 20, 30, 40}, {20, 30, 40, 10},
                {30, 40, 10, 20}, {40, 10, 20, 30}
        };
        for (int rotation = 0; rotation < 4; rotation++) {
            WorldDocument sourceDocument = new WorldDocument(64, 64, 4);
            WorldObject object = new WorldObject(42, 10, 0, 0, 0, 0);
            sourceDocument.tile(0, 0, 0).restore(new TileSnapshot(
                    10, 20, 30, 40, 1, 2, 3, 0, 0, java.util.List.of(object)));
            WorldRegionWindow source = new WorldRegionWindow(0, 0, 1, 1,
                    java.util.Map.of(0, new WorldRegion(0, 0, sourceDocument)));
            InstanceChunkTemplate template = new InstanceChunkTemplate(1, 0, 0,
                    0, 0, 0, rotation);
            InstanceChunkGrid grid = InstanceChunkGrid.decode(
                    new int[][][]{{{-1}}, {{template.encode()}}}, 3200, 3216);

            WorldDocument instance = new InstanceWorldBuilder().build(source, grid, 8, 8, 4);
            int destinationX = destinationTiles[rotation][0];
            int destinationY = destinationTiles[rotation][1];
            TileSnapshot destination = instance.tile(1, destinationX, destinationY).snapshot();
            assertEquals(destinationCorners[rotation][0], destination.southWestHeight(), rotation + ": sw");
            assertEquals(destinationCorners[rotation][1], destination.southEastHeight(), rotation + ": se");
            assertEquals(destinationCorners[rotation][2], destination.northEastHeight(), rotation + ": ne");
            assertEquals(destinationCorners[rotation][3], destination.northWestHeight(), rotation + ": nw");
            assertEquals(rotation, destination.overlayRotation(), rotation + ": overlay rotation");
            assertEquals(new WorldObject(42, 10, rotation, 1, destinationX, destinationY),
                    destination.objects().get(0), rotation + ": object");
        }
    }
}
