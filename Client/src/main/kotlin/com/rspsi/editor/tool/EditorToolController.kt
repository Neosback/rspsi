package com.rspsi.editor.tool

import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.input.PointerEventSink

/**
 * Thin frontend-neutral dispatcher for the currently active editor tool.
 *
 * This class deliberately does not interpret gestures, own tool state, or mutate the map.
 * Those responsibilities stay with EditorTool implementations and command/history services.
 * A future sealed ToolEvent/ToolAction layer can therefore evolve at the neutral tool boundary
 * without coupling input dispatch to Studio, JavaFX, GLFW, or renderer code.
 */
class EditorToolController : PointerEventSink {
    private var activeToolState: EditorTool? = null

    fun activate(
        tool: EditorTool?,
        context: ToolContext?,
    ) {
        // Preserve the Java controller's fail-before-deactivate behavior: an invalid
        // replacement must not tear down the currently active tool.
        val nextTool = tool ?: throw NullPointerException("tool")
        val nextContext = context ?: throw NullPointerException("context")

        activeToolState?.deactivate()
        activeToolState = nextTool
        nextTool.activate(nextContext)
    }

    fun deactivate() {
        val current = activeToolState ?: return
        current.deactivate()
        activeToolState = null
    }

    fun activeTool(): EditorTool? = activeToolState

    override fun pointerDown(event: PointerEvent?) {
        activeToolState?.pointerDown(event)
    }

    override fun pointerDrag(event: PointerEvent?) {
        activeToolState?.pointerDrag(event)
    }

    override fun pointerUp(event: PointerEvent?) {
        activeToolState?.pointerUp(event)
    }

    override fun pointerMove(event: PointerEvent?) {
        activeToolState?.pointerMove(event)
    }
}
