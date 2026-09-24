package com.rspsi.editor.render;

/**
 * Independent fingerprints for the immutable data streams of one native zone.
 *
 * <p>Vanilla keeps stable geometry, per-vertex color/light data, face-local
 * shading metadata and indices independently resident. Normals remain an
 * optional auxiliary stream until a consuming shader enables them.</p>
 */
public record GpuZoneStreamFingerprints(
        long geometry,
        long vertexShading,
        long faceShading,
        long indices,
        long normals,
        long pickerIds
) {
    /** Compatibility constructor from before picker IDs were a separate stream. */
    public GpuZoneStreamFingerprints(
            long geometry, long vertexShading, long faceShading,
            long indices, long normals) {
        this(geometry, vertexShading, faceShading, indices, normals, 0L);
    }

    /** Compatibility constructor for callers that still model shading as one stream. */
    public GpuZoneStreamFingerprints(long geometry, long shading, long indices, long normals) {
        this(geometry, shading, 0L, indices, normals, 0L);
    }

    /** Aggregate identity for callers that do not need the split shading boundary. */
    public long shading() {
        long hash = 1125899906842597L;
        hash = hash * 31L + vertexShading;
        hash = hash * 31L + faceShading;
        return hash;
    }

    public long nativeFingerprint() {
        long hash = 1125899906842597L;
        hash = hash * 31L + geometry;
        hash = hash * 31L + vertexShading;
        hash = hash * 31L + faceShading;
        hash = hash * 31L + indices;
        return hash;
    }
}
