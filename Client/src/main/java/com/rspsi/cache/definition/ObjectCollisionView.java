package com.rspsi.cache.definition;

/** Neutral subset of an object definition required to build map collision. */
public record ObjectCollisionView(
        int id,
        int width,
        int length,
        int blockWalk,
        boolean blockProjectile,
        boolean breakRouteFinding
) {
    public ObjectCollisionView {
        if (id < 0 || width <= 0 || length <= 0 || blockWalk < 0) {
            throw new IllegalArgumentException("Invalid object collision definition");
        }
    }
}
