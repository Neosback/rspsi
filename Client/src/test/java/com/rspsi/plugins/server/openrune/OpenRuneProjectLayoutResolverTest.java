package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.content.ContentArtifact;
import com.rspsi.editor.integration.content.ContentCapability;
import com.rspsi.editor.integration.content.ContentDiscoveryService;
import com.rspsi.editor.integration.content.ProjectLayoutResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneProjectLayoutResolverTest {
    @TempDir
    Path root;

    @Test
    void discoversModernOpenRuneDefinitionAndSymbolSources() throws IOException {
        Path npc = write(".data/raw-cache/map/npcs/lumbridge.toml");
        Path area = write(".data/raw-cache/map/area/lumbridge.toml");
        Path server = write(".data/raw-cache/server/items.toml");
        Path client = write(".data/raw-cache/items.toml");
        Path rscm = write(".data/gamevals/loc.rscm");
        Path pack = write(
                "content/skills/mining/pack/src/main/resources/pack/configs/rocks.toml");
        Path pluginGamevals = write(
                "content/skills/mining/src/main/resources/gamevals.toml");
        write("content/skills/mining/build/resources/main/gamevals.toml");

        OpenRuneProjectLayoutResolver resolver = new OpenRuneProjectLayoutResolver();
        ProjectLayoutResolver.ResolvedLayout layout = resolver.resolve(root).orElseThrow();

        assertEquals(OpenRuneProjectLayoutResolver.ID, layout.resolverId());
        assertTrue(layout.knownRoots().get(ContentCapability.SERVER_DEFINITIONS)
                .contains(root.resolve(".data/raw-cache/server").toAbsolutePath().normalize()));
        assertTrue(layout.knownRoots().get(ContentCapability.PACK_DEFINITIONS)
                .contains(pack.getParent().toAbsolutePath().normalize()));
        assertTrue(layout.knownRoots().get(ContentCapability.CACHE_DEFINITIONS)
                .contains(root.resolve(".data/raw-cache").toAbsolutePath().normalize()));
        assertTrue(layout.knownRoots().get(ContentCapability.GAMEVALS)
                .contains(pluginGamevals.toAbsolutePath().normalize()));
        assertTrue(layout.knownRoots().get(ContentCapability.SYMBOLS)
                .contains(root.resolve(".data/gamevals").toAbsolutePath().normalize()));

        ContentDiscoveryService.Discovery discovery =
                new ContentDiscoveryService().discover(root, layout);
        Map<Path, ContentCapability> capabilities = discovery.artifacts().stream()
                .filter(ContentArtifact::recognized)
                .collect(Collectors.toMap(
                        artifact -> artifact.path().toAbsolutePath().normalize(),
                        artifact -> artifact.capability().orElseThrow()));

        assertEquals(ContentCapability.NPC_SPAWNS, capabilities.get(npc.toAbsolutePath().normalize()));
        assertEquals(ContentCapability.AREAS, capabilities.get(area.toAbsolutePath().normalize()));
        assertEquals(ContentCapability.SERVER_DEFINITIONS,
                capabilities.get(server.toAbsolutePath().normalize()));
        assertEquals(ContentCapability.CACHE_DEFINITIONS,
                capabilities.get(client.toAbsolutePath().normalize()));
        assertEquals(ContentCapability.PACK_DEFINITIONS,
                capabilities.get(pack.toAbsolutePath().normalize()));
        assertEquals(ContentCapability.GAMEVALS,
                capabilities.get(pluginGamevals.toAbsolutePath().normalize()));
        assertEquals(ContentCapability.SYMBOLS,
                capabilities.get(rscm.toAbsolutePath().normalize()));

        assertFalse(discovery.artifacts().stream()
                .anyMatch(artifact -> artifact.path().toString().contains(
                        "build/resources/main/gamevals.toml")));
    }

    @Test
    void recognizesServerProjectFromCacheBuilderMarkerBeforeDataExists() throws IOException {
        write("or-cache/build.gradle.kts");

        ProjectLayoutResolver.ResolvedLayout layout =
                new OpenRuneProjectLayoutResolver().resolve(root).orElseThrow();

        assertEquals(OpenRuneProjectLayoutResolver.ID, layout.resolverId());
        assertTrue(layout.manifestRoots().contains(root));
    }

    @Test
    void rejectsUnrelatedDirectory() {
        assertTrue(new OpenRuneProjectLayoutResolver().resolve(root).isEmpty());
    }

    private Path write(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "# fixture\n");
        return file;
    }
}
