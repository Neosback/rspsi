package com.rspsi.editor.plugin;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;

/** Stable metadata used to order and identify one editor plugin. */
public record EditorPluginDescriptor(String id, String name, String version,
                                     List<String> dependencies) {
    public EditorPluginDescriptor {
        id = text(id, "plugin id");
        name = text(name, "plugin name");
        version = text(version, "plugin version");
        dependencies = (dependencies == null ? List.<String>of() : dependencies).stream()
                .map(value -> Objects.requireNonNull(value, "plugin dependency").trim())
                .toList();
        if (dependencies.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Plugin dependencies cannot be blank");
        }
        if (new HashSet<>(dependencies).size() != dependencies.size()) {
            throw new IllegalArgumentException("Plugin dependencies cannot be duplicated");
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Plugin cannot depend on itself: " + id);
        }
    }

    public static EditorPluginDescriptor of(String id) {
        return new EditorPluginDescriptor(id, id, "0.1.0", List.of());
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
