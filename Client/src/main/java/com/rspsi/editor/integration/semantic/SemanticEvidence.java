package com.rspsi.editor.integration.semantic;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provenance for a graph node or edge.
 *
 * <p>Some evidence has an exact PSI source span; generated mappings and older declarative
 * reference indexes may only have a file/location and line. Missing coordinates are represented
 * as zero rather than fabricated.</p>
 */
public record SemanticEvidence(
        SemanticEvidenceKind kind,
        String provider,
        Optional<SourceSpan> sourceSpan,
        String location,
        int line,
        float confidence,
        Map<String, String> attributes) {

    public SemanticEvidence {
        kind = Objects.requireNonNull(kind, "kind");
        provider = provider == null ? "" : provider.trim();
        sourceSpan = sourceSpan == null ? Optional.empty() : sourceSpan;
        location = location == null ? "" : location.trim();
        if (line < 0) throw new IllegalArgumentException("line cannot be negative");
        if (Float.isNaN(confidence) || confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    public static SemanticEvidence source(SemanticSourceFact fact) {
        Objects.requireNonNull(fact, "fact");
        return new SemanticEvidence(
                SemanticEvidenceKind.SOURCE_PSI,
                "semantic-source-index",
                Optional.of(fact.source()),
                fact.source().file().toString(),
                fact.source().startLine(),
                fact.confidence(),
                fact.attributes());
    }
}
