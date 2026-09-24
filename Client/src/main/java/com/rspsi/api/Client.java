package com.rspsi.api;

/**
 * The simulated player/client state a map editor can reason about, shaped
 * after {@code net.runelite.api.Client}.
 *
 * <p>Player variables: a varbit is a bit range of a varp; changing either one
 * re-resolves every loc whose appearance depends on it (multilocs), exactly
 * as the client does. This is simulation state, not authored map data, so it
 * is not recorded in undo history.</p>
 *
 * <p>Cache lookups: object definitions, models and map elements, read from the
 * loaded cache (including the var state for multiloc impostors).</p>
 */
public interface Client {
    /** Value of a varbit ({@code net.runelite.api.Client#getVarbitValue}). */
    int getVarbitValue(int varbitId);

    /** Value of a varp ({@code net.runelite.api.Client#getVarpValue}). */
    int getVarpValue(int varpId);

    /** Sets a varbit by rewriting its bits in the parent varp ({@code Client#setVarbit}). */
    void setVarbit(int varbitId, int value);

    /**
     * Studio extra: sets a whole varp. RuneLite plugins write
     * {@code client.getVarps()[id]}; Studio's state is not an exposed array.
     */
    void setVarpValue(int varpId, int value);

    /** Layout of a varbit, or {@code null} when the cache has no such varbit. */
    VarbitComposition getVarbit(int varbitId);

    /** Studio extra: every var back to 0, as on a fresh account. */
    void resetVars();

    /** Object definition, or {@code null} ({@code Client#getObjectDefinition}). */
    ObjectComposition getObjectDefinition(int objectId);

    /**
     * Unlit model from the cache, or {@code null} ({@code Client#loadModelData}).
     * Each call returns a fresh copy that is safe to transform.
     */
    ModelData loadModelData(int id);

    /** {@code loadModelData(id).light()}, or {@code null} ({@code Client#loadModel}). */
    default Model loadModel(int id) {
        ModelData data = loadModelData(id);
        return data == null ? null : data.light();
    }

    /** Loads, recolours pairwise and lights a model ({@code Client#loadModel(int, short[], short[])}). */
    default Model loadModel(int id, short[] colorToFind, short[] colorToReplace) {
        ModelData data = loadModelData(id);
        if (data == null) return null;
        if (colorToFind != null && colorToReplace != null) {
            for (int i = 0; i < Math.min(colorToFind.length, colorToReplace.length); i++) {
                data.recolor(colorToFind[i], colorToReplace[i]);
            }
        }
        return data.light();
    }

    /** Map element (map function icon), or {@code null} ({@code Client#getMapElementConfig}). */
    com.rspsi.api.worldmap.MapElementConfig getMapElementConfig(int id);

    /**
     * The map navigator ({@code Client#getWorldMap}); {@code null} when no
     * Studio view is attached (headless use).
     */
    com.rspsi.api.worldmap.WorldMap getWorldMap();
}
