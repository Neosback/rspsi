package com.rspsi.editor.plugin.event

import com.rspsi.editor.model.TileCoordinate

/**
 * Kotlin semantic owner for selection-change event normalization.
 *
 * The public Java record remains the JVM compatibility shell because its compact constructor
 * replaces the incoming set with a defensive immutable copy before record storage.
 */
object SelectionChangedEventSemantics {
    @JvmStatic
    fun normalize(
        selectedTiles: Set<TileCoordinate>?,
    ): Set<TileCoordinate> =
        java.util.Set.copyOf(selectedTiles ?: java.util.Set.of())
}
