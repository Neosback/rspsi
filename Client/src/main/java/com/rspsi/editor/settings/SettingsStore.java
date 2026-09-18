package com.rspsi.editor.settings;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Mutable, typed setting layers that produce immutable snapshots for frames.
 * The renderer never reads this store while drawing.
 */
public final class SettingsStore {
    private static final SettingScope[] PRECEDENCE = {
            SettingScope.GLOBAL, SettingScope.PROJECT,
            SettingScope.VIEWPORT, SettingScope.TRANSIENT
    };

    private final SettingsRegistry registry;
    private final EnumMap<SettingScope, Map<SettingKey<?>, Object>> layers =
            new EnumMap<>(SettingScope.class);
    private final CopyOnWriteArrayList<Consumer<SettingChange>> listeners = new CopyOnWriteArrayList<>();
    private long revision;

    public SettingsStore(SettingsRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "settings registry");
        for (SettingScope scope : SettingScope.values()) layers.put(scope, new LinkedHashMap<>());
    }

    public SettingsRegistry registry() {
        return registry;
    }

    public <T> void set(SettingKey<T> key, T value) {
        SettingSpec<T> specification = registry.specification(Objects.requireNonNull(key, "setting key"));
        set(specification.scope(), key, value);
    }

    public void set(SettingScope scope, SettingKey<?> key, Object value) {
        Objects.requireNonNull(scope, "setting scope");
        SettingSpec<?> specification = registry.specification(Objects.requireNonNull(key, "setting key"));
        validate(specification, value);
        Map<SettingKey<?>, Object> layer = layers.get(scope);
        Object previous = snapshot().values().getOrDefault(key, specification.defaultValue());
        layer.put(key, value);
        Object current = snapshot().values().getOrDefault(key, specification.defaultValue());
        if (!Objects.equals(previous, current)) {
            revision++;
            SettingChange change = new SettingChange(key, scope, previous, current,
                    specification.invalidations());
            listeners.forEach(listener -> listener.accept(change));
        }
    }

    public void reset(SettingKey<?> key) {
        registry.specification(Objects.requireNonNull(key, "setting key"));
        SettingsSnapshot before = snapshot();
        for (Map<SettingKey<?>, Object> layer : layers.values()) layer.remove(key);
        notifyIfChanged(key, SettingScope.TRANSIENT, before);
    }

    /** Removes only one override layer, revealing the next lower-precedence value. */
    public void clear(SettingScope scope, SettingKey<?> key) {
        Objects.requireNonNull(scope, "setting scope");
        registry.specification(Objects.requireNonNull(key, "setting key"));
        SettingsSnapshot before = snapshot();
        layers.get(scope).remove(key);
        notifyIfChanged(key, scope, before);
    }

    public SettingsSnapshot snapshot() {
        Map<SettingKey<?>, Object> values = new LinkedHashMap<>(registry.defaults().values());
        for (SettingScope scope : PRECEDENCE) values.putAll(layers.get(scope));
        return new SettingsSnapshot(values);
    }

    /** Returns a copy of one persisted layer, excluding registry defaults. */
    public Map<SettingKey<?>, Object> values(SettingScope scope) {
        Objects.requireNonNull(scope, "setting scope");
        return Map.copyOf(layers.get(scope));
    }

    /** Monotonic revision for frontend caches of compiled settings projections. */
    public long revision() {
        return revision;
    }

    public void addListener(Consumer<SettingChange> listener) {
        listeners.add(Objects.requireNonNull(listener, "setting listener"));
    }

    public void removeListener(Consumer<SettingChange> listener) {
        listeners.remove(listener);
    }

    private void notifyIfChanged(SettingKey<?> key, SettingScope scope, SettingsSnapshot before) {
        Object previous = before.values().get(key);
        Object current = snapshot().values().get(key);
        if (!Objects.equals(previous, current)) {
            revision++;
            SettingSpec<?> specification = registry.specification(key);
            SettingChange change = new SettingChange(key, scope, previous, current,
                    specification.invalidations());
            listeners.forEach(listener -> listener.accept(change));
        }
    }

    private static void validate(SettingSpec<?> specification, Object value) {
        Objects.requireNonNull(value, "setting value");
        if (!specification.key().valueType().isInstance(value)) {
            throw new IllegalArgumentException("Value type mismatch for setting "
                    + specification.key().id());
        }
        if (specification.options().size() > 0 && !specification.options().contains(value)) {
            throw new IllegalArgumentException("Value is not an allowed option for setting "
                    + specification.key().id());
        }
        if (value instanceof Number number && specification.minimum() != null
                && (number.doubleValue() < specification.minimum()
                || number.doubleValue() > specification.maximum())) {
            throw new IllegalArgumentException("Value is outside the range for setting "
                    + specification.key().id());
        }
    }
}
