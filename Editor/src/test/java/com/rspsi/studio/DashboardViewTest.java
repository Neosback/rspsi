package com.rspsi.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DashboardViewTest {

    @Test
    void emptyPathDoesNotPointToDirectoryOrValidCache() {
        DashboardView view = new DashboardView("");
        assertFalse(view.pointsToDirectory());
        assertFalse(view.pointsToValidCache());
    }

    @Test
    void nonExistentPathDoesNotPointToDirectoryOrValidCache(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        DashboardView view = new DashboardView(missing.toString());
        assertFalse(view.pointsToDirectory());
        assertFalse(view.pointsToValidCache());
    }

    @Test
    void directoryWithoutDat2PointsToDirectoryButNotValidCache(@TempDir Path tempDir) {
        DashboardView view = new DashboardView(tempDir.toString());
        assertTrue(view.pointsToDirectory());
        assertFalse(view.pointsToValidCache());
    }

    @Test
    void directoryWithDat2PointsToDirectoryAndValidCache(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("main_file_cache.dat2"));
        DashboardView view = new DashboardView(tempDir.toString());
        assertTrue(view.pointsToDirectory());
        assertTrue(view.pointsToValidCache());
    }

    @Test
    void regionTextDefaultsToLumbridge() {
        DashboardView view = new DashboardView(null);
        assertEquals("50,50", view.regionText());
    }
}
