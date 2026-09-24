package com.rspsi.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Lightweight launcher metadata. The descriptor remains authoritative. */
public record RecentStudioProject(
        String projectId,
        String name,
        StudioProjectKind kind,
        String descriptorPath,
        String displayPath,
        long lastOpenedEpochMillis,
        boolean pinned) {

    public RecentStudioProject {
        projectId = requireText(projectId, "projectId");
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        descriptorPath = requireText(descriptorPath, "descriptorPath");
        displayPath = requireText(displayPath, "displayPath");
        if (lastOpenedEpochMillis <= 0) {
            throw new IllegalArgumentException("lastOpenedEpochMillis must be positive");
        }
    }

    public Path descriptor() {
        return Path.of(descriptorPath).toAbsolutePath().normalize();
    }

    public boolean available() {
        return Files.isRegularFile(descriptor());
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
