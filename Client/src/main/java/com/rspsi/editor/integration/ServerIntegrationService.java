package com.rspsi.editor.integration;

import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Studio service coordinating server project detection, active connection sessions,
 * and service bindings (symbols, content references, NPC spawns).
 */
public final class ServerIntegrationService {
    private final List<ServerIntegrationProvider> providers = new CopyOnWriteArrayList<>();
    private final SymbolService symbolService;
    private final ReferenceService referenceService;
    private final NpcSpawnService npcSpawnService;
    private IntegrationSession activeSession;

    /**
     * Creates a standalone integration service with its own isolated symbol, reference, and
     * spawn service instances. Suitable for unit tests or one-off CLI use.
     *
     * <p>The application-level instance should always prefer the three-argument constructor so
     * all Studio services share a single source of truth.</p>
     */
    public ServerIntegrationService() {
        this(new SymbolService(), new ReferenceService(), new NpcSpawnService());
    }

    public ServerIntegrationService(SymbolService symbolService,
                                  ReferenceService referenceService,
                                  NpcSpawnService npcSpawnService) {
        this.symbolService = Objects.requireNonNull(symbolService, "symbolService");
        this.referenceService = Objects.requireNonNull(referenceService, "referenceService");
        this.npcSpawnService = Objects.requireNonNull(npcSpawnService, "npcSpawnService");
    }

    public SymbolService symbolService() {
        return symbolService;
    }

    public ReferenceService referenceService() {
        return referenceService;
    }

    public NpcSpawnService npcSpawnService() {
        return npcSpawnService;
    }

    public void registerProvider(ServerIntegrationProvider provider) {
        Objects.requireNonNull(provider, "provider");
        providers.add(provider);
    }

    public void unregisterProvider(String providerId) {
        providers.removeIf(p -> p.id().equals(providerId));
    }

    public List<ServerIntegrationProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    public Optional<IntegrationSession> activeSession() {
        return Optional.ofNullable(activeSession);
    }

    public boolean isConnected() {
        return activeSession != null;
    }

    /** Returns the authoritative inspection for the active project when the provider exposes one. */
    public Optional<ServerProjectInspection> activeProjectInspection() {
        return activeSession == null ? Optional.empty() : activeSession.projectInspection();
    }

    /**
     * Probes all registered providers against a prospective server project folder.
     */
    public Optional<IntegrationProbe> probe(Path path) {
        if (path == null) return Optional.empty();
        for (ServerIntegrationProvider provider : providers) {
            if (provider.canOpen(path)) {
                return Optional.of(provider.probe(path));
            }
        }
        return Optional.empty();
    }

    /** Probes a persisted project connection without discarding its overrides or fingerprint. */
    public Optional<IntegrationProbe> probe(ServerConnection connection) {
        if (connection == null) return Optional.empty();
        Path path = connection.root();
        for (ServerIntegrationProvider provider : providers) {
            if (provider.canOpen(path)) {
                return Optional.of(provider.probe(connection));
            }
        }
        return Optional.empty();
    }

    /**
     * Connects a server project, opening a session and binding its providers to Studio services.
     */
    public IntegrationSession connect(Path path, IntegrationOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        return connectPath(path, options);
    }

    /**
     * Connects an already-persisted server connection without throwing away path, command or
     * fingerprint overrides established by project inspection/settings.
     */
    public IntegrationSession connect(ServerConnection connection, IntegrationOptions options) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(options, "options");

        disconnect();
        Path path = connection.root();
        for (ServerIntegrationProvider provider : providers) {
            if (provider.canOpen(path)) {
                return bind(provider.open(connection, options));
            }
        }
        throw new IllegalArgumentException("No server integration provider recognized project at: " + path);
    }

    private IntegrationSession connectPath(Path path, IntegrationOptions options) {
        disconnect();
        for (ServerIntegrationProvider provider : providers) {
            if (provider.canOpen(path)) {
                return bind(provider.open(path, options));
            }
        }
        throw new IllegalArgumentException("No server integration provider recognized project at: " + path);
    }

    private IntegrationSession bind(IntegrationSession session) {
        this.activeSession = Objects.requireNonNull(session, "session");
        session.symbolProvider().ifPresent(symbolService::registerProvider);
        session.referenceProvider().ifPresent(referenceService::registerProvider);
        session.npcSpawnProvider().ifPresent(npcSpawnService::registerProvider);
        return session;
    }

    /**
     * Disconnects the active session and unbinds its providers.
     */
    public void disconnect() {
        if (activeSession != null) {
            activeSession.symbolProvider().ifPresent(p -> symbolService.unregisterProvider(p.id()));
            activeSession.referenceProvider().ifPresent(p -> referenceService.unregisterProvider(p.id()));
            activeSession.npcSpawnProvider().ifPresent(p -> npcSpawnService.unregisterProvider(p.id()));

            activeSession.close();
            activeSession = null;
        }
    }
}
