package com.rspsi.editor.selection

import com.rspsi.editor.model.WorldObject

/**
 * Selection containing one world object.
 *
 * The nullable component preserves the exact Java constructor null-failure message while valid
 * instances remain non-null by construction.
 */
@JvmRecord
data class ObjectSelection(
    val object: WorldObject?,
) : Selection {
    init {
        if (object == null) {
            throw NullPointerException("object")
        }
    }

    override fun toString(): String =
        "ObjectSelection[object=$object]"
}
