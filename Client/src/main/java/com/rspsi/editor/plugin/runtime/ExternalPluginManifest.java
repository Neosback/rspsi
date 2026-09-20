package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPluginApi;
import com.rspsi.editor.plugin.EditorPluginDescriptor;
import com.rspsi.editor.plugin.PluginPermission;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Versioned contract stored at META-INF/rspsi-plugin.json inside managed plugin JARs. */
public record ExternalPluginManifest(
        int schemaVersion,
        String id,
        String name,
        SemanticVersion version,
        int apiVersion,
        String description,
        List<String> authors,
        List<String> tags,
        String website,
        String sourceUrl,
        String supportUrl,
        SemanticVersion minimumStudioVersion,
        List<PluginDependency> dependencies,
        Set<PluginPermission> permissions) {

    public static final int CURRENT_SCHEMA = 1;

    public ExternalPluginManifest {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported plugin manifest schema: " + schemaVersion);
        }
        id = requireText(id, "plugin id");
        name = requireText(name, "plugin name");
        version = Objects.requireNonNull(version, "plugin version");
        if (apiVersion < 1) throw new IllegalArgumentException("Plugin API version must be positive");
        description = description == null ? "" : description.trim();
        authors = List.copyOf(authors == null ? List.of() : authors);
        tags = List.copyOf(tags == null ? List.of() : tags);
        website = website == null ? "" : website.trim();
        sourceUrl = sourceUrl == null ? "" : sourceUrl.trim();
        supportUrl = supportUrl == null ? "" : supportUrl.trim();
        minimumStudioVersion = minimumStudioVersion == null
                ? new SemanticVersion(1, 0, 0, "") : minimumStudioVersion;
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);

        Set<String> ids = new LinkedHashSet<>();
        for (PluginDependency dependency : dependencies) {
            if (!ids.add(dependency.id())) {
                throw new IllegalArgumentException("Duplicate plugin dependency: " + dependency.id());
            }
            if (dependency.id().equals(id)) {
                throw new IllegalArgumentException("Plugin cannot depend on itself: " + id);
            }
        }
    }

    public EditorPluginDescriptor descriptor() {
        List<String> required = dependencies.stream()
                .filter(dependency -> !dependency.optional())
                .map(PluginDependency::id).toList();
        List<String> optional = dependencies.stream()
                .filter(PluginDependency::optional)
                .map(PluginDependency::id).toList();
        return new EditorPluginDescriptor(
                id, name, version.toString(), apiVersion, required,
                description, authors, tags, website, sourceUrl, supportUrl,
                minimumStudioVersion.toString(), optional, List.of(), permissions,
                false, true);
    }

    public boolean apiCompatible() {
        return apiVersion == EditorPluginApi.CURRENT_VERSION;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
