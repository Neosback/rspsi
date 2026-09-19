package com.rspsi.editor.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /**
     * Removes a dynamically-owned setting so its id can be reused. Statically declared
     * settings (registered once at startup) are never expected to call this; it exists
     * for {@link SettingsService}'s owned/plugin registration lifecycle.
     */
    public boolean unregister(SettingKey<?> key) {
        return specifications.remove(Objects.requireNonNull(key, "setting key").id()) != null;
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

    public Set<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        for (SettingSpec<?> spec : specifications.values()) {
            categories.add(spec.category());
        }
        return Set.copyOf(categories);
    }

    public Map<String, List<SettingSpec<?>>> categorized() {
        Map<String, List<SettingSpec<?>>> map = new LinkedHashMap<>();
        for (SettingSpec<?> spec : specifications.values()) {
            map.computeIfAbsent(spec.category(), k -> new ArrayList<>()).add(spec);
        }
        for (List<SettingSpec<?>> list : map.values()) {
            list.sort(Comparator.<SettingSpec<?>>comparingInt(s -> s.order()).thenComparing(s -> s.key().id()));
        }
        return Collections.unmodifiableMap(map);
    }

    public List<SettingSpec<?>> search(String query) {
        if (query == null || query.isBlank()) {
            return specifications();
        }
        String q = query.trim().toLowerCase();
        return specifications.values().stream()
                .filter(spec -> spec.key().id().toLowerCase().contains(q)
                        || spec.label().toLowerCase().contains(q)
                        || spec.description().toLowerCase().contains(q)
                        || spec.category().toLowerCase().contains(q))
                .sorted(Comparator.<SettingSpec<?>>comparingInt(s -> s.order()).thenComparing(s -> s.key().id()))
                .toList();
    }

    public SettingsSnapshot defaults() {
        Map<SettingKey<?>, Object> values = new LinkedHashMap<>();
        specifications.values().forEach(specification ->
                values.put(specification.key(), specification.defaultValue()));
        return new SettingsSnapshot(values);
    }
}
