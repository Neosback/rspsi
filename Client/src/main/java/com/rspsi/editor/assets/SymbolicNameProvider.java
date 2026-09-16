package com.rspsi.editor.assets;

import java.util.Optional;

/**
 * Supplies stable, backend-owned names such as RSCM or GameVal symbols.
 *
 * <p>The provider is deliberately smaller than any particular naming
 * library. Cache adapters may translate their naming system at this boundary
 * without exposing it to tools, documents, or frontends.</p>
 */
@FunctionalInterface
public interface SymbolicNameProvider {
    Optional<String> name(String type, int id);

    /** A provider for caches that do not have symbolic names loaded yet. */
    static SymbolicNameProvider none() {
        return (type, id) -> Optional.empty();
    }
}
