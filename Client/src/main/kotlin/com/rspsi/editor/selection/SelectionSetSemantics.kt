package com.rspsi.editor.selection

import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.WorldObject
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Canonical normalization semantics for collection-backed selection records.
 *
 * The public records stay in Java because they are direct implementations of the sealed Java
 * [Selection] hierarchy. Kotlin cannot extend that Java sealed interface directly, so this owner
 * moves only the mutable-input normalization/validation behavior while preserving the record ABI.
 */
object SelectionSetSemantics {
    @JvmStatic
    fun normalizeTiles(
        coordinates: Set<TileCoordinate>?,
    ): Set<TileCoordinate> {
        val copy = LinkedHashSet<TileCoordinate>()
        if (coordinates != null) {
            copy.addAll(coordinates)
        }
        if (copy.isEmpty()) {
            throw IllegalArgumentException("A tile selection cannot be empty")
        }
        return Collections.unmodifiableSet(copy)
    }

    @JvmStatic
    fun normalizeObjects(
        objects: Set<WorldObject>?,
    ): Set<WorldObject> {
        val copy = LinkedHashSet<WorldObject>()
        if (objects != null) {
            copy.addAll(objects)
        }
        if (copy.isEmpty()) {
            throw IllegalArgumentException("An object selection cannot be empty")
        }
        return Collections.unmodifiableSet(copy)
    }
}
