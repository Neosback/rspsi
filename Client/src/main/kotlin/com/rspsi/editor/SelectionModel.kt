package com.rspsi.editor

import com.rspsi.editor.model.TileBounds
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.WorldFragment
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.selection.FragmentSelection
import com.rspsi.editor.selection.ObjectSelection
import com.rspsi.editor.selection.ObjectSetSelection
import com.rspsi.editor.selection.Selection
import com.rspsi.editor.selection.TileAreaSelection
import com.rspsi.editor.selection.TileSelection
import com.rspsi.editor.selection.TileSetSelection
import com.rspsi.editor.selection.VertexSelection
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Editor selection state kept separate from scene rendering state.
 *
 * This is a direct behavioral migration of the Java model. In particular, [tiles] exposes a live
 * unmodifiable view of the backing tile set, replacement selections clear prior state before
 * validating the new value, and every public mutation notifies listeners exactly once.
 */
class SelectionModel {
    private val selectedTiles = LinkedHashSet<TileCoordinate>()
    private val listeners = CopyOnWriteArrayList<SelectionChangeListener>()
    private var currentSelection: Selection? = null

    fun select(coordinate: TileCoordinate?) {
        val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
        selectedTiles.add(safeCoordinate)
        currentSelection =
            if (selectedTiles.size == 1) {
                TileSelection(safeCoordinate)
            } else {
                TileSetSelection(selectedTiles)
            }
        notifyChanged()
    }

    fun deselect(coordinate: TileCoordinate?) {
        if (coordinate != null) {
            selectedTiles.remove(coordinate)
        }
        currentSelection =
            when (selectedTiles.size) {
                0 -> null
                1 -> TileSelection(selectedTiles.iterator().next())
                else -> TileSetSelection(selectedTiles)
            }
        notifyChanged()
    }

    fun clear() {
        clearInternal()
        notifyChanged()
    }

    private fun clearInternal() {
        selectedTiles.clear()
        currentSelection = null
    }

    fun contains(coordinate: TileCoordinate?): Boolean =
        coordinate != null && selectedTiles.contains(coordinate)

    /**
     * Returns the historical live, read-only view rather than a snapshot copy.
     *
     * Existing callers can retain this view and observe later tile-selection mutations.
     */
    fun tiles(): Set<TileCoordinate> =
        Collections.unmodifiableSet(selectedTiles)

    /**
     * Resolves all currently selected tile coordinates across single, set, and area selections.
     */
    fun selectedCoordinates(): Set<TileCoordinate> {
        val selection = currentSelection
        if (selection is TileSelection) {
            return java.util.Set.of(selection.coordinate())
        }
        if (selection is TileSetSelection) {
            return selection.coordinates()
        }
        if (selection is TileAreaSelection) {
            val areaTiles = LinkedHashSet<TileCoordinate>()
            val bounds = selection.bounds()
            for (x in bounds.minX..bounds.maxX) {
                for (y in bounds.minY..bounds.maxY) {
                    areaTiles.add(TileCoordinate(selection.plane(), x, y))
                }
            }
            return Collections.unmodifiableSet(areaTiles)
        }
        return Collections.unmodifiableSet(selectedTiles)
    }

    fun current(): Selection? =
        currentSelection

    fun addChangeListener(listener: SelectionChangeListener?) {
        listeners.add(listener ?: throw NullPointerException("listener"))
    }

    fun removeChangeListener(listener: SelectionChangeListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    fun selectArea(
        plane: Int,
        bounds: TileBounds?,
    ) {
        clearInternal()
        currentSelection = TileAreaSelection(plane, bounds)
        notifyChanged()
    }

    /** Replaces the current selection with an arbitrary set of tiles. */
    fun selectTiles(
        coordinates: Set<@JvmSuppressWildcards TileCoordinate?>?,
    ) {
        clearInternal()
        if (coordinates != null) {
            for (coordinate in coordinates) {
                // Java's LinkedHashSet accepted null here. The legacy model only encounters the
                // resulting null later when it tries to materialize a concrete selection value.
                @Suppress("UNCHECKED_CAST")
                (selectedTiles as java.util.Set<TileCoordinate?>).add(coordinate)
            }
        }

        currentSelection =
            when (selectedTiles.size) {
                0 -> null
                1 -> TileSelection(selectedTiles.iterator().next())
                else -> TileSetSelection(selectedTiles)
            }
        notifyChanged()
    }

    fun selectVertex(vertex: VertexSelection?) {
        clearInternal()
        currentSelection = vertex ?: throw NullPointerException("vertex")
        notifyChanged()
    }

    fun selectObject(objectPlacement: WorldObject?) {
        clearInternal()
        currentSelection = ObjectSelection(objectPlacement)
        notifyChanged()
    }

    fun selectObjects(
        objects: Set<@JvmSuppressWildcards WorldObject?>?,
    ) {
        clearInternal()
        if (objects == null || objects.isEmpty()) {
            notifyChanged()
            return
        }

        currentSelection =
            if (objects.size == 1) {
                ObjectSelection(objects.iterator().next())
            } else {
                @Suppress("UNCHECKED_CAST")
                ObjectSetSelection(objects as Set<WorldObject>)
            }
        notifyChanged()
    }

    fun selectFragment(fragment: WorldFragment?) {
        clearInternal()
        currentSelection = FragmentSelection(fragment)
        notifyChanged()
    }

    private fun notifyChanged() {
        for (listener in listeners) {
            listener.changed(currentSelection)
        }
    }
}
