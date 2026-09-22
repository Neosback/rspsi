package com.rspsi.editor.render;

import java.util.Objects;

/**
 * Backend-neutral render-order state carried by one GPU draw command.
 *
 * <p>This key contains only state that actually affects command ordering or
 * native batching. Back-face culling is a viewport-level validation mode and
 * is intentionally not encoded here.</p>
 */
public record RenderOrderKey(
        int modelPriority,
        int facePriority,
        GpuDrawCommand.RenderMode depthMode,
        int faceBias
) {
    public RenderOrderKey {
        depthMode = Objects.requireNonNull(depthMode, "depthMode");
        if (modelPriority < 0 || modelPriority > 255
                || facePriority < 0 || facePriority > 255
                || faceBias < 0 || faceBias > 255) {
            throw new IllegalArgumentException("Invalid render-order key");
        }
    }

    public static RenderOrderKey from(GpuDrawCommand command) {
        Objects.requireNonNull(command, "command");
        return new RenderOrderKey(
                command.layer().ordinal(),
                command.priority(),
                command.renderMode(),
                command.depthBias());
    }

    /**
     * Returns whether two commands may share native draw state after material
     * identity is checked separately. Face priority is intentionally excluded:
     * it controls ordering, not GL state, and ordered multi-draw preserves it.
     */
    public boolean sameNativeState(RenderOrderKey other) {
        Objects.requireNonNull(other, "other");
        return modelPriority == other.modelPriority
                && depthMode == other.depthMode
                && faceBias == other.faceBias;
    }
}
