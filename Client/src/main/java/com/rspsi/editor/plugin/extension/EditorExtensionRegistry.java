package com.rspsi.editor.plugin.extension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed inter-plugin extension registry.
 *
 * <p>Registrations are disposable and therefore naturally follow plugin
 * lifecycle. This is the generic escape hatch for future systems such as
 * layer codecs, map-piece libraries, similarity metrics, WFC solvers,
 * inpainting engines, exporters, path policies, placement policies and
 * world-map writers without adding one hard dependency to core for each.</p>
 */
public final class EditorExtensionRegistry {
    private final Map<String, Map<String, Registration<?>>> registrations =
            new LinkedHashMap<>();

    public synchronized <T> AutoCloseable register(
            ExtensionPoint<T> point, String id, int priority, T extension) {
        Objects.requireNonNull(point, "point");
        String normalizedId = Objects.requireNonNull(id, "extension id").trim();
        if (normalizedId.isEmpty()) throw new IllegalArgumentException("Extension id cannot be empty");
        T checked = point.type().cast(Objects.requireNonNull(extension, "extension"));

        Map<String, Registration<?>> values =
                registrations.computeIfAbsent(point.id(), ignored -> new LinkedHashMap<>());
        Registration<T> registration = new Registration<>(
                point, normalizedId, priority, checked);
        if (values.putIfAbsent(normalizedId, registration) != null) {
            throw new IllegalArgumentException(
                    "Duplicate extension " + normalizedId + " for " + point.id());
        }
        return () -> unregister(point, normalizedId);
    }

    public synchronized <T> void unregister(ExtensionPoint<T> point, String id) {
        Objects.requireNonNull(point, "point");
        Map<String, Registration<?>> values = registrations.get(point.id());
        if (values == null) return;
        values.remove(id);
        if (values.isEmpty()) registrations.remove(point.id());
    }

    public synchronized <T> List<Registration<T>> registrations(ExtensionPoint<T> point) {
        Objects.requireNonNull(point, "point");
        Map<String, Registration<?>> values =
                registrations.getOrDefault(point.id(), Map.of());
        List<Registration<T>> result = new ArrayList<>();
        for (Registration<?> registration : values.values()) {
            Object extension = point.type().cast(registration.extension());
            @SuppressWarnings("unchecked")
            Registration<T> cast = new Registration<>(
                    point, registration.id(), registration.priority(), (T) extension);
            result.add(cast);
        }
        result.sort(Comparator.comparingInt(Registration<T>::priority).reversed()
                .thenComparing(Registration::id));
        return List.copyOf(result);
    }

    public synchronized <T> Optional<T> highestPriority(ExtensionPoint<T> point) {
        List<Registration<T>> values = registrations(point);
        return values.isEmpty() ? Optional.empty()
                : Optional.of(values.get(0).extension());
    }

    public synchronized void clear() {
        registrations.clear();
    }

    public record Registration<T>(
            ExtensionPoint<T> point,
            String id,
            int priority,
            T extension) {
        public Registration {
            point = Objects.requireNonNull(point, "point");
            id = Objects.requireNonNull(id, "id");
            extension = Objects.requireNonNull(extension, "extension");
        }
    }
}
