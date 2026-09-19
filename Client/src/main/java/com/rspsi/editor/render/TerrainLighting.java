package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds renderer-neutral OSRS directional terrain lighting inputs. */
public final class TerrainLighting {
    private TerrainLighting() {
    }

    /** Returns per-tile corner values for every plane in document coordinates. */
    public static Map<TileCoordinate, TerrainLight> build(WorldDocument document) {
        return build(document, LightingProfile.osrs());
    }

    public static Map<TileCoordinate, TerrainLight> build(WorldDocument document,
                                                           LightingProfile profile) {
        return build(document, profile, null);
    }

    /** Builds terrain light with optional client-style object shadow penalties. */
    public static Map<TileCoordinate, TerrainLight> build(WorldDocument document,
                                                           LightingProfile profile,
                                                           TerrainShadowMap shadows) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(profile, "profile");
        Map<TileCoordinate, TerrainLight> result = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            int[][] heights = cornerHeights(document, plane);
            int[][] lights = lights(heights, document.width(), document.length(), profile, shadows, plane);
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

    private static int[][] lights(int[][] heights, int width, int length, LightingProfile profile,
                                  TerrainShadowMap shadows, int plane) {
        int[][] lights = new int[width + 1][length + 1];
        for (int x = 1; x < width; x++) {
            for (int y = 1; y < length; y++) {
                int heightDeltaX = heights[x + 1][y] - heights[x - 1][y];
                int heightDeltaY = heights[x][y + 1] - heights[x][y - 1];
                int shadowPenalty = shadows == null ? 0
                        : com.rspsi.osrs.rules.terrain.TerrainLightRules.calculateShadowPenalty(
                                shadows.cornerStrength(plane, x, y),
                                shadows.cornerStrength(plane, x - 1, y),
                                shadows.cornerStrength(plane, x + 1, y),
                                shadows.cornerStrength(plane, x, y - 1),
                                shadows.cornerStrength(plane, x, y + 1));
                lights[x][y] = com.rspsi.osrs.rules.terrain.TerrainLightRules.calculateCornerLight(
                        heightDeltaX, heightDeltaY, profile, shadowPenalty);
            }
        }
        // TSPS leaves the outer normal samples at their zero-initialized edge
        // values. A neutral scene still needs valid corner inputs for edge
        // tiles, so use ambient light there rather than black geometry.
        for (int x = 0; x <= width; x++) {
            lights[x][0] = profile.ambient();
            lights[x][length] = profile.ambient();
        }
        for (int y = 0; y <= length; y++) {
            lights[0][y] = profile.ambient();
            lights[width][y] = profile.ambient();
        }
        return lights;
    }
}
