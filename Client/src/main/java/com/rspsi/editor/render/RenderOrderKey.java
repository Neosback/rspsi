package com.rspsi.editor.render;

import java.util.Objects;

/**
 * Backend-neutral render-order state carried by one GPU draw command.
 *
 * <p>This makes the pieces that affect RuneScape ordering explicit in one
 * contract instead of letting each renderer infer them independently. The
 * current native baseline is deliberately two-sided until winding parity is
 * proven for every relevant cache-model and shaped-tile family.</p>
 */
public record RenderOrderKey(
        int modelPriority,
        int facePriority,
        GpuDrawCommand.RenderMode depthMode,
        int faceBias,
        FacingPolicy facingPolicy
) {
    public enum FacingPolicy {
        TWO_SIDED,
        CULL_CLOCKWISE
    }

    public RenderOrderKey {
        depthMode = Objects.requireNonNull(depthMode, "depthMode");
        facingPolicy = Objects.requireNonNull(facingPolicy, "facingPolicy");
        if (modelPriority < 0 || modelPriority > 255
                || facePriority < 0 || facePriority > 255
                || faceBias < 0 || faceBias > 255) {
            throw new IllegalArgumentException("Invalid render-order key");
        }
    }

    /**
     * Builds the current native baseline key from a neutral command.
     *
     * <p>Scene-layer ordinal is the explicit model/category priority already
     * carried by the scene packet. Face priority and face bias remain their
     * authored cache values. Facing stays two-sided until the winding fixture
     * backlog is complete.</p>
     */
    public static RenderOrderKey from(GpuDrawCommand command) {
        Objects.requireNonNull(command, "command");
        return new RenderOrderKey(
                command.layer().ordinal(),
                command.priority(),
                command.renderMode(),
                command.depthBias(),
                FacingPolicy.TWO_SIDED);
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
                && faceBias == other.faceBias
                && facingPolicy == other.facingPolicy;
    }
}
