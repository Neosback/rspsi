package com.rspsi.editor.knowledge.graph;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Node in the Asset Knowledge Graph.
 */
public record GraphNode(
        String id,
        NodeType type,
        Map<String, Object> attributes
) {
    public enum NodeType {
        REGION,
        TILE,
        OVERLAY,
        OBJECT_DEFINITION,
        MODEL,
        TEXTURE,
        SEMANTIC_TAG
    }

    public GraphNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(attributes, "attributes");
        attributes = Collections.unmodifiableMap(Map.copyOf(attributes));
    }

    public static GraphNode of(String id, NodeType type) {
        return new GraphNode(id, type, Map.of());
    }

    public static GraphNode of(String id, NodeType type, Map<String, Object> attributes) {
        return new GraphNode(id, type, attributes);
    }
}
