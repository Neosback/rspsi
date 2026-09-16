package com.rspsi.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Stable on-disk layout for an RSPSi OSRS project.
 *
 * <p>Cache data is selected explicitly by the caller and is not copied into
 * this directory by this class. The project directory owns identity,
 * autosave records, and edit journals.</p>
 */
public record ProjectLayout(Path root) {
    public static final String METADATA_FILE = "project.json";
    public static final String AUTOSAVE_DIRECTORY = "autosave";
    public static final String EDITS_DIRECTORY = "edits";

    public ProjectLayout {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path metadataFile() {
        return root.resolve(METADATA_FILE);
    }

    public Path autosaveDirectory() {
        return root.resolve(AUTOSAVE_DIRECTORY);
    }

    public Path editsDirectory() {
        return root.resolve(EDITS_DIRECTORY);
    }

    /** Creates the project directories and writes the initial identity file. */
    public void initialize(ProjectMetadata metadata) throws IOException {
        Objects.requireNonNull(metadata, "metadata");
        Files.createDirectories(autosaveDirectory());
        Files.createDirectories(editsDirectory());
        ProjectMetadataStore.write(metadataFile(), metadata);
    }

    public ProjectMetadata readMetadata() throws IOException {
        return ProjectMetadataStore.read(metadataFile());
    }
}
