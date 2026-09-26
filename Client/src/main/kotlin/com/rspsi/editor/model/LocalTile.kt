package com.rspsi.editor.model

/**
 * Tile coordinate local to one [WorldDocument].
 *
 * This type must never represent absolute OSRS world coordinates. Keeping LocalTile and
 * WorldTile as distinct JVM-record types prevents document-local and world-space coordinates
 * from being mixed accidentally while preserving the Java record ABI during migration.
 */
@JvmRecord
data class LocalTile(
    val plane: Int,
    val x: Int,
    val y: Int,
) {
    init {
        if (plane < 0 || x < 0 || y < 0) {
            throw IllegalArgumentException("Local tile coordinates cannot be negative")
        }
    }

    /** Compatibility bridge while older command/selection contracts still use TileCoordinate. */
    fun coordinate(): TileCoordinate = TileCoordinate(plane, x, y)

    override fun toString(): String =
        "LocalTile[plane=$plane, x=$x, y=$y]"

    companion object {
        @JvmStatic
        fun from(coordinate: TileCoordinate?): LocalTile {
            val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
            return LocalTile(
                safeCoordinate.plane,
                safeCoordinate.x,
                safeCoordinate.y,
            )
        }
    }
}
