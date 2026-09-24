package com.rspsi.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent launcher-level project identity.
 *
 * <p>The descriptor is intentionally provider-neutral. A server project stores a provider ID and
 * root path; provider-specific connection details remain behind that provider's own connection
 * model rather than becoming fields here.</p>
 */
public record StudioProjectDescriptor(
        int formatVersion,
        String projectId,
        String name,
        StudioProjectKind kind,
        long createdAtEpochMillis,
        String projectDataLocation,
        String sourcePath,
        String providerId,
        Set<ProjectIntegrationCapability> capabilities) {

    public static final int CURRENT_FORMAT_VERSION = 1;

    public StudioProjectDescriptor {
        if (formatVersion <= 0) {
            throw new IllegalArgumentException("Project descriptor format must be positive");
        }
        projectId = requireText(projectId, "projectId");
        name = requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        if (createdAtEpochMillis <= 0) {
            throw new IllegalArgumentException("createdAtEpochMillis must be positive");
        }
        projectDataLocation = requireText(projectDataLocation, "projectDataLocation");
        sourcePath = requireText(sourcePath, "sourcePath");
        providerId = providerId == null ? "" : providerId.trim();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);

        if (kind == StudioProjectKind.OPENRUNE_SERVER && providerId.isBlank()) {
            throw new IllegalArgumentException("Server projects require a providerId");
        }
        if (kind == StudioProjectKind.STANDALONE_OSRS_CACHE && !providerId.isBlank()) {
            throw new IllegalArgumentException("Standalone cache projects cannot declare a providerId");
        }
    }

    public static StudioProjectDescriptor standalone(
            String name,
            Path projectDataLocation,
            Path cachePath) {
        return new StudioProjectDescriptor(
                CURRENT_FORMAT_VERSION,
                UUID.randomUUID().toString(),
                name,
                StudioProjectKind.STANDALONE_OSRS_CACHE,
                Instant.now().toEpochMilli(),
                normalize(projectDataLocation),
                normalize(cachePath),
                "",
                Set.of(ProjectIntegrationCapability.PROJECT_READ));
    }

    public static StudioProjectDescriptor server(
            String name,
            Path projectDataLocation,
            Path serverRoot,
            String providerId,
            Set<ProjectIntegrationCapability> capabilities) {
        Set<ProjectIntegrationCapability> effective =
                capabilities == null || capabilities.isEmpty()
                        ? Set.of(ProjectIntegrationCapability.PROJECT_READ)
                        : Set.copyOf(capabilities);
        return new StudioProjectDescriptor(
                CURRENT_FORMAT_VERSION,
                UUID.randomUUID().toString(),
                name,
                StudioProjectKind.OPENRUNE_SERVER,
                Instant.now().toEpochMilli(),
                normalize(projectDataLocation),
                normalize(serverRoot),
                requireText(providerId, "providerId"),
                effective);
    }

    public Path projectDataPath() {
        return Path.of(projectDataLocation).toAbsolutePath().normalize();
    }

    public Path sourcePathValue() {
        return Path.of(sourcePath).toAbsolutePath().normalize();
    }

    private static String normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
