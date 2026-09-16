package com.rspsi.project;

import com.rspsi.cache.OsrsCacheMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLayoutTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesDocumentedProjectDirectoriesAndMetadata() throws Exception {
        ProjectLayout layout = new ProjectLayout(temporaryDirectory.resolve("castle"));
        ProjectMetadata metadata = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, 2, "cache-fingerprint"));

        layout.initialize(metadata);

        assertTrue(Files.isDirectory(layout.root()));
        assertTrue(Files.isDirectory(layout.autosaveDirectory()));
        assertTrue(Files.isDirectory(layout.editsDirectory()));
        assertEquals(layout.autosaveDirectory().resolve("session.json"),
                layout.sessionAutosaveFile());
        assertEquals(metadata, layout.readMetadata());
    }

    @Test
    void metadataUpdatesAreReadableAfterAtomicReplacement() throws Exception {
        ProjectLayout layout = new ProjectLayout(temporaryDirectory.resolve("castle"));
        ProjectMetadata first = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, null, "first"));
        ProjectMetadata second = ProjectMetadata.forCache(
                new OsrsCacheMetadata(241, null, "second"));
        layout.initialize(first);

        ProjectMetadataStore.write(layout.metadataFile(), second);

        assertEquals(second, layout.readMetadata());
        try (var files = Files.list(layout.root())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
