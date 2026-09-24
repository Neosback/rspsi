package com.rspsi.project;

import com.rspsi.server.OpenRuneServerAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

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
