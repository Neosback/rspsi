package com.rspsi.project;

import com.rspsi.cache.OsrsCacheMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectCompatibilityTest {

    private static final OsrsCacheMetadata CACHE =
            new OsrsCacheMetadata(240, 3, "sha256:cache-a");

    @Test
    void matchingCacheCanBeOpenedWritable() {
        ProjectCompatibility result = ProjectCompatibility.assess(ProjectMetadata.forCache(CACHE), CACHE);

        assertTrue(result.compatible());
        assertFalse(result.readOnly());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void cacheIdentityMismatchIsExplicitlyReadOnly() {
        ProjectCompatibility result = ProjectCompatibility.assess(
                ProjectMetadata.forCache(CACHE),
                new OsrsCacheMetadata(241, 3, "sha256:cache-b"));

        assertFalse(result.compatible());
        assertTrue(result.readOnly());
        assertEquals(java.util.List.of(
                "cache revision differs",
                "cache fingerprint differs"), result.issues());
    }
}
