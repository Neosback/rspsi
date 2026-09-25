package com.rspsi.editor.integration;

import com.rspsi.editor.integration.content.ContentDiscoveryService;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.integration.semantic.SemanticContentGraph;
import com.rspsi.editor.integration.semantic.SemanticSourceIndex;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
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

    /** Returns the active neutral declarative content catalog when it has been loaded. */
    public Optional<ContentDiscoveryService.Discovery> activeContentDiscovery() {
        return activeSession == null ? Optional.empty() : activeSession.contentDiscovery();
    }

    /** Returns the active provider-neutral semantic source snapshot when available. */
    public Optional<SemanticSourceIndex> activeSemanticSourceIndex() {
        return activeSession == null ? Optional.empty() : activeSession.semanticSourceIndex();
    }

    /** Returns the active provider-neutral semantic content graph when available. */
    public Optional<SemanticContentGraph> activeSemanticContentGraph() {
        return activeSession == null ? Optional.empty() : activeSession.semanticContentGraph();
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
     * Promotes the active project-owned integration session to include additional capabilities.
     *
     * <p>This is the canonical lazy-loading path for expensive OpenRune services such as source
     * semantics, declarative content, references and NPC spawns. The existing persisted
     * connection/overrides and provider settings are preserved. If opening the promoted session
     * fails, the currently active session remains bound.</p>
     */
    public synchronized IntegrationSession ensureCapabilities(Set<IntegrationCapability> requested) {
        Objects.requireNonNull(requested, "requested");
        if (activeSession == null) {
            throw new IllegalStateException("No server integration session is active");
        }
        if (requested.isEmpty() || activeSession.activeCapabilities().containsAll(requested)) {
            return activeSession;
        }

        EnumSet<IntegrationCapability> desired = EnumSet.noneOf(IntegrationCapability.class);
        desired.addAll(activeSession.activeCapabilities());
        desired.addAll(requested);

        IntegrationSession current = activeSession;
        ServerIntegrationProvider provider = current.provider();
        ServerConnection connection = current.connection()
                .orElseGet(() -> ServerConnection.forRoot(current.projectRoot()));
        IntegrationOptions previous = current.options();
        IntegrationOptions promotedOptions = new IntegrationOptions(
                current.projectRoot(), desired, previous.settings());

        IntegrationSession promoted = provider.open(connection, promotedOptions);

        unbind(current);
        activeSession = null;
        try {
            return bind(promoted);
        } catch (RuntimeException failure) {
            try {
                promoted.close();
            } catch (RuntimeException ignored) {
            }
            // Re-open the previous capability set so callers do not lose the project-owned
            // integration merely because an optional lazy capability failed to bind.
            IntegrationSession restored = provider.open(connection, previous);
            return bind(restored);
        }
    }

    public IntegrationSession ensureCapabilities(IntegrationCapability... requested) {
        Objects.requireNonNull(requested, "requested");
        EnumSet<IntegrationCapability> capabilities = EnumSet.noneOf(IntegrationCapability.class);
        java.util.Collections.addAll(capabilities, requested);
        return ensureCapabilities(capabilities);
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
        IntegrationSession candidate = Objects.requireNonNull(session, "session");
        String symbolId = candidate.symbolProvider().map(provider -> provider.id()).orElse(null);
        String referenceId = candidate.referenceProvider().map(provider -> provider.id()).orElse(null);
        String spawnId = candidate.npcSpawnProvider().map(provider -> provider.id()).orElse(null);
        try {
            candidate.symbolProvider().ifPresent(symbolService::registerProvider);
            candidate.referenceProvider().ifPresent(referenceService::registerProvider);
            candidate.npcSpawnProvider().ifPresent(npcSpawnService::registerProvider);
            this.activeSession = candidate;
            return candidate;
        } catch (RuntimeException failure) {
            if (symbolId != null) symbolService.unregisterProvider(symbolId);
            if (referenceId != null) referenceService.unregisterProvider(referenceId);
            if (spawnId != null) npcSpawnService.unregisterProvider(spawnId);
            throw failure;
        }
    }

    /**
     * Disconnects the active session and unbinds its providers.
     */
    public synchronized void disconnect() {
        if (activeSession == null) return;
        IntegrationSession closing = activeSession;
        activeSession = null;
        unbind(closing);
    }

    private void unbind(IntegrationSession session) {
        session.symbolProvider().ifPresent(p -> symbolService.unregisterProvider(p.id()));
        session.referenceProvider().ifPresent(p -> referenceService.unregisterProvider(p.id()));
        session.npcSpawnProvider().ifPresent(p -> npcSpawnService.unregisterProvider(p.id()));
        session.close();
    }
}
