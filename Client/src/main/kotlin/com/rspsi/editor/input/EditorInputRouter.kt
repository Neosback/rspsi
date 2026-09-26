package com.rspsi.editor.input

import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.tool.EditorToolController

/**
 * Routes frontend-translated input through neutral editor contracts.
 *
 * Frontends may disagree about focus representation, but the neutral ordering is fixed:
 * a focused text editor keeps keyboard input; otherwise pressed keys may trigger registered
 * editor shortcuts. Pointer lifecycle events are forwarded unchanged to the active tool.
 *
 * This router deliberately does not interpret gestures or mutate editor state. That keeps
 * frontend translation separate from future sealed tool-event/state-machine work.
 */
class EditorInputRouter(
    pluginContext: EditorPluginContext?,
    tools: EditorToolController?,
) : PointerEventSink {
    private val pluginContextValue =
        pluginContext ?: throw NullPointerException("pluginContext")
    private val toolsValue =
        tools ?: throw NullPointerException("tools")

    /** Dispatches a key when no frontend text editor currently owns focus. */
    fun key(event: EditorKeyEvent?): Boolean = key(event, false)

    /**
     * Dispatches one translated key event.
     *
     * When [textInputFocused] is true, global editor shortcuts are intentionally skipped
     * so normal text editing remains owned by the frontend control with focus.
     */
    fun key(
        event: EditorKeyEvent?,
        textInputFocused: Boolean,
    ): Boolean {
        val safeEvent = event ?: throw NullPointerException("event")
        if (textInputFocused || !safeEvent.pressed()) {
            return false
        }
        return pluginContextValue.registry().dispatchShortcut(pluginContextValue, safeEvent)
    }

    fun tools(): EditorToolController = toolsValue

    override fun pointerDown(event: PointerEvent?) {
        toolsValue.pointerDown(event)
    }

    override fun pointerDrag(event: PointerEvent?) {
        toolsValue.pointerDrag(event)
    }

    override fun pointerUp(event: PointerEvent?) {
        toolsValue.pointerUp(event)
    }

    override fun pointerMove(event: PointerEvent?) {
        toolsValue.pointerMove(event)
    }
}
