package com.rspsi.editor.terrain;

import com.rspsi.editor.model.TileSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RSPSi-owned shaped-tile topology. The tables are intentionally isolated
 * here so classic renderer classes cannot become the canonical world model.
 */
public final class TerrainMeshBuilder {
    private static final int[][] SHAPE_POINTS = {
            {1, 3, 5, 7}, {1, 3, 5, 7}, {1, 3, 5, 7}, {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 6}, {1, 3, 5, 7, 6}, {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 2, 6}, {1, 3, 5, 7, 2, 8}, {1, 3, 5, 7, 2, 8},
            {1, 3, 5, 7, 11, 12}, {1, 3, 5, 7, 11, 12}, {1, 3, 5, 7, 13, 14}
    };

    private static final int[][] ELEMENTS = {
            {0, 1, 2, 3, 0, 0, 1, 3},
            {1, 1, 2, 3, 1, 0, 1, 3},
            {0, 1, 2, 3, 1, 0, 1, 3},
            {0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3},
            {0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4},
            {0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4},
            {0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3},
            {0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3},
            {0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5},
            {0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5},
            {0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3,
                    1, 5, 4, 3, 1, 4, 2, 3},
            {1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3,
                    0, 5, 4, 3, 0, 4, 2, 3},
            {1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3,
                    0, 5, 2, 3, 0, 1, 2, 5}
    };

    public TerrainMesh build(TileSnapshot tile) {
        Objects.requireNonNull(tile, "tile");
        // The map codec stores overlay shapes as 0..11. The scene topology
        // table has one additional entry: shape 0 is the flat underlay model,
        // while overlay shapes occupy topology entries 1..12.
        int shape = tile.overlayId() == 0 ? 0 : tile.overlayShape() + 1;
        int rotation = tile.overlayRotation();
        if (shape < 0 || shape >= SHAPE_POINTS.length) {
            throw new IllegalArgumentException("Encoded overlay shape must be between 0 and 11");
        }
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Terrain rotation must be between 0 and 3");
        }

        List<TerrainVertex> vertices = new ArrayList<>();
        for (int point : SHAPE_POINTS[shape]) {
            int rotatedPoint = rotatePoint(point, rotation);
            vertices.add(vertex(rotatedPoint, tile));
        }

        List<TerrainFace> faces = new ArrayList<>();
        int[] elements = ELEMENTS[shape];
        for (int offset = 0; offset < elements.length; offset += 4) {
            int material = elements[offset];
            int a = rotateCornerIndex(elements[offset + 1], rotation);
            int b = rotateCornerIndex(elements[offset + 2], rotation);
            int c = rotateCornerIndex(elements[offset + 3], rotation);
            faces.add(new TerrainFace(material, a, b, c));
        }
        return new TerrainMesh(vertices, faces);
    }

    public static int shapeCount() {
        return SHAPE_POINTS.length;
    }

    private static int rotatePoint(int point, int rotation) {
        if ((point & 1) == 0 && point <= 8) {
            return (point - rotation * 2 - 1 & 7) + 1;
        }
        if (point > 8 && point <= 12) {
            return (point - 9 - rotation & 3) + 9;
        }
        if (point > 12 && point <= 16) {
            return (point - 13 - rotation & 3) + 13;
        }
        return point;
    }

    private static int rotateCornerIndex(int index, int rotation) {
        return index < 4 ? index - rotation & 3 : index;
    }

    private static TerrainVertex vertex(int point, TileSnapshot tile) {
        int sw = tile.southWestHeight();
        int se = tile.southEastHeight();
        int ne = tile.northEastHeight();
        int nw = tile.northWestHeight();
        return switch (point) {
            case 1 -> new TerrainVertex(0, 0, sw);
            case 2 -> new TerrainVertex(64, 0, average(sw, se));
            case 3 -> new TerrainVertex(128, 0, se);
            case 4 -> new TerrainVertex(128, 64, average(se, ne));
            case 5 -> new TerrainVertex(128, 128, ne);
            case 6 -> new TerrainVertex(64, 128, average(ne, nw));
            case 7 -> new TerrainVertex(0, 128, nw);
            case 8 -> new TerrainVertex(0, 64, average(nw, sw));
            case 9 -> new TerrainVertex(64, 32, average(sw, se));
            case 10 -> new TerrainVertex(96, 64, average(se, ne));
            case 11 -> new TerrainVertex(64, 96, average(ne, nw));
            case 12 -> new TerrainVertex(32, 64, average(nw, sw));
            case 13 -> new TerrainVertex(32, 32, sw);
            case 14 -> new TerrainVertex(96, 32, se);
            case 15 -> new TerrainVertex(96, 96, ne);
            case 16 -> new TerrainVertex(32, 96, nw);
            default -> throw new IllegalArgumentException("Unknown terrain point " + point);
        };
    }

    private static int average(int first, int second) {
        return first + second >> 1;
    }
}
