package com.rspsi.editor.knowledge;

import java.util.Objects;

/**
 * Type-safe key for querying extensible region or world statistical metrics.
 *
 * @param <T> the value type associated with this metric
 */
public record MetricKey<T>(String name, Class<T> type) {
    public static final MetricKey<Double> AVERAGE_HEIGHT = of("average_height", Double.class);
    public static final MetricKey<Integer> HEIGHT_VARIANCE = of("height_variance", Integer.class);
    public static final MetricKey<Double> MAX_SLOPE = of("max_slope", Double.class);
    public static final MetricKey<Double> WALKABLE_RATIO = of("walkable_ratio", Double.class);
    public static final MetricKey<Double> WATER_RATIO = of("water_ratio", Double.class);
    public static final MetricKey<Double> OBJECT_DENSITY = of("object_density", Double.class);
    public static final MetricKey<Integer> BUILDING_COUNT = of("building_count", Integer.class);

    public MetricKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Metric name cannot be blank");
        }
    }

    public static <T> MetricKey<T> of(String name, Class<T> type) {
        return new MetricKey<>(name.trim().toLowerCase(), type);
    }
}
