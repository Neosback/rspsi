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

    int totalReferenceCount();
}
