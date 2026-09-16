package com.rspsi.editor.inspector;

import java.util.List;

/** Neutral definition details suitable for any frontend's object inspector. */
public record ObjectDefinitionSummary(
        int id,
        String name,
        int width,
        int length,
        List<Integer> modelIds,
        List<String> actions
) {
    public ObjectDefinitionSummary {
        if (id < 0 || width <= 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid object definition summary");
        }
        name = name == null ? "" : name;
        modelIds = List.copyOf(modelIds == null ? List.of() : modelIds);
        actions = List.copyOf(actions == null ? List.of() : actions);
    }
}
