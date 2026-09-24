package com.rspsi.renderer.opengl;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Reusable off-heap staging buffers for zone uploads.
 *
 * <p>Buffers grow geometrically and are explicitly freed on close. Because
 * every upload fully rewrites the staging contents, growth never needs to copy
 * old data.</p>
 */
final class GpuUploadScratch implements AutoCloseable {
    static final int INITIAL_VERTEX_FLOATS = 65_536;
    static final int INITIAL_INDICES = 16_384;

    private FloatBuffer vertices;
    private IntBuffer indices;
    private int vertexGrowths;
    private int indexGrowths;

    FloatBuffer vertices(int requiredFloats) {
        if (requiredFloats < 0) {
            throw new IllegalArgumentException("Required float count cannot be negative");
        }
        if (vertices == null || vertices.capacity() < requiredFloats) {
            int next = nextCapacity(vertices == null ? 0 : vertices.capacity(),
                    requiredFloats, INITIAL_VERTEX_FLOATS);
            if (vertices != null) MemoryUtil.memFree(vertices);
            vertices = MemoryUtil.memAllocFloat(next);
            vertexGrowths++;
        }
        vertices.clear();
        return vertices;
    }

    IntBuffer indices(int requiredIndices) {
        if (requiredIndices < 0) {
            throw new IllegalArgumentException("Required index count cannot be negative");
        }
        if (indices == null || indices.capacity() < requiredIndices) {
            int next = nextCapacity(indices == null ? 0 : indices.capacity(),
                    requiredIndices, INITIAL_INDICES);
            if (indices != null) MemoryUtil.memFree(indices);
            indices = MemoryUtil.memAllocInt(next);
            indexGrowths++;
        }
        indices.clear();
        return indices;
    }

    int vertexCapacityFloats() {
        return vertices == null ? 0 : vertices.capacity();
    }

    int indexCapacity() {
        return indices == null ? 0 : indices.capacity();
    }

    int vertexGrowths() {
        return vertexGrowths;
    }

    int indexGrowths() {
        return indexGrowths;
    }

    @Override
    public void close() {
        if (vertices != null) {
            MemoryUtil.memFree(vertices);
            vertices = null;
        }
        if (indices != null) {
            MemoryUtil.memFree(indices);
            indices = null;
        }
    }

    static int nextCapacity(int current, int required, int minimum) {
        if (required < 0 || minimum <= 0) {
            throw new IllegalArgumentException("Invalid staging-buffer capacity request");
        }
        int capacity = Math.max(current, minimum);
        if (required <= capacity) return capacity;
        while (capacity < required) {
            if (capacity > Integer.MAX_VALUE / 2) return required;
            capacity *= 2;
        }
        return capacity;
    }
}
