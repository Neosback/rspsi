package com.rspsi.editor.transform;

import java.util.Objects;
import java.util.Optional;

/**
 * Orthogonal world-fragment transform. Mirrors are applied first, followed by
 * quarter-turn rotation.
 */
public record WorldFragmentTransform(
        int quarterTurns,
        boolean mirrorX,
        boolean mirrorY,
        Optional<Pivot> pivot
) {
    public WorldFragmentTransform {
        if (quarterTurns < 0 || quarterTurns > 3) {
            throw new IllegalArgumentException("Quarter turns must be in [0, 3]");
        }
        pivot = Objects.requireNonNull(pivot, "pivot");
    }

    public WorldFragmentTransform(int quarterTurns, boolean mirrorX, boolean mirrorY) {
        this(quarterTurns, mirrorX, mirrorY, Optional.empty());
    }

    public static WorldFragmentTransform identity() {
        return new WorldFragmentTransform(0, false, false);
    }

    public static WorldFragmentTransform rotate(int quarterTurns) {
        return new WorldFragmentTransform(quarterTurns, false, false);
    }

    public static WorldFragmentTransform mirrorX() {
        return new WorldFragmentTransform(0, true, false);
    }

    public static WorldFragmentTransform mirrorY() {
        return new WorldFragmentTransform(0, false, true);
    }

    public WorldFragmentTransform around(Pivot pivot) {
        return new WorldFragmentTransform(
                quarterTurns, mirrorX, mirrorY, Optional.of(Objects.requireNonNull(pivot, "pivot")));
    }

    /** Absolute source tile that must remain at the same world coordinate. */
    public record Pivot(int x, int y) {
        public Pivot {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("Fragment pivot cannot be negative");
            }
        }
    }
}
