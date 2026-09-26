package com.rspsi.editor.model

/**
 * Canonical compatibility semantics for [TileSnapshot].
 *
 * [TileSnapshot] remains a minimal Java record shell for now because its compact constructor
 * normalizes nullable record components before they are stored, which Kotlin JVM records cannot
 * represent without changing observable constructor behavior. All non-structural behavior lives
 * here so there is still one semantic owner while the record ABI remains intact.
 */
object TileSnapshotSemantics {
    @JvmStatic
    fun validateOverlay(
        overlayShape: Int,
        overlayRotation: Int,
    ) {
        if (overlayShape !in 0..11 || overlayRotation !in 0..3) {
            throw IllegalArgumentException(
                "Overlay shape must be 0..11 and rotation 0..3",
            )
        }
    }

    @JvmStatic
    fun normalizeObjects(objects: List<WorldObject>?): List<WorldObject> =
        java.util.List.copyOf(objects ?: java.util.List.of())

    @JvmStatic
    fun normalizeHeightSource(source: TerrainHeightSource?): TerrainHeightSource =
        source ?: TerrainHeightSource.unknown()

    /**
     * Authored-value equality deliberately excludes terrain provenance.
     *
     * Provenance participates in save/replay decisions but historically has not changed whether
     * two tile snapshots represent the same authored tile values.
     */
    @JvmStatic
    fun authoredEquals(
        left: TileSnapshot,
        right: TileSnapshot,
    ): Boolean =
        left.southWestHeight() == right.southWestHeight() &&
            left.southEastHeight() == right.southEastHeight() &&
            left.northEastHeight() == right.northEastHeight() &&
            left.northWestHeight() == right.northWestHeight() &&
            left.underlayId() == right.underlayId() &&
            left.overlayId() == right.overlayId() &&
            left.overlayShape() == right.overlayShape() &&
            left.overlayRotation() == right.overlayRotation() &&
            left.flags() == right.flags() &&
            left.objects() == right.objects()

    @JvmStatic
    fun authoredHash(snapshot: TileSnapshot): Int =
        java.util.Objects.hash(
            snapshot.southWestHeight(),
            snapshot.southEastHeight(),
            snapshot.northEastHeight(),
            snapshot.northWestHeight(),
            snapshot.underlayId(),
            snapshot.overlayId(),
            snapshot.overlayShape(),
            snapshot.overlayRotation(),
            snapshot.flags(),
            snapshot.objects(),
        )
}
