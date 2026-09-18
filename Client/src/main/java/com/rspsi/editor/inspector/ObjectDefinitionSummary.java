package com.rspsi.editor.inspector;

import java.util.List;

/** Neutral definition details suitable for any frontend's object inspector. */
public record ObjectDefinitionSummary(
        int id,
        String name,
        int width,
        int length,
        List<Integer> modelIds,
        List<Integer> modelTypes,
        List<String> actions
) {
    public ObjectDefinitionSummary {
        if (id < 0 || width <= 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid object definition summary");
        }
        name = name == null ? "" : name;
        modelIds = List.copyOf(modelIds == null ? List.of() : modelIds);
        modelTypes = List.copyOf(modelTypes == null ? List.of() : modelTypes);
        if (!modelTypes.isEmpty() && modelTypes.size() != modelIds.size()) {
            throw new IllegalArgumentException(
                    "Object model types and model IDs must be paired: " + modelIds.size()
                            + " ids vs " + modelTypes.size() + " types");
        }
        actions = List.copyOf(actions == null ? List.of() : actions);
    }

    /** Source-compatible constructor for summaries without model-type pairing. */
    public ObjectDefinitionSummary(int id, String name, int width, int length,
                                   List<Integer> modelIds, List<String> actions) {
        this(id, name, width, length, modelIds, List.of(), actions);
    }
}
