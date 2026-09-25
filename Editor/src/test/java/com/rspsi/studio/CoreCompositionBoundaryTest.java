package com.rspsi.studio;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Prevents hidden core-tool composition from creeping back into the native shell. */
class CoreCompositionBoundaryTest {

    @Test
    void studioApplicationUsesOnlyTheCanonicalCoreManifest() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/rspsi/studio/StudioApplication.java");
        String source = Files.readString(sourcePath);

        assertTrue(source.contains("CoreEditorModules.all()"),
                "StudioApplication must mount the canonical core manifest");
        assertTrue(source.contains("initializeWithCoreModules"),
                "StudioApplication must use the shared core-aware runtime host");

        for (String forbidden : List.of(
                "CoreToolsPlugin",
                "TilePainterToolPlugin",
                "SplinePathToolPlugin",
                "plugin.builtin.tool")) {
            if (source.contains(forbidden)) {
                fail("StudioApplication contains retired core composition path: " + forbidden);
            }
        }
    }
}
