package com.rspsi.server;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Metadata-only view of a built-in or external OpenRune plugin. */
public record ServerPluginInfo(
        String id,
        Path source,
        boolean external,
        String name,
        String description,
        String revision,
        String author) {
    public ServerPluginInfo {
        id = requireText(id, "id");
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        name = optionalText(name);
        description = optionalText(description);
        revision = optionalText(revision);
        author = optionalText(author);
    }

    public boolean hasManifest() {
        return !name.isEmpty() && !description.isEmpty() && !revision.isEmpty() && !author.isEmpty();
    }

    public Optional<String> manifestName() {
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
