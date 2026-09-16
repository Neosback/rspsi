package com.rspsi.editor.model;

import java.util.Objects;

/** Explicit authored-plane to effective-plane relationship for a bridge tile. */
public record BridgeLink(TileCoordinate upper, TileCoordinate lower) {
    public BridgeLink {
        upper = Objects.requireNonNull(upper, "upper");
        lower = Objects.requireNonNull(lower, "lower");
        if (upper.x() != lower.x() || upper.y() != lower.y()
                || upper.plane() != lower.plane() + 1) {
            throw new IllegalArgumentException("Bridge links must connect adjacent planes at one tile");
        }
    }
}
