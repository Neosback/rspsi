package com.rspsi.editor.integration.content;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Data-only integration sidecar. Studio discovers server content from this
 * manifest and declared data resources, never Kotlin source or compiled classes.
 */
public record ContentManifest(
        int manifestVersion,
        String pluginId,
        String name,
        List<String> authors,
        String homepage,
        Set<ContentCapability> capabilities,
        Map<String, String> schemaVersions,
        Map<String, List<String>> resources,
        Path source) {

    public static final int CURRENT_MANIFEST_VERSION = 1;

    public ContentManifest {
        if (manifestVersion < 1) throw new IllegalArgumentException("manifestVersion must be positive");
        pluginId = requireText(pluginId, "pluginId");
        name = requireText(name, "name");
        authors = List.copyOf(authors == null ? List.of() : authors);
        homepage = homepage == null ? "" : homepage.trim();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
        schemaVersions = Map.copyOf(schemaVersions == null ? Map.of() : schemaVersions);
        resources = resources == null ? Map.of() : resources.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        source = Objects.requireNonNull(source, "source");
    }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be empty");
        return result;
    }
}
