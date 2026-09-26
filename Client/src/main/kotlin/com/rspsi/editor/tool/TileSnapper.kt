package com.rspsi.editor.tool

/**
 * Deterministic tile-grid snapping shared by object and placement tools.
 *
 * This helper stays frontend- and renderer-neutral: callers provide document-space integer
 * coordinates and receive a coordinate clamped to the current document boundary.
 */
object TileSnapper {
    /**
     * Snaps to the nearest grid origin and keeps the result inside [limitExclusive].
     *
     * A grid size of one preserves an in-bounds coordinate. Coordinates beyond the document
     * are still clamped to the final tile, matching the historical Java helper.
     */
    @JvmStatic
    fun snap(
        coordinate: Int,
        gridSize: Int,
        limitExclusive: Int,
    ): Int {
        if (coordinate < 0 || limitExclusive <= 0) {
            throw IllegalArgumentException("Coordinate and limit must be positive")
        }
        if (gridSize < 1) {
            throw IllegalArgumentException("Grid size must be at least one tile")
        }

        // Keep integer arithmetic identical to the Java implementation. In particular,
        // midpoint handling intentionally rounds toward the next grid origin.
        val snapped = ((coordinate + gridSize / 2) / gridSize) * gridSize
        return minOf(snapped, limitExclusive - 1)
    }
}
