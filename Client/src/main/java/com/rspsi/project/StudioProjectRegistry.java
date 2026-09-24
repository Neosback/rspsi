package com.rspsi.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned lightweight recent-project registry used by the pre-project launcher.
 *
 * <p>The registry never loads caches or server projects. Missing descriptor paths remain visible
 * until explicitly removed so moved projects can be repaired.</p>
 */
public final class StudioProjectRegistry {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path registryFile;

    public StudioProjectRegistry() {
        this(Path.of(System.getProperty("user.home"),
                ".openrune-studio", "recent-projects.json"));
    }

    public StudioProjectRegistry(Path registryFile) {
        this.registryFile = Objects.requireNonNull(registryFile, "registryFile")
                .toAbsolutePath().normalize();
    }

    public Path registryFile() {
        return registryFile;
    }

    public synchronized List<RecentStudioProject> recent() {
        RegistryDocument document = read();
        return document.projects().stream()
                .sorted(Comparator
                        .comparing(RecentStudioProject::pinned).reversed()
                        .thenComparing(RecentStudioProject::lastOpenedEpochMillis).reversed())
                .toList();
    }

    public synchronized void remember(
            Path descriptorPath,
            StudioProjectDescriptor descriptor) {
        Objects.requireNonNull(descriptorPath, "descriptorPath");
        Objects.requireNonNull(descriptor, "descriptor");

        RegistryDocument current = read();
        Map<String, RecentStudioProject> entries = new LinkedHashMap<>();
        current.projects().forEach(entry -> entries.put(entry.projectId(), entry));

        RecentStudioProject prior = entries.get(descriptor.projectId());
        entries.put(
                descriptor.projectId(),
                new RecentStudioProject(
                        descriptor.projectId(),
                        descriptor.name(),
                        descriptor.kind(),
                        descriptorPath.toAbsolutePath().normalize().toString(),
                        descriptor.sourcePathValue().toString(),
                        Instant.now().toEpochMilli(),
                        prior != null && prior.pinned()));

        write(new RegistryDocument(FORMAT_VERSION, List.copyOf(entries.values())));
    }

    public synchronized void setPinned(String projectId, boolean pinned) {
        requireProjectId(projectId);
        RegistryDocument current = read();
        List<RecentStudioProject> updated = new ArrayList<>();
        for (RecentStudioProject entry : current.projects()) {
            if (entry.projectId().equals(projectId)) {
                updated.add(new RecentStudioProject(
                        entry.projectId(), entry.name(), entry.kind(),
                        entry.descriptorPath(), entry.displayPath(),
                        entry.lastOpenedEpochMillis(), pinned));
            } else {
                updated.add(entry);
            }
        }
        write(new RegistryDocument(FORMAT_VERSION, updated));
    }

    public synchronized void remove(String projectId) {
        requireProjectId(projectId);
        RegistryDocument current = read();
        write(new RegistryDocument(
                FORMAT_VERSION,
                current.projects().stream()
                        .filter(entry -> !entry.projectId().equals(projectId))
                        .toList()));
    }

    private RegistryDocument read() {
        if (!Files.isRegularFile(registryFile)) {
            return new RegistryDocument(FORMAT_VERSION, List.of());
        }
        try {
            RegistryDocument document =
                    GSON.fromJson(Files.readString(registryFile), RegistryDocument.class);
            if (document == null) return new RegistryDocument(FORMAT_VERSION, List.of());
            if (document.formatVersion() > FORMAT_VERSION) {
                return new RegistryDocument(FORMAT_VERSION, List.of());
            }
            return new RegistryDocument(
                    document.formatVersion(),
                    document.projects() == null ? List.of() : document.projects());
        } catch (IOException | RuntimeException ignored) {
            return new RegistryDocument(FORMAT_VERSION, List.of());
        }
    }

    private void write(RegistryDocument document) {
        try {
            Path parent = registryFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "recent-projects-", ".tmp");
            try {
                Files.writeString(
                        temporary,
                        GSON.toJson(document),
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(
                            temporary,
                            registryFile,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(
                            temporary,
                            registryFile,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ignored) {
            // Losing launcher recency must never invalidate the project descriptor itself.
        }
    }

    private static void requireProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId cannot be blank");
        }
    }

    private record RegistryDocument(int formatVersion, List<RecentStudioProject> projects) {
        private RegistryDocument {
            projects = List.copyOf(projects == null ? List.of() : projects);
        }
    }
}
