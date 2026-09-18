package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Set;

/**
 * Native-backend-neutral lifecycle for GPU scene resources. Implementations
 * may use OpenGL, WebGPU, or another API, but those types must not cross this
 * interface.
 */
public interface GpuSceneUploader extends AutoCloseable {
    /** Uploads the already-derived, world-space plan; no cache access occurs here. */
    void upload(GpuUploadPlan plan);

    /** Compatibility bridge for callers that still hold the tile packet. */
    default void upload(GpuScenePacket packet) {
        upload(new GpuUploadPlanBuilder().build(packet));
    }

    void invalidate(Set<TileCoordinate> tiles);

    @Override
    void close();
}
