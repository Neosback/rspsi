package com.rspsi.editor

/**
 * Observes session state changes such as save-marker, edit, undo, or redo.
 *
 * The nullable parameter preserves the original Java SAM boundary without introducing an
 * implicit Kotlin null check.
 */
@java.lang.FunctionalInterface
fun interface SessionStateListener {
    fun changed(session: EditorSession?)
}
