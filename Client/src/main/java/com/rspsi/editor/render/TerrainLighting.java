package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds the renderer-neutral OSRS directional terrain lighting baseline. */
public final class TerrainLighting {
    private static final int LIGHT_DIR_X = -50;
    private static final int LIGHT_DIR_Y = -10;
    private static final int LIGHT_DIR_Z = -50;
    private static final int LIGHT_INTENSITY_BASE = 96;
    private static final int LIGHT_INTENSITY_FACTOR = 768;
    private static final int HEIGHT_SCALE = 65536;
    private static final int LIGHT_MAGNITUDE = 71;
    private static final int LIGHT_INTENSITY = (LIGHT_MAGNITUDE * LIGHT_INTENSITY_FACTOR) >> 8;

    private TerrainLighting() {
    }

    /** Returns per-tile corner values for every plane in document coordinates. */
    public static Map<TileCoordinate, TerrainLight> build(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        Map<TileCoordinate, TerrainLight> result = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            int[][] heights = cornerHeights(document, plane);
            int[][] lights = lights(heights, document.width(), document.length());
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    result.put(new TileCoordinate(plane, x, y), new TerrainLight(
                            lights[x][y], lights[x + 1][y], lights[x + 1][y + 1], lights[x][y + 1]));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static int[][] cornerHeights(WorldDocument document, int plane) {
        int[][] heights = new int[document.width() + 1][document.length() + 1];
        for (int x = 0; x <= document.width(); x++) {
            for (int y = 0; y <= document.length(); y++) {
                int sourceX = Math.min(x, document.width() - 1);
                int sourceY = Math.min(y, document.length() - 1);
                TileSnapshot tile = document.tile(plane, sourceX, sourceY).snapshot();
                heights[x][y] = x == document.width()
                        ? y == document.length() ? tile.northEastHeight() : tile.southEastHeight()
                        : y == document.length() ? tile.northWestHeight() : tile.southWestHeight();
            }
        }
        return heights;
    }

    private static int[][] lights(int[][] heights, int width, int length) {
        int[][] lights = new int[width + 1][length + 1];
        for (int x = 1; x < width; x++) {
            for (int y = 1; y < length; y++) {
                int heightDeltaX = heights[x + 1][y] - heights[x - 1][y];
                int heightDeltaY = heights[x][y + 1] - heights[x][y - 1];
                int normalLength = (int) Math.sqrt((long) heightDeltaY * heightDeltaY
                        + (long) heightDeltaX * heightDeltaX + HEIGHT_SCALE);
                int normalX = (heightDeltaX << 8) / normalLength;
                int normalY = HEIGHT_SCALE / normalLength;
                int normalZ = (heightDeltaY << 8) / normalLength;
                int dot = normalX * LIGHT_DIR_X + normalY * LIGHT_DIR_Y + normalZ * LIGHT_DIR_Z;
                lights[x][y] = (int) ((double) dot / LIGHT_INTENSITY) + LIGHT_INTENSITY_BASE;
            }
        }
        // TSPS leaves the outer normal samples at their zero-initialized edge
        // values. A neutral scene still needs valid corner inputs for edge
        // tiles, so use ambient light there rather than black geometry.
        for (int x = 0; x <= width; x++) {
            lights[x][0] = LIGHT_INTENSITY_BASE;
            lights[x][length] = LIGHT_INTENSITY_BASE;
        }
        for (int y = 0; y <= length; y++) {
            lights[0][y] = LIGHT_INTENSITY_BASE;
            lights[width][y] = LIGHT_INTENSITY_BASE;
        }
        return lights;
    }
}
