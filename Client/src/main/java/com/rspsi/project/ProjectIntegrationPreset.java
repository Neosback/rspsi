package com.rspsi.project;

import java.util.Set;

/** User-facing project policy presets backed by explicit neutral capabilities. */
public enum ProjectIntegrationPreset {
    INSPECT(Set.of(
            ProjectIntegrationCapability.PROJECT_READ)),

    AUTHOR(Set.of(
            ProjectIntegrationCapability.PROJECT_READ,
            ProjectIntegrationCapability.PROJECT_SOURCE_WRITE)),

    MANAGED_BUILD(Set.of(
            ProjectIntegrationCapability.PROJECT_READ,
            ProjectIntegrationCapability.PROJECT_SOURCE_WRITE,
            ProjectIntegrationCapability.CACHE_BUILD,
            ProjectIntegrationCapability.GAMEVAL_BUILD,
            ProjectIntegrationCapability.CS2_BUILD)),

    DEVELOPER(Set.of(
            ProjectIntegrationCapability.PROJECT_READ,
            ProjectIntegrationCapability.PROJECT_SOURCE_WRITE,
            ProjectIntegrationCapability.CACHE_BUILD,
            ProjectIntegrationCapability.GAMEVAL_BUILD,
            ProjectIntegrationCapability.CS2_BUILD,
            ProjectIntegrationCapability.SERVER_LAUNCH,
            ProjectIntegrationCapability.EXTERNAL_COMMAND));

    private final Set<ProjectIntegrationCapability> capabilities;

    ProjectIntegrationPreset(Set<ProjectIntegrationCapability> capabilities) {
        this.capabilities = Set.copyOf(capabilities);
    }

    public Set<ProjectIntegrationCapability> capabilities() {
        return capabilities;
    }
}
