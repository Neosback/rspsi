package com.rspsi.project;

import com.rspsi.cache.OsrsCacheMetadata;

import java.util.List;
import java.util.Objects;

/**
 * Safe-open decision for a project and the cache currently selected by the
 * user. A mismatch is deliberately read-only until ID migration exists.
 */
public record ProjectCompatibility(boolean readOnly, List<String> issues) {
    public ProjectCompatibility {
        issues = List.copyOf(issues == null ? List.of() : issues);
        if (!readOnly && !issues.isEmpty()) {
            throw new IllegalArgumentException("A writable project cannot have compatibility issues");
        }
    }

    public boolean compatible() {
        return issues.isEmpty();
    }

    public static ProjectCompatibility assess(ProjectMetadata project, OsrsCacheMetadata cache) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(cache, "cache");
        if (project.matches(cache)) {
            return new ProjectCompatibility(false, List.of());
        }

        java.util.ArrayList<String> issues = new java.util.ArrayList<>();
        if (project.cacheRevision() != cache.revision()) {
            issues.add("cache revision differs");
        }
        if (!Objects.equals(project.cacheSubRevision(), cache.subRevision())) {
            issues.add("cache subrevision differs");
        }
        if (!project.cacheFingerprint().equals(cache.fingerprint())) {
            issues.add("cache fingerprint differs");
        }
        return new ProjectCompatibility(true, issues);
    }
}
