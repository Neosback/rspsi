package com.rspsi.editor.tool
import com.rspsi.editor.RotateObjectsCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw
import com.rspsi.editor.selection.ObjectSelection
import com.rspsi.editor.selection.ObjectSetSelection
import com.rspsi.editor.selection.Selection
import java.util.LinkedHashSet

/** Rotates the current object selection clockwise on click. */
class RotateSelectionTool : EditorTool {
    private var context: ToolContext? = null
    private var quarterTurns = 1
    fun quarterTurns() = quarterTurns
    fun setQuarterTurns(value: Int) { quarterTurns = Math.floorMod(value, 4) }
    override fun id() = "rotate-selection"
    override fun activate(context: ToolContext) { this.context = context }
    override fun deactivate() { context = null }
    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        if (event.button() != PointerButton.PRIMARY) return
        val objects = selectedObjects(ctx.session().selection().current()) ?: return
        ctx.worldTileAt(event.x(), event.y()).filter { contains(ctx, objects, it) }.ifPresent {
            if (ctx.session().canEdit()) {
                ctx.session().execute(RotateObjectsCommand(objects, quarterTurns))
                ctx.session().selection().selectObjects(rotated(objects, quarterTurns))
            }
        }
    }
    override fun pointerDrag(event: PointerEvent) = Unit
    override fun pointerUp(event: PointerEvent) = Unit
    override fun inspector() = ToolInspector { listOf(PropertyDescriptor("quarterTurns", "Quarter turns", PropertyDescriptor.ValueType.INTEGER, 0, 3)) }
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
    private fun rotated(objects: Set<WorldObject>, turns: Int): Set<WorldObject> =
        objects.mapTo(LinkedHashSet()) { WorldObject(it.id, it.type, (it.rotation + turns) and 3, it.plane, it.x, it.y) }
}
