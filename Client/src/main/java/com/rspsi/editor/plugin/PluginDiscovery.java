package com.rspsi.editor.plugin;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

/** Owned result of external plugin discovery, including its closeable loader. */
public final class PluginDiscovery implements AutoCloseable {
    private final List<EditorPlugin> plugins;
    private final URLClassLoader classLoader;
    private boolean closed;

    public PluginDiscovery(List<? extends EditorPlugin> plugins, URLClassLoader classLoader) {
        this.plugins = List.copyOf(Objects.requireNonNull(plugins, "plugins"));
        this.classLoader = classLoader;
    }

    public List<EditorPlugin> plugins() { return plugins; }

    public URLClassLoader classLoader() { return classLoader; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Unable to close external plugin classloader", error);
            }
        }
    }
}
