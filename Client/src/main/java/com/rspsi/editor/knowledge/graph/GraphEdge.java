package com.rspsi.editor.knowledge.graph;

import java.util.Objects;

/**
 * Directed typed edge in the Asset Knowledge Graph.
 */
public record GraphEdge(
        String sourceId,
        String targetId,
        EdgeType type,
        float weight
) {
    public enum EdgeType {
        USES,
        CONTAINS,
        ADJACENT_TO,
        TEXTURED_BY,
        SIMILAR_TO,
        TAGGED_AS
    }

    public GraphEdge {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(type, "type");
    }

    public static GraphEdge of(String sourceId, String targetId, EdgeType type) {
        return new GraphEdge(sourceId, targetId, type, 1.0f);
    }

    public static GraphEdge of(String sourceId, String targetId, EdgeType type, float weight) {
        return new GraphEdge(sourceId, targetId, type, weight);
    }
}
