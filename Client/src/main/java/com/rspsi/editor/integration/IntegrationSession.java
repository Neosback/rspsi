package com.rspsi.editor.integration;

import com.rspsi.editor.integration.npc.NpcSpawnProvider;
import com.rspsi.editor.integration.reference.ReferenceProvider;
import com.rspsi.editor.symbols.SymbolProvider;

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

    Optional<SymbolProvider> symbolProvider();

    Optional<ReferenceProvider> referenceProvider();

    Optional<NpcSpawnProvider> npcSpawnProvider();

    @Override
    void close();
}
