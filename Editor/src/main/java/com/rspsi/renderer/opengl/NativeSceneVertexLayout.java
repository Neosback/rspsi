package com.rspsi.renderer.opengl;

/**
 * Native vanilla scene streams shared by the zone uploader and renderer
 * diagnostics. Renderer-neutral {@code GpuSceneVertex} remains richer than
 * these backend-specific packed streams.
 */
final class NativeSceneVertexLayout {
    /** Position XYZ + UV. Stable geometry stream. */
    static final int GEOMETRY_FLOATS_PER_VERTEX = 5;

    /** Interpolated/source vertex color-light data: encoded value + derived RGB. */
    static final int VERTEX_SHADING_FLOATS_PER_VERTEX = 4;

    /**
     * Packed face/material metadata: two uints instead of three floats.
     *
     * <p>word0 = alpha[7:0], renderType[22:8], depthBias[30:23], terrain[31].
     * word1 = priority[7:0], textureId+1[31:8]. A zero texture code means
     * untextured.</p>
     */
    static final int FACE_METADATA_INTS_PER_VERTEX = 2;
    static final int FACE_METADATA_BYTES_PER_VERTEX =
            FACE_METADATA_INTS_PER_VERTEX * Integer.BYTES;

    /** Optional normal XYZ + magnitude stream, enabled only by a consuming shader. */
    static final int NORMAL_FLOATS_PER_VERTEX = 4;

    /** Current vanilla resident bytes across mandatory vertex/metadata streams. */
    static final int BYTES_PER_VERTEX =
            (GEOMETRY_FLOATS_PER_VERTEX + VERTEX_SHADING_FLOATS_PER_VERTEX) * Float.BYTES
                    + FACE_METADATA_BYTES_PER_VERTEX;

    static final int NORMAL_BYTES_PER_VERTEX = NORMAL_FLOATS_PER_VERTEX * Float.BYTES;

    private NativeSceneVertexLayout() {
    }
}
