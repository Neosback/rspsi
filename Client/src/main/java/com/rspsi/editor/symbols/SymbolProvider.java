package com.rspsi.editor.symbols;

import java.util.List;
import java.util.Optional;

/**
 * Source provider of symbolic identifiers (e.g. Cache Definition Provider, OpenRune GameVal Provider,
 * or User Custom Symbol Provider).
 */
public interface SymbolProvider {

    String id();

    String name();

    Optional<Symbol> resolve(SymbolNamespace namespace, String name);

    List<Symbol> reverse(SymbolNamespace namespace, int id);

    List<Symbol> search(SymbolNamespace namespace, String query);

    List<Symbol> all(SymbolNamespace namespace);
}
