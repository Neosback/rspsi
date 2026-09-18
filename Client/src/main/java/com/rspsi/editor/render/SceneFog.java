package com.rspsi.editor.render;

import java.util.Objects;

/** RuneScape-style scene-edge fog shared by reference and native backends. */
public final class SceneFog {
    private static final float TILE_SIZE = 128.0f;
    private static final float CORNER_ROUNDING = 1.5f * TILE_SIZE;
    private static final float CORNER_ROUNDING_SQUARED = CORNER_ROUNDING * CORNER_ROUNDING;

    private SceneFog() {
    }

    public static Bounds bounds(GpuUploadPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.vertices().isEmpty()) return new Bounds(0, 0, 0, 0);
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (GpuSceneVertex vertex : plan.vertices()) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minZ = Math.min(minZ, vertex.z());
            maxZ = Math.max(maxZ, vertex.z());
        }
        return new Bounds(minX, maxX, minZ, maxZ);
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
