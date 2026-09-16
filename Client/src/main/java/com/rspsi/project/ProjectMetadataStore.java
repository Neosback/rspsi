package com.rspsi.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.Objects;

/** Reads and writes the small, cache-independent project metadata file. */
public final class ProjectMetadataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectMetadataStore() {
    }

    public static void write(Path path, ProjectMetadata metadata) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(metadata, "metadata");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = GSON.toJson(metadata);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, json, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static ProjectMetadata read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        ProjectMetadata metadata = GSON.fromJson(Files.readString(path), ProjectMetadata.class);
        if (metadata == null) {
            throw new IOException("Project metadata is empty: " + path);
        }
        return metadata;
    }
}
