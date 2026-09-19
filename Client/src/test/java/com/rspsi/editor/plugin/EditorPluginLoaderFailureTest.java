package com.rspsi.editor.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPluginLoaderFailureTest {

    @Test
    void discoverOwnedCapturesBrokenPluginJarInsteadOfSwallowingIt(@TempDir Path tempDir) throws IOException {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path jarPath = pluginsDir.resolve("broken-plugin.jar");
        writeJarWithMissingServiceProvider(jarPath);

        PluginDiscovery discovery = EditorPluginLoader.discoverOwned(
                pluginsDir, Thread.currentThread().getContextClassLoader());
        try {
            assertTrue(discovery.plugins().isEmpty(), "a broken candidate must not silently vanish as zero plugins");
            assertEquals(1, discovery.failures().size());
            PluginLoadFailure failure = discovery.failures().get(0);
            assertEquals(jarPath, failure.jarPath());
            assertNotNull(failure.cause());
        } finally {
            discovery.close();
        }
    }

    private static void writeJarWithMissingServiceProvider(Path jarPath) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("META-INF/services/com.rspsi.editor.plugin.EditorPlugin"));
            jar.write("com.rspsi.editor.plugin.NoSuchPluginClass\n".getBytes());
            jar.closeEntry();
        }
    }
}
