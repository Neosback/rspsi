package com.rspsi.cache.definition;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

/** Stable, editor-facing subset of an object definition. */
public record ObjectDefinitionView(
        int id,
        String name,
        int width,
        int length,
        List<String> interactions,
        int[] modelIds,
        int mapSceneId
) {
    /** Source-compatible constructor for definitions without map-scene data. */
    public ObjectDefinitionView(int id, String name, int width, int length,
                                List<String> interactions, int[] modelIds) {
        this(id, name, width, length, interactions, modelIds, -1);
    }

    public ObjectDefinitionView {
        interactions = List.copyOf(interactions == null ? List.of() : interactions);
        modelIds = modelIds == null ? new int[0] : modelIds.clone();
    }

    @Override
    public int[] modelIds() {
        return modelIds.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ObjectDefinitionView value)) return false;
        return id == value.id
                && width == value.width
                && length == value.length
                && mapSceneId == value.mapSceneId
                && Objects.equals(name, value.name)
                && Objects.equals(interactions, value.interactions)
                && Arrays.equals(modelIds, value.modelIds);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, width, length, interactions, mapSceneId);
        return 31 * result + Arrays.hashCode(modelIds);
    }
}
