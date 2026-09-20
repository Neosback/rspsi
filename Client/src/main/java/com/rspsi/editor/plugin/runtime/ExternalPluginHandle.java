package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPlugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loaded external plugin artifact and the isolated classloader that owns it. */
public final class ExternalPluginHandle implements AutoCloseable {
    private final Path jarPath;
    private final String sha256;
    private final ExternalPluginManifest manifest;
    private final IsolatedPluginClassLoader classLoader;
    private final List<EditorPlugin> plugins;
    private boolean closed;

    ExternalPluginHandle(Path jarPath, String sha256, ExternalPluginManifest manifest,
                         IsolatedPluginClassLoader classLoader,
                         List<? extends EditorPlugin> plugins) {
        this.jarPath = Objects.requireNonNull(jarPath, "jarPath");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.manifest = manifest;
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.plugins = List.copyOf(plugins);
    }

    public Path jarPath() { return jarPath; }
    public String sha256() { return sha256; }
    public Optional<ExternalPluginManifest> manifest() { return Optional.ofNullable(manifest); }
    public List<EditorPlugin> plugins() { return plugins; }
    public ClassLoader classLoader() { return classLoader; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            classLoader.close();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to close plugin " + jarPath, error);
        }
    }
}
