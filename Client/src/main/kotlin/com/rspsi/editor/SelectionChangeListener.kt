package com.rspsi.editor

import com.rspsi.editor.selection.Selection

/**
 * Observes the single neutral selection value used by all editor tools.
 *
 * The nullable parameter preserves the historical Java interface contract: listeners may receive
 * null when selection state is cleared. The explicit [FunctionalInterface] annotation keeps the
 * Java reflection/SAM surface unchanged in addition to Kotlin's [fun interface] support.
 */
@java.lang.FunctionalInterface
fun interface SelectionChangeListener {
    fun changed(selection: Selection?)
}
