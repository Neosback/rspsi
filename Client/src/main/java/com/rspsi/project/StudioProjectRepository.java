package com.rspsi.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Owns Studio project descriptors and the lightweight recent-project registry.
 *
 * <p>Linked external projects remain untouched. Their Studio-owned metadata lives under this
 * repository root, normally {@code ~/.openrune-studio/projects/<project-id>/}.</p>
 */
public final class StudioProjectRepository {
    public static final String DESCRIPTOR_FILE = "project.json";
    public static final String RECENTS_FILE = "recent-projects.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type RECENT_LIST = new TypeToken<List<StudioRecentProject>>() { }.getType();

    private final Path root;
    private final Path projects;
    private final Path recents;

    public StudioProjectRepository(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.projects = this.root.resolve("projects");
        this.recents = this.root.resolve(RECENTS_FILE);
    }

    public static StudioProjectRepository defaultRepository() {
        return new StudioProjectRepository(
                Path.of(System.getProperty("user.home"), ".openrune-studio"));
    }

    public StudioProjectDescriptor create(
            String name,
            StudioProjectKind kind,
            Path source,
            String providerId,
            Set<String> enabledCapabilities,
            Map<String, String> settings) throws IOException {
        StudioProjectDescriptor descriptor = StudioProjectDescriptor.create(
                name, kind, source, providerId, enabledCapabilities, settings);
        write(descriptor);
        remember(descriptor);
        return descriptor;
    }

    public void write(StudioProjectDescriptor descriptor) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        Path directory = projectDirectory(descriptor.projectId());
        Files.createDirectories(directory.resolve("autosave"));
        Files.createDirectories(directory.resolve("edits"));
        Files.createDirectories(directory.resolve("provenance"));
        atomicJson(descriptorFile(descriptor.projectId()), GSON.toJson(descriptor));
    }

    public StudioProjectDescriptor read(String projectId) throws IOException {
        return read(descriptorFile(projectId));
    }

    public StudioProjectDescriptor read(Path descriptorPath) throws IOException {
        Objects.requireNonNull(descriptorPath, "descriptorPath");
        StudioProjectDescriptor descriptor =
                GSON.fromJson(Files.readString(descriptorPath), StudioProjectDescriptor.class);
        if (descriptor == null) throw new IOException("Studio project descriptor is empty: " + descriptorPath);
        if (descriptor.formatVersion() > StudioProjectDescriptor.CURRENT_FORMAT_VERSION) {
            throw new IOException("Studio project format " + descriptor.formatVersion()
                    + " is newer than supported format "
                    + StudioProjectDescriptor.CURRENT_FORMAT_VERSION);
        }
        return descriptor;
    }

    public List<StudioRecentProject> recent() {
        if (!Files.isRegularFile(recents)) return List.of();
        try {
            List<StudioRecentProject> values = GSON.fromJson(Files.readString(recents), RECENT_LIST);
            if (values == null) return List.of();
            return values.stream()
                    .sorted(Comparator
                            .comparing(StudioRecentProject::pinned).reversed()
                            .thenComparing(StudioRecentProject::lastOpenedAtEpochMillis).reversed())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Optional<StudioProjectDescriptor> resolve(StudioRecentProject recent) {
        if (recent == null) return Optional.empty();
        try {
            return Optional.of(read(Path.of(recent.descriptorPath())));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public StudioProjectDescriptor markOpened(StudioProjectDescriptor descriptor) throws IOException {
        StudioProjectDescriptor opened = Objects.requireNonNull(descriptor, "descriptor").openedNow();
        write(opened);
        remember(opened);
        return opened;
    }

    public void removeRecent(String projectId) throws IOException {
        String id = requireText(projectId, "projectId");
        List<StudioRecentProject> values = new ArrayList<>(recent());
        values.removeIf(entry -> entry.projectId().equals(id));
        writeRecents(values);
    }

    public void setPinned(String projectId, boolean pinned) throws IOException {
        String id = requireText(projectId, "projectId");
        List<StudioRecentProject> values = new ArrayList<>();
        for (StudioRecentProject entry : recent()) {
            values.add(entry.projectId().equals(id)
                    ? new StudioRecentProject(
                            entry.projectId(), entry.name(), entry.kind(), entry.sourcePath(),
                            entry.descriptorPath(), entry.lastOpenedAtEpochMillis(), pinned)
                    : entry);
        }
        writeRecents(values);
    }

    public Path projectDirectory(String projectId) {
        return projects.resolve(requireText(projectId, "projectId")).toAbsolutePath().normalize();
    }

    public Path descriptorFile(String projectId) {
        return projectDirectory(projectId).resolve(DESCRIPTOR_FILE);
    }

    private void remember(StudioProjectDescriptor descriptor) throws IOException {
        LinkedHashMap<String, StudioRecentProject> byId = new LinkedHashMap<>();
        for (StudioRecentProject entry : recent()) byId.put(entry.projectId(), entry);
        StudioRecentProject previous = byId.get(descriptor.projectId());
        boolean pinned = previous != null && previous.pinned();
        byId.put(descriptor.projectId(), new StudioRecentProject(
                descriptor.projectId(),
                descriptor.name(),
                descriptor.kind(),
                descriptor.sourcePath(),
                descriptorFile(descriptor.projectId()).toString(),
                descriptor.lastOpenedAtEpochMillis(),
                pinned));
        writeRecents(new ArrayList<>(byId.values()));
    }

    private void writeRecents(List<StudioRecentProject> values) throws IOException {
        values.sort(Comparator
                .comparing(StudioRecentProject::pinned).reversed()
                .thenComparing(StudioRecentProject::lastOpenedAtEpochMillis).reversed());
        atomicJson(recents, GSON.toJson(values, RECENT_LIST));
    }

    private static void atomicJson(Path path, String json) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, json, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
