package com.rspsi.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A declarative build action shown by Studio; execution remains a host concern. */
public record ServerBuildTask(
        String id,
        String label,
        List<String> command,
        Path workingDirectory,
        Set<ServerCapability> capabilities,
        List<Path> outputPaths) {
    public ServerBuildTask(String id, String label, List<String> command,
                           Path workingDirectory, Set<ServerCapability> capabilities) {
        this(id, label, command, workingDirectory, capabilities, List.of());
    }

    public ServerBuildTask {
        id = requireText(id, "id");
        label = requireText(label, "label");
        command = List.copyOf(command == null ? List.of() : command);
        if (command.isEmpty()) throw new IllegalArgumentException("command cannot be empty");
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("at least one capability is required");
        }
        outputPaths = List.copyOf(outputPaths == null ? List.of() : outputPaths.stream()
                .map(path -> Objects.requireNonNull(path, "output path").toAbsolutePath().normalize())
                .toList());
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
