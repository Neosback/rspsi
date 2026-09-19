package com.rspsi.editor.integration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User-selected configuration options for opening a server integration session.
 */
public record IntegrationOptions(
        Path projectRoot,
        Set<IntegrationCapability> enabledCapabilities,
        Map<String, String> settings
) {
    public IntegrationOptions {
        Objects.requireNonNull(projectRoot, "projectRoot");
        enabledCapabilities = Collections.unmodifiableSet(Set.copyOf(enabledCapabilities == null ? Set.of() : enabledCapabilities));
        settings = Collections.unmodifiableMap(Map.copyOf(settings == null ? Map.of() : settings));
    }

    public static IntegrationOptions defaults(Path projectRoot, Set<IntegrationCapability> capabilities) {
        return new IntegrationOptions(projectRoot, capabilities, Map.of());
    }

    public boolean isEnabled(IntegrationCapability capability) {
        return enabledCapabilities.contains(capability);
    }
}
