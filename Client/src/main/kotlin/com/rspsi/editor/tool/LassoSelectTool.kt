package com.rspsi.editor.tool
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.LocalTile
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw
import java.util.LinkedHashSet

/** Selects discrete tiles or tile-owned objects whose tile centers fall inside a world-space pointer lasso. */
class LassoSelectTool : EditorTool {
    enum class Target { TILES, OBJECTS }
    private var target = Target.TILES
    private var context: ToolContext? = null
    private val points = mutableListOf<WorldTile>()
    private var plane = -1
    fun target() = target
    fun setTarget(target: Target) { this.target = target }
    override fun id() = "lasso-select"
    override fun activate(context: ToolContext) { this.context = context; clear() }
    override fun deactivate() { clear(); context = null }
    override fun pointerDown(event: PointerEvent) { if (context != null && event.button() == PointerButton.PRIMARY) { clear(); addPoint(event) } }
    override fun pointerDrag(event: PointerEvent) { if (context != null && points.isNotEmpty() && event.button() == PointerButton.PRIMARY) addPoint(event) }
    override fun pointerUp(event: PointerEvent) {
        val ctx = context
        if (ctx == null || points.size < 3 || event.button() != PointerButton.PRIMARY) { clear(); return }
        val locals = localsInsideLasso()
        if (target == Target.TILES) {
            ctx.session().selection().selectTiles(locals.mapTo(LinkedHashSet<TileCoordinate>()) { it.coordinate() })
        } else {
            val objects = LinkedHashSet<WorldObject>()
            locals.forEach { ctx.session().world().tile(it).snapshot().objects().forEach(objects::add) }
            ctx.session().selection().selectObjects(objects)
        }
        clear()
    }
    override fun inspector() = ToolInspector { listOf(PropertyDescriptor("target", "Select", PropertyDescriptor.ValueType.ENUM, 0, 1)) }
    override fun renderOverlay(draw: OverlayDraw) { val ctx=context?:return; localsInsideLasso().map(ctx::world).forEach(draw::tileOutline) }
    private fun addPoint(event: PointerEvent) {
        val ctx=context?:return
        ctx.worldTileAt(event.x(),event.y()).filter { plane < 0 || it.plane == plane }.filter { ctx.local(it).isPresent }.ifPresent {
            if (plane < 0) plane=it.plane
            if (points.isEmpty() || points.last()!=it) points.add(it)
        }
    }
    private fun localsInsideLasso(): Set<LocalTile> {
        val ctx=context?:return emptySet()
        if(points.size<3 || plane<0)return emptySet()
        val selected=LinkedHashSet<LocalTile>(); val world=ctx.session().world()
        for(x in 0 until world.width()) for(y in 0 until world.length()) {
            val local=LocalTile(plane,x,y); val absolute=ctx.world(local)
            if(contains(absolute.x+0.5,absolute.y+0.5)) selected.add(local)
        }
        return selected.toSet()
    }
    private fun contains(x:Double,y:Double):Boolean {
        var inside=false; var previous=points.size-1
        for(index in points.indices) {
            val currentX=points[index].x+0.5; val currentY=points[index].y+0.5
            val previousX=points[previous].x+0.5; val previousY=points[previous].y+0.5
            if((currentY>y)!=(previousY>y) && x < (previousX-currentX)*(y-currentY)/(previousY-currentY)+currentX) inside=!inside
            previous=index
        }
        return inside
    }
    private fun clear(){points.clear();plane=-1}
}
