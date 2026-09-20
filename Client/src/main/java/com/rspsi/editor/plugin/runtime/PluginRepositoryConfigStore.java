package com.rspsi.editor.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Persists the community repository list independently from plugin enable state. */
public final class PluginRepositoryConfigStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();

    public PluginRepositoryConfigStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public List<URI> load() {
        if (!Files.isRegularFile(path)) return List.of();
        try {
            JsonNode root = mapper.readTree(path.toFile());
            JsonNode values = root.path("repositories");
            if (!values.isArray()) return List.of();
            List<URI> result = new ArrayList<>();
            values.forEach(node -> {
                String value = node.asText("").trim();
                if (!value.isEmpty()) result.add(URI.create(value));
            });
            return List.copyOf(result);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Unable to load plugin repositories " + path, error);
        }
    }

    public void save(List<URI> repositories) {
        Objects.requireNonNull(repositories, "repositories");
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", 1);
            ArrayNode array = root.putArray("repositories");
            repositories.forEach(uri -> array.add(uri.toString()));
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save plugin repositories " + path, error);
        }
    }
}
