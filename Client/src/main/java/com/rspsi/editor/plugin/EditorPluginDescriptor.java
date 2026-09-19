package com.rspsi.editor.plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stable metadata used to order, identify, and configure one editor plugin.
 */
public record EditorPluginDescriptor(
        String id,
        String name,
        String version,
        int apiVersion,
        List<String> dependencies,
        String description,
        List<String> authors,
        List<String> tags,
        String website,
        String sourceUrl,
        String supportUrl,
        String minimumStudioVersion,
        List<String> optionalDependencies,
        List<String> conflicts,
        Set<PluginPermission> permissions,
        boolean builtIn,
        boolean defaultEnabled
) {
    public EditorPluginDescriptor(String id, String name, String version, List<String> dependencies) {
        this(id, name, version, EditorPluginApi.CURRENT_VERSION, dependencies);
    }

    public EditorPluginDescriptor(String id, String name, String version, int apiVersion,
                                  List<String> dependencies) {
        this(id, name, version, apiVersion, dependencies, "", List.of(), List.of(),
                "", "", "", "1.0.0", List.of(), List.of(), Set.of(), false, true);
    }

    public EditorPluginDescriptor {
        id = text(id, "plugin id");
        name = text(name, "plugin name");
        version = text(version, "plugin version");
        if (apiVersion < 1) throw new IllegalArgumentException("Plugin API version must be positive");
        dependencies = normalizeList(dependencies, "plugin dependency");
        if (new HashSet<>(dependencies).size() != dependencies.size()) {
            throw new IllegalArgumentException("Plugin dependencies cannot be duplicated");
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Plugin cannot depend on itself: " + id);
        }
        description = description == null ? "" : description.trim();
        authors = normalizeList(authors, "author");
        tags = normalizeList(tags, "tag");
        website = website == null ? "" : website.trim();
        sourceUrl = sourceUrl == null ? "" : sourceUrl.trim();
        supportUrl = supportUrl == null ? "" : supportUrl.trim();
        minimumStudioVersion = minimumStudioVersion == null || minimumStudioVersion.isBlank()
                ? "1.0.0" : minimumStudioVersion.trim();
        optionalDependencies = normalizeList(optionalDependencies, "optional dependency");
        conflicts = normalizeList(conflicts, "conflict");
        permissions = permissions == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    public static EditorPluginDescriptor of(String id) {
        return new EditorPluginDescriptor(id, id, "0.1.0", List.of());
    }

    private static List<String> normalizeList(List<String> input, String name) {
        if (input == null) return List.of();
        List<String> normalized = input.stream()
                .map(v -> Objects.requireNonNull(v, name).trim())
                .toList();
        if (normalized.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return List.copyOf(normalized);
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
