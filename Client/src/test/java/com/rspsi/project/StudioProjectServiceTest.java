package com.rspsi.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioProjectServiceTest {
    @TempDir
    Path temp;

    @Test
    void standaloneProjectPersistsDescriptorWithoutLoadingCache() throws Exception {
        Path cache = temp.resolve("cache");
        Files.createDirectories(cache);
        Files.write(cache.resolve("main_file_cache.dat2"), new byte[]{0});

        Path registryFile = temp.resolve("recent-projects.json");
        StudioProjectRegistry registry = new StudioProjectRegistry(registryFile);
        StudioProjectService service = new StudioProjectService(registry);
        Path projectData = temp.resolve("projects/world-edit");

        StudioProjectDescriptor descriptor =
                service.createStandalone("World Edit", projectData, cache);

        assertEquals(StudioProjectKind.STANDALONE_OSRS_CACHE, descriptor.kind());
        assertEquals(cache.toAbsolutePath().normalize(), descriptor.sourcePathValue());
        assertTrue(Files.isRegularFile(
                StudioProjectDescriptorStore.descriptorPath(projectData)));
        assertEquals(1, registry.recent().size());
        assertEquals(descriptor.projectId(), registry.recent().get(0).projectId());
    }

    @Test
    void openRuneProjectPersistsNeutralProviderAndPolicyCapabilities() throws Exception {
        Path server = temp.resolve("server");
        Files.createDirectories(server.resolve("or-cache"));
        Files.writeString(server.resolve("or-cache/build.gradle.kts"), "");
        Files.writeString(server.resolve("game.yml"), "revision: 240.2\n");
        Files.writeString(server.resolve("gradlew"), "#!/bin/sh\n");

        StudioProjectRegistry registry =
                new StudioProjectRegistry(temp.resolve("recent-projects.json"));
        StudioProjectService service = new StudioProjectService(registry);

        StudioProjectDescriptor descriptor = service.linkOpenRune(
                "My OpenRune",
                temp.resolve("projects/my-openrune"),
                server,
                ProjectIntegrationPreset.MANAGED_BUILD);

        assertEquals(StudioProjectKind.OPENRUNE_SERVER, descriptor.kind());
        assertEquals("server.openrune", descriptor.providerId());
        assertTrue(descriptor.capabilities()
                .contains(ProjectIntegrationCapability.PROJECT_SOURCE_WRITE));
        assertTrue(descriptor.capabilities()
                .contains(ProjectIntegrationCapability.CACHE_BUILD));
        assertFalse(descriptor.capabilities()
                .contains(ProjectIntegrationCapability.FRESH_CACHE_RESET));
    }

    @Test
    void recentRegistryKeepsPinnedFirstThenNewest() throws Exception {
        StudioProjectRegistry registry =
                new StudioProjectRegistry(temp.resolve("recent-projects.json"));
        StudioProjectService service = new StudioProjectService(registry);

        Path cacheA = cache("cache-a");
        Path cacheB = cache("cache-b");
        StudioProjectDescriptor a =
                service.createStandalone("A", temp.resolve("projects/a"), cacheA);
        Thread.sleep(2L);
        StudioProjectDescriptor b =
                service.createStandalone("B", temp.resolve("projects/b"), cacheB);

        List<RecentStudioProject> initial = registry.recent();
        assertEquals("B", initial.get(0).name());

        registry.setPinned(a.projectId(), true);
        List<RecentStudioProject> pinned = registry.recent();
        assertEquals("A", pinned.get(0).name());
        assertTrue(pinned.get(0).pinned());
    }

    @Test
    void missingRecentProjectRemainsVisibleUntilRemoved() throws Exception {
        StudioProjectRegistry registry =
                new StudioProjectRegistry(temp.resolve("recent-projects.json"));
        StudioProjectService service = new StudioProjectService(registry);
        StudioProjectDescriptor descriptor = service.createStandalone(
                "A", temp.resolve("projects/a"), cache("cache-a"));

        Path descriptorPath =
                StudioProjectDescriptorStore.descriptorPath(descriptor.projectDataPath());
        Files.delete(descriptorPath);

        RecentStudioProject recent = registry.recent().get(0);
        assertFalse(recent.available());

        registry.remove(descriptor.projectId());
        assertTrue(registry.recent().isEmpty());
    }

    @Test
    void invalidStandaloneSourceDoesNotCommitDescriptor() {
        StudioProjectRegistry registry =
                new StudioProjectRegistry(temp.resolve("recent-projects.json"));
        StudioProjectService service = new StudioProjectService(registry);
        Path projectData = temp.resolve("projects/bad");

        assertThrows(Exception.class, () ->
                service.createStandalone("Bad", projectData, temp.resolve("not-a-cache")));
        assertFalse(Files.exists(StudioProjectDescriptorStore.descriptorPath(projectData)));
        assertTrue(registry.recent().isEmpty());
    }

    @Test
    void automaticStandaloneNeedsOnlyCacheDirectoryAndReusesProject() throws Exception {
        Path cache = cache("my-cache");
        StudioProjectRegistry registry =
                new StudioProjectRegistry(temp.resolve("studio/recent-projects.json"));
        StudioProjectService service = new StudioProjectService(registry);

        StudioProjectDescriptor first = service.createStandalone(cache);
        StudioProjectDescriptor reopened = service.createStandalone(cache);

        assertEquals("my-cache", first.name());
        assertEquals(first.projectId(), reopened.projectId());
        assertEquals(cache.toAbsolutePath().normalize(), first.sourcePathValue());
        assertTrue(first.projectDataPath().startsWith(temp.resolve("studio/projects").toAbsolutePath()));
        assertTrue(Files.isRegularFile(
                StudioProjectDescriptorStore.descriptorPath(first.projectDataPath())));
        assertEquals(1, registry.recent().size());
    }

    @Test
    void automaticOpenRuneImportNeedsOnlyServerDirectoryAndAccessLevel() throws Exception {
        Path server = temp.resolve("OpenRune-Server");
        Files.createDirectories(server.resolve("or-cache"));
        Files.writeString(server.resolve("or-cache/build.gradle.kts"), "");
        Files.writeString(server.resolve("game.yml"), "revision: 240.2\n");
        Files.writeString(server.resolve("gradlew"), "#!/bin/sh\n");

        StudioProjectRegistry registry =
                new StudioProjectRegistry(temp.resolve("studio/recent-projects.json"));
        StudioProjectService service = new StudioProjectService(registry);

        StudioProjectDescriptor descriptor =
                service.linkOpenRune(server, ProjectIntegrationPreset.AUTHOR);

        assertEquals("OpenRune-Server", descriptor.name());
        assertEquals(StudioProjectKind.OPENRUNE_SERVER, descriptor.kind());
        assertEquals(server.toAbsolutePath().normalize(), descriptor.sourcePathValue());
        assertTrue(descriptor.capabilities()
                .contains(ProjectIntegrationCapability.PROJECT_SOURCE_WRITE));
        assertFalse(descriptor.capabilities()
                .contains(ProjectIntegrationCapability.CACHE_BUILD));
        assertTrue(descriptor.projectDataPath().startsWith(
                temp.resolve("studio/projects").toAbsolutePath()));
    }

    private Path cache(String name) throws Exception {
        Path cache = temp.resolve(name);
        Files.createDirectories(cache);
        Files.write(cache.resolve("main_file_cache.dat2"), new byte[]{0});
        return cache;
    }
}
