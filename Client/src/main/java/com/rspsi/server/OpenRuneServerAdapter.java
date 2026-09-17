package com.rspsi.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * First-party, capability-only integration for an OpenRune-Server checkout.
 *
 * <p>This adapter describes the project and its Gradle actions. It does not
 * import OpenRune classes, open a cache, or make Studio depend on a server
 * checkout. The OSRS bundle remains usable when this adapter is absent.</p>
 */
public final class OpenRuneServerAdapter implements ServerAdapter {
    public static final String ID = "openrune-server";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "OpenRune Server";
    }

    @Override
    public ServerDetection detect(Path root) {
        Path normalized = normalize(root);
        if (!Files.isDirectory(normalized)) {
            return ServerDetection.notMatched(normalized, "project root is not a directory");
        }
        List<String> evidence = new ArrayList<>();
        if (Files.isRegularFile(normalized.resolve("or-cache/build.gradle.kts"))) {
            evidence.add("or-cache/build.gradle.kts");
        }
        if (Files.isRegularFile(normalized.resolve("game.yml"))) {
            evidence.add("game.yml");
        }
        if (Files.isDirectory(normalized.resolve(".data"))) {
            evidence.add(".data/");
        }
        if (Files.isRegularFile(normalized.resolve("gradlew"))
                || Files.isRegularFile(normalized.resolve("gradlew.bat"))) {
            evidence.add("Gradle wrapper");
        }
        boolean matched = evidence.contains("or-cache/build.gradle.kts")
                || (evidence.contains("game.yml") && evidence.contains("Gradle wrapper"));
        int confidence = evidence.contains("or-cache/build.gradle.kts") ? 100
                : matched ? 70 : 0;
        String message = matched
                ? "OpenRune project markers detected"
                : "no OpenRune project markers found";
        return new ServerDetection(matched, confidence, evidence, message);
    }

    @Override
    public ServerProject project(Path root) {
        Path normalized = normalize(root);
        requireDetected(normalized);
        EnumSet<ServerCapability> capabilities = EnumSet.of(
                ServerCapability.PROJECT_LAYOUT,
                ServerCapability.CACHE_DISCOVERY,
                ServerCapability.BUILD_CACHE,
                ServerCapability.FRESH_CACHE,
                ServerCapability.CLEAN_CS2,
                ServerCapability.MERGE_GAMEVALS,
                ServerCapability.PACK_MODULES);
        return new ServerProject(normalized,
                Path.of(".data/cache/LIVE"),
                Path.of(".data/cache/SERVER"),
                List.of(Path.of("content"), Path.of("or-cache")),
                capabilities);
    }

    @Override
    public Optional<ServerBuildProvider> buildProvider(Path root) {
        Path normalized = normalize(root);
        requireDetected(normalized);
        return Optional.of(() -> List.of(
                task(normalized, "build-cache", "Build Cache", ":or-cache:buildCache",
                        ServerCapability.BUILD_CACHE),
                task(normalized, "fresh-cache", "Fresh Cache", ":or-cache:freshCache",
                        ServerCapability.FRESH_CACHE),
                task(normalized, "clean-cs2", "Clean CS2", ":or-cache:cleanCs2",
                        ServerCapability.CLEAN_CS2),
                task(normalized, "merge-plugin-gamevals", "Merge Plugin GameVals",
                        ":or-cache:mergePluginGamevals", ServerCapability.MERGE_GAMEVALS)));
    }

    private static ServerBuildTask task(Path root, String id, String label, String gradleTask,
                                        ServerCapability capability) {
        return new ServerBuildTask(id, label,
                List.of("./gradlew", gradleTask),
                root,
                java.util.Set.of(capability));
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "root").toAbsolutePath().normalize();
    }
}
