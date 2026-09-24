package com.rspsi.server;

import com.rspsi.server.gradle.GradleProjectModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, explainable snapshot of an inspected server connection. */
public record ServerProjectInspection(
        ServerConnection connection,
        ServerDetection detection,
        ServerIntegrationStatus status,
        Map<ServerPathKey, Path> paths,
        String revision,
        ServerGitState git,
        List<ServerContentEntry> content,
        List<ServerPluginInfo> plugins,
        List<ServerBuildTask> buildTasks,
        Set<ServerCapability> capabilities,
        Optional<GradleProjectModel> gradleModel,
        List<String> diagnostics,
        String fingerprint) {

    /** Compatibility constructor for passive inspections that have not evaluated the Gradle build. */
    public ServerProjectInspection(
            ServerConnection connection,
            ServerDetection detection,
            ServerIntegrationStatus status,
            Map<ServerPathKey, Path> paths,
            String revision,
            ServerGitState git,
            List<ServerContentEntry> content,
            List<ServerPluginInfo> plugins,
            List<ServerBuildTask> buildTasks,
            Set<ServerCapability> capabilities,
            List<String> diagnostics,
            String fingerprint) {
        this(connection, detection, status, paths, revision, git, content, plugins, buildTasks,
                capabilities, Optional.empty(), diagnostics, fingerprint);
    }

    public ServerProjectInspection {
        connection = Objects.requireNonNull(connection, "connection");
        detection = Objects.requireNonNull(detection, "detection");
        status = Objects.requireNonNull(status, "status");
        paths = Map.copyOf(paths == null ? Map.of() : paths);
        revision = revision == null ? "" : revision.trim();
        git = Objects.requireNonNull(git, "git");
        content = List.copyOf(content == null ? List.of() : content);
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
        buildTasks = List.copyOf(buildTasks == null ? List.of() : buildTasks);
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
        gradleModel = gradleModel == null ? Optional.empty() : gradleModel;
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint").trim();
        if (fingerprint.isEmpty()) throw new IllegalArgumentException("fingerprint cannot be empty");
    }

    public Optional<Path> path(ServerPathKey key) {
        return Optional.ofNullable(paths.get(Objects.requireNonNull(key, "key")));
    }

    public boolean supports(ServerCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }
}
