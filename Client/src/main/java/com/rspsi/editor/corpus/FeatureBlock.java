package com.rspsi.editor.corpus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Sparse named feature vector used for explainable region similarity. */
public record FeatureBlock(String id, Map<String, Double> values) {
    public FeatureBlock {
        id = Objects.requireNonNull(id, "id").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Feature block id cannot be empty");
        Map<String, Double> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) return;
                if (value != 0.0) copy.put(key, value);
            });
        }
        values = Map.copyOf(copy);
    }

    public double magnitude() {
        double sum = 0.0;
        for (double value : values.values()) sum += value * value;
        return Math.sqrt(sum);
    }
}
