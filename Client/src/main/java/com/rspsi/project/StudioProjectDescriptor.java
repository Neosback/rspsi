package com.rspsi.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent project-first identity used by the launcher.
 *
 * <p>The source path is deliberately neutral: for standalone projects it is the selected cache
 * directory; for connected projects it is the provider/server root. Provider-specific settings
 * remain opaque strings/capabilities rather than leaking OpenRune classes into this descriptor.</p>
 */
public record StudioProjectDescriptor(
        int formatVersion,
        String projectId,
        String name,
        StudioProjectKind kind,
        String sourcePath,
        String providerId,
        Set<String> enabledCapabilities,
        Map<String, String> settings,
        long createdAtEpochMillis,
        long lastOpenedAtEpochMillis) {

    public static final int CURRENT_FORMAT_VERSION = 1;

    public StudioProjectDescriptor {
        if (formatVersion <= 0) throw new IllegalArgumentException("formatVersion must be positive");
        projectId = requireText(projectId, "projectId");
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        sourcePath = requireText(sourcePath, "sourcePath");
        providerId = providerId == null ? "" : providerId.trim();
        enabledCapabilities = Set.copyOf(enabledCapabilities == null ? Set.of() : enabledCapabilities);
        settings = Map.copyOf(settings == null ? Map.of() : settings);
        if (createdAtEpochMillis <= 0) throw new IllegalArgumentException("createdAtEpochMillis must be positive");
        if (lastOpenedAtEpochMillis < 0) throw new IllegalArgumentException("lastOpenedAtEpochMillis cannot be negative");
    }

    public static StudioProjectDescriptor create(
            String name,
            StudioProjectKind kind,
            Path source,
            String providerId,
            Set<String> enabledCapabilities,
            Map<String, String> settings) {
        long now = Instant.now().toEpochMilli();
        return new StudioProjectDescriptor(
                CURRENT_FORMAT_VERSION,
                UUID.randomUUID().toString(),
                name,
                kind,
                Objects.requireNonNull(source, "source").toAbsolutePath().normalize().toString(),
                providerId,
                enabledCapabilities,
                settings,
                now,
                0L);
    }

    public Path source() {
        return Path.of(sourcePath).toAbsolutePath().normalize();
    }

    public StudioProjectDescriptor openedNow() {
        return new StudioProjectDescriptor(
                formatVersion, projectId, name, kind, sourcePath, providerId,
                enabledCapabilities, settings, createdAtEpochMillis,
                Instant.now().toEpochMilli());
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
