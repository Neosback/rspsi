package com.rspsi.server;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Optional integration point for server layouts and build commands. */
public interface ServerAdapter {
    String id();

    String displayName();

    ServerDetection detect(Path root);

    ServerProject project(Path root);

    default Optional<ServerBuildProvider> buildProvider(Path root) {
        return Optional.empty();
    }

    default void requireDetected(Path root) {
        Objects.requireNonNull(root, "root");
        ServerDetection detection = detect(root);
        if (!detection.matched()) {
            throw new IllegalArgumentException(displayName() + " was not detected at " + root
                    + ": " + detection.message());
        }
    }
}
