package com.rspsi.editor.inspector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Frontend-neutral server/content semantics for one selected map object.
 *
 * <p>This is a projection of the semantic graph, not an independent source of truth.</p>
 */
public record ObjectContentFacet(
        int objectId,
        String objectSymbol,
        Optional<String> inheritSymbol,
        Optional<String> contentGroup,
        List<String> handlers,
        Map<String, String> params,
        Optional<String> authoredSource,
        boolean authoredSourceWritable,
        boolean editLensAvailable) {

    public ObjectContentFacet {
        if (objectId < 0) throw new IllegalArgumentException("objectId cannot be negative");
        objectSymbol = Objects.requireNonNull(objectSymbol, "objectSymbol");
        inheritSymbol = inheritSymbol == null ? Optional.empty() : inheritSymbol;
        contentGroup = contentGroup == null ? Optional.empty() : contentGroup;
        handlers = List.copyOf(handlers == null ? List.of() : handlers);
        params = Map.copyOf(params == null ? Map.of() : params);
        authoredSource = authoredSource == null ? Optional.empty() : authoredSource;
    }
}
