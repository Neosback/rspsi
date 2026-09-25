package com.rspsi.editor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Architecture memory for the modular-monolith composition rule.
 *
 * <p>Core Studio behavior belongs under editor/core and must not drift back
 * into an internal "built-in plugin" hierarchy.</p>
 */
class CoreCompositionBoundaryTest {

    @Test
    void builtInPluginProductionPackageDoesNotReturn() {
        Path retired = Path.of("src/main/java/com/rspsi/editor/plugin/builtin");
        assertFalse(Files.exists(retired),
                "Core features are CoreEditorModules, not built-in EditorPlugins");
    }

    @Test
    void coreModulesDoNotBecomePluginsOrSelfDiscoveringSubsystems() throws Exception {
        Path root = Path.of("src/main/java/com/rspsi/editor/core");
        List<String> forbidden = List.of(
                "implements EditorPlugin",
                "extends EditorPlugin",
                "ServiceLoader.load(",
                "EditorPluginLoader.discover(");

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    for (String token : forbidden) {
                        if (source.contains(token)) {
                            fail("Forbidden core composition pattern '" + token + "' in " + path);
                        }
                    }
                } catch (java.io.IOException error) {
                    fail("Unable to inspect " + path, error);
                }
            });
        }
    }
}
