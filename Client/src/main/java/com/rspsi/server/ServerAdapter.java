package com.rspsi.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Optional integration point for server layouts and build commands. */
public interface ServerAdapter {
    String id();

    String displayName();

    ServerDetection detect(Path root);

    ServerProject project(Path root);

    /** Inspects a saved connection without opening its cache or loading server code. */
    default ServerProjectInspection inspect(ServerConnection connection) {
        throw new UnsupportedOperationException("Adapter does not provide project inspection: " + id());
    }

    /** Returns the capabilities supported by an already inspected connection. */
    default Set<ServerCapability> capabilities(ServerProjectInspection inspection) {
        return Objects.requireNonNull(inspection, "inspection").capabilities();
    }

    /** Returns declarative build tasks for an already saved connection. */
    default List<ServerBuildTask> buildTasks(ServerConnection connection) {
        return inspect(connection).buildTasks();
    }

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
