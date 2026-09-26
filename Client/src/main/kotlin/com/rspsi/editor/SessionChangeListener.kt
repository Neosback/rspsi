package com.rspsi.editor

import com.rspsi.editor.model.TileCoordinate

/**
 * Receives the affected tiles after a command, undo, or redo completes.
 *
 * The nullable parameter avoids adding a Kotlin-generated null check that did not exist on the
 * Java SAM boundary. [JvmSuppressWildcards] preserves the Java generic signature as
 * Set<TileCoordinate> rather than widening it to a wildcarded collection.
 */
@java.lang.FunctionalInterface
fun interface SessionChangeListener {
    fun changed(
        tiles: Set<@JvmSuppressWildcards TileCoordinate>?,
    )
}
