package com.rspsi.editor.knowledge;

import java.util.List;
import java.util.Objects;

/**
 * An immutable unit of world knowledge with complete epistemic provenance, confidence, and evidence.
 *
 * @param <T> the type of value represented by the fact (e.g. {@link SemanticTag}, Boolean, Integer, or geometry)
 * @param value the factual payload
 * @param source the origin layer that produced this fact
 * @param confidence epistemic confidence between 0.0 (uncertain) and 1.0 (exact/certain)
 * @param evidence list of contributing signals justifying this fact
 */
public record KnowledgeFact<T>(
        T value,
        KnowledgeSource source,
        float confidence,
        List<Evidence> evidence
) {
    public KnowledgeFact {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(evidence, "evidence");
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0, was " + confidence);
        }
        evidence = List.copyOf(evidence);
    }

    /**
     * Creates an exact fact with 1.0 confidence and no heuristic evidence (e.g. from Cache or Scene Resolver).
     */
    public static <T> KnowledgeFact<T> exact(T value, KnowledgeSource source) {
        return new KnowledgeFact<>(value, source, 1.0f, List.of());
    }

    /**
     * Creates an inferred fact with a probabilistic confidence score and supporting evidence.
     */
    public static <T> KnowledgeFact<T> inferred(T value, float confidence, Evidence... evidence) {
        return new KnowledgeFact<>(value, KnowledgeSource.INFERRED, confidence, List.of(evidence));
    }

    /**
     * Creates an inferred fact with a probabilistic confidence score and supporting evidence list.
     */
    public static <T> KnowledgeFact<T> inferred(T value, float confidence, List<Evidence> evidence) {
        return new KnowledgeFact<>(value, KnowledgeSource.INFERRED, confidence, evidence);
    }

    /**
     * Creates a user-specified metadata fact with 1.0 confidence.
     */
    public static <T> KnowledgeFact<T> user(T value) {
        return new KnowledgeFact<>(value, KnowledgeSource.USER, 1.0f, List.of());
    }
}
