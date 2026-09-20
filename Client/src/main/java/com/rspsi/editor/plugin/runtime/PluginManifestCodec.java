package com.rspsi.editor.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rspsi.editor.plugin.PluginPermission;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;

/** JSON codec for managed plugin manifests and update-repository indices. */
public final class PluginManifestCodec {
    public static final String JAR_MANIFEST_PATH = "META-INF/rspsi-plugin.json";

    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<ExternalPluginManifest> readJarManifest(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(JAR_MANIFEST_PATH);
            if (entry == null) return Optional.empty();
            try (InputStream input = jar.getInputStream(entry)) {
                return Optional.of(readManifest(input));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read plugin manifest from " + jarPath, error);
        }
    }

    public ExternalPluginManifest readManifest(InputStream input) {
        try {
            return manifest(mapper.readTree(input));
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid plugin manifest JSON", error);
        }
    }

    public PluginRepositoryIndex readRepository(InputStream input, URI source) {
        try {
            JsonNode root = mapper.readTree(input);
            int schema = integer(root, "schemaVersion", 1);
            List<PluginRepositoryEntry> entries = new ArrayList<>();
            JsonNode plugins = root.path("plugins");
            if (!plugins.isArray()) {
                throw new IllegalArgumentException("Plugin repository must contain a plugins array");
            }
            for (JsonNode node : plugins) {
                JsonNode manifestNode = node.has("manifest") ? node.get("manifest") : node;
                ExternalPluginManifest manifest = manifest(manifestNode);
                String download = text(node, "downloadUrl", "");
                String sha256 = text(node, "sha256", "");
                if (download.isBlank()) {
                    throw new IllegalArgumentException(
                            "Repository entry has no downloadUrl: " + manifest.id());
                }
                URI downloadUri = source.resolve(download);
                entries.add(new PluginRepositoryEntry(manifest, downloadUri, sha256));
            }
            return new PluginRepositoryIndex(schema, source, entries);
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid plugin repository JSON", error);
        }
    }

    private ExternalPluginManifest manifest(JsonNode root) {
        int schema = integer(root, "schemaVersion", ExternalPluginManifest.CURRENT_SCHEMA);
        String id = requiredText(root, "id");
        String name = text(root, "name", id);
        SemanticVersion version = SemanticVersion.parse(requiredText(root, "version"));
        int apiVersion = integer(root, "apiVersion", 1);
        String description = text(root, "description", "");
        List<String> authors = strings(root.path("authors"));
        List<String> tags = strings(root.path("tags"));
        String website = text(root, "website", "");
        String sourceUrl = text(root, "sourceUrl", "");
        String supportUrl = text(root, "supportUrl", "");
        SemanticVersion minimumStudio = SemanticVersion.parse(
                text(root, "minimumStudioVersion", "1.0.0"));

        List<PluginDependency> dependencies = new ArrayList<>();
        JsonNode deps = root.path("dependencies");
        if (deps.isArray()) {
            for (JsonNode dependency : deps) {
                dependencies.add(new PluginDependency(
                        requiredText(dependency, "id"),
                        VersionConstraint.parse(text(dependency, "version", "*")),
                        dependency.path("optional").asBoolean(false)));
            }
        }

        Set<PluginPermission> permissions = EnumSet.noneOf(PluginPermission.class);
        JsonNode permissionNodes = root.path("permissions");
        if (permissionNodes.isArray()) {
            for (JsonNode permission : permissionNodes) {
                String value = permission.asText("").trim();
                if (value.isEmpty()) continue;
                permissions.add(PluginPermission.valueOf(
                        value.replace('-', '_').replace(' ', '_').toUpperCase()));
            }
        }

        return new ExternalPluginManifest(schema, id, name, version, apiVersion,
                description, authors, tags, website, sourceUrl, supportUrl,
                minimumStudio, dependencies, permissions);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field, "");
        if (value.isBlank()) throw new IllegalArgumentException("Missing plugin field: " + field);
        return value;
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue).trim();
    }

    private static int integer(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            String text = value.asText("").trim();
            if (!text.isEmpty()) values.add(text);
        });
        return List.copyOf(values);
    }
}
