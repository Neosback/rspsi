package com.rspsi.editor.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Keeps the editor contracts usable by JavaFX, Dear ImGui, or another future
 * frontend. This is intentionally a source-level check: the forbidden types
 * must not enter the neutral package boundary even indirectly through a new
 * import.
 */
class UiNeutralImportTest {

    private static final List<String> NEUTRAL_SOURCE_DIRECTORIES = List.of(
            "com/rspsi/editor",
            "com/rspsi/project",
            "com/rspsi/cache/definition",
            "com/rspsi/cache/map");

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import javafx.",
            "import imgui.",
            "import org.lwjgl.",
            "import com.displee.",
            "import org.openrs2.",
            "import org.rsmod.",
            "import org.openrune.");

    @Test
    void neutralPackagesDoNotImportFrontendRendererOrDonorTypes() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();

        for (String directory : NEUTRAL_SOURCE_DIRECTORIES) {
            Path packageRoot = sourceRoot.resolve(directory);
            assertTrue(Files.isDirectory(packageRoot), "Missing neutral source directory: " + packageRoot);
            try (Stream<Path> files = Files.walk(packageRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collectForbiddenImports(path, violations));
            }
        }

        assertTrue(violations.isEmpty(), () -> "UI-neutral import violations:\n" + String.join("\n", violations));
    }

    private static void collectForbiddenImports(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
                String line = lines.get(lineNumber).trim();
                for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                    if (line.startsWith(prefix)) {
                        violations.add(path + ":" + (lineNumber + 1) + ": " + line);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect neutral source file " + path, exception);
        }
    }
}
