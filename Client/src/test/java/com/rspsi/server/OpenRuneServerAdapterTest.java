package com.rspsi.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void startupInspectionDoesNotWalkCacheOrContentTrees() throws Exception {
        Path root = fixtureRoot("240.2");
        Path livePayload = root.resolve(".data/cache/LIVE/7/42.dat");
        Path contentPayload = root.resolve("content/skills/mining/Mining.kt");
        Files.createDirectories(livePayload.getParent());
        Files.createDirectories(contentPayload.getParent());
        Files.writeString(livePayload, "first");
        Files.writeString(contentPayload, "class MiningOne");

        OpenRuneServerAdapter adapter = new OpenRuneServerAdapter();
        ServerProjectInspection first = adapter.inspectStartup(root);

        assertTrue(first.content().isEmpty());
        assertTrue(first.gradleModel().isEmpty());
        assertFalse(first.git().available(),
                "startup should not run repository-wide Git status");

        Files.writeString(livePayload, "second");
        Files.writeString(contentPayload, "class MiningTwo");
        ServerProjectInspection nestedChange = adapter.inspectStartup(root);
        assertEquals(first.fingerprint(), nestedChange.fingerprint(),
                "nested cache/content file changes must not require startup tree traversal");

        Files.writeString(root.resolve("game.yml"),
                "revision: 240.2\nname: Changed Startup Identity\n");
        ServerProjectInspection identityChange = adapter.inspectStartup(root);
        assertNotEquals(first.fingerprint(), identityChange.fingerprint(),
                "startup identity should still react to important project configuration");
    }

    @Test
    void startupDefersFullStaleFingerprintValidation() throws Exception {
        Path root = fixtureRoot("240.2");
        OpenRuneServerAdapter adapter = new OpenRuneServerAdapter();
        ServerConnection expected = ServerConnection.forRoot(root)
                .withExpectedFingerprint("full-inspection-fingerprint");

        ServerProjectInspection startup = adapter.inspectStartup(expected);
        assertFalse(startup.status() == ServerIntegrationStatus.STALE);

        ServerProjectInspection full = adapter.inspect(expected);
        assertEquals(ServerIntegrationStatus.STALE, full.status());
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
    void connectedInspectionUsesEvaluatedGradleSourceSetsAndCustomTaskPaths() throws Exception {
        Path root = Files.createTempDirectory("openrune-gradle-model");
        Path live = root.resolve(".data/cache/LIVE");
        Path module = root.resolve("modules/gameplay/mining");
        Path sources = module.resolve("src/customKotlin");
        Path resources = module.resolve("src/customResources");
        Files.createDirectories(live);
        Files.createDirectories(sources);
        Files.createDirectories(resources.resolve("pack/configs"));
        Files.writeString(sources.resolve("Mining.kt"), "class Mining\n");
        Files.writeString(resources.resolve("gamevals.toml"),
                "[gamevals.obj]\ncoal = 2000\n");
        Files.writeString(resources.resolve("pack/configs/mining.toml"),
                "target = \"loc.coal_rock\"\n");
        Files.writeString(root.resolve("game.yml"), "revision: 240.2\n");

        String payload = "{"
                + "\"rootName\":\"CustomOpenRune\","
                + "\"gradleVersion\":\"8.14.3\","
                + "\"projects\":["
                + "{"
                + "\"path\":\":gameplay:mining\","
                + "\"name\":\"mining\","
                + "\"projectDir\":\"" + json(module) + "\","
                + "\"buildFile\":\"" + json(module.resolve("build.gradle.kts")) + "\","
                + "\"sourceSets\":[{"
                + "\"name\":\"main\","
                + "\"sources\":[\"" + json(sources) + "\"],"
                + "\"resources\":[\"" + json(resources) + "\"],"
                + "\"outputs\":[]"
                + "}],"
                + "\"tasks\":[],"
                + "\"projectDependencies\":[\":api\"],"
                + "\"pluginClasses\":[]"
                + "},"
                + "{"
                + "\"path\":\":cache-tools\","
                + "\"name\":\"cache-tools\","
                + "\"projectDir\":\"" + json(root.resolve("tools/cache")) + "\","
                + "\"buildFile\":\"" + json(root.resolve("tools/cache/build.gradle.kts")) + "\","
                + "\"sourceSets\":[],"
                + "\"tasks\":[{"
                + "\"path\":\":cache-tools:buildCache\","
                + "\"name\":\"buildCache\","
                + "\"group\":\"cache\","
                + "\"description\":\"Build cache\""
                + "}],"
                + "\"projectDependencies\":[],"
                + "\"pluginClasses\":[]"
                + "}"
                + "]}";
        Path wrapperSentinel = root.resolve("wrapper-invoked");
        Files.writeString(root.resolve("gradlew"),
                "#!/bin/sh\n"
                        + "touch '" + wrapperSentinel.toAbsolutePath() + "'\n"
                        + "printf '%s\\n' 'RSPSI_GRADLE_MODEL="
                        + payload.replace("'", "'\\''") + "'\n");

        ServerProjectInspection passive = new OpenRuneServerAdapter().inspect(root);
        assertTrue(passive.gradleModel().isEmpty());
        assertFalse(Files.exists(wrapperSentinel), "passive inspection must not execute Gradle");
        assertFalse(passive.content().stream()
                .anyMatch(entry -> entry.path().equals(sources.resolve("Mining.kt").toAbsolutePath().normalize())));

        ServerProjectInspection connected = new OpenRuneServerAdapter().inspectConnected(root);

        assertTrue(Files.exists(wrapperSentinel), "connected inspection should evaluate Gradle");
        assertTrue(connected.gradleModel().isPresent(), connected.diagnostics().toString());
        assertTrue(connected.supports(ServerCapability.GRADLE_PROJECT_MODEL));
        assertTrue(connected.supports(ServerCapability.CONTENT_INVENTORY));
        assertTrue(connected.supports(ServerCapability.GAMEVALS));
        assertTrue(connected.content().stream()
                .anyMatch(entry -> entry.path().equals(
                        sources.resolve("Mining.kt").toAbsolutePath().normalize())
                        && entry.kind() == ServerContentKind.SERVER_SCRIPT));
        assertTrue(connected.content().stream()
                .anyMatch(entry -> entry.path().equals(
                        resources.resolve("pack/configs/mining.toml").toAbsolutePath().normalize())
                        && entry.kind() == ServerContentKind.CONFIG));

        ServerBuildTask buildCache = connected.buildTasks().stream()
                .filter(task -> task.id().equals("build-cache"))
                .findFirst()
                .orElseThrow();
        assertEquals(":cache-tools:buildCache", buildCache.command().get(1));
        assertFalse(connected.diagnostics().stream()
                .anyMatch(message -> message.contains("Gradle task not found in or-cache build: buildCache")));
        assertEquals(":gameplay:mining",
                connected.gradleModel().orElseThrow().projects().get(0).path());
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

    private static String json(Path path) {
        return path.toAbsolutePath().normalize().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
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
