package com.rspsi.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Atomic JSON persistence for launcher-level Studio project descriptors. */
public final class StudioProjectDescriptorStore {
    public static final String DESCRIPTOR_FILE = "project.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private StudioProjectDescriptorStore() {
    }

    public static Path descriptorPath(Path projectDataRoot) {
        return Objects.requireNonNull(projectDataRoot, "projectDataRoot")
                .toAbsolutePath().normalize().resolve(DESCRIPTOR_FILE);
    }

    public static void write(Path descriptorPath, StudioProjectDescriptor descriptor)
            throws IOException {
        Objects.requireNonNull(descriptorPath, "descriptorPath");
        Objects.requireNonNull(descriptor, "descriptor");
        Path normalized = descriptorPath.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "project-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    GSON.toJson(descriptor),
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(
                        temporary,
                        normalized,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static StudioProjectDescriptor read(Path descriptorPath) throws IOException {
        Objects.requireNonNull(descriptorPath, "descriptorPath");
        StudioProjectDescriptor descriptor =
                GSON.fromJson(Files.readString(descriptorPath), StudioProjectDescriptor.class);
        if (descriptor == null) {
            throw new IOException("Studio project descriptor is empty: " + descriptorPath);
        }
        if (descriptor.formatVersion() > StudioProjectDescriptor.CURRENT_FORMAT_VERSION) {
            throw new IOException(
                    "Studio project descriptor format " + descriptor.formatVersion()
                            + " is newer than supported format "
                            + StudioProjectDescriptor.CURRENT_FORMAT_VERSION);
        }
        return descriptor;
    }
}
