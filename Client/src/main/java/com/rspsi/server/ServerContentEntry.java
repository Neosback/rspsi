package com.rspsi.server;

import java.nio.file.Path;
import java.util.Objects;

/** A read-only inventory entry discovered in a server project. */
public record ServerContentEntry(
        Path path,
        ServerContentKind kind,
        String source,
        String detail) {
    public ServerContentEntry {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        kind = Objects.requireNonNull(kind, "kind");
        source = requireText(source, "source");
        detail = detail == null ? "" : detail.trim();
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
