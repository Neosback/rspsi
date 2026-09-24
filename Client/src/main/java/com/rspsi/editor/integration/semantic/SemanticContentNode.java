package com.rspsi.editor.integration.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SemanticContentNode(
        String id,
        SemanticContentNodeKind kind,
        String key,
        String label,
        Map<String, String> attributes,
        List<SemanticEvidence> evidence) {

    public SemanticContentNode {
        id = requireText(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        key = requireText(key, "key");
        label = label == null || label.isBlank() ? key : label.trim();
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
