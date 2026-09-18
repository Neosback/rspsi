package com.rspsi.server;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persistable connection settings for a server located anywhere on disk. */
public record ServerConnection(
        Path root,
        String adapterId,
        Map<ServerPathKey, String> pathOverrides,
        Map<String, List<String>> commandOverrides,
        String expectedFingerprint) {
    public ServerConnection {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        adapterId = requireText(adapterId, "adapterId");
        pathOverrides = copyPaths(pathOverrides);
        commandOverrides = copyCommands(commandOverrides);
        expectedFingerprint = expectedFingerprint == null ? "" : expectedFingerprint.trim();
    }

    public static ServerConnection forRoot(Path root) {
        return new ServerConnection(root, OpenRuneServerAdapter.ID, Map.of(), Map.of(), "");
    }

    public Optional<String> pathOverride(ServerPathKey key) {
        return Optional.ofNullable(pathOverrides.get(Objects.requireNonNull(key, "key")));
    }

    public Optional<List<String>> commandOverride(String taskId) {
        return Optional.ofNullable(commandOverrides.get(requireText(taskId, "taskId")));
    }

    public ServerConnection withPath(ServerPathKey key, String value) {
        LinkedHashMap<ServerPathKey, String> paths = new LinkedHashMap<>(pathOverrides);
        paths.put(Objects.requireNonNull(key, "key"), requireText(value, "value"));
        return new ServerConnection(root, adapterId, paths, commandOverrides, expectedFingerprint);
    }

    public ServerConnection withCommand(String taskId, List<String> command) {
        LinkedHashMap<String, List<String>> commands = new LinkedHashMap<>(commandOverrides);
        commands.put(requireText(taskId, "taskId"), List.copyOf(command));
        return new ServerConnection(root, adapterId, pathOverrides, commands, expectedFingerprint);
    }

    public ServerConnection withExpectedFingerprint(String fingerprint) {
        return new ServerConnection(root, adapterId, pathOverrides, commandOverrides, fingerprint);
    }

    private static Map<ServerPathKey, String> copyPaths(Map<ServerPathKey, String> values) {
        LinkedHashMap<ServerPathKey, String> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "path key"),
                    requireText(value, "path override")));
        }
        return Map.copyOf(copy);
    }

    private static Map<String, List<String>> copyCommands(Map<String, List<String>> values) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> copy.put(requireText(key, "command id"),
                    List.copyOf(value == null ? List.of() : value)));
        }
        return Map.copyOf(copy);
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
