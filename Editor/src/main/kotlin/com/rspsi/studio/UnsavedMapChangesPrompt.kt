package com.rspsi.studio

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.EditorSession
import com.rspsi.editor.plugin.EditorNotificationService
import imgui.ImGui

/**
 * The modal that guards leaving a map with unsaved changes, either by closing Map Studio or
 * by going to another region.
 *
 * Save writes the map session only (Save Project); definition edits belong to the cache
 * session and need an explicit output-cache publish, so when leaving Map Studio with
 * unpublished definitions the prompt says so instead of pretending Save covers them. A
 * region switch keeps the cache session, so definition edits never block it.
 */
internal class UnsavedMapChangesPrompt {
    private var open = false
    private var leavingRegion = false
    private var onLeave: Runnable? = null

    /** Opens the prompt; [onLeave] runs after Save or Discard. */
    fun ask(leavingRegion: Boolean, onLeave: Runnable) {
        this.open = true
        this.leavingRegion = leavingRegion
        this.onLeave = onLeave
    }

    /** Closes the prompt without leaving, e.g. when the map was unloaded some other way. */
    fun dismiss() {
        open = false
        leavingRegion = false
        onLeave = null
    }

    /**
     * Draws the modal while it is open. [status] receives user-facing results (save failed,
     * definitions still unpublished).
     */
    fun render(
        session: EditorSession?,
        cache: LoadedOsrsCacheSession?,
        notifications: EditorNotificationService,
        status: (String) -> Unit,
    ) {
        if (open) ImGui.openPopup(POPUP_ID)
        if (!ImGui.beginPopupModal(POPUP_ID)) return

        val definitionsUnpublished = (cache?.objectDefinitions()?.unpublishedCount() ?: 0) > 0
        val externalDirty = (definitionsUnpublished && !leavingRegion) ||
            (session != null && session.hasUnsavedExternalState())
        ImGui.textWrapped(
            when {
                leavingRegion && !externalDirty -> "This map has unsaved changes. Save before leaving this region?"
                externalDirty -> "This Studio session contains unpublished definition edits. " +
                    "Map Save does not write those definitions. Publish them from Object Viewer > Properties " +
                    "to a separate output cache, or Discard & Close to lose the in-memory preview."
                else -> "This map has unsaved changes. Save before closing Map Studio?"
            },
        )

        val canSave = session != null && session.canSave() && session.isSessionSaveDirty()
        ImGui.beginDisabled(!canSave)
        if (ImGui.button(if (externalDirty) "Save Map" else "Save") && session != null) {
            try {
                session.save()
                val definitionsRemain = !leavingRegion && definitionsUnpublished
                if (session.hasUnsavedExternalState() || definitionsRemain) {
                    status("Map changes saved. Definition edits still need an output-cache build.")
                } else {
                    ImGui.closeCurrentPopup()
                    leave()
                }
            } catch (failure: RuntimeException) {
                val message = "Save failed: ${rootMessage(failure)}"
                status(message)
                notifications.error("Map save failed", message)
            }
        }
        ImGui.endDisabled()

        ImGui.sameLine()
        val discard = when {
            !externalDirty -> "Discard"
            leavingRegion -> "Discard & Leave"
            else -> "Discard & Close"
        }
        if (ImGui.button(discard)) {
            ImGui.closeCurrentPopup()
            leave()
        }
        ImGui.sameLine()
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup()
            dismiss()
        }
        ImGui.endPopup()
    }

    private fun leave() {
        val action = onLeave
        dismiss()
        action?.run()
    }

    private companion object {
        const val POPUP_ID = "Unsaved Studio changes##dashboard"
    }
}

/** The innermost cause's message, for one-line status text. */
internal fun rootMessage(failure: Throwable): String {
    var current = failure
    while (current.cause != null) current = current.cause!!
    return current.message ?: current.javaClass.simpleName
}
