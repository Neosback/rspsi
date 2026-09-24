package com.rspsi.editor.render;

import java.util.Objects;

/**
 * Backend-neutral native draw-state key.
 *
 * <p>Face priority controls submission order and is deliberately not part of
 * this key. Commands with different priorities may still share one ordered
 * multi-draw when their actual GPU state is identical.</p>
 */
public final class RenderOrderKey {
    private RenderOrderKey() {
    }

    /**
     * Packs the command state that must remain identical inside one native
     * draw batch. GpuDrawCommand validates the component ranges.
     */
    public static long nativeState(GpuDrawCommand command) {
        Objects.requireNonNull(command, "command");
        // Texture layer and face depth bias are packed into the native
        // per-vertex face-material stream. They no longer require a draw-state
        // break. Terrain remains distinct because OpenGL back-face culling is
        // fixed-function state: client models cull, shaped terrain is two-sided.
        long key = command.layer() == SceneLayer.Kind.TERRAIN ? 1L : 0L;
        key = key * 8L + command.renderMode().ordinal();
        return key;
    }

    public static boolean sameNativeState(GpuDrawCommand first, GpuDrawCommand second) {
        return nativeState(first) == nativeState(second);
    }
}
