package com.rspsi.cache.data;

import java.util.Objects;

/** Discoverable metadata for one FileStore/cache data family. */
public record DecodedDataFamily(
        String id,
        String label,
        Group group,
        int count,
        boolean queryable) {

    public DecodedDataFamily {
        id = Objects.requireNonNull(id, "id").trim();
        label = Objects.requireNonNull(label, "label").trim();
        group = Objects.requireNonNull(group, "group");
        if (id.isEmpty() || label.isEmpty()) {
            throw new IllegalArgumentException("Decoded data family text cannot be empty");
        }
        if (count < -1) throw new IllegalArgumentException("Decoded data count cannot be less than -1");
    }

    public enum Group {
        AUDIO,
        VISUAL,
        WORLD,
        DEFINITIONS,
        ENGINE,
        INTEGRATION
    }
}
