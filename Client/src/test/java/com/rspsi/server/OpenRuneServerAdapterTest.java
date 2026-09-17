package com.rspsi.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneServerAdapterTest {
    @Test
    void detectsProjectAndExposesCapabilityBasedBuildTasks() throws Exception {
        Path root = Files.createTempDirectory("openrune-server");
        Files.createDirectories(root.resolve("or-cache"));
        Files.writeString(root.resolve("or-cache/build.gradle.kts"), "plugins {}");
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
    }

    @Test
    void doesNotClaimAnArbitraryFolder() throws Exception {
        Path root = Files.createTempDirectory("not-openrune");
        ServerDetection detection = new OpenRuneServerAdapter().detect(root);
        assertFalse(detection.matched());
        assertTrue(detection.evidence().isEmpty());
    }
}
