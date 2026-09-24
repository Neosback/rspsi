package com.rspsi.editor.integration;

import com.rspsi.editor.integration.npc.NpcSpawnProvider;
import com.rspsi.editor.integration.reference.ReferenceProvider;
import com.rspsi.editor.integration.semantic.SemanticSourceIndex;
import com.rspsi.editor.symbols.SymbolProvider;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Handle to an active connected server project integration session.
 */
public interface IntegrationSession extends AutoCloseable {

    ServerIntegrationProvider provider();

    Path projectRoot();

    Set<IntegrationCapability> activeCapabilities();

    /**
     * Returns the persisted neutral connection when this integration was opened from one.
     */
    default Optional<ServerConnection> connection() {
        return Optional.empty();
    }

    /**
     * Returns the authoritative project inspection used to open this session when available.
     *
     * <p>This is intentionally neutral server-project metadata: cache roles, source roots,
     * build tasks, fingerprints, runtime plugins and diagnostics. Provider-specific source
     * semantics remain behind their own adapters.</p>
     */
    default Optional<ServerProjectInspection> projectInspection() {
        return Optional.empty();
    }

    Optional<SymbolProvider> symbolProvider();

    Optional<ReferenceProvider> referenceProvider();

    Optional<NpcSpawnProvider> npcSpawnProvider();

    /** Source-derived semantic facts with exact provenance when the provider supports them. */
    default Optional<SemanticSourceIndex> semanticSourceIndex() {
        return Optional.empty();
    }

    @Override
    void close();
}
