package com.rspsi.editor.tool

import com.rspsi.editor.PlaceObjectCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.render.OverlayDraw

/** Places a configured object through the canonical object command. */
class PlaceObjectTool(id: Int, type: Int, rotation: Int) : EditorTool {
    private var objectId = 0
    private var objectType = 0
    private var objectRotation = 0
    private var context: ToolContext? = null
    init { setId(id); setType(type); setRotation(rotation) }
    fun setId(value: Int) { require(value >= 0) { "Object ID cannot be negative" }; objectId = value }
    fun setType(value: Int) { require(value >= 0) { "Object type cannot be negative" }; objectType = value }
    fun setRotation(value: Int) { require(value in 0..3) { "Object rotation must be between 0 and 3" }; objectRotation = value }
    fun idValue() = objectId
    fun type() = objectType
    fun rotation() = objectRotation
    override fun id() = "place-object"
    override fun activate(context: ToolContext) { this.context = context }
    override fun deactivate() { context = null }
    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        if (event.button() != PointerButton.PRIMARY) return
        ctx.localTileAt(event.x(), event.y()).ifPresent { local ->
            if (ctx.session().canEdit()) {
                ctx.session().execute(PlaceObjectCommand(
                    WorldObject(objectId, objectType, objectRotation, local.plane, local.x, local.y)
                ))
            }
        }
    }
    override fun pointerDrag(event: PointerEvent) = Unit
    override fun pointerUp(event: PointerEvent) = Unit
    override fun inspector() = ToolInspector { listOf(
        PropertyDescriptor("id", "Object", PropertyDescriptor.ValueType.INTEGER, 0, Int.MAX_VALUE),
        PropertyDescriptor("type", "Type", PropertyDescriptor.ValueType.INTEGER, 0, Int.MAX_VALUE),
        PropertyDescriptor("rotation", "Rotation", PropertyDescriptor.ValueType.INTEGER, 0, 3)
    ) }
    override fun renderOverlay(draw: OverlayDraw) = Unit
}
