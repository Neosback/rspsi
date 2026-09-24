package com.rspsi.editor.render;

/**
 * Independent fingerprints for the immutable data streams of one native zone.
 *
 * <p>The vanilla backend currently uploads geometry, shading and indices.
 * Normals remain a renderer-neutral auxiliary stream until a shader consumes
 * them, so normal-only changes do not force a vanilla VBO upload.</p>
 */
public record GpuZoneStreamFingerprints(
        long geometry,
        long shading,
        long indices,
        long normals
) {
    public long nativeFingerprint() {
        long hash = 1125899906842597L;
        hash = hash * 31L + geometry;
        hash = hash * 31L + shading;
        hash = hash * 31L + indices;
        return hash;
    }
}
