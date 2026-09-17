package com.rspsi.editor.model;

import java.util.Objects;

/** Resolves an unrotated OSRS object footprint for instance materialization. */
@FunctionalInterface
public interface InstanceObjectFootprintResolver {
    Footprint resolve(WorldObject object);

    record Footprint(int width, int length) {
        public Footprint {
            if (width <= 0 || length <= 0) {
                throw new IllegalArgumentException("Object footprint must be positive");
            }
        }
    }

    static InstanceObjectFootprintResolver unit() {
        return ignored -> new Footprint(1, 1);
    }

    static InstanceObjectFootprintResolver requireNonNull(InstanceObjectFootprintResolver resolver) {
        return Objects.requireNonNull(resolver, "resolver");
    }
}
