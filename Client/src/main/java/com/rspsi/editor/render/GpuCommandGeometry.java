package com.rspsi.editor.render;

import java.util.function.Consumer;

/**
 * Read-only command geometry used by camera-dependent runtime algorithms.
 *
 * <p>Commands remain globally ordered while their referenced vertices may
 * live in one flat buffer or in resident 8x8 world-zone buffers.</p>
 */
public interface GpuCommandGeometry {
    int commandCount();

    GpuDrawCommand command(int commandIndex);

    GpuSceneVertex indexedVertex(int commandIndex, int indexOffset);

    int vertexCount();

    int indexCount();

    void forEachUniqueVertex(Consumer<GpuSceneVertex> consumer);
}
