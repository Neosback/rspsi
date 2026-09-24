package com.rspsi.studio;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.project.ProjectIntegrationCapability;
import com.rspsi.project.StudioProjectDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StudioApplicationStartupPolicyTest {

    @Test
    void startupDoesNotRequestOpenRuneContentProviders() {
        StudioProjectDescriptor project = StudioProjectDescriptor.server(
                "OpenRune",
                Path.of("build/test-project"),
                Path.of("OpenRune-Server-main"),
                "openrune-server",
                Set.of(
                        ProjectIntegrationCapability.PROJECT_READ,
                        ProjectIntegrationCapability.PROJECT_SOURCE_WRITE));

        Set<IntegrationCapability> capabilities =
                StudioApplication.integrationCapabilities(project);

        assertEquals(Set.of(), capabilities);
        assertFalse(capabilities.contains(IntegrationCapability.SYMBOLS));
        assertFalse(capabilities.contains(IntegrationCapability.GAMEVALS));
        assertFalse(capabilities.contains(IntegrationCapability.SOURCE_SEMANTICS));
        assertFalse(capabilities.contains(IntegrationCapability.CONTENT_GRAPH));
    }

    @Test
    void startupKeepsExplicitCacheBuildPermissionWithoutContentIndexing() {
        StudioProjectDescriptor project = StudioProjectDescriptor.server(
                "OpenRune",
                Path.of("build/test-project"),
                Path.of("OpenRune-Server-main"),
                "openrune-server",
                Set.of(
                        ProjectIntegrationCapability.PROJECT_READ,
                        ProjectIntegrationCapability.PROJECT_SOURCE_WRITE,
                        ProjectIntegrationCapability.CACHE_BUILD));

        assertEquals(
                Set.of(IntegrationCapability.CACHE_BUILD),
                StudioApplication.integrationCapabilities(project));
    }
}
