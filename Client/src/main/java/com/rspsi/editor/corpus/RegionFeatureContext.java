package com.rspsi.editor.corpus;

import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.WorldRegion;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Input to feature extractors. Attachments let integrations add decoded data
 * such as NPC spawns, world-map records or server metadata without coupling
 * the corpus core to those systems.
 */
public record RegionFeatureContext(
        WorldRegion region,
        AssetRepository assets,
        Map<String, Object> attachments) {

    public RegionFeatureContext {
        region = Objects.requireNonNull(region, "region");
        assets = Objects.requireNonNull(assets, "assets");
        attachments = Map.copyOf(attachments == null ? Map.of() : attachments);
    }

    public <T> Optional<T> attachment(String id, Class<T> type) {
        Object value = attachments.get(id);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
}
