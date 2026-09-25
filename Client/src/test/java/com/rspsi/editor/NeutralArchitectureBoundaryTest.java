package com.rspsi.editor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class NeutralArchitectureBoundaryTest {
    private static final List<String> FORBIDDEN = List.of(
            "javafx.", "imgui.", "org.lwjgl.", "com.displee.", "dev.openrune.");

    @Test
    void neutralCoreSourcesDoNotImportFrontendOrBackendTypes() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/rspsi/editor"),
                Path.of("src/main/java/com/rspsi/cache"),
                Path.of("src/main/java/com/rspsi/project"),
                Path.of("src/main/java/com/rspsi/osrs"),
                Path.of("src/main/java/com/rspsi/server"));
        roots.forEach(root -> {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().contains("/cache/store/"))
                        .filter(path -> !path.toString().contains("/cache/workspace/"))
                        .filter(path -> !path.toString().contains("/cache/verify/"))
                        .forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int index = 0; index < lines.size(); index++) {
                        String line = lines.get(index);
                        for (String token : FORBIDDEN) {
                            if (line.contains(token)) {
                                fail("Forbidden neutral-core dependency " + token + " in "
                                        + path + ":" + (index + 1));
                            }
                        }
                    }
                } catch (java.io.IOException error) {
                    fail("Unable to inspect " + path, error);
                }
            });
            } catch (java.io.IOException error) {
                fail("Unable to inspect " + root, error);
            }
        });
    }
}
