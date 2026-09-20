package com.rspsi.editor.plugin.runtime;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Versioned plugin-update feed downloaded from a repository URL. */
public record PluginRepositoryIndex(int schemaVersion, URI source, List<PluginRepositoryEntry> plugins) {
    public PluginRepositoryIndex {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported repository schema: " + schemaVersion);
        source = Objects.requireNonNull(source, "source");
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
    }
}
