package com.rspsi.editor.integration;

import java.nio.file.Path;

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

    IntegrationSession open(Path project, IntegrationOptions options);
}
