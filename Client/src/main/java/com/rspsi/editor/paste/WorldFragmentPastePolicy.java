package com.rspsi.editor.paste;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable policy for converting a {@link WorldFragment} into a world-space
 * paste proposal.
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
        terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
        objectMode = Objects.requireNonNull(objectMode, "objectMode");
        heightMode = Objects.requireNonNull(heightMode, "heightMode");
        conflictMode = Objects.requireNonNull(conflictMode, "conflictMode");
        heightAnchor = Objects.requireNonNull(heightAnchor, "heightAnchor");

        LinkedHashSet<Integer> types = new LinkedHashSet<>(
                Objects.requireNonNull(objectTypes, "objectTypes"));
        for (int type : types) {
            if (type < 0 || type > 22) {
                throw new IllegalArgumentException(
                        "OSRS location type filter must be between 0 and 22: " + type);
            }
        }
        objectTypes = Set.copyOf(types);
    }

    public static WorldFragmentPastePolicy replaceAll() {
        return new WorldFragmentPastePolicy(
                TerrainMode.REPLACE,
                ObjectMode.REPLACE,
                HeightMode.SOURCE_ABSOLUTE,
                ConflictMode.REPORT,
                Optional.empty(),
                Set.of());
    }

    public static WorldFragmentPastePolicy terrainOnly() {
        return new WorldFragmentPastePolicy(
                TerrainMode.REPLACE,
                ObjectMode.PRESERVE,
                HeightMode.SOURCE_ABSOLUTE,
                ConflictMode.REPORT,
                Optional.empty(),
                Set.of());
    }

    public static WorldFragmentPastePolicy objectsOnly() {
        return new WorldFragmentPastePolicy(
                TerrainMode.PRESERVE,
                ObjectMode.REPLACE,
                HeightMode.PRESERVE_DESTINATION,
                ConflictMode.REPORT,
                Optional.empty(),
                Set.of());
    }

    public static WorldFragmentPastePolicy mergeObjects() {
        return new WorldFragmentPastePolicy(
                TerrainMode.PRESERVE,
                ObjectMode.MERGE,
                HeightMode.PRESERVE_DESTINATION,
                ConflictMode.REPORT,
                Optional.empty(),
                Set.of());
    }

    public WorldFragmentPastePolicy withHeightMode(HeightMode mode) {
        return new WorldFragmentPastePolicy(
                terrainMode, objectMode, mode, conflictMode, heightAnchor, objectTypes);
    }

    public WorldFragmentPastePolicy withConflictMode(ConflictMode mode) {
        return new WorldFragmentPastePolicy(
                terrainMode, objectMode, heightMode, mode, heightAnchor, objectTypes);
    }

    public WorldFragmentPastePolicy withHeightAnchor(HeightAnchor anchor) {
        return new WorldFragmentPastePolicy(
                terrainMode, objectMode, heightMode, conflictMode,
                Optional.of(Objects.requireNonNull(anchor, "anchor")), objectTypes);
    }

    /**
     * Restricts object replace/merge work to native OSRS location types.
     * An empty set means all location types.
     */
    public WorldFragmentPastePolicy withObjectTypes(Set<Integer> types) {
        return new WorldFragmentPastePolicy(
                terrainMode, objectMode, heightMode, conflictMode, heightAnchor, types);
    }

    public boolean includesObjectType(int type) {
        return objectTypes.isEmpty() || objectTypes.contains(type);
    }

    public void validateFor(WorldFragment fragment) {
        Objects.requireNonNull(fragment, "fragment");
        if (heightAnchor.isPresent()) {
            HeightAnchor anchor = heightAnchor.orElseThrow();
            TileBounds bounds = fragment.bounds();
            if (!bounds.contains(anchor.x(), anchor.y())) {
                throw new IllegalArgumentException("Paste height anchor must be inside fragment bounds");
            }
        }
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
            if (plane < 0 || x < 0 || y < 0) {
                throw new IllegalArgumentException("Paste height anchor cannot be negative");
            }
        }
    }
}
