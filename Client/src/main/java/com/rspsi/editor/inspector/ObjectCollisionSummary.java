package com.rspsi.editor.inspector;

/** Neutral collision details suitable for an object inspector or overlay. */
public record ObjectCollisionSummary(
        int blockWalk,
        boolean blockProjectile,
        boolean breakRouteFinding
) {
    public ObjectCollisionSummary {
        if (blockWalk < 0) {
            throw new IllegalArgumentException("Movement collision mask cannot be negative");
        }
    }
}
