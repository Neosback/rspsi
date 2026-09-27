package com.rspsi.editor.tool
import com.rspsi.editor.MoveObjectsCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.LocalTile
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw
import com.rspsi.editor.selection.ObjectSelection
import com.rspsi.editor.selection.ObjectSetSelection
import com.rspsi.editor.selection.Selection
import java.util.LinkedHashSet

/** Moves the current single- or multi-object selection by pointer drag. */
class MoveSelectionTool : EditorTool {
    private var context: ToolContext? = null
    private var objects: Set<WorldObject>? = null
    private var anchor: WorldTile? = null
    private var target: WorldTile? = null
    private var snapGridSize = 1
    fun snapGridSize() = snapGridSize
    fun setSnapGridSize(value: Int) { require(value >= 1) { "Snap grid size must be at least one tile" }; snapGridSize = value }
    override fun id() = "move-selection"
    override fun activate(context: ToolContext) { this.context = context; clear() }
    override fun deactivate() { clear(); context = null }
    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        if (event.button() != PointerButton.PRIMARY) return
        clear()
        objects = selectedObjects(ctx.session().selection().current())
        val selected = objects ?: return
        ctx.worldTileAt(event.x(), event.y())
            .filter { contains(ctx, selected, it) }
            .ifPresent { anchor = it; target = it }
        if (anchor == null) objects = null
    }
    override fun pointerDrag(event: PointerEvent) {
        val ctx = context ?: return
        val selected = objects ?: return
        if (event.button() != PointerButton.PRIMARY) return
        val start = anchor ?: return
        ctx.worldTileAt(event.x(), event.y()).filter { it.plane == start.plane }.map { snap(ctx, it) }.ifPresent { target = it }
    }
    override fun pointerUp(event: PointerEvent) {
        val ctx = context
        val selected = objects
        val start = anchor
        val end = target
        if (ctx != null && selected != null && start != null && end != null && event.button() == PointerButton.PRIMARY) {
            val anchorLocal = ctx.local(start).orElse(null)
            val targetLocal = ctx.local(end).orElse(null)
            if (anchorLocal != null && targetLocal != null) {
                val deltaX = targetLocal.x - anchorLocal.x
                val deltaY = targetLocal.y - anchorLocal.y
                if ((deltaX != 0 || deltaY != 0) && ctx.session().canEdit()) {
                    ctx.session().execute(MoveObjectsCommand(selected, deltaX, deltaY))
                    ctx.session().selection().selectObjects(selected.mapTo(LinkedHashSet()) {
                        WorldObject(it.id, it.type, it.rotation, it.plane, it.x + deltaX, it.y + deltaY)
                    })
                }
            }
        }
        clear()
    }
    override fun inspector() = ToolInspector { listOf(PropertyDescriptor("snapGridSize", "Snap grid", PropertyDescriptor.ValueType.INTEGER, 1, 64)) }
    override fun renderOverlay(draw: OverlayDraw) { target?.let(draw::tileOutline) }
    private fun snap(ctx: ToolContext, tile: WorldTile): WorldTile {
        val local = ctx.local(tile).orElse(null) ?: return tile
        return ctx.world(LocalTile(local.plane,
            TileSnapper.snap(local.x, snapGridSize, ctx.session().world().width()),
            TileSnapper.snap(local.y, snapGridSize, ctx.session().world().length())))
    }
    private fun contains(ctx: ToolContext, values: Set<WorldObject>, tile: WorldTile): Boolean {
        val local = ctx.local(tile).orElse(null) ?: return false
        return values.any { it.plane == local.plane && it.x == local.x && it.y == local.y }
    }
    private fun selectedObjects(selection: Selection?): Set<WorldObject>? = when (selection) {
        is ObjectSelection -> setOf(selection.`object`)
        is ObjectSetSelection -> LinkedHashSet(selection.objects())
        else -> null
    }
    
    private fun clear() { objects = null; anchor = null; target = null }
}
