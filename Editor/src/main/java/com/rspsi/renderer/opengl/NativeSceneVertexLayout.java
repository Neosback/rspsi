package com.rspsi.renderer.opengl;

/**
 * Native vanilla scene-vertex layout shared by the zone uploader and renderer
 * diagnostics. Renderer-neutral {@code GpuSceneVertex} remains richer than this
 * backend-specific packed stream.
 */
final class NativeSceneVertexLayout {
    static final int FLOATS_PER_VERTEX = 12;
    static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * Float.BYTES;

    private NativeSceneVertexLayout() {
    }
}
