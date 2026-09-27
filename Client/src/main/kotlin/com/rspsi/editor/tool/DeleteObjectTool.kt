package com.rspsi.editor.tool

import com.rspsi.editor.DeleteObjectCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw

/** Deletes the viewport-picked object, falling back to the first object owned by the clicked tile. */
class DeleteObjectTool : EditorTool {
    private var context: ToolContext? = null
    override fun id() = "delete-object"
    override fun activate(context: ToolContext) { this.context = context }
    override fun deactivate() { context = null }
    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        if (event.button() != PointerButton.PRIMARY) return
        ctx.viewport().objectAt(event.x(), event.y())
            .or { ctx.worldTileAt(event.x(), event.y()).flatMap(::firstObject) }
            .ifPresent(::delete)
    }
    override fun pointerDrag(event: PointerEvent) = Unit
    override fun pointerUp(event: PointerEvent) = Unit
    override fun inspector() = ToolInspector { emptyList() }
    override fun renderOverlay(draw: OverlayDraw) = Unit
    private fun firstObject(worldTile: WorldTile): java.util.Optional<WorldObject> {
        val ctx = context ?: return java.util.Optional.empty()
        return ctx.local(worldTile).flatMap(ctx.session().world()::tileOpt)
            .flatMap { it.snapshot().objects().stream().findFirst() }
    }
    private fun delete(obj: WorldObject) {
        val ctx = context ?: return
        if (ctx.session().canEdit()) ctx.session().execute(DeleteObjectCommand(obj))
    }
}
