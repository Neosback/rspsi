package com.rspsi.editor.symbols;

import java.util.Objects;

/**
 * Diagnostics record indicating when two or more symbol providers map the same
 * symbolic identifier to conflicting numeric IDs.
 */
public record SymbolConflict(
        SymbolNamespace namespace,
        String name,
        int idA,
        String sourceA,
        int idB,
        String sourceB
) {
    public SymbolConflict {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceA, "sourceA");
        Objects.requireNonNull(sourceB, "sourceB");
    }

    public String message() {
        return "Symbol conflict in " + namespace + " '" + name + "': "
                + sourceA + " maps to ID " + idA + ", but "
                + sourceB + " maps to ID " + idB;
    }
}
