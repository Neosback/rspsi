package com.rspsi.editor.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable values captured for one settings compilation or frame. */
public final class SettingsSnapshot {
    private final Map<SettingKey<?>, Object> values;

    public SettingsSnapshot(Map<SettingKey<?>, Object> values) {
        Map<SettingKey<?>, Object> copy = new LinkedHashMap<>();
        Objects.requireNonNull(values, "setting values").forEach((key, value) -> {
            Objects.requireNonNull(key, "setting key");
            Objects.requireNonNull(value, "setting value");
            if (!key.valueType().isInstance(value)) {
                throw new IllegalArgumentException("Value type mismatch for setting " + key.id());
            }
            copy.put(key, value);
        });
        this.values = Map.copyOf(copy);
    }

    public <T> T get(SettingKey<T> key) {
        Objects.requireNonNull(key, "setting key");
        Object value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Setting is not present: " + key.id());
        return key.valueType().cast(value);
    }

    public <T> T getOrDefault(SettingKey<T> key, T defaultValue) {
        Objects.requireNonNull(defaultValue, "setting default");
        Object value = values.get(key);
        return value == null ? defaultValue : key.valueType().cast(value);
    }

    public Map<SettingKey<?>, Object> values() {
        return values;
    }

    public SettingsSnapshot with(SettingKey<?> key, Object value) {
        Map<SettingKey<?>, Object> copy = new LinkedHashMap<>(values);
        copy.put(Objects.requireNonNull(key, "setting key"), Objects.requireNonNull(value, "setting value"));
        return new SettingsSnapshot(copy);
    }
}
