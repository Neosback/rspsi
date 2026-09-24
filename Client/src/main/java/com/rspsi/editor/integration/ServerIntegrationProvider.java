package com.rspsi.editor.integration;

import com.rspsi.server.ServerConnection;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Service Provider Interface for server ecosystem integration (e.g. OpenRune, RSMod, or custom server).
 *
 * <p>Enables Studio to connect to server repositories, inspect capabilities, and bind symbols,
 * source cross-references, and NPC spawns without coupling core Studio to any server implementation.</p>
 */
public interface ServerIntegrationProvider {

    String id();

    String name();

    String description();

    boolean canOpen(Path project);

    IntegrationProbe probe(Path project);

    /**
     * Probes a persisted connection without discarding provider-neutral path/task overrides.
     *
     * <p>Providers that understand {@link ServerConnection} should override this method. The
     * default keeps older providers source-compatible and falls back to their path-only probe.</p>
     */
    default IntegrationProbe probe(ServerConnection connection) {
        Objects.requireNonNull(connection, "connection");
        return probe(connection.root());
    }

    IntegrationSession open(Path project, IntegrationOptions options);

    /**
     * Opens a persisted connection without losing its inspected project contract.
     *
     * <p>The path-only method remains the compatibility entry point for providers that do not use
     * Studio's neutral server connection model.</p>
     */
    default IntegrationSession open(ServerConnection connection, IntegrationOptions options) {
        Objects.requireNonNull(connection, "connection");
        return open(connection.root(), options);
    }
}
