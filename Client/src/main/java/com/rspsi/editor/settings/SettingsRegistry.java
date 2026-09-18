package com.rspsi.editor.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry of stable setting specifications; no renderer reads it per draw. */
public final class SettingsRegistry {
    private final Map<String, SettingSpec<?>> specifications = new LinkedHashMap<>();

    public <T> SettingSpec<T> register(SettingSpec<T> specification) {
        Objects.requireNonNull(specification, "setting specification");
        String id = specification.key().id();
        if (specifications.putIfAbsent(id, specification) != null) {
            throw new IllegalArgumentException("Duplicate setting id: " + id);
        }
        return specification;
    }

    public SettingSpec<?> specification(String id) {
        SettingSpec<?> specification = specifications.get(Objects.requireNonNull(id, "setting id"));
        if (specification == null) throw new IllegalArgumentException("Unknown setting: " + id);
        return specification;
    }

    public <T> SettingSpec<T> specification(SettingKey<T> key) {
        SettingSpec<?> specification = specification(key.id());
        if (!specification.key().equals(key)) {
            throw new IllegalArgumentException("Setting type mismatch: " + key.id());
        }
        @SuppressWarnings("unchecked") SettingSpec<T> typed = (SettingSpec<T>) specification;
        return typed;
    }

    public List<SettingSpec<?>> specifications() {
        return List.copyOf(new ArrayList<>(specifications.values()));
    }

    public SettingsSnapshot defaults() {
        Map<SettingKey<?>, Object> values = new LinkedHashMap<>();
        specifications.values().forEach(specification ->
                values.put(specification.key(), specification.defaultValue()));
        return new SettingsSnapshot(values);
    }
}
