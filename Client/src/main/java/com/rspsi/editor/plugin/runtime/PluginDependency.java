package com.rspsi.editor.plugin.runtime;

import java.util.Objects;

/** One required or optional dependency declared by a managed plugin manifest. */
public record PluginDependency(String id, VersionConstraint version, boolean optional) {
    public PluginDependency {
        id = Objects.requireNonNull(id, "dependency id").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Dependency id cannot be empty");
        version = version == null ? VersionConstraint.ANY : version;
    }

    public static PluginDependency required(String id, String version) {
        return new PluginDependency(id, VersionConstraint.parse(version), false);
    }

    public static PluginDependency optional(String id, String version) {
        return new PluginDependency(id, VersionConstraint.parse(version), true);
    }
}
