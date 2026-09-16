package com.rspsi.editor.terrain;

import com.rspsi.editor.model.TileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that canonical adjacent terrain meshes share world-space edges. */
class TerrainSharedEdgeInvariantTest {
    @Test
    void eastWestEdgesSharePositionAndHeightForAllShapeAndRotationPairs() {
        TerrainMeshBuilder builder = new TerrainMeshBuilder();
        for (int leftShape = 0; leftShape < TerrainMeshBuilder.shapeCount(); leftShape++) {
            for (int rightShape = 0; rightShape < TerrainMeshBuilder.shapeCount(); rightShape++) {
                for (int leftRotation = 0; leftRotation < 4; leftRotation++) {
                    for (int rightRotation = 0; rightRotation < 4; rightRotation++) {
                        TerrainMesh left = builder.build(tile(100, 500, 700, 300,
                                leftShape, leftRotation));
                        TerrainMesh right = builder.build(tile(500, 900, 1100, 700,
                                rightShape, rightRotation));
                        assertSharedEdge(edge(left, 128), edge(right, 0),
                                leftShape + "/" + leftRotation + " -> " + rightShape + "/" + rightRotation);
                    }
                }
            }
        }
    }

    @Test
    void northSouthEdgesSharePositionAndHeightForAllShapeAndRotationPairs() {
        TerrainMeshBuilder builder = new TerrainMeshBuilder();
        for (int southShape = 0; southShape < TerrainMeshBuilder.shapeCount(); southShape++) {
            for (int northShape = 0; northShape < TerrainMeshBuilder.shapeCount(); northShape++) {
                for (int southRotation = 0; southRotation < 4; southRotation++) {
                    for (int northRotation = 0; northRotation < 4; northRotation++) {
                        TerrainMesh south = builder.build(tile(100, 300, 700, 500,
                                southShape, southRotation));
                        TerrainMesh north = builder.build(tile(500, 700, 1100, 900,
                                northShape, northRotation));
                        assertSharedEdge(edge(south, 128, true), edge(north, 0, true),
                                southShape + "/" + southRotation + " -> " + northShape + "/" + northRotation);
                    }
                }
            }
        }
    }

    private static void assertSharedEdge(Map<Integer, Integer> first,
                                         Map<Integer, Integer> second, String caseName) {
        for (int coordinate : first.keySet()) {
            if (second.containsKey(coordinate)) {
                assertEquals(first.get(coordinate), second.get(coordinate), caseName + " at " + coordinate);
            }
        }
    }

    private static Map<Integer, Integer> edge(TerrainMesh mesh, int coordinate) {
        return edge(mesh, coordinate, false);
    }

    private static Map<Integer, Integer> edge(TerrainMesh mesh, int coordinate, boolean horizontal) {
        Function<TerrainVertex, Integer> axis = horizontal ? TerrainVertex::x : TerrainVertex::y;
        return mesh.vertices().stream()
                .filter(vertex -> (horizontal ? vertex.y() : vertex.x()) == coordinate)
                .collect(Collectors.toMap(axis, TerrainVertex::height, (first, second) -> {
                    assertEquals(first, second, "duplicate edge coordinate has different heights");
                    return first;
                }));
    }

    private static TileSnapshot tile(int sw, int se, int ne, int nw, int topology, int rotation) {
        return new TileSnapshot(sw, se, ne, nw, 1, topology == 0 ? 0 : 2,
                Math.max(0, topology - 1), rotation, 0, List.of());
    }
}
