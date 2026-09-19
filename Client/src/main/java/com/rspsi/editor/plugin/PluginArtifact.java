package com.rspsi.editor.plugin;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Isolated runtime container for an external plugin loaded from a JAR.
 *
 * <p>Each artifact owns its individual classloader and SHA-256 integrity hash,
 * enabling independent unloading, dependency isolation, and fault tolerance.</p>
 */
public final class PluginArtifact implements AutoCloseable {
    private final Path jarPath;
    private final String sha256Hash;
    private final URLClassLoader classLoader;
    private final List<EditorPlugin> plugins;
    private boolean closed;

    public PluginArtifact(Path jarPath, String sha256Hash,
                          URLClassLoader classLoader, List<? extends EditorPlugin> plugins) {
        this.jarPath = Objects.requireNonNull(jarPath, "jarPath");
        this.sha256Hash = Objects.requireNonNull(sha256Hash, "sha256Hash");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.plugins = List.copyOf(Objects.requireNonNull(plugins, "plugins"));
    }

    public Path jarPath() { return jarPath; }

    public String sha256Hash() { return sha256Hash; }

    public URLClassLoader classLoader() { return classLoader; }

    public List<EditorPlugin> plugins() { return plugins; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            classLoader.close();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Failed to close classloader for " + jarPath, error);
        }
    }
}
