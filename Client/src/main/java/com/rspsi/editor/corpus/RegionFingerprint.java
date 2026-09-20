package com.rspsi.editor.corpus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explainable feature fingerprint for one 64x64 OSRS map square or other region document. */
public record RegionFingerprint(
        int regionId,
        int regionX,
        int regionY,
        Map<String, FeatureBlock> blocks) {

    public RegionFingerprint {
        if (regionId < 0 || regionX < 0 || regionY < 0) {
            throw new IllegalArgumentException("Region identity cannot be negative");
        }
        Map<String, FeatureBlock> copy = new LinkedHashMap<>();
        if (blocks != null) {
            blocks.forEach((id, block) -> {
                FeatureBlock checked = Objects.requireNonNull(block, "feature block");
                copy.put(id, checked);
            });
        }
        blocks = Map.copyOf(copy);
    }

    public Optional<FeatureBlock> block(String id) {
        return Optional.ofNullable(blocks.get(id));
    }
}
