package com.rspsi.editor.model

import java.util.Optional

/**
 * World-space placement of one canonical local document window.
 *
 * [LocalTile] remains document-local while [WorldTile] represents absolute OSRS
 * coordinates. This type is the explicit conversion boundary between those spaces;
 * callers should not reinterpret matching numeric components across the two types.
 */
@JvmRecord
data class WorldWindow(
    val originX: Int,
    val originY: Int,
    val width: Int,
    val length: Int,
) {
    init {
        if (width <= 0 || length <= 0) {
            throw IllegalArgumentException("World window dimensions must be positive")
        }
    }

    fun contains(coordinate: LocalTile?): Boolean {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        return safeCoordinate.x >= 0 &&
            safeCoordinate.x < width &&
            safeCoordinate.y >= 0 &&
            safeCoordinate.y < length
    }

    fun contains(coordinate: WorldTile?): Boolean {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        return safeCoordinate.x >= originX &&
            safeCoordinate.x < originX + width &&
            safeCoordinate.y >= originY &&
            safeCoordinate.y < originY + length
    }

    fun toWorld(coordinate: LocalTile?): WorldTile {
        val safeCoordinate = requireContains(coordinate)
        return WorldTile(
            safeCoordinate.plane,
            originX + safeCoordinate.x,
            originY + safeCoordinate.y,
        )
    }

    fun tryToLocal(coordinate: WorldTile?): Optional<LocalTile> {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        if (!contains(safeCoordinate)) {
            return Optional.empty()
        }
        return Optional.of(
            LocalTile(
                safeCoordinate.plane,
                safeCoordinate.x - originX,
                safeCoordinate.y - originY,
            ),
        )
    }

    fun toLocal(coordinate: WorldTile?): LocalTile =
        tryToLocal(coordinate).orElseThrow {
            IndexOutOfBoundsException("World tile outside window: $coordinate")
        }

    fun worldX(coordinate: LocalTile?): Int {
        val safeCoordinate = requireContains(coordinate)
        return originX + safeCoordinate.x
    }

    fun worldY(coordinate: LocalTile?): Int {
        val safeCoordinate = requireContains(coordinate)
        return originY + safeCoordinate.y
    }

    /**
     * Compatibility bridge for older local-coordinate code.
     *
     * New code should use [LocalTile] explicitly. This overload intentionally remains only
     * to support command/history/selection code that has not completed its coordinate migration.
     */
    @Deprecated("Use LocalTile explicitly")
    fun contains(coordinate: TileCoordinate?): Boolean =
        coordinate != null && contains(LocalTile.from(coordinate))

    @Deprecated("Use LocalTile explicitly")
    fun worldX(coordinate: TileCoordinate?): Int =
        worldX(LocalTile.from(coordinate))

    @Deprecated("Use LocalTile explicitly")
    fun worldY(coordinate: TileCoordinate?): Int =
        worldY(LocalTile.from(coordinate))

    private fun requireContains(coordinate: LocalTile?): LocalTile {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        if (!contains(safeCoordinate)) {
            throw IndexOutOfBoundsException("Tile outside world window: $safeCoordinate")
        }
        return safeCoordinate
    }

    override fun toString(): String =
        "WorldWindow[originX=$originX, originY=$originY, width=$width, length=$length]"
}
