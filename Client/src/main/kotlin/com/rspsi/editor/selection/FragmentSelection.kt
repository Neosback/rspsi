package com.rspsi.editor.selection

import com.rspsi.editor.model.WorldFragment

/**
 * Selection containing one captured world fragment.
 *
 * The nullable Kotlin component type is intentional: it lets the JVM record constructor preserve
 * the historical Java `NullPointerException("fragment")` contract exactly. Valid instances never
 * retain a null fragment.
 */
@JvmRecord
data class FragmentSelection(
    val fragment: WorldFragment?,
) : Selection {
    init {
        if (fragment == null) {
            throw NullPointerException("fragment")
        }
    }

    override fun toString(): String =
        "FragmentSelection[fragment=$fragment]"
}
