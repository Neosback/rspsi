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
        long key = command.textureId() + 1L;
        key = key * 17L + command.layer().ordinal();
        key = key * 257L + command.depthBias();
        key = key * 8L + command.renderMode().ordinal();
        return key;
    }

    public static boolean sameNativeState(GpuDrawCommand first, GpuDrawCommand second) {
        return nativeState(first) == nativeState(second);
    }
}
