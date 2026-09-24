package com.rspsi.editor.integration.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One source-derived semantic fact with exact provenance.
 *
 * <p>Facts intentionally preserve adapter attributes rather than baking OpenRune concepts into
 * the neutral API. A later semantic graph can consume these facts without depending on Kotlin PSI.</p>
 */
public record SemanticSourceFact(
        SemanticFactKind kind,
        String name,
        String owner,
        List<String> arguments,
        Map<String, String> attributes,
        SourceSpan source,
        float confidence) {

    public SemanticSourceFact {
        kind = Objects.requireNonNull(kind, "kind");
        name = requireText(name, "name");
        owner = owner == null ? "" : owner.trim();
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        source = Objects.requireNonNull(source, "source");
        if (Float.isNaN(confidence) || confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
