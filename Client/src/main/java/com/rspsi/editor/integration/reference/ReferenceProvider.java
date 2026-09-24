package com.rspsi.editor.integration.reference;

import com.rspsi.editor.symbols.SymbolNamespace;

import java.util.List;

/**
 * Provider interface supplying server source code references for map objects, NPCs, and interfaces.
 */
public interface ReferenceProvider {

    String id();

    List<ContentReference> referencesFor(SymbolNamespace namespace, int id, String symbolicName);

    List<ContentReference> referencesInModule(String module);

    /**
     * Enumerates this provider's current reference snapshot for graph/index consumers.
     *
     * <p>Query-only providers may keep the default empty implementation.</p>
     */
    default List<ContentReference> allReferences() {
        return List.of();
    }

    int totalReferenceCount();
}
