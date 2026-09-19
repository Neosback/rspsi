package com.rspsi.editor.symbols;

import java.util.Objects;

/**
 * Concrete symbolic mapping associating a human-readable identifier with a numeric cache/content ID.
 *
 * <p>Preserves epistemic provenance (source provider name, defining source file path, and confidence).</p>
 */
public record Symbol(
        SymbolNamespace namespace,
        String name,
        int id,
        String source,
        String filePath,
        float confidence
) {
    public Symbol {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        if (filePath == null) filePath = "";
    }

    public static Symbol of(SymbolNamespace namespace, String name, int id, String source) {
        return new Symbol(namespace, name, id, source, "", 1.0f);
    }

    public static Symbol of(SymbolNamespace namespace, String name, int id, String source, String filePath) {
        return new Symbol(namespace, name, id, source, filePath, 1.0f);
    }

    /** Returns the canonical qualified representation, e.g. "loc.bank_booth". */
    public String qualifiedName() {
        return namespace.qualify(name);
    }

    /** Returns the bare unqualified name, e.g. "bank_booth". */
    public String bareName() {
        return namespace.unqualify(name);
    }
}
