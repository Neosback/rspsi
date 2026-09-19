package com.rspsi.editor.settings;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete metadata and default for a registered setting. */
public record SettingSpec<T>(
        SettingKey<T> key,
        T defaultValue,
        SettingScope scope,
        String label,
        String description,
        Set<SettingInvalidation> invalidations,
        Double minimum,
        Double maximum,
        List<T> options,
        String category,
        int order
) {
    public SettingSpec {
        key = Objects.requireNonNull(key, "setting key");
        defaultValue = Objects.requireNonNull(defaultValue, "setting default");
        scope = Objects.requireNonNull(scope, "setting scope");
        label = text(label, "setting label");
        description = text(description, "setting description");
        invalidations = Set.copyOf(Objects.requireNonNull(invalidations, "setting invalidations"));
        options = List.copyOf(Objects.requireNonNull(options, "setting options"));
        category = text(category != null && !category.isBlank() ? category : "General", "setting category");
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("Setting range is invalid");
        }
        if (!options.isEmpty() && !options.contains(defaultValue)) {
            throw new IllegalArgumentException("Setting default is not one of its options");
        }
    }

    /** Backwards-compatible constructor defaulting to category "General" and order 0. */
    public SettingSpec(SettingKey<T> key, T defaultValue, SettingScope scope,
                       String label, String description, Set<SettingInvalidation> invalidations,
                       Double minimum, Double maximum, List<T> options) {
        this(key, defaultValue, scope, label, description, invalidations, minimum, maximum, options, "General", 0);
    }

    public SettingSpec<T> withCategory(String newCategory) {
        return new SettingSpec<>(key, defaultValue, scope, label, description, invalidations,
                minimum, maximum, options, newCategory, order);
    }

    public SettingSpec<T> withOrder(int newOrder) {
        return new SettingSpec<>(key, defaultValue, scope, label, description, invalidations,
                minimum, maximum, options, category, newOrder);
    }

    public static <T> SettingSpec<T> of(SettingKey<T> key, T defaultValue,
                                        SettingScope scope, String label, String description,
                                        Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, null, null, List.of(), "General", 0);
    }

    public static <T> SettingSpec<T> of(SettingKey<T> key, T defaultValue,
                                        SettingScope scope, String category,
                                        String label, String description,
                                        Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, null, null, List.of(), category, 0);
    }

    public static SettingSpec<Integer> integer(SettingKey<Integer> key, int defaultValue,
                                               int minimum, int maximum, SettingScope scope,
                                               String label, String description,
                                               Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, (double) minimum, (double) maximum, List.of(), "General", 0);
    }

    public static SettingSpec<Integer> integer(SettingKey<Integer> key, int defaultValue,
                                               int minimum, int maximum, SettingScope scope,
                                               String category, String label, String description,
                                               Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, (double) minimum, (double) maximum, List.of(), category, 0);
    }

    public static SettingSpec<Double> decimal(SettingKey<Double> key, double defaultValue,
                                              double minimum, double maximum, SettingScope scope,
                                              String label, String description,
                                              Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, minimum, maximum, List.of(), "General", 0);
    }

    public static SettingSpec<Double> decimal(SettingKey<Double> key, double defaultValue,
                                              double minimum, double maximum, SettingScope scope,
                                              String category, String label, String description,
                                              Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, minimum, maximum, List.of(), category, 0);
    }

    public static <T> SettingSpec<T> enumeration(SettingKey<T> key, T defaultValue,
                                                 List<T> options, SettingScope scope,
                                                 String label, String description,
                                                 Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, null, null, options, "General", 0);
    }

    public static <T> SettingSpec<T> enumeration(SettingKey<T> key, T defaultValue,
                                                 List<T> options, SettingScope scope,
                                                 String category, String label, String description,
                                                 Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, null, null, options, category, 0);
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
