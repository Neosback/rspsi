package com.rspsi.editor.model;

import java.util.List;

/**
 * Portable copy/paste payload for terrain and locations.
 *
 * <p>Coordinates remain world coordinates in the captured fragment; paste operations translate
 * them relative to {@link #bounds()}.</p>
 *
 * <p>This remains a Java record compatibility shell because its compact constructor historically
 * accepts nullable terrain/object lists and normalizes them before the record components are
 * stored. Behavioral semantics are centralized in {@link WorldFragmentSemantics}.</p>
 */
public record WorldFragment(
        TileBounds bounds,
        List<TerrainTilePatch> terrain,
        List<WorldObject> objects
) {
    public WorldFragment {
        bounds = WorldFragmentSemantics.requireBounds(bounds);
        terrain = WorldFragmentSemantics.normalizeTerrain(terrain);
        objects = WorldFragmentSemantics.normalizeObjects(objects);
        WorldFragmentSemantics.validateContents(bounds, terrain, objects);
    }

    /** Captures every plane in the selected rectangle from a canonical document. */
    public static WorldFragment capture(WorldDocument document, TileBounds bounds) {
        return WorldFragmentSemantics.capture(document, bounds);
    }
}
