package com.rspsi.project;

import com.rspsi.server.OpenRuneServerAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Creates/opens launcher-level Studio projects without loading caches or entering a workspace.
 *
 * <p>This service performs only cheap project validation and descriptor persistence. Runtime cache
 * and server integration initialization belongs to the application project-loading gate.</p>
 */
public final class StudioProjectService {
    private final StudioProjectRegistry registry;
    private final OpenRuneServerAdapter openRune = new OpenRuneServerAdapter();

    public StudioProjectService(StudioProjectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }


    /**
     * Creates or reopens a lightweight standalone project using a deterministic
     * Studio-managed data directory. The launcher therefore only needs the
     * cache directory from the user.
     */
    public StudioProjectDescriptor createStandalone(Path cacheRoot) throws IOException {
        Path cache = normalize(cacheRoot, "cacheRoot");
        validateStandaloneCache(cache);
        String name = displayName(cache, "OSRS Cache");
        Path data = automaticProjectDataRoot(cache, "cache");
        StudioProjectDescriptor existing = existingAutomaticProject(
                data, StudioProjectKind.STANDALONE_OSRS_CACHE, cache);
        if (existing != null) return existing;
        return createStandalone(name, data, cache);
    }

    /**
     * Links or reopens an OpenRune server checkout using a deterministic
     * Studio-managed data directory. The server checkout itself is never used
     * as Studio's private project-data directory.
     */
    public StudioProjectDescriptor linkOpenRune(
            Path serverRoot,
            ProjectIntegrationPreset preset) throws IOException {
        Objects.requireNonNull(preset, "preset");
        Path root = normalize(serverRoot, "serverRoot");
        if (!Files.isDirectory(root)) {
            throw new IOException("Server project directory does not exist: " + root);
        }
        if (!openRune.detect(root).matched()) {
            throw new IOException("OpenRune project markers were not detected at: " + root);
        }
        String name = displayName(root, "OpenRune Server");
        Path data = automaticProjectDataRoot(root, "openrune");
        StudioProjectDescriptor existing = existingAutomaticProject(
                data, StudioProjectKind.OPENRUNE_SERVER, root);
        if (existing != null) {
            if (!existing.capabilities().equals(preset.capabilities())) {
                StudioProjectDescriptor updated = new StudioProjectDescriptor(
                        existing.formatVersion(),
                        existing.projectId(),
                        existing.name(),
                        existing.kind(),
                        existing.createdAtEpochMillis(),
                        existing.projectDataLocation(),
                        existing.sourcePath(),
                        existing.providerId(),
                        preset.capabilities());
                Path descriptorPath = StudioProjectDescriptorStore.descriptorPath(data);
                StudioProjectDescriptorStore.write(descriptorPath, updated);
                registry.remember(descriptorPath, updated);
                return updated;
            }
            return existing;
        }
        return linkOpenRune(name, data, root, preset);
    }

    public StudioProjectDescriptor createStandalone(
            String name,
            Path projectDataRoot,
            Path cacheRoot) throws IOException {
        Path cache = normalize(cacheRoot, "cacheRoot");
        validateStandaloneCache(cache);
        Path data = normalize(projectDataRoot, "projectDataRoot");
        ensureDescriptorDoesNotExist(data);

        StudioProjectDescriptor descriptor =
                StudioProjectDescriptor.standalone(name, data, cache);
        Path descriptorPath = StudioProjectDescriptorStore.descriptorPath(data);
        StudioProjectDescriptorStore.write(descriptorPath, descriptor);
        registry.remember(descriptorPath, descriptor);
        return descriptor;
    }

    public StudioProjectDescriptor linkOpenRune(
            String name,
            Path projectDataRoot,
            Path serverRoot,
            ProjectIntegrationPreset preset) throws IOException {
        Objects.requireNonNull(preset, "preset");
        return linkServer(
                name,
                projectDataRoot,
                serverRoot,
                "server.openrune",
                preset.capabilities());
    }

