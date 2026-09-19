package com.rspsi.editor.integration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Inspection result from probing a prospective server project folder.
 */
public record IntegrationProbe(
        Path projectRoot,
        String providerId,
        String serverName,
        boolean valid,
        Set<IntegrationCapability> detectedCapabilities,
        Map<String, String> details
) {
    public IntegrationProbe {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(serverName, "serverName");
        detectedCapabilities = Collections.unmodifiableSet(Set.copyOf(detectedCapabilities == null ? Set.of() : detectedCapabilities));
        details = Collections.unmodifiableMap(Map.copyOf(details == null ? Map.of() : details));
    }

    public static IntegrationProbe invalid(Path projectRoot, String providerId) {
        return new IntegrationProbe(projectRoot, providerId, "Unknown", false, Set.of(), Map.of());
    }

    public boolean supports(IntegrationCapability capability) {
        return detectedCapabilities.contains(capability);
    }
}
