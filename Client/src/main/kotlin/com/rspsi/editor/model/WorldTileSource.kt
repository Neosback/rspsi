package com.rspsi.editor.model

/**
 * A decoded tile plus the cache-height provenance that produced it.
 *
 * Kept as a real JVM record so Java callers retain component accessors such as
 * snapshot() and heightSource() while Kotlin gains native data-class ergonomics.
 */
@JvmRecord
data class WorldTileSource(
    val snapshot: TileSnapshot,
    val heightSource: TerrainHeightSource,
) {
    override fun toString(): String =
        "WorldTileSource[snapshot=$snapshot, heightSource=$heightSource]"
}
