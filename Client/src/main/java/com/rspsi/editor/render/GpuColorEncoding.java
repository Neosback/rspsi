package com.rspsi.editor.render;

/** Encoding of the per-vertex color value retained for a GPU submission. */
public enum GpuColorEncoding {
    /** Packed OSRS/Jagex HSL; the backend applies the selected client palette. */
    PACKED_JAGEX_HSL,
    /** Client lightness scalar used to modulate a sampled texture. */
    TEXTURE_LIGHTNESS
}
