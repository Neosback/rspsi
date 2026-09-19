package com.rspsi.editor.knowledge.cache;

import com.rspsi.cache.definition.ModelGeometryView;

import java.util.Objects;

/**
 * Layer 1 Cache Fact: Exact, zero-heuristics geometric properties of a cache 3D model.
 */
public record ModelKnowledge(
        int id,
        int vertexCount,
        int triangleCount,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        boolean hasTextures,
        boolean hasAlpha,
        boolean hasPriorities,
        int textureTriangleCount
) {
    public static ModelKnowledge from(ModelGeometryView view) {
        Objects.requireNonNull(view, "view");
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        int[] positions = view.vertexPositions();
        for (int i = 0; i < view.vertexCount(); i++) {
            int x = positions[i * 3];
            int y = positions[i * 3 + 1];
            int z = positions[i * 3 + 2];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        if (view.vertexCount() == 0) {
            minX = maxX = minY = maxY = minZ = maxZ = 0;
        }

        boolean hasTextures = view.triangleTextures().length > 0;
        boolean hasAlpha = view.triangleAlphas().length > 0;
        boolean hasPriorities = view.triangleRenderPriorities().length > 0;
        int texCount = view.textureTriangleIndices().length / 3;

        return new ModelKnowledge(
                view.id(),
                view.vertexCount(),
                view.triangleCount(),
                minX, maxX, minY, maxY, minZ, maxZ,
                hasTextures, hasAlpha, hasPriorities,
                texCount
        );
    }

    public int width() {
        return maxX - minX;
    }

    public int height() {
        return maxY - minY;
    }

    public int depth() {
        return maxZ - minZ;
    }
}
