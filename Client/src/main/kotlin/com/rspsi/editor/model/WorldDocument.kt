@file:Suppress("DEPRECATION")

package com.rspsi.editor.model

import java.util.ArrayList
import java.util.Optional

/**
 * Canonical mutable world/document state for editor operations.
 *
 * This type deliberately contains data only. Cache formats, renderers, reactive editor state,
 * and UI frontends adapt to it rather than becoming part of the authored document model.
 */
open class WorldDocument {
    private val width: Int
    private val length: Int
    private val planes: Int
    private val tiles: Array<Array<Array<Tile>>>

    constructor(
        width: Int,
        length: Int,
    ) : this(width, length, DEFAULT_PLANES)

    constructor(
        width: Int,
        length: Int,
        planes: Int,
    ) {
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw IllegalArgumentException("World dimensions must be positive")
        }
        this.width = width
        this.length = length
        this.planes = planes
        this.tiles =
            Array(planes) { plane ->
                Array(width) { x ->
                    Array(length) { y ->
                        Tile(TileCoordinate(plane, x, y))
                    }
                }
            }
    }

    fun width(): Int = width

    fun length(): Int = length

    fun planes(): Int = planes

    /** Returns an independent document copy without changing authored state. */
    fun copy(): WorldDocument {
        val copy = WorldDocument(width, length, planes)
        for (plane in 0 until planes) {
            for (x in 0 until width) {
                for (y in 0 until length) {
                    val original = tile(plane, x, y)
                    val destination = copy.tile(plane, x, y)
                    destination.restore(original.snapshot())
                    destination.heightSource(original.heightSource())
                }
            }
        }
        return copy
    }

    fun contains(
        plane: Int,
        x: Int,
        y: Int,
    ): Boolean =
        plane >= 0 &&
            plane < planes &&
            x >= 0 &&
            x < width &&
            y >= 0 &&
            y < length

    fun contains(coordinate: LocalTile?): Boolean =
        coordinate != null && contains(coordinate.plane, coordinate.x, coordinate.y)

    fun tileOpt(coordinate: LocalTile?): Optional<Tile> {
        if (!contains(coordinate)) {
            return Optional.empty()
        }
        val safeCoordinate = coordinate!!
        return Optional.of(tiles[safeCoordinate.plane][safeCoordinate.x][safeCoordinate.y])
    }

    /** @deprecated Use LocalTile so coordinate space is compiler-visible. */
    @kotlin.Deprecated("Use LocalTile so coordinate space is compiler-visible")
    @java.lang.Deprecated
    fun contains(coordinate: TileCoordinate?): Boolean =
        coordinate != null && contains(LocalTile.from(coordinate))

    /** @deprecated Use LocalTile so coordinate space is compiler-visible. */
    @kotlin.Deprecated("Use LocalTile so coordinate space is compiler-visible")
    @java.lang.Deprecated
    fun tileOpt(coordinate: TileCoordinate?): Optional<Tile> =
        if (coordinate == null) {
            Optional.empty()
        } else {
            tileOpt(LocalTile.from(coordinate))
        }

    fun tile(
        plane: Int,
        x: Int,
        y: Int,
    ): Tile {
        if (!contains(plane, x, y)) {
            throw IndexOutOfBoundsException("Tile outside world: $plane,$x,$y")
        }
        return tiles[plane][x][y]
    }

    fun tile(coordinate: LocalTile?): Tile {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        return tile(safeCoordinate.plane, safeCoordinate.x, safeCoordinate.y)
    }

    /** @deprecated Use LocalTile so absolute world coordinates cannot cross this boundary. */
    @kotlin.Deprecated("Use LocalTile so absolute world coordinates cannot cross this boundary")
    @java.lang.Deprecated
    fun tile(coordinate: TileCoordinate?): Tile =
        tile(LocalTile.from(coordinate))

    /**
     * Returns the effective OSRS plane for authored terrain/object data at a local tile.
     *
     * A bridge flag on authored plane 1 demotes the complete column by one plane; plane 0
     * consequently has no effective collision surface at that column, matching scene ordering.
     */
    fun effectivePlane(
        authoredPlane: Int,
        x: Int,
        y: Int,
    ): Int {
        if (authoredPlane < 0 || authoredPlane >= planes) {
            throw IndexOutOfBoundsException("Invalid authored plane: $authoredPlane")
        }
        tile(authoredPlane, x, y)
        val bridged =
            planes > 1 &&
                OsrsTileFlags.hasBridge(tile(1, x, y).snapshot().flags())
        return if (bridged) authoredPlane - 1 else authoredPlane
    }

    fun effectivePlane(coordinate: TileCoordinate?): Int {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        return effectivePlane(safeCoordinate.plane, safeCoordinate.x, safeCoordinate.y)
    }

    /** Returns the bridge relation for one authored tile, when its flag is set. */
    fun bridgeLink(coordinate: TileCoordinate?): Optional<BridgeLink> {
        if (
            coordinate == null ||
            coordinate.plane == 0 ||
            coordinate.plane >= planes ||
            coordinate.x < 0 ||
            coordinate.x >= width ||
            coordinate.y < 0 ||
            coordinate.y >= length
        ) {
            return Optional.empty()
        }

        if (!OsrsTileFlags.hasBridge(tile(1, coordinate.x, coordinate.y).snapshot().flags())) {
            return Optional.empty()
        }

        return Optional.of(
            BridgeLink(
                coordinate,
                TileCoordinate(
                    effectivePlane(coordinate),
                    coordinate.x,
                    coordinate.y,
                ),
            ),
        )
    }

    /** Returns all authored-plane bridge links represented by the document flags. */
    fun bridgeLinks(): List<BridgeLink> {
        if (planes <= 1) {
            return java.util.List.of()
        }

        val links = ArrayList<BridgeLink>()
        for (x in 0 until width) {
            for (y in 0 until length) {
                if (!OsrsTileFlags.hasBridge(tile(1, x, y).snapshot().flags())) {
                    continue
                }
                for (plane in 1 until planes) {
                    links.add(
                        BridgeLink(
                            TileCoordinate(plane, x, y),
                            TileCoordinate(plane - 1, x, y),
                        ),
                    )
                }
            }
        }
        return java.util.List.copyOf(links)
    }

    companion object {
        const val DEFAULT_PLANES: Int = 4
    }
}
