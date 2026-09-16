package com.rspsi.cache.definition;

import java.util.List;

/** Stable, editor-facing subset of an object definition. */
public record ObjectDefinitionView(
        int id,
        String name,
        int width,
        int length,
        List<String> interactions,
        int[] modelIds
) {
    public ObjectDefinitionView {
        interactions = List.copyOf(interactions == null ? List.of() : interactions);
        modelIds = modelIds == null ? new int[0] : modelIds.clone();
    }

    @Override
    public int[] modelIds() {
        return modelIds.clone();
    }
}
