package com.rspsi.renderer.opengl;

/**
 * Native vanilla scene streams shared by the zone uploader and renderer
 * diagnostics. Renderer-neutral {@code GpuSceneVertex} remains richer than
 * these backend-specific packed streams.
 */
final class NativeSceneVertexLayout {
    /** Position XYZ + UV. Stable geometry stream. */
    static final int GEOMETRY_FLOATS_PER_VERTEX = 5;

    /** Encoded color/light + alpha + render type + RGB + priority. */
    static final int SHADING_FLOATS_PER_VERTEX = 7;

    /** Optional normal XYZ + magnitude stream, enabled only by a consuming shader. */
    static final int NORMAL_FLOATS_PER_VERTEX = 4;

    /** Current vanilla resident bytes across both mandatory vertex streams. */
    static final int BYTES_PER_VERTEX =
            (GEOMETRY_FLOATS_PER_VERTEX + SHADING_FLOATS_PER_VERTEX) * Float.BYTES;

    static final int NORMAL_BYTES_PER_VERTEX = NORMAL_FLOATS_PER_VERTEX * Float.BYTES;

    private NativeSceneVertexLayout() {
    }
}
