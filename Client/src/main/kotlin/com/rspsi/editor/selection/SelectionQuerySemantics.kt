package com.rspsi.editor.selection

import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.TileSnapshot
import com.rspsi.editor.model.WorldDocument
import com.rspsi.editor.model.WorldObject
import java.util.LinkedHashSet

/**
 * Kotlin semantic owner for neutral editor selection queries.
 *
 * The public Java utility/record shell is retained for its static API and nested JVM-record ABI.
 * Query traversal, filter validation, and matching behavior live here.
 */
object SelectionQuerySemantics {
    @JvmStatic
    fun objects(
        world: WorldDocument?,
        filter: SelectionQuery.ObjectFilter?,
    ): Set<WorldObject> {
        val safeWorld = world ?: throw NullPointerException("world")
        val safeFilter = filter ?: throw NullPointerException("filter")
        val result = LinkedHashSet<WorldObject>()

        for (plane in 0 until safeWorld.planes()) {
            for (x in 0 until safeWorld.width()) {
                for (y in 0 until safeWorld.length()) {
                    for (objectPlacement in safeWorld.tile(plane, x, y).snapshot().objects()) {
                        if (matchesObject(safeFilter, objectPlacement)) {
                            result.add(objectPlacement)
                        }
                    }
                }
            }
        }
        return java.util.Set.copyOf(result)
    }

    @JvmStatic
    fun tiles(
        world: WorldDocument?,
        filter: SelectionQuery.TileFilter?,
    ): Set<TileCoordinate> {
        val safeWorld = world ?: throw NullPointerException("world")
        val safeFilter = filter ?: throw NullPointerException("filter")
        val result = LinkedHashSet<TileCoordinate>()

        for (plane in 0 until safeWorld.planes()) {
            for (x in 0 until safeWorld.width()) {
                for (y in 0 until safeWorld.length()) {
                    val tile = safeWorld.tile(plane, x, y).snapshot()
                    if (matchesTile(safeFilter, plane, tile)) {
                        result.add(TileCoordinate(plane, x, y))
                    }
                }
            }
        }
        return java.util.Set.copyOf(result)
    }

    @JvmStatic
    fun validateObjectFilter(
        id: Int?,
        type: Int?,
        plane: Int?,
        rotation: Int?,
    ) {
        if (id != null && id < 0) {
            throw IllegalArgumentException("Object ID cannot be negative")
        }
        if (type != null && type !in 0..63) {
            throw IllegalArgumentException("Object type must be 0..63")
        }
        if (plane != null && plane < 0) {
            throw IllegalArgumentException("Object plane cannot be negative")
        }
        if (rotation != null && rotation !in 0..3) {
            throw IllegalArgumentException("Object rotation must be 0..3")
        }
    }

    @JvmStatic
    fun matchesObject(
        filter: SelectionQuery.ObjectFilter,
        objectPlacement: WorldObject?,
    ): Boolean {
        val objectValue = objectPlacement ?: throw NullPointerException()
        return (filter.id() == null || filter.id() == objectValue.id) &&
            (filter.type() == null || filter.type() == objectValue.type) &&
            (filter.plane() == null || filter.plane() == objectValue.plane) &&
            (filter.rotation() == null || filter.rotation() == objectValue.rotation) &&
            (filter.category() == null || filter.category() == objectValue.category())
    }

    @JvmStatic
    fun validateTileFilter(
        plane: Int?,
        underlayId: Int?,
        overlayId: Int?,
        requiredFlagsMask: Int?,
    ) {
        if (plane != null && plane < 0) {
            throw IllegalArgumentException("Tile plane cannot be negative")
        }
        if (underlayId != null && underlayId !in 0..255) {
            throw IllegalArgumentException("Underlay ID must be 0..255")
        }
        if (overlayId != null && overlayId !in 0..65535) {
            throw IllegalArgumentException("Overlay ID must be 0..65535")
        }
        if (requiredFlagsMask != null && requiredFlagsMask < 0) {
            throw IllegalArgumentException("Required flags mask cannot be negative")
        }
    }

    @JvmStatic
    fun matchesTile(
        filter: SelectionQuery.TileFilter,
        tilePlane: Int,
        tile: TileSnapshot?,
    ): Boolean {
        val tileValue = tile ?: throw NullPointerException()
        return (filter.plane() == null || filter.plane() == tilePlane) &&
            (filter.underlayId() == null || filter.underlayId() == tileValue.underlayId()) &&
            (filter.overlayId() == null || filter.overlayId() == tileValue.overlayId()) &&
            (
                filter.requiredFlagsMask() == null ||
                    (tileValue.flags() and filter.requiredFlagsMask()) == filter.requiredFlagsMask()
            )
    }
}
