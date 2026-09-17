package com.rspsi.cache.definition;

/**
 * Backend-neutral model geometry for previews and future scene renderers.
 *
 * <p>Vertices are packed as {@code x,y,z} triples and triangle indices as
 * {@code a,b,c} triples. Optional per-face arrays are empty when the cache
 * does not provide that channel. The view owns all arrays so a decoder or
 * renderer cannot mutate cached model data through this boundary.</p>
 */
public record ModelGeometryView(
        int id,
        int[] vertexPositions,
        int[] triangleIndices,
        short[] triangleColors,
        int[] triangleAlphas,
        int[] triangleTextures
) {
    public ModelGeometryView {
        if (id < 0 || vertexPositions == null || triangleIndices == null) {
            throw new IllegalArgumentException("Model geometry identity and arrays are required");
        }
        if (vertexPositions.length % 3 != 0 || triangleIndices.length % 3 != 0) {
            throw new IllegalArgumentException("Model geometry arrays must contain complete triples");
        }
        int vertexCount = vertexPositions.length / 3;
        for (int index : triangleIndices) {
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException("Model triangle index is outside the vertex array");
            }
        }
        int triangleCount = triangleIndices.length / 3;
        triangleColors = optionalFaceArray(triangleColors, triangleCount, "colors");
        triangleAlphas = optionalFaceArray(triangleAlphas, triangleCount, "alphas");
        triangleTextures = optionalFaceArray(triangleTextures, triangleCount, "textures");
        vertexPositions = vertexPositions.clone();
        triangleIndices = triangleIndices.clone();
    }

    private static short[] optionalFaceArray(short[] values, int triangleCount, String name) {
        if (values == null || values.length == 0) return new short[0];
        if (values.length != triangleCount) {
            throw new IllegalArgumentException("Model " + name + " count does not match triangles");
        }
        return values.clone();
    }

    private static int[] optionalFaceArray(int[] values, int triangleCount, String name) {
        if (values == null || values.length == 0) return new int[0];
        if (values.length != triangleCount) {
            throw new IllegalArgumentException("Model " + name + " count does not match triangles");
        }
        return values.clone();
    }

    public int vertexCount() {
        return vertexPositions.length / 3;
    }

    public int triangleCount() {
        return triangleIndices.length / 3;
    }

    @Override
    public int[] vertexPositions() {
        return vertexPositions.clone();
    }

    @Override
    public int[] triangleIndices() {
        return triangleIndices.clone();
    }

    @Override
    public short[] triangleColors() {
        return triangleColors.clone();
    }

    @Override
    public int[] triangleAlphas() {
        return triangleAlphas.clone();
    }

    @Override
    public int[] triangleTextures() {
        return triangleTextures.clone();
    }
}
