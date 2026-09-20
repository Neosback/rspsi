package com.rspsi.editor.model;

import java.util.List;
import java.util.Objects;

/** Complete editable state for one tile, independent of rendering objects. */
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
        if (overlayShape < 0 || overlayShape > 11 || overlayRotation < 0 || overlayRotation > 3) {
            throw new IllegalArgumentException("Overlay shape must be 0..11 and rotation 0..3");
        }
        objects = List.copyOf(objects == null ? List.of() : objects);
        heightSource = heightSource == null ? TerrainHeightSource.unknown() : heightSource;
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
     * Provenance is metadata, not an authored-value channel. Equality remains
     * compatible with historical snapshot comparisons while the source is
     * still available for save/replay decisions.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TileSnapshot that)) return false;
        return southWestHeight == that.southWestHeight
                && southEastHeight == that.southEastHeight
                && northEastHeight == that.northEastHeight
                && northWestHeight == that.northWestHeight
                && underlayId == that.underlayId
                && overlayId == that.overlayId
                && overlayShape == that.overlayShape
                && overlayRotation == that.overlayRotation
                && flags == that.flags
                && objects.equals(that.objects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(southWestHeight, southEastHeight, northEastHeight, northWestHeight,
                underlayId, overlayId, overlayShape, overlayRotation, flags, objects);
    }
}
