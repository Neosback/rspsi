package com.rspsi.editor.render;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Scene-wide flat compatibility geometry backed by immutable per-tile fragments.
 *
 * <p>The native path can query counts and individual indexed vertices without
 * ever creating giant scene-wide vertex/index lists. The ordinary List views
 * materialize both lists together only when a compatibility caller actually
 * reads flat geometry (for example picking, software/reference rendering, or
 * diagnostics that explicitly inspect the flat buffers).</p>
 */
final class LazyGpuFlatGeometry {
    private final List<GpuUploadPlan> fragments;
    private final int[] vertexStarts;
    private final int[] indexStarts;
    private final List<GpuSceneVertex> verticesView;
    private final List<Integer> indicesView;
    private volatile List<GpuSceneVertex> materializedVertices;
    private volatile List<Integer> materializedIndices;
    private volatile int materializationCount;

    LazyGpuFlatGeometry(List<GpuUploadPlan> fragments) {
        this.fragments = List.copyOf(Objects.requireNonNull(fragments, "fragments"));
        this.vertexStarts = new int[this.fragments.size() + 1];
        this.indexStarts = new int[this.fragments.size() + 1];

        int vertices = 0;
        int indices = 0;
        for (int i = 0; i < this.fragments.size(); i++) {
            GpuUploadPlan fragment = Objects.requireNonNull(this.fragments.get(i), "fragment");
            vertexStarts[i] = vertices;
            indexStarts[i] = indices;
            vertices = Math.addExact(vertices, fragment.vertexCount());
            indices = Math.addExact(indices, fragment.indexCount());
        }
        vertexStarts[this.fragments.size()] = vertices;
        indexStarts[this.fragments.size()] = indices;
        this.verticesView = new VertexView(this);
        this.indicesView = new IndexView(this);
    }

    List<GpuSceneVertex> verticesView() {
        return verticesView;
    }

    List<Integer> indicesView() {
        return indicesView;
    }

    int vertexCount() {
        return vertexStarts[vertexStarts.length - 1];
    }

    int indexCount() {
        return indexStarts[indexStarts.length - 1];
    }

    GpuSceneVertex directVertex(int index) {
        checkIndex(index, vertexCount(), "vertex");
        List<GpuSceneVertex> materialized = materializedVertices;
        if (materialized != null) return materialized.get(index);
        int fragmentIndex = segment(vertexStarts, index);
        return fragments.get(fragmentIndex).directVertexAt(index - vertexStarts[fragmentIndex]);
    }

    int directIndex(int index) {
        checkIndex(index, indexCount(), "index");
        List<Integer> materialized = materializedIndices;
        if (materialized != null) return materialized.get(index);
        int fragmentIndex = segment(indexStarts, index);
        GpuUploadPlan fragment = fragments.get(fragmentIndex);
        return vertexStarts[fragmentIndex]
                + fragment.directIndexAt(index - indexStarts[fragmentIndex]);
    }

    int materializationCount() {
        return materializationCount;
    }

    boolean isMaterialized() {
        return materializedVertices != null;
    }

    private synchronized void materialize() {
        if (materializedVertices != null) return;

        ArrayList<GpuSceneVertex> vertices = new ArrayList<>(vertexCount());
        ArrayList<Integer> indices = new ArrayList<>(indexCount());
        for (int fragmentIndex = 0; fragmentIndex < fragments.size(); fragmentIndex++) {
            GpuUploadPlan fragment = fragments.get(fragmentIndex);
            for (int i = 0; i < fragment.vertexCount(); i++) {
                vertices.add(fragment.directVertexAt(i));
            }
            int vertexBase = vertexStarts[fragmentIndex];
            for (int i = 0; i < fragment.indexCount(); i++) {
                indices.add(vertexBase + fragment.directIndexAt(i));
            }
        }
        materializedVertices = List.copyOf(vertices);
        materializedIndices = List.copyOf(indices);
        materializationCount++;
    }

    static LazyGpuFlatGeometry ownerOf(List<?> list) {
        return list instanceof GeometryView<?> view ? view.owner : null;
    }

    private static int segment(int[] starts, int index) {
        int low = 0;
        int high = starts.length - 2;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (index < starts[mid]) {
                high = mid - 1;
            } else if (index >= starts[mid + 1]) {
                low = mid + 1;
            } else {
                return mid;
            }
        }
        throw new IndexOutOfBoundsException("No flat-geometry segment for index " + index);
    }

    private static void checkIndex(int index, int size, String label) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(label + " " + index);
        }
    }

    private abstract static class GeometryView<E> extends AbstractList<E>
            implements RandomAccess {
        final LazyGpuFlatGeometry owner;

        private GeometryView(LazyGpuFlatGeometry owner) {
            this.owner = owner;
        }

        @Override
        public int size() {
            return sizeOf(owner);
        }

        abstract int sizeOf(LazyGpuFlatGeometry owner);
    }

    private static final class VertexView extends GeometryView<GpuSceneVertex> {
        private VertexView(LazyGpuFlatGeometry owner) {
            super(owner);
        }

        @Override
        public GpuSceneVertex get(int index) {
            owner.materialize();
            return owner.materializedVertices.get(index);
        }

        @Override
        int sizeOf(LazyGpuFlatGeometry owner) {
            return owner.vertexCount();
        }
    }

    private static final class IndexView extends GeometryView<Integer> {
        private IndexView(LazyGpuFlatGeometry owner) {
            super(owner);
        }

        @Override
        public Integer get(int index) {
            owner.materialize();
            return owner.materializedIndices.get(index);
        }

        @Override
        int sizeOf(LazyGpuFlatGeometry owner) {
            return owner.indexCount();
        }
    }
}
