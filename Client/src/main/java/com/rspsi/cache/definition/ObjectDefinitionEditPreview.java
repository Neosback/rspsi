package com.rspsi.cache.definition;

import java.util.Objects;

/**
 * Memory-only result of applying and codec-round-tripping a definition edit.
 *
 * <p>No cache write is implied by this value. The encoded bytes are the
 * canonical payload that a later explicit output-cache commit may stage.</p>
 */
public record ObjectDefinitionEditPreview(
        ObjectDefinitionRawView before,
        ObjectDefinitionRawView after,
        byte[] encodedBytes
) {
    public ObjectDefinitionEditPreview {
        before = Objects.requireNonNull(before, "before");
        after = Objects.requireNonNull(after, "after");
        if (before.id() != after.id()) {
            throw new IllegalArgumentException("Definition preview ids must match");
        }
        encodedBytes = encodedBytes == null ? new byte[0] : encodedBytes.clone();
        if (encodedBytes.length == 0) {
            throw new IllegalArgumentException("Definition preview needs encoded bytes");
        }
    }

    @Override
    public byte[] encodedBytes() {
        return encodedBytes.clone();
    }
}
