package com.rspsi.editor.integration.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SemanticContentEdge(
        String id,
        String from,
        String to,
        SemanticRelationKind relation,
        float confidence,
        Map<String, String> attributes,
        List<SemanticEvidence> evidence) {

    public SemanticContentEdge {
        id = requireText(id, "id");
        from = requireText(from, "from");
        to = requireText(to, "to");
        relation = Objects.requireNonNull(relation, "relation");
        if (Float.isNaN(confidence) || confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
