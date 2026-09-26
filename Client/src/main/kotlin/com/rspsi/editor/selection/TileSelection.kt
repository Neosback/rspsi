package com.rspsi.editor.selection

import com.rspsi.editor.model.TileCoordinate

/**
 * Selection containing one authored tile coordinate.
 *
 * The nullable component preserves the exact historical Java null-constructor contract.
 */
@JvmRecord
data class TileSelection(
    val coordinate: TileCoordinate?,
) : Selection {
    init {
        if (coordinate == null) {
            throw NullPointerException("coordinate")
        }
    }

    override fun toString(): String =
        "TileSelection[coordinate=$coordinate]"
}
