package com.rspsi.editor.render;

/**
 * One world-space vertex in the backend-neutral GPU upload plan.
 *
 * <p>{@code pickerPlane}/{@code pickerTileX}/{@code pickerTileY}/{@code pickerSlot} carry the
 * same value across every vertex of one tile's terrain, or one object's mesh (broadcast, not
 * deduplicated). The optional GPU picker-ID pass packs tile X/Y and category per
 * {@link PickerId}; plane stays in vertex/command metadata because the 32-bit GPU key reserves
 * 14 bits for each Jagex world axis. Its single-pixel result then constrains the zone-resident
 * DDA picker, which resolves the exact plane and object metadata.</p>
 */
public record GpuSceneVertex(
        float x,
        float y,
        float z,
        float u,
        float v,
        int encodedColor,
        GpuColorEncoding colorEncoding,
        int renderType,
        int normalX,
        int normalY,
        int normalZ,
        int normalMagnitude,
        int textureId,
        int alpha,
        int priority,
        int pickerPlane,
        int pickerTileX,
        int pickerTileY,
        int pickerSlot
) {
    public GpuFaceShading faceShading() {
        return GpuFaceShading.from(this);
    }

    public GpuSceneVertex {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(u) || !Float.isFinite(v)) {
            throw new IllegalArgumentException("GPU scene vertex values must be finite");
        }
        colorEncoding = java.util.Objects.requireNonNull(colorEncoding, "colorEncoding");
        if (normalMagnitude < 0 || textureId < -1 || alpha < 0 || alpha > 255
                || priority < 0 || priority > 255 || renderType < 0) {
            throw new IllegalArgumentException("Invalid GPU scene vertex material data");
        }
        if (pickerPlane < 0 || pickerTileX < 0 || pickerTileY < 0 || pickerSlot < 0) {
            throw new IllegalArgumentException("Picker payload cannot be negative");
        }
    }
}
