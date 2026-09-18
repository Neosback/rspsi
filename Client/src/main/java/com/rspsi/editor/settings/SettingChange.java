package com.rspsi.editor.settings;

import java.util.Objects;

/** Immutable notification emitted when one setting layer changes. */
public record SettingChange(
        SettingKey<?> key,
        SettingScope scope,
        Object previousValue,
        Object newValue,
        java.util.Set<SettingInvalidation> invalidations
) {
    public SettingChange {
        key = Objects.requireNonNull(key, "setting key");
        scope = Objects.requireNonNull(scope, "setting scope");
        previousValue = Objects.requireNonNull(previousValue, "previous setting value");
        newValue = Objects.requireNonNull(newValue, "new setting value");
        invalidations = java.util.Set.copyOf(Objects.requireNonNull(invalidations, "setting invalidations"));
    }
}
