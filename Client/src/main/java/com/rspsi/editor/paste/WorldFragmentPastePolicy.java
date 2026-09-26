package com.rspsi.editor.paste;

import com.rspsi.editor.model.WorldFragment;

import java.util.Optional;
import java.util.Set;

/**
 * Immutable policy for converting a {@link WorldFragment} into a world-space paste proposal.
 *
 * <p>This remains a minimal Java record shell because its compact constructor historically
 * validates and defensively replaces record components before storage. Behavioral semantics are
 * centralized in {@link WorldFragmentPastePolicySemantics}.</p>
 */
public record WorldFragmentPastePolicy(
        TerrainMode terrainMode,
        ObjectMode objectMode,
        HeightMode heightMode,
        ConflictMode conflictMode,
        Optional<HeightAnchor> heightAnchor,
        Set<Integer> objectTypes
) {
    public WorldFragmentPastePolicy {
        terrainMode = WorldFragmentPastePolicySemantics.requireTerrainMode(terrainMode);
        objectMode = WorldFragmentPastePolicySemantics.requireObjectMode(objectMode);
        heightMode = WorldFragmentPastePolicySemantics.requireHeightMode(heightMode);
        conflictMode = WorldFragmentPastePolicySemantics.requireConflictMode(conflictMode);
        heightAnchor = WorldFragmentPastePolicySemantics.requireHeightAnchor(heightAnchor);
        objectTypes = WorldFragmentPastePolicySemantics.copyObjectTypes(objectTypes);
    }

    public static WorldFragmentPastePolicy replaceAll() {
        return WorldFragmentPastePolicySemantics.replaceAll();
    }

    public static WorldFragmentPastePolicy terrainOnly() {
        return WorldFragmentPastePolicySemantics.terrainOnly();
    }

    public static WorldFragmentPastePolicy objectsOnly() {
        return WorldFragmentPastePolicySemantics.objectsOnly();
    }

    public static WorldFragmentPastePolicy mergeObjects() {
        return WorldFragmentPastePolicySemantics.mergeObjects();
    }

    public WorldFragmentPastePolicy withHeightMode(HeightMode mode) {
        return WorldFragmentPastePolicySemantics.withHeightMode(this, mode);
    }

    public WorldFragmentPastePolicy withConflictMode(ConflictMode mode) {
        return WorldFragmentPastePolicySemantics.withConflictMode(this, mode);
    }

    public WorldFragmentPastePolicy withHeightAnchor(HeightAnchor anchor) {
        return WorldFragmentPastePolicySemantics.withHeightAnchor(this, anchor);
    }

    /** Restricts object replace/merge work to native OSRS location types. */
    public WorldFragmentPastePolicy withObjectTypes(Set<Integer> types) {
        return WorldFragmentPastePolicySemantics.withObjectTypes(this, types);
    }

    /** An empty object-type filter means all native OSRS location types. */
    public boolean includesObjectType(int type) {
        return WorldFragmentPastePolicySemantics.includesObjectType(this, type);
    }

    /** Validates source-dependent policy constraints before planning begins. */
    public void validateFor(WorldFragment fragment) {
        WorldFragmentPastePolicySemantics.validateFor(this, fragment);
    }

    public enum TerrainMode {
        REPLACE,
        PRESERVE
    }

    public enum ObjectMode {
        REPLACE,
        MERGE,
        PRESERVE
    }

    public enum HeightMode {
        /** Copy source corner heights as authored values at the destination. */
        SOURCE_ABSOLUTE,
        /** Keep all destination corner heights unchanged. */
        PRESERVE_DESTINATION,
        /**
         * Add one global delta to every copied source corner so the selected
         * source anchor's south-west vertex meets the destination anchor.
         */
        OFFSET_FROM_ANCHOR
    }

    public enum ConflictMode {
        /** Report conflicts and block commit until the user resolves them. */
        REPORT,
        /** Omit conflicting destination tiles and permit a partial commit. */
        SKIP
    }

    /** Source-fragment tile whose south-west height vertex is used for alignment. */
    public record HeightAnchor(int plane, int x, int y) {
        public HeightAnchor {
            WorldFragmentPastePolicySemantics.validateHeightAnchor(plane, x, y);
        }
    }
}
