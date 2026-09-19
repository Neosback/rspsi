package com.rspsi.editor.integration.reference;

import com.rspsi.editor.symbols.SymbolNamespace;

import java.util.Objects;

/**
 * Concrete reference link between a game asset/symbol and a server script line.
 */
public record ContentReference(
        String targetSymbol,
        SymbolNamespace namespace,
        int id,
        String module,
        String relativePath,
        int lineNumber,
        String snippet,
        String contextType
) {
    public ContentReference {
        Objects.requireNonNull(targetSymbol, "targetSymbol");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(relativePath, "relativePath");
        if (snippet == null) snippet = "";
        if (contextType == null) contextType = "Script";
    }

    /** Formatted location string: "module/path:line". */
    public String displayLocation() {
        return module + "/" + relativePath + ":" + lineNumber;
    }
}
