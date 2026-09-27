package com.rspsi.editor.tool
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.LocalTile
import com.rspsi.editor.model.TileBounds
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw
import java.util.LinkedHashSet

/** Selects a rectangular tile area or the objects owned by that area. */
class BoxSelectTool : EditorTool {
    enum class Target { TILES, OBJECTS }
    enum class Mode { SINGLE, MULTI }
    private var target = Target.TILES
    private var mode = Mode.MULTI
    private var context: ToolContext? = null
    private var start: WorldTile? = null
    private var current: WorldTile? = null
    private var downX = 0f
    private var downY = 0f
    fun target() = target
    fun setTarget(target: Target) { this.target = target }
    fun mode() = mode
    fun setMode(mode: Mode) { this.mode = mode }
    override fun id() = "box-select"
    override fun activate(context: ToolContext) { this.context=context; clear() }
    override fun deactivate() { clear(); context=null }
    override fun pointerDown(event: PointerEvent) {
        val ctx=context?:return
        if(event.button()!=PointerButton.PRIMARY)return
        clear(); downX=event.x(); downY=event.y()
        ctx.worldTileAt(event.x(),event.y()).ifPresent { start=it; current=it }
    }
    override fun pointerDrag(event: PointerEvent) {
        if(mode==Mode.SINGLE)return
        val ctx=context?:return; val first=start?:return
        if(event.button()!=PointerButton.PRIMARY)return
        ctx.worldTileAt(event.x(),event.y()).filter { it.plane==first.plane }.ifPresent { current=it }
    }
    override fun pointerUp(event: PointerEvent) {
        val ctx=context; val first=start; val last=current
        if(ctx==null || first==null || last==null || event.button()!=PointerButton.PRIMARY){clear();return}
        val localStart=ctx.local(first).orElse(null); val localCurrent=ctx.local(last).orElse(null)
        if(localStart==null || localCurrent==null){clear();return}
        val localBounds=if(mode==Mode.SINGLE) TileBounds(localStart.x,localStart.y,localStart.x,localStart.y) else bounds(localStart,localCurrent)
        if(target==Target.TILES) ctx.session().selection().selectArea(localStart.plane,localBounds)
        else {
            val objects=LinkedHashSet<WorldObject>()
            if(mode==Mode.SINGLE) ctx.hitAt(downX,downY).flatMap { it.`object`() }.ifPresent(objects::add)
            if(objects.isEmpty()){
                val world=ctx.session().world()
                for(x in localBounds.minX..localBounds.maxX) for(y in localBounds.minY..localBounds.maxY)
                    world.tile(LocalTile(localStart.plane,x,y)).snapshot().objects().forEach(objects::add)
            }
            ctx.session().selection().selectObjects(objects)
            if(objects.isEmpty()){clear();return}
        }
    }
    override fun inspector()=ToolInspector { listOf(PropertyDescriptor("target","Select",PropertyDescriptor.ValueType.ENUM,0,1)) }
    override fun renderOverlay(draw: OverlayDraw) {
        val first=start?:return
        if(target!=Target.TILES)return
        val last=current
        if(mode==Mode.SINGLE || last==null){draw.tileFilled(first);draw.tileOutline(first);return}
        val b=bounds(first,last); val plane=first.plane
        for(x in b.minX..b.maxX) for(y in b.minY..b.maxY) draw.tileFilled(WorldTile(plane,x,y))
        for(x in b.minX..b.maxX){draw.tileOutline(WorldTile(plane,x,b.minY));if(b.maxY!=b.minY)draw.tileOutline(WorldTile(plane,x,b.maxY))}
        for(y in b.minY+1 until b.maxY){draw.tileOutline(WorldTile(plane,b.minX,y));if(b.maxX!=b.minX)draw.tileOutline(WorldTile(plane,b.maxX,y))}
    }
    private fun bounds(a:WorldTile,b:WorldTile)=TileBounds(minOf(a.x,b.x),minOf(a.y,b.y),maxOf(a.x,b.x),maxOf(a.y,b.y))
    private fun bounds(a:LocalTile,b:LocalTile)=TileBounds(minOf(a.x,b.x),minOf(a.y,b.y),maxOf(a.x,b.x),maxOf(a.y,b.y))
    private fun clear(){start=null;current=null}
}
