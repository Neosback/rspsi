package com.rspsi.editor.selection

import com.rspsi.editor.model.TileBounds

/**
 * Inclusive rectangular selection on one authored plane.
 *
 * Validation order intentionally matches the Java record: plane validation happens before the
 * bounds null check.
 */
@JvmRecord
data class TileAreaSelection(
    val plane: Int,
    val bounds: TileBounds?,
) : Selection {
    init {
        if (plane < 0) {
            throw IllegalArgumentException("Selection plane cannot be negative")
        }
        if (bounds == null) {
            throw NullPointerException("bounds")
        }
    }

    override fun toString(): String =
        "TileAreaSelection[plane=$plane, bounds=$bounds]"
}
