package com.rspsi.editor.tool

import com.rspsi.editor.model.TileSnapshot

/**
 * Backend- and frontend-neutral bilinear terrain-height sampling for one tile.
 *
 * The corner convention follows TileSnapshot directly: south-west/south-east are
 * interpolated first, then north-west/north-east, then the two rows are blended by Y.
 */
object TerrainHeightSampler {
    /** Samples [tile] at normalized local coordinates in the inclusive 0..1 range. */
    @JvmStatic
    fun sample(
        tile: TileSnapshot?,
        localX: Double,
        localY: Double,
    ): Int {
        val safeTile = tile ?: throw NullPointerException("tile")
        if (localX < 0.0 || localX > 1.0 || localY < 0.0 || localY > 1.0) {
            throw IllegalArgumentException("Local tile coordinates must be between zero and one")
        }

        val south =
            safeTile.southWestHeight() +
                (safeTile.southEastHeight() - safeTile.southWestHeight()) * localX
        val north =
            safeTile.northWestHeight() +
                (safeTile.northEastHeight() - safeTile.northWestHeight()) * localX

        // Use java.lang.Math.round deliberately. This is a behavior-preserving migration,
        // not an opportunity to substitute Kotlin rounding semantics in map-height math.
        return java.lang.Math.round(south + (north - south) * localY).toInt()
    }
}
