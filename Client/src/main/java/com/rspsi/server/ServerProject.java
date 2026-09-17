package com.rspsi.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Neutral description of a detected server project. */
public record ServerProject(
        Path root,
        Path liveCache,
        Path serverCache,
        List<Path> contentRoots,
        Set<ServerCapability> capabilities) {
    public ServerProject {
        root = normalize(root, "root");
        Path projectRoot = root;
        liveCache = normalize(projectRoot.resolve(liveCache), "liveCache");
        serverCache = normalize(projectRoot.resolve(serverCache), "serverCache");
        contentRoots = contentRoots == null ? List.of() : contentRoots.stream()
                .map(path -> normalize(projectRoot.resolve(path), "content root"))
                .toList();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
