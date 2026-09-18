package com.rspsi.editor.settings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Declares which application subsystem consumes each registered setting.
 *
 * <p>The catalog is deliberately independent from the frontend.  A renderer,
 * tool, or plugin can register its contract without exposing implementation
 * state to an ImGui or JavaFX host.</p>
 */
public final class SettingConsumerCatalog {
    private final Map<String, Set<SettingKey<?>>> consumers = new LinkedHashMap<>();

    public void register(String consumerId, Collection<? extends SettingKey<?>> keys) {
        String id = requireId(consumerId);
        Objects.requireNonNull(keys, "consumer keys");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("Setting consumer must declare at least one key: " + id);
        }
        LinkedHashSet<SettingKey<?>> normalized = new LinkedHashSet<>();
        for (SettingKey<?> key : keys) normalized.add(Objects.requireNonNull(key, "consumer key"));
        if (consumers.putIfAbsent(id, Set.copyOf(normalized)) != null) {
            throw new IllegalArgumentException("Duplicate setting consumer: " + id);
        }
    }

    public void register(String consumerId, SettingKey<?>... keys) {
        register(consumerId, List.of(keys));
    }

    public Map<String, Set<SettingKey<?>>> consumers() {
        return Map.copyOf(consumers);
    }

    public Set<SettingKey<?>> consumedKeys() {
        LinkedHashSet<SettingKey<?>> keys = new LinkedHashSet<>();
        consumers.values().forEach(keys::addAll);
        return Set.copyOf(keys);
    }

    private static String requireId(String value) {
        String normalized = Objects.requireNonNull(value, "consumer id").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Consumer id cannot be empty");
        return normalized;
    }
}
