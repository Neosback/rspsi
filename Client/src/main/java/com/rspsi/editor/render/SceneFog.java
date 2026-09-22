package com.rspsi.editor.render;

import java.util.Objects;

/** RuneScape-style scene-edge fog shared by reference and native backends. */
public final class SceneFog {
    private static final float TILE_SIZE = 128.0f;
    private static final float CORNER_ROUNDING = 1.5f * TILE_SIZE;
    private static final float CORNER_ROUNDING_SQUARED = CORNER_ROUNDING * CORNER_ROUNDING;

    private SceneFog() {
    }

    public static Bounds bounds(GpuCommandGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (geometry.vertexCount() == 0) return new Bounds(0, 0, 0, 0);
        float[] bounds = {
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        geometry.forEachUniqueVertex(vertex -> {
            bounds[0] = Math.min(bounds[0], vertex.x());
            bounds[1] = Math.max(bounds[1], vertex.x());
            bounds[2] = Math.min(bounds[2], vertex.z());
            bounds[3] = Math.max(bounds[3], vertex.z());
        });
        return new Bounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    public static float amount(float worldX, float worldZ, Bounds bounds, int depthTiles) {
        Objects.requireNonNull(bounds, "bounds");
        if (depthTiles <= 0) return 0.0f;
        float xDistance = Math.min(worldX - bounds.minX(), bounds.maxX() - worldX);
        float zDistance = Math.min(worldZ - bounds.minZ(), bounds.maxZ() - worldZ);
        float nearest = Math.min(xDistance, zDistance);
        float second = Math.max(xDistance, zDistance);
        float distance = nearest - CORNER_ROUNDING * Math.max(0.0f,
                (nearest + CORNER_ROUNDING_SQUARED)
                        / (second + CORNER_ROUNDING_SQUARED));
        return 1.0f - clamp(distance / (depthTiles * TILE_SIZE));
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public record Bounds(float minX, float maxX, float minZ, float maxZ) {
        public Bounds {
            if (!Float.isFinite(minX) || !Float.isFinite(maxX)
                    || !Float.isFinite(minZ) || !Float.isFinite(maxZ)
                    || minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid fog bounds");
            }
        }
    }
}
