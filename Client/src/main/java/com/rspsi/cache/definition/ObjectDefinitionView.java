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
        int[] modelTypes,
        int mapSceneId,
        boolean interactive
) {
    /** Source-compatible constructor for definitions without map-scene data. */
    public ObjectDefinitionView(int id, String name, int width, int length,
                                List<String> interactions, int[] modelIds) {
        this(id, name, width, length, interactions, modelIds, null, -1,
                interactions != null && !interactions.isEmpty());
    }

    /** Source-compatible constructor for definitions with map-scene data. */
    public ObjectDefinitionView(int id, String name, int width, int length,
                                List<String> interactions, int[] modelIds, int mapSceneId) {
        this(id, name, width, length, interactions, modelIds, null, mapSceneId,
                interactions != null && !interactions.isEmpty());
    }

    /** Source-compatible constructor for explicit interactivity without model-type pairing. */
    public ObjectDefinitionView(int id, String name, int width, int length,
                                List<String> interactions, int[] modelIds, int mapSceneId,
                                boolean interactive) {
        this(id, name, width, length, interactions, modelIds, null, mapSceneId, interactive);
    }

    public ObjectDefinitionView {
        interactions = List.copyOf(interactions == null ? List.of() : interactions);
        modelIds = modelIds == null ? new int[0] : modelIds.clone();
        modelTypes = modelTypes == null ? new int[0] : modelTypes.clone();
        if (modelTypes.length != 0 && modelTypes.length != modelIds.length) {
            throw new IllegalArgumentException(
                    "Object model types and model IDs must be paired: " + modelIds.length
                            + " ids vs " + modelTypes.length + " types");
        }
    }

    @Override
    public int[] modelIds() {
        return modelIds.clone();
    }

    /**
     * OSRS location-type/model pairing from opcodes 1/2 (and 6/7 families).
     * Empty when the definition declares models without type pairing, in which
     * case every model is a game-object (type 10) model.
     */
    @Override
    public int[] modelTypes() {
        return modelTypes.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ObjectDefinitionView value)) return false;
        return id == value.id
                && width == value.width
                && length == value.length
                && mapSceneId == value.mapSceneId
                && interactive == value.interactive
                && Objects.equals(name, value.name)
                && Objects.equals(interactions, value.interactions)
                && Arrays.equals(modelIds, value.modelIds)
                && Arrays.equals(modelTypes, value.modelTypes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, width, length, interactions, mapSceneId, interactive);
        return 31 * result + Arrays.hashCode(modelIds);
    }
}
