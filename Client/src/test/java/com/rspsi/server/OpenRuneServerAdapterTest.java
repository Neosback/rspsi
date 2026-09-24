package com.rspsi.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneServerAdapterTest {
    @Test
    void detectsProjectAndExposesCapabilityBasedBuildTasks() throws Exception {
        Path root = Files.createTempDirectory("openrune-server");
        Files.createDirectories(root.resolve("or-cache"));
        Files.writeString(root.resolve("or-cache/build.gradle.kts"),
                "tasks.register(\"buildCache\") {}\n"
                        + "tasks.register(\"freshCache\") {}\n"
                        + "tasks.register(\"cleanCs2\") {}\n"
                        + "tasks.register(\"mergePluginGamevals\") {}\n");
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n");

        OpenRuneServerAdapter adapter = new OpenRuneServerAdapter();
        ServerDetection detection = adapter.detect(root);
        assertTrue(detection.matched());
        assertEquals(100, detection.confidence());

        ServerProject project = adapter.project(root);
        assertEquals(root.toAbsolutePath().normalize(), project.root());
        assertEquals(root.resolve(".data/cache/LIVE").toAbsolutePath().normalize(),
                project.liveCache());
        assertTrue(project.capabilities().contains(ServerCapability.BUILD_CACHE));

        var tasks = adapter.buildProvider(root).orElseThrow().tasks();
        assertEquals(4, tasks.size());
        assertEquals(":or-cache:buildCache", tasks.get(0).command().get(1));
        assertEquals(root.toAbsolutePath().normalize(), tasks.get(0).workingDirectory());

        ServerConnection connection = ServerConnection.forRoot(root);
        assertEquals(tasks, adapter.buildTasks(connection));
        assertEquals(adapter.inspect(connection).capabilities(),
                adapter.capabilities(adapter.inspect(connection)));
    }

    @Test
    void doesNotClaimAnArbitraryFolder() throws Exception {
        Path root = Files.createTempDirectory("not-openrune");
        ServerDetection detection = new OpenRuneServerAdapter().detect(root);
        assertFalse(detection.matched());
        assertTrue(detection.evidence().isEmpty());
    }

    @Test
    void inspectsCachesContentPacksGamevalsAndExternalPluginsWithoutLoadingCode() throws Exception {
        Path root = fixtureRoot("240.2");
        Files.createDirectories(root.resolve("content/events/example/pack/src/main/resources/pack/configs"));
        Files.createDirectories(root.resolve("content/events/example/pack/src/main/resources/pack/models"));
        Files.createDirectories(root.resolve("content/events/example/pack/src/main/resources/pack/cs2"));
        Files.createDirectories(root.resolve("content/events/example/src/main/resources"));
        Files.writeString(root.resolve("content/events/example/pack/src/main/resources/pack/configs/example.toml"), "x");
        Files.writeString(root.resolve("content/events/example/pack/src/main/resources/pack/models/example.dat"), "x");
        Files.writeString(root.resolve("content/events/example/pack/src/main/resources/pack/cs2/example.cs2"), "x");
        Files.writeString(root.resolve("content/events/example/src/main/resources/gamevals.toml"), "[gamevals.obj]");
        Files.writeString(root.resolve("content/events/example/Example.kt"), "class Example");
        Files.createDirectories(root.resolve(".data/raw-cache/map"));
        Files.writeString(root.resolve(".data/raw-cache/map/m50_50.dat"), "x");
        Files.writeString(root.resolve(".data/gamevals/obj.rscm"), "obj.example=63000");
        Path plugin = root.resolve("plugins/example-plugin");
        Files.createDirectories(plugin);
        Files.writeString(plugin.resolve("plugin.properties"),
                "name=Example\ndescription=Test\nrevision=240.2\nauthor=Studio\n");
        Files.writeString(plugin.resolve("Example.class"), "not executed");

        ServerProjectInspection inspection = new OpenRuneServerAdapter().inspect(root);

        assertEquals(ServerIntegrationStatus.SUPPORTED, inspection.status());
        assertEquals("240.2", inspection.revision());
        assertTrue(inspection.supports(ServerCapability.CACHE_DISCOVERY));
        assertTrue(inspection.supports(ServerCapability.PACK_MODULES));
        assertTrue(inspection.supports(ServerCapability.GAMEVALS));
        assertTrue(inspection.content().stream().anyMatch(e -> e.kind() == ServerContentKind.CONFIG));
        assertTrue(inspection.content().stream().anyMatch(e -> e.kind() == ServerContentKind.MODEL));
        assertTrue(inspection.content().stream().anyMatch(e -> e.kind() == ServerContentKind.CS2));
        assertEquals(1, inspection.plugins().size());
        assertEquals("Example", inspection.plugins().get(0).name());
        assertTrue(inspection.plugins().get(0).external());
    }

    @Test
    void supportsMovedPathsAndReportsAChangedConnectionAsStale() throws Exception {
        Path root = fixtureRoot("240.2");
        Path customLive = root.resolve("custom/client-cache");
        Files.createDirectories(customLive);
        ServerConnection connection = ServerConnection.forRoot(root)
                .withPath(ServerPathKey.LIVE_CACHE, "custom/client-cache");
        OpenRuneServerAdapter adapter = new OpenRuneServerAdapter();
        ServerProjectInspection first = adapter.inspect(connection);
        assertEquals(ServerIntegrationStatus.SUPPORTED_WITH_OVERRIDES, first.status());
        assertEquals(customLive.toAbsolutePath().normalize(),
                first.path(ServerPathKey.LIVE_CACHE).orElseThrow());

        Files.writeString(root.resolve("game.yml"), "revision: 240.2\nname: Changed\n");
        ServerProjectInspection stale = adapter.inspect(connection.withExpectedFingerprint(first.fingerprint()));
        assertEquals(ServerIntegrationStatus.STALE, stale.status());
        assertTrue(stale.diagnostics().stream().anyMatch(message -> message.contains("changed")));
    }

    @Test
    void keepsProjectIntegrationAvailableOutsideVerifiedCacheRevisionProfile() throws Exception {
        Path root = fixtureRoot("317");
        ServerProjectInspection inspection = new OpenRuneServerAdapter().inspect(root);
        assertEquals(ServerIntegrationStatus.SUPPORTED, inspection.status());
        assertTrue(inspection.diagnostics().stream()
                .anyMatch(message -> message.contains("cache semantics require validation")));
    }

    @Test
    void doesNotAdvertiseUndeclaredGradleTasksWithoutAnOverride() throws Exception {
        Path root = Files.createTempDirectory("openrune-server");
        Files.createDirectories(root.resolve("or-cache"));
        Files.createDirectories(root.resolve(".data/cache/LIVE"));
        Files.writeString(root.resolve("or-cache/build.gradle.kts"), "plugins {}\n");
        Files.writeString(root.resolve("game.yml"), "revision: 240.2\n");
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n");

        ServerProjectInspection inspection = new OpenRuneServerAdapter().inspect(root);

        assertTrue(inspection.buildTasks().isEmpty());
        assertTrue(inspection.diagnostics().stream()
                .anyMatch(message -> message.contains("Gradle task not found")));
    }

    @Test
    void connectionTomlRoundTripsOverridesAndFingerprint() throws Exception {
        Path root = fixtureRoot("240.2");
        ServerConnection original = ServerConnection.forRoot(root)
                .withPath(ServerPathKey.LIVE_CACHE, "custom/live")
                .withCommand("build-cache", List.of("gradlew", ":or-cache:buildCache"))
                .withExpectedFingerprint("abc123");
        Path file = Files.createTempFile("rspsi-server", ".toml");
        ServerConnectionToml.write(file, original);

        ServerConnection restored = ServerConnectionToml.read(file);
        assertEquals(original.root(), restored.root());
        assertEquals(original.pathOverrides(), restored.pathOverrides());
        assertEquals(original.commandOverrides(), restored.commandOverrides());
        assertEquals(original.expectedFingerprint(), restored.expectedFingerprint());
    }

    @Test
    void buildRunnerExecutesDeclaredArgumentsFromTheDeclaredRoot() throws Exception {
        Path root = Files.createTempDirectory("openrune-build");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ServerBuildTask task = new ServerBuildTask("probe", "Probe",
                List.of(java, "-version"), root, Set.of(ServerCapability.BUILD_CACHE));

        ServerBuildResult result = new ServerBuildRunner().run(task, Duration.ofSeconds(5));

        assertTrue(result.succeeded());
        assertFalse(result.output().isEmpty());
    }

    private static Path fixtureRoot(String revision) throws Exception {
        Path root = Files.createTempDirectory("openrune-server");
        Files.createDirectories(root.resolve("or-cache"));
        Files.createDirectories(root.resolve(".data/cache/LIVE"));
        Files.createDirectories(root.resolve(".data/cache/SERVER"));
        Files.createDirectories(root.resolve(".data/raw-cache"));
        Files.createDirectories(root.resolve(".data/gamevals"));
        Files.createDirectories(root.resolve("content"));
        Files.writeString(root.resolve("or-cache/build.gradle.kts"),
                "tasks.register(\"buildCache\") {}\n"
                        + "tasks.register(\"freshCache\") {}\n"
                        + "tasks.register(\"cleanCs2\") {}\n"
                        + "tasks.register(\"mergePluginGamevals\") {}\n");
        Files.writeString(root.resolve("build.gradle.kts"), "tasks.register(\"run\") {}\n");
        Files.writeString(root.resolve("game.yml"), "revision: " + revision + "\nname: Test\n");
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n");
        return root;
    }
}
