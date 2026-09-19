package com.rspsi.editor.plugin;

import java.util.Objects;

/**
 * Stable identity of whatever contributed a setting, panel, command, or other
 * owned extension point: the core application, a workspace, or a plugin.
 * Contributions tied to an owner are expected to disappear together when
 * that owner unloads.
 */
public record ContributionOwner(String id) {
    public ContributionOwner {
        Objects.requireNonNull(id, "owner id");
        if (id.isBlank()) throw new IllegalArgumentException("owner id must not be blank");
    }

    public static final ContributionOwner SYSTEM = new ContributionOwner("core");

    public static ContributionOwner core() {
        return SYSTEM;
    }

    public static ContributionOwner system() {
        return SYSTEM;
    }

    public static ContributionOwner workspace(String name) {
        Objects.requireNonNull(name, "workspace name");
        return new ContributionOwner("workspace:" + name);
    }

    public static ContributionOwner plugin(String pluginId) {
        Objects.requireNonNull(pluginId, "plugin id");
        return new ContributionOwner("plugin:" + pluginId);
    }
}
