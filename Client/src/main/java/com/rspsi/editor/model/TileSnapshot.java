package com.rspsi.editor.model;

import java.util.List;

/**
 * Complete editable state for one tile, independent of rendering objects.
 *
 * <p>This remains a Java record compatibility shell because its canonical constructor historically
 * accepts nullable object/provenance inputs and normalizes them before storing the record
 * components. Kotlin JVM records cannot currently preserve that compact-constructor behavior
 * exactly. Behavioral semantics are centralized in {@link TileSnapshotSemantics}.</p>
 */
public record TileSnapshot(
        int southWestHeight,
        int southEastHeight,
        int northEastHeight,
        int northWestHeight,
        int underlayId,
        int overlayId,
        int overlayShape,
        int overlayRotation,
        int flags,
        List<WorldObject> objects,
        TerrainHeightSource heightSource
) {
    public TileSnapshot {
        TileSnapshotSemantics.validateOverlay(overlayShape, overlayRotation);
        objects = TileSnapshotSemantics.normalizeObjects(objects);
        heightSource = TileSnapshotSemantics.normalizeHeightSource(heightSource);
    }

    /** Source-compatible constructor for callers that do not yet carry provenance explicitly. */
    public TileSnapshot(
            int southWestHeight,
            int southEastHeight,
            int northEastHeight,
            int northWestHeight,
            int underlayId,
            int overlayId,
            int overlayShape,
            int overlayRotation,
            int flags,
            List<WorldObject> objects) {
        this(southWestHeight, southEastHeight, northEastHeight, northWestHeight,
                underlayId, overlayId, overlayShape, overlayRotation, flags, objects,
                TerrainHeightSource.unknown());
    }

    public TileSnapshot withHeightSource(TerrainHeightSource source) {
        return new TileSnapshot(southWestHeight, southEastHeight, northEastHeight, northWestHeight,
                underlayId, overlayId, overlayShape, overlayRotation, flags, objects, source);
    }

    /**
     * Provenance is metadata, not an authored-value channel. Equality remains compatible with
     * historical snapshot comparisons while the source stays available for save/replay decisions.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof TileSnapshot that
                && TileSnapshotSemantics.authoredEquals(this, that);
    }

    @Override
    public int hashCode() {
        return TileSnapshotSemantics.authoredHash(this);
    }
}
