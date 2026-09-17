package com.rspsi.editor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class NeutralPluginContractTest {
    @Test
    void pluginContractsDoNotImportFrontendOrCacheBackends() throws Exception {
        List<String> forbidden = List.of(
                "javafx.", "com.displee.", "dev.openrune.", "org.openrs2.");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/rspsi/editor/plugin"))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int index = 0; index < lines.size(); index++) {
                        for (String token : forbidden) {
                            if (lines.get(index).contains(token)) {
                                fail("Forbidden plugin dependency " + token + " in "
                                        + path + ":" + (index + 1));
                            }
                        }
                    }
                } catch (java.io.IOException error) {
                    fail("Unable to inspect " + path, error);
                }
            });
        }
    }
}
