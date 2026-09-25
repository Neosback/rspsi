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
    void allApplicationCompositionRootsUseTheCanonicalCoreManifest() throws Exception {
        assertCanonicalComposition(
                Path.of("src/main/java/com/rspsi/studio/StudioApplication.java"),
                "StudioApplication");
        assertCanonicalComposition(
                Path.of("src/main/java/com/rspsi/ui/workspace/ControlledWorkspaceBridge.java"),
                "ControlledWorkspaceBridge");
    }

    private static void assertCanonicalComposition(Path sourcePath, String owner) throws Exception {
        String source = Files.readString(sourcePath);

        assertTrue(source.contains("CoreEditorModules.all()"),
                owner + " must mount the canonical core manifest");
        assertTrue(source.contains("initializeWithCoreModules"),
                owner + " must use the shared core-aware runtime host");

        for (String forbidden : List.of(
                "CoreToolsPlugin",
                "TilePainterToolPlugin",
                "SplinePathToolPlugin",
                "plugin.builtin.tool")) {
            if (source.contains(forbidden)) {
                fail(owner + " contains retired core composition path: " + forbidden);
            }
        }
    }
}
