package com.rspsi.editor.terrain;

import com.rspsi.editor.model.TileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainMeshBuilderTest {
    private final TerrainMeshBuilder builder = new TerrainMeshBuilder();

    @Test
    void everyShapeAndRotationBuildsValidGeometry() {
        TileSnapshot tile = tile(10, 20, 30, 40, 0, 3);

        for (int shape = 0; shape < TerrainMeshBuilder.shapeCount(); shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                TerrainMesh mesh = builder.build(topologyTile(tile, shape, rotation));
                assertFalse(mesh.vertices().isEmpty(), shape + "/" + rotation);
                assertFalse(mesh.faces().isEmpty(), shape + "/" + rotation);
                assertTrue(mesh.vertices().stream().allMatch(vertex ->
                        vertex.x() >= 0 && vertex.x() <= 128 && vertex.y() >= 0 && vertex.y() <= 128));
            }
        }
    }

    @Test
    void cornerHeightsRemainSharedEdgeValuesForAllShapesAndRotations() {
        TileSnapshot tile = tile(100, 200, 300, 400, 0, 0);
        for (int shape = 0; shape < TerrainMeshBuilder.shapeCount(); shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                TerrainMesh mesh = builder.build(topologyTile(tile, shape, rotation));
                assertTrue(mesh.vertices().contains(new TerrainVertex(0, 0, 100)), shape + "/" + rotation);
                assertTrue(mesh.vertices().contains(new TerrainVertex(128, 0, 200)), shape + "/" + rotation);
                assertTrue(mesh.vertices().contains(new TerrainVertex(128, 128, 300)), shape + "/" + rotation);
                assertTrue(mesh.vertices().contains(new TerrainVertex(0, 128, 400)), shape + "/" + rotation);
            }
        }
    }

    @Test
    void midpointHeightsUseRuneScapeIntegerInterpolation() {
        TerrainMesh mesh = builder.build(tile(1, 4, 9, 16, 8, 0));

        assertTrue(mesh.vertices().contains(new TerrainVertex(64, 0, 2)));
        assertTrue(mesh.vertices().contains(new TerrainVertex(0, 64, 8)));

        TerrainMesh east = builder.build(tile(1, 4, 9, 16, 3, 1));
        assertTrue(east.vertices().contains(new TerrainVertex(128, 64, 6)));
        TerrainMesh north = builder.build(tile(1, 4, 9, 16, 3, 0));
        assertTrue(north.vertices().contains(new TerrainVertex(64, 128, 12)));
    }

    @Test
    void allFacesHaveConsistentWinding() {
        TileSnapshot tile = tile(10, 20, 30, 40, 0, 0);
        for (int shape = 0; shape < TerrainMeshBuilder.shapeCount(); shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                TerrainMesh mesh = builder.build(topologyTile(tile, shape, rotation));
                for (TerrainFace face : mesh.faces()) {
                    TerrainVertex a = mesh.vertices().get(face.a());
                    TerrainVertex b = mesh.vertices().get(face.b());
                    TerrainVertex c = mesh.vertices().get(face.c());
                    int cross = (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
                    assertTrue(cross > 0, "Shape " + shape + " rot " + rotation + " face " + face + " cross=" + cross);
                }
            }
        }
    }

    private static TileSnapshot tile(int sw, int se, int ne, int nw, int shape, int rotation) {
        return new TileSnapshot(sw, se, ne, nw, 0, shape == 0 ? 0 : 1,
                shape, rotation, 0, List.of());
    }

    private static TileSnapshot withShape(TileSnapshot source, int shape, int rotation) {
        return new TileSnapshot(source.southWestHeight(), source.southEastHeight(),
                source.northEastHeight(), source.northWestHeight(), source.underlayId(),
                shape == 0 ? 0 : source.overlayId() == 0 ? 1 : source.overlayId(),
                shape == 0 ? 0 : shape, rotation, source.flags(), source.objects());
    }

    private static TileSnapshot topologyTile(TileSnapshot source, int topologyShape, int rotation) {
        // Topology 0 is the flat/no-overlay model; topology 1..12 map to
        // encoded overlay shapes 0..11.
        return new TileSnapshot(source.southWestHeight(), source.southEastHeight(),
                source.northEastHeight(), source.northWestHeight(), source.underlayId(),
                topologyShape == 0 ? 0 : 1,
                Math.max(0, topologyShape - 1), rotation, source.flags(), source.objects());
    }
}
