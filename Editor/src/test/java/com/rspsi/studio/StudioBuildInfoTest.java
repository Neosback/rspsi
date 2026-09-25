package com.rspsi.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudioBuildInfoTest {

    @Test
    void explicitVersionPropertyWinsAndFeedsDisplayVersion() {
        String key = "openrune.studio.version";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "9.8.7-test");

            assertEquals("9.8.7-test", StudioBuildInfo.version());
            assertEquals(
                    "OpenRune Content Studio  v9.8.7-test",
                    StudioBuildInfo.displayVersion());
        } finally {
            restore(key, previous);
        }
    }

    @Test
    void blankExplicitVersionFallsBackToBuildMetadataOrDevelopmentVersion() {
        String key = "openrune.studio.version";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "   ");

            String version = StudioBuildInfo.version();
            assertNotNull(version);
            assertFalse(version.isBlank());
            assertTrue(StudioBuildInfo.displayVersion().endsWith(version));
        } finally {
            restore(key, previous);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
