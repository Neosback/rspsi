package com.rspsi.editor.plugin;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owned result of external plugin discovery, including its isolated artifacts and closeable loaders. */
public final class PluginDiscovery implements AutoCloseable {
    private final List<EditorPlugin> plugins;
    private final List<PluginArtifact> artifacts;
    private final URLClassLoader classLoader;
    private boolean closed;

    public PluginDiscovery(List<? extends EditorPlugin> plugins, URLClassLoader classLoader) {
        this(plugins, List.of(), classLoader);
    }

    public PluginDiscovery(List<? extends EditorPlugin> plugins, List<PluginArtifact> artifacts,
                           URLClassLoader classLoader) {
        this.plugins = List.copyOf(Objects.requireNonNull(plugins, "plugins"));
        this.artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        this.classLoader = classLoader;
    }

    public List<EditorPlugin> plugins() { return plugins; }

    public List<PluginArtifact> artifacts() { return artifacts; }

    public URLClassLoader classLoader() { return classLoader; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List<Throwable> errors = new ArrayList<>();
        for (PluginArtifact artifact : artifacts) {
            try {
                artifact.close();
            } catch (Throwable t) {
                errors.add(t);
            }
        }
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (java.io.IOException error) {
                errors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("Failed to close one or more plugin classloaders");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
