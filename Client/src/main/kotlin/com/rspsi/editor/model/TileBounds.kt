package com.rspsi.editor.model

/**
 * Inclusive rectangular tile bounds used by fragments and selection tools.
 *
 * Width and height are therefore `max - min + 1`; this is intentionally not a half-open
 * rectangle. Keeping that convention explicit avoids off-by-one errors at selection edges.
 */
@JvmRecord
data class TileBounds(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
) {
    init {
        if (minX < 0 || minY < 0 || maxX < minX || maxY < minY) {
            throw IllegalArgumentException("Invalid inclusive tile bounds")
        }
    }

    fun width(): Int = maxX - minX + 1

    fun height(): Int = maxY - minY + 1

    fun contains(
        x: Int,
        y: Int,
    ): Boolean = x in minX..maxX && y in minY..maxY

    override fun toString(): String =
        "TileBounds[minX=$minX, minY=$minY, maxX=$maxX, maxY=$maxY]"
}
