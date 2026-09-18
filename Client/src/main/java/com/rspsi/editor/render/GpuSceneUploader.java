package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Set;

/**
 * Native-backend-neutral lifecycle for GPU scene resources. Implementations
 * may use OpenGL, WebGPU, or another API, but those types must not cross this
 * interface.
 */
public interface GpuSceneUploader extends AutoCloseable {
    void upload(GpuScenePacket packet);

    void invalidate(Set<TileCoordinate> tiles);

    @Override
    void close();
}
