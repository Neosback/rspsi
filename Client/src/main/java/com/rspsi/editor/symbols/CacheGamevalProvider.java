package com.rspsi.editor.symbols;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Built-in SymbolProvider that automatically derives baseline symbolic names
 * from loaded cache definition strings (e.g. object names).
 */
public final class CacheGamevalProvider implements SymbolProvider {
    private final DefinitionProvider definitions;
    private final Map<Integer, Symbol> locSymbolsById = new HashMap<>();
    private final Map<String, Symbol> locSymbolsByName = new HashMap<>();

    public CacheGamevalProvider(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        indexLocs();
    }

    private void indexLocs() {
        for (int id : definitions.objectIds()) {
            Optional<ObjectDefinitionView> def = definitions.object(id);
            if (def.isPresent() && def.get().name() != null && !def.get().name().isBlank()) {
                String cleanName = sanitizeName(def.get().name());
                if (!cleanName.isBlank()) {
                    Symbol s = Symbol.of(SymbolNamespace.LOC, cleanName, id, "OSRS Cache");
                    locSymbolsById.putIfAbsent(id, s);
                    locSymbolsByName.putIfAbsent(cleanName, s);
                }
            }
        }
    }

    private static String sanitizeName(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    @Override
    public String id() {
        return "osrs.cache.gamevals";
    }

    @Override
    public String name() {
        return "OSRS Cache Definitions";
    }

    @Override
    public Optional<Symbol> resolve(SymbolNamespace namespace, String name) {
        if (namespace == SymbolNamespace.LOC) {
            return Optional.ofNullable(locSymbolsByName.get(name.toLowerCase(Locale.ROOT)));
        }
        return Optional.empty();
    }

    @Override
    public List<Symbol> reverse(SymbolNamespace namespace, int id) {
        if (namespace == SymbolNamespace.LOC) {
            Symbol s = locSymbolsById.get(id);
            return s != null ? List.of(s) : List.of();
        }
        return List.of();
    }

    @Override
    public List<Symbol> search(SymbolNamespace namespace, String query) {
        if (namespace != SymbolNamespace.LOC) return List.of();
        String lower = query.toLowerCase(Locale.ROOT);
        List<Symbol> results = new ArrayList<>();
        for (Symbol s : locSymbolsByName.values()) {
            if (s.bareName().contains(lower)) {
                results.add(s);
            }
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public List<Symbol> all(SymbolNamespace namespace) {
        if (namespace == SymbolNamespace.LOC) {
            return List.copyOf(locSymbolsByName.values());
        }
        return List.of();
    }
}
