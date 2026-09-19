package com.rspsi.editor.knowledge;

import com.rspsi.editor.model.ObjectCategory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Statistical summary and structural profile of an editor region or world document (RegionProfile 2.0).
 *
 * <p>Supports typed extensible metrics ({@link MetricKey}) and frequency histograms alongside
 * traditional terrain and object distributions.</p>
 */
public record RegionProfile(
        int regionId,
        int width,
        int length,
        int planes,
        int tileCount,
        int minHeight,
        int maxHeight,
        double averageHeight,
        Map<Integer, Integer> underlayDistribution,
        Map<Integer, Integer> overlayDistribution,
        Map<ObjectCategory, Integer> objectCategoryDistribution,
        Map<String, Object> customMetrics
) {
    /** Compatibility constructor for the 11-argument profile. */
    public RegionProfile(
            int regionId,
            int width,
            int length,
            int planes,
            int tileCount,
            int minHeight,
            int maxHeight,
            double averageHeight,
            Map<Integer, Integer> underlayDistribution,
            Map<Integer, Integer> overlayDistribution,
            Map<ObjectCategory, Integer> objectCategoryDistribution
    ) {
        this(regionId, width, length, planes, tileCount, minHeight, maxHeight, averageHeight,
                underlayDistribution, overlayDistribution, objectCategoryDistribution, Map.of());
    }

    public RegionProfile {
        Objects.requireNonNull(underlayDistribution, "underlayDistribution");
        Objects.requireNonNull(overlayDistribution, "overlayDistribution");
        Objects.requireNonNull(objectCategoryDistribution, "objectCategoryDistribution");
        Objects.requireNonNull(customMetrics, "customMetrics");
        underlayDistribution = Collections.unmodifiableMap(Map.copyOf(underlayDistribution));
        overlayDistribution = Collections.unmodifiableMap(Map.copyOf(overlayDistribution));
        objectCategoryDistribution = Collections.unmodifiableMap(Map.copyOf(objectCategoryDistribution));
        customMetrics = Collections.unmodifiableMap(Map.copyOf(customMetrics));
    }

    /** Returns the most frequent underlay ID in the region, if any underlays exist. */
    public OptionalInt dominantUnderlay() {
        return underlayDistribution.entrySet().stream()
                .filter(e -> e.getKey() > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> OptionalInt.of(e.getKey()))
                .orElse(OptionalInt.empty());
    }

    /** Returns the most frequent overlay ID in the region, if any overlays exist. */
    public OptionalInt dominantOverlay() {
        return overlayDistribution.entrySet().stream()
                .filter(e -> e.getKey() > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> OptionalInt.of(e.getKey()))
                .orElse(OptionalInt.empty());
    }

    /** Returns the value of a typed metric if present, or calculates standard metrics. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> metric(MetricKey<T> key) {
        Objects.requireNonNull(key, "key");
        if (key.equals(MetricKey.AVERAGE_HEIGHT)) {
            return Optional.of((T) Double.valueOf(averageHeight));
        }
        if (key.equals(MetricKey.HEIGHT_VARIANCE)) {
            return Optional.of((T) Integer.valueOf(maxHeight - minHeight));
        }

        Object val = customMetrics.get(key.name());
        if (val != null && key.type().isInstance(val)) {
            return Optional.of((T) val);
        }
        return Optional.empty();
    }
}
