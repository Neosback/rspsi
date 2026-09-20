package com.rspsi.editor.corpus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime registry for built-in and plugin-provided cache feature extractors. */
public final class RegionFeatureRegistry {
    private final Map<String, RegionFeatureExtractor> extractors = new LinkedHashMap<>();

    public synchronized AutoCloseable register(RegionFeatureExtractor extractor) {
        Objects.requireNonNull(extractor, "extractor");
        String id = Objects.requireNonNull(extractor.id(), "extractor id").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Extractor id cannot be empty");
        if (extractors.putIfAbsent(id, extractor) != null) {
            throw new IllegalArgumentException("Duplicate region feature extractor: " + id);
        }
        return () -> unregister(id);
    }

    public synchronized void unregister(String id) {
        if (id != null) extractors.remove(id);
    }

    public synchronized List<RegionFeatureExtractor> extractors() {
        return List.copyOf(extractors.values());
    }

    public synchronized RegionFingerprint fingerprint(RegionFeatureContext context) {
        Map<String, FeatureBlock> blocks = new LinkedHashMap<>();
        for (RegionFeatureExtractor extractor : extractors.values()) {
            for (FeatureBlock block : extractor.extract(context)) {
                if (blocks.putIfAbsent(block.id(), block) != null) {
                    throw new IllegalStateException("Duplicate fingerprint block: " + block.id());
                }
            }
        }
        var region = context.region();
        return new RegionFingerprint(region.regionId(), region.regionX(), region.regionY(), blocks);
    }
}
