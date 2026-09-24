package com.rspsi.project;

import java.util.Objects;

/** Lightweight launcher entry; the project descriptor remains authoritative. */
public record StudioRecentProject(
        String projectId,
        String name,
        StudioProjectKind kind,
        String sourcePath,
        String descriptorPath,
        long lastOpenedAtEpochMillis,
        boolean pinned) {

    public StudioRecentProject {
        projectId = requireText(projectId, "projectId");
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        sourcePath = requireText(sourcePath, "sourcePath");
        descriptorPath = requireText(descriptorPath, "descriptorPath");
        if (lastOpenedAtEpochMillis < 0) {
            throw new IllegalArgumentException("lastOpenedAtEpochMillis cannot be negative");
        }
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
