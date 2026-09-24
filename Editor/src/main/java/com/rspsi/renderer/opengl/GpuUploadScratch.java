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
    private IntBuffer faceMetadata;
    private IntBuffer markers;
    private int vertexGrowths;
    private int indexGrowths;

    FloatBuffer vertices(int requiredFloats) {
        if (requiredFloats < 0) {
            throw new IllegalArgumentException("Required float count cannot be negative");
        }
        if (vertices == null || vertices.capacity() < requiredFloats) {
            int next = nextCapacity(vertices == null ? 0 : vertices.capacity(),
                    requiredFloats, INITIAL_VERTEX_FLOATS);
            FloatBuffer replacement = MemoryUtil.memAllocFloat(next);
            if (vertices != null) MemoryUtil.memFree(vertices);
            vertices = replacement;
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
            IntBuffer replacement = MemoryUtil.memAllocInt(next);
            if (indices != null) MemoryUtil.memFree(indices);
            indices = replacement;
            indexGrowths++;
        }
        indices.clear();
        return indices;
    }

    IntBuffer faceMetadata(int requiredInts) {
        if (requiredInts < 0) {
            throw new IllegalArgumentException("Required metadata count cannot be negative");
        }
        if (faceMetadata == null || faceMetadata.capacity() < requiredInts) {
            int next = nextCapacity(faceMetadata == null ? 0 : faceMetadata.capacity(),
                    requiredInts, INITIAL_INDICES);
            IntBuffer replacement = MemoryUtil.memAllocInt(next);
            if (faceMetadata != null) MemoryUtil.memFree(faceMetadata);
            faceMetadata = replacement;
        }
        faceMetadata.clear();
        return faceMetadata;
    }

    IntBuffer markers(int requiredInts) {
        if (requiredInts < 0) {
            throw new IllegalArgumentException("Required marker count cannot be negative");
        }
        if (markers == null || markers.capacity() < requiredInts) {
            int next = nextCapacity(markers == null ? 0 : markers.capacity(),
                    requiredInts, INITIAL_INDICES);
            IntBuffer replacement = MemoryUtil.memAllocInt(next);
            if (markers != null) MemoryUtil.memFree(markers);
            markers = replacement;
        }
        markers.clear();
        return markers;
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
        if (faceMetadata != null) {
            MemoryUtil.memFree(faceMetadata);
            faceMetadata = null;
        }
        if (markers != null) {
            MemoryUtil.memFree(markers);
            markers = null;
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
