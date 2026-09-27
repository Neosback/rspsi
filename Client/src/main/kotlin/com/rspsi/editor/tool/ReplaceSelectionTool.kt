package com.rspsi.editor.tool
import com.rspsi.editor.ReplaceObjectsCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw
import com.rspsi.editor.selection.ObjectSelection
import com.rspsi.editor.selection.ObjectSetSelection
import com.rspsi.editor.selection.Selection
import java.util.LinkedHashSet

/** Replaces the definition ID of the current object selection on click. */
class ReplaceSelectionTool(replacementId: Int) : EditorTool {
    private var replacementId = 0
    private var context: ToolContext? = null
    init { setReplacementId(replacementId) }
    fun replacementId() = replacementId
    fun setReplacementId(value: Int) { require(value >= 0) { "Replacement object ID cannot be negative" }; replacementId = value }
    override fun id() = "replace-selection"
    override fun activate(context: ToolContext) { this.context = context }
    override fun deactivate() { context = null }
    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        if (event.button() != PointerButton.PRIMARY) return
        val objects = selectedObjects(ctx.session().selection().current()) ?: return
        ctx.worldTileAt(event.x(), event.y()).filter { contains(ctx, objects, it) }.ifPresent {
            if (ctx.session().canEdit()) {
                val command = ReplaceObjectsCommand(objects, replacementId)
                ctx.session().execute(command)
                ctx.session().selection().selectObjects(command.replacementObjects())
            }
        }
    }
    override fun pointerDrag(event: PointerEvent) = Unit
    override fun pointerUp(event: PointerEvent) = Unit
    override fun inspector() = ToolInspector { listOf(PropertyDescriptor("replacementId", "Replacement", PropertyDescriptor.ValueType.INTEGER, 0, Int.MAX_VALUE)) }
    override fun renderOverlay(draw: OverlayDraw) = Unit
    private fun contains(ctx: ToolContext, objects: Set<WorldObject>, tile: WorldTile): Boolean {
        val local = ctx.local(tile).orElse(null) ?: return false
        return objects.any { it.plane == local.plane && it.x == local.x && it.y == local.y }
    }
    private fun selectedObjects(selection: Selection?): Set<WorldObject>? = when (selection) {
        is ObjectSelection -> setOf(selection.`object`)
        is ObjectSetSelection -> LinkedHashSet(selection.objects())
        else -> null
    }
}
