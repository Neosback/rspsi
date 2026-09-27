package com.rspsi.editor.tool

import com.rspsi.editor.DuplicateObjectCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.LocalTile
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw

/** Picks an object and places a copy at the release tile. */
class DuplicateObjectTool : EditorTool {
    private var context: ToolContext? = null
    private var sourceObject: WorldObject? = null
    private var target: WorldTile? = null
    private var snapGridSize = 1

    fun snapGridSize() = snapGridSize

    fun setSnapGridSize(value: Int) {
        require(value >= 1) { "Snap grid size must be at least one tile" }
        snapGridSize = value
    }

    override fun id() = "duplicate-object"

    override fun activate(context: ToolContext) {
        this.context = context
        clear()
    }

    override fun deactivate() {
        clear()
        context = null
    }

    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        if (event.button() != PointerButton.PRIMARY) return
        clear()

        ctx.worldTileAt(event.x(), event.y()).ifPresent { worldTile ->
            val local = ctx.local(worldTile).orElse(null) ?: return@ifPresent
            sourceObject = ctx.viewport().objectAt(event.x(), event.y()).orElseGet {
                ctx.session().world().tile(local).snapshot().objects().stream().findFirst().orElse(null)
            }
            target = worldTile
        }
    }

    override fun pointerDrag(event: PointerEvent) {
        val ctx = context ?: return
        if (sourceObject != null && event.button() == PointerButton.PRIMARY) {
            ctx.worldTileAt(event.x(), event.y()).map(::snap).ifPresent { target = it }
        }
    }

    override fun pointerUp(event: PointerEvent) {
        val ctx = context
        val source = sourceObject
        val targetLocal = if (ctx != null) target?.let { ctx.local(it).orElse(null) } else null

        if (ctx != null && source != null && targetLocal != null &&
            targetLocal.plane == source.plane && ctx.session().canEdit()
        ) {
            ctx.session().execute(DuplicateObjectCommand(source, targetLocal.x, targetLocal.y))
        }
        clear()
    }

    override fun inspector() = ToolInspector {
        listOf(PropertyDescriptor("snapGridSize", "Snap grid", PropertyDescriptor.ValueType.INTEGER, 1, 64))
    }

    override fun renderOverlay(draw: OverlayDraw) {
        target?.let(draw::tileOutline)
    }

    private fun snap(absolute: WorldTile): WorldTile {
        val ctx = context ?: return absolute
        val local = ctx.local(absolute).orElse(null) ?: return absolute
        val world = ctx.session().world()
        return ctx.world(
            LocalTile(
                local.plane,
                TileSnapper.snap(local.x, snapGridSize, world.width()),
                TileSnapper.snap(local.y, snapGridSize, world.length())
            )
        )
    }

    private fun clear() {
        sourceObject = null
        target = null
    }
}
