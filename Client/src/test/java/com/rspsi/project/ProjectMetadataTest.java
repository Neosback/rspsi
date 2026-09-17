package com.rspsi.project;

import com.rspsi.cache.OsrsCacheMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMetadataTest {
    private static final OsrsCacheMetadata CACHE = new OsrsCacheMetadata(240, 2, "sha256:test");

    @Test
    void metadataRoundTripsAndMatchesItsCache() throws Exception {
        ProjectMetadata metadata = ProjectMetadata.forCache(CACHE);
        Path file = Files.createTempFile("rspsi-project", ".json");

        ProjectMetadataStore.write(file, metadata);

        assertEquals(metadata, ProjectMetadataStore.read(file));
        assertTrue(Files.readString(file).contains("\"game\": \"oldschool\""));
        assertTrue(metadata.matches(CACHE));
        assertFalse(metadata.matches(new OsrsCacheMetadata(241, 0, "sha256:new")));
    }

    @Test
    void metadataIsOsrsOnly() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectMetadata(1, "317", 240, null, "cache"));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrsCacheMetadata(0, null, "cache"));
    }

    @Test
    void futureMetadataFormatFailsClosed() throws Exception {
        Path file = Files.createTempFile("rspsi-future-project", ".json");
        Files.writeString(file, """
                {"formatVersion":2,"game":"oldschool","cacheRevision":240,
                 "cacheSubRevision":null,"cacheFingerprint":"cache"}
                """);

        Exception error = assertThrows(java.io.IOException.class,
                () -> ProjectMetadataStore.read(file));
        assertTrue(error.getMessage().contains("newer than the supported format"));
    }
}