    public StudioProjectDescriptor linkServer(
            String name,
            Path projectDataRoot,
            Path serverRoot,
            String providerId,
            Set<ProjectIntegrationCapability> capabilities) throws IOException {
        Path root = normalize(serverRoot, "serverRoot");
        if (!Files.isDirectory(root)) {
            throw new IOException("Server project directory does not exist: " + root);
        }
        if ("server.openrune".equals(providerId) && !openRune.detect(root).matched()) {
            throw new IOException("OpenRune project markers were not detected at: " + root);
        }

        Path data = normalize(projectDataRoot, "projectDataRoot");
        ensureDescriptorDoesNotExist(data);

        StudioProjectDescriptor descriptor = StudioProjectDescriptor.server(
                name,
                data,
                root,
                providerId,
                capabilities);
        Path descriptorPath = StudioProjectDescriptorStore.descriptorPath(data);
        StudioProjectDescriptorStore.write(descriptorPath, descriptor);
        registry.remember(descriptorPath, descriptor);
        return descriptor;
    }

    public StudioProjectDescriptor open(Path descriptorPath) throws IOException {
        Path path = normalize(descriptorPath, "descriptorPath");
        StudioProjectDescriptor descriptor = StudioProjectDescriptorStore.read(path);
        validateSource(descriptor);
        registry.remember(path, descriptor);
        return descriptor;
    }

    public void validateSource(StudioProjectDescriptor descriptor) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        switch (descriptor.kind()) {
            case STANDALONE_OSRS_CACHE -> validateStandaloneCache(descriptor.sourcePathValue());
            case OPENRUNE_SERVER -> {
                Path root = descriptor.sourcePathValue();
                if (!Files.isDirectory(root)) {
                    throw new IOException("Server project directory does not exist: " + root);
                }
                if ("server.openrune".equals(descriptor.providerId())
                        && !openRune.detect(root).matched()) {
                    throw new IOException("OpenRune project markers were not detected at: " + root);
                }
            }
        }
    }

    private StudioProjectDescriptor existingAutomaticProject(
            Path projectDataRoot,
            StudioProjectKind expectedKind,
            Path expectedSource) throws IOException {
        Path descriptorPath = StudioProjectDescriptorStore.descriptorPath(projectDataRoot);
        if (!Files.isRegularFile(descriptorPath)) return null;

        StudioProjectDescriptor existing = StudioProjectDescriptorStore.read(descriptorPath);
        if (existing.kind() != expectedKind
                || !existing.sourcePathValue().equals(expectedSource.toAbsolutePath().normalize())) {
            throw new IOException("Studio project data is already used by another source: "
                    + descriptorPath);
        }
        validateSource(existing);
        registry.remember(descriptorPath, existing);
        return existing;
    }

    private Path automaticProjectDataRoot(Path source, String kind) {
        Path normalized = source.toAbsolutePath().normalize();
        String name = displayName(normalized, kind);
        String slug = name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) slug = kind;
        String stableId = UUID.nameUUIDFromBytes(
                        normalized.toString().getBytes(StandardCharsets.UTF_8))
                .toString()
                .substring(0, 8);
        Path studioRoot = registry.registryFile().getParent();
        if (studioRoot == null) {
            studioRoot = Path.of(System.getProperty("user.home"), ".openrune-studio");
        }
        return studioRoot.resolve("projects").resolve(slug + "-" + stableId)
                .toAbsolutePath().normalize();
    }

    private static String displayName(Path source, String fallback) {
        Path fileName = source.getFileName();
        if (fileName == null) return fallback;
        String value = fileName.toString().trim();
        return value.isBlank() ? fallback : value;
    }

    private static void validateStandaloneCache(Path cache) throws IOException {
        if (!Files.isDirectory(cache)) {
            throw new IOException("Cache directory does not exist: " + cache);
        }
        if (!Files.isRegularFile(cache.resolve("main_file_cache.dat2"))) {
            throw new IOException(
                    "Selected directory is not an OSRS cache (main_file_cache.dat2 missing): "
                            + cache);
        }
    }

    private static void ensureDescriptorDoesNotExist(Path projectDataRoot) throws IOException {
        Path descriptor = StudioProjectDescriptorStore.descriptorPath(projectDataRoot);
        if (Files.exists(descriptor)) {
            throw new IOException("A Studio project already exists at: " + descriptor);
        }
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
