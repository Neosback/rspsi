package com.rspsi.cache.definition;

/** Neutral subset of an object definition required to build map collision. */
public record ObjectCollisionView(
        int id,
        int width,
        int length,
        int blockWalk,
        boolean blockProjectile,
        boolean breakRouteFinding,
        int clipType
) {
    /** Compatibility constructor for callers that predate the neutral clip-type field. */
    public ObjectCollisionView(int id, int width, int length, int blockWalk,
                               boolean blockProjectile, boolean breakRouteFinding) {
        this(id, width, length, blockWalk, blockProjectile, breakRouteFinding,
                blockWalk == 0 ? 0 : 2);
    }

    public ObjectCollisionView {
        if (id < 0 || width <= 0 || length <= 0 || blockWalk < 0 || clipType < 0) {
            throw new IllegalArgumentException("Invalid object collision definition");
        }
    }
}
