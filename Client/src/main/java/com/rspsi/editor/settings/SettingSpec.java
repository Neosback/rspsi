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
        List<T> options
) {
    public SettingSpec {
        key = Objects.requireNonNull(key, "setting key");
        defaultValue = Objects.requireNonNull(defaultValue, "setting default");
        scope = Objects.requireNonNull(scope, "setting scope");
        label = text(label, "setting label");
        description = text(description, "setting description");
        invalidations = Set.copyOf(Objects.requireNonNull(invalidations, "setting invalidations"));
        options = List.copyOf(Objects.requireNonNull(options, "setting options"));
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("Setting range is invalid");
        }
        if (!options.isEmpty() && !options.contains(defaultValue)) {
            throw new IllegalArgumentException("Setting default is not one of its options");
        }
    }

    public static <T> SettingSpec<T> of(SettingKey<T> key, T defaultValue,
                                        SettingScope scope, String label, String description,
                                        Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, null, null, List.of());
    }

    public static SettingSpec<Integer> integer(SettingKey<Integer> key, int defaultValue,
                                               int minimum, int maximum, SettingScope scope,
                                               String label, String description,
                                               Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, (double) minimum, (double) maximum, List.of());
    }

    public static SettingSpec<Double> decimal(SettingKey<Double> key, double defaultValue,
                                              double minimum, double maximum, SettingScope scope,
                                              String label, String description,
                                              Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, minimum, maximum, List.of());
    }

    public static <T> SettingSpec<T> enumeration(SettingKey<T> key, T defaultValue,
                                                 List<T> options, SettingScope scope,
                                                 String label, String description,
                                                 Set<SettingInvalidation> invalidations) {
        return new SettingSpec<>(key, defaultValue, scope, label, description,
                invalidations, null, null, options);
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
