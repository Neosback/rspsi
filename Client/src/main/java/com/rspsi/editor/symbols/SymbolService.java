package com.rspsi.editor.symbols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Studio service coordinating symbolic identifier lookups, multi-provider aggregation,
 * reverse ID mapping, and conflict detection across cache and server content.
 */
public final class SymbolService {
    private final List<SymbolProvider> providers = new CopyOnWriteArrayList<>();

    public void registerProvider(SymbolProvider provider) {
        Objects.requireNonNull(provider, "provider");
        providers.add(0, provider); // Newest providers have highest lookup priority
    }

    public void unregisterProvider(String providerId) {
        providers.removeIf(p -> p.id().equals(providerId));
    }

    public List<SymbolProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Resolves a symbolic name to its Symbol mapping.
     */
    public Optional<Symbol> resolve(SymbolNamespace namespace, String name) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        String bare = namespace.unqualify(name);

        for (SymbolProvider provider : providers) {
            Optional<Symbol> symbol = provider.resolve(namespace, bare);
            if (symbol.isPresent()) return symbol;
        }
        return Optional.empty();
    }

    /**
     * Finds all symbols mapped to the given numeric ID across all registered providers.
     */
    public List<Symbol> reverse(SymbolNamespace namespace, int id) {
        Objects.requireNonNull(namespace, "namespace");
        List<Symbol> results = new ArrayList<>();
        for (SymbolProvider provider : providers) {
            results.addAll(provider.reverse(namespace, id));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the primary (highest-priority) qualified symbolic name for the given numeric ID, if known.
     */
    public Optional<String> primaryName(SymbolNamespace namespace, int id) {
        List<Symbol> symbols = reverse(namespace, id);
        return symbols.isEmpty() ? Optional.empty() : Optional.of(symbols.get(0).qualifiedName());
    }

    /**
     * Searches for symbols matching the query string across all namespaces.
     */
    public List<Symbol> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        String lower = query.toLowerCase(Locale.ROOT).trim();

        Map<String, Symbol> matched = new LinkedHashMap<>();
        for (SymbolNamespace ns : SymbolNamespace.values()) {
            for (SymbolProvider provider : providers) {
                for (Symbol s : provider.search(ns, lower)) {
                    matched.putIfAbsent(s.qualifiedName(), s);
                }
            }
        }
        return List.copyOf(matched.values());
    }

    /**
     * Audits all providers and detects any conflicting mappings where the same
     * symbolic name resolves to different numeric IDs.
     */
    public List<SymbolConflict> conflicts() {
        List<SymbolConflict> conflicts = new ArrayList<>();
        for (SymbolNamespace ns : SymbolNamespace.values()) {
            Map<String, Symbol> seen = new HashMap<>();
            for (SymbolProvider provider : providers) {
                for (Symbol s : provider.all(ns)) {
                    Symbol existing = seen.get(s.bareName());
                    if (existing != null && existing.id() != s.id()) {
                        conflicts.add(new SymbolConflict(
                                ns, s.bareName(),
                                existing.id(), existing.source(),
                                s.id(), s.source()
                        ));
                    } else if (existing == null) {
                        seen.put(s.bareName(), s);
                    }
                }
            }
        }
        return Collections.unmodifiableList(conflicts);
    }

    public int totalSymbolCount() {
        int count = 0;
        for (SymbolNamespace ns : SymbolNamespace.values()) {
            for (SymbolProvider p : providers) {
                count += p.all(ns).size();
            }
        }
        return count;
    }
}
