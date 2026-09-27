package com.rspsi.editor.tool

import com.rspsi.editor.*
import com.rspsi.editor.brush.*
import com.rspsi.editor.brush.builtin.SquareBrush
import com.rspsi.editor.input.*
import com.rspsi.editor.model.*
import com.rspsi.editor.render.OverlayDraw
import com.rspsi.editor.terrain.TerrainHeightEdit
import com.rspsi.editor.tool.state.TilePainterState
import java.util.LinkedHashSet

/** Composite painter using authoritative state, canonical brushes, and shared terrain vertices. */
class CompositeTilePainterTool @JvmOverloads constructor(
    private var state: TilePainterState = TilePainterState(),
    private val brushEngine: BrushEngine = BrushEngine(),
) : EditorTool, BrushAwareTool {
    private var brush: EditorBrush
    private var context: ToolContext? = null
    private val visited = LinkedHashSet<WorldTile>()
    private val targetLocals = LinkedHashSet<LocalTile>()
    private var lastMask: BrushMask? = null
    private var lastCenter: WorldTile? = null

    init {
        brush = try { brushEngine.brush(state.brushId()) } catch (_: IllegalArgumentException) {
            SquareBrush().also { brushEngine.register(it); state.setBrushId(it.id()) }
        }
        attachStateListener(state)
    }

    fun state() = state
    fun bindState(state: TilePainterState) {
        this.state = state
        try { brush = brushEngine.brush(state.brushId()) } catch (_: IllegalArgumentException) { state.setBrushId(brush.id()) }
        attachStateListener(state)
    }
    private fun attachStateListener(observed: TilePainterState) {
        observed.addListener { changed ->
            try { brush = brushEngine.brush(changed.brushId()) } catch (_: IllegalArgumentException) { }
        }
    }

    fun applyUnderlay()=state.applyUnderlay(); fun setApplyUnderlay(v:Boolean)=state.setApplyUnderlay(v)
    fun underlayId()=state.underlayId(); fun setUnderlayId(v:Int)=state.setUnderlayId(v)
    fun applyOverlay()=state.applyOverlay(); fun setApplyOverlay(v:Boolean)=state.setApplyOverlay(v)
    fun overlayId()=state.overlayId(); fun setOverlayId(v:Int)=state.setOverlayId(v)
    fun applyShape()=state.applyShape(); fun setApplyShape(v:Boolean)=state.setApplyShape(v)
    fun shape()=state.shape(); fun setShape(v:Int)=state.setShape(v)
    fun applyRotation()=state.applyRotation(); fun setApplyRotation(v:Boolean)=state.setApplyRotation(v)
    fun rotation()=state.rotation(); fun setRotation(v:Int)=state.setRotation(v)
    fun applyFlags()=state.applyFlags(); fun setApplyFlags(v:Boolean)=state.setApplyFlags(v)
    fun flags()=state.flags(); fun setFlags(v:Int)=state.setFlags(v)
    fun applyHeight()=state.applyHeight(); fun setApplyHeight(v:Boolean)=state.setApplyHeight(v)
    fun height()=state.height(); fun setHeight(v:Int)=state.setHeight(v)

    override fun brush()=brush
    override fun setBrush(value: EditorBrush) { brush=value; brushEngine.register(value); state.setBrushId(value.id()) }
    override fun brushRadius()=state.brushRadius()
    override fun setBrushRadius(radius:Int)=state.setBrushRadius(radius)
    override fun id()="tile-painter"
    override fun activate(context:ToolContext){this.context=context;clearStroke()}
    override fun deactivate(){clearStroke();context=null}
    override fun pointerDown(event:PointerEvent){if(context!=null&&event.button()==PointerButton.PRIMARY){clearStroke();sample(event)}}
    override fun pointerDrag(event:PointerEvent){if(context!=null&&event.button()==PointerButton.PRIMARY)sample(event)}
    override fun pointerUp(event:PointerEvent){
        val ctx=context
        if(ctx!=null&&targetLocals.isNotEmpty()&&ctx.session().canEdit()){
            val commands=buildCommands(ctx.session(),targetLocals.map{it.coordinate()})
            if(commands.isNotEmpty())ctx.session().execute(CompositeEditCommand("Paint composite tiles ("+targetLocals.size+" targets)",commands))
        }
        clearStroke()
    }
    override fun inspector()=ToolInspector{listOf(
        PropertyDescriptor("underlayId","Underlay",PropertyDescriptor.ValueType.INTEGER,0,Int.MAX_VALUE),
        PropertyDescriptor("overlayId","Overlay",PropertyDescriptor.ValueType.INTEGER,0,Int.MAX_VALUE),
        PropertyDescriptor("shape","Shape",PropertyDescriptor.ValueType.INTEGER,0,11),
        PropertyDescriptor("rotation","Rotation",PropertyDescriptor.ValueType.INTEGER,0,3),
        PropertyDescriptor("height","Height",PropertyDescriptor.ValueType.INTEGER,-2048,2048),
        PropertyDescriptor("brushRadius","Brush radius",PropertyDescriptor.ValueType.INTEGER,0,64))}
    override fun renderOverlay(draw:OverlayDraw){lastMask?.samples()?.forEach{draw.tileOutline(it.absolute())}?:visited.forEach{draw.tileOutline(it)}}

    fun transformTile(b:TileSnapshot)=TileSnapshot(
        if(state.applyHeight())state.height() else b.southWestHeight(),if(state.applyHeight())state.height() else b.southEastHeight(),
        if(state.applyHeight())state.height() else b.northEastHeight(),if(state.applyHeight())state.height() else b.northWestHeight(),
        if(state.applyUnderlay())state.underlayId() else b.underlayId(),if(state.applyOverlay())state.overlayId() else b.overlayId(),
        if(state.applyShape())state.shape() else b.overlayShape(),if(state.applyRotation())state.rotation() else b.overlayRotation(),
        if(state.applyFlags())state.flags() else b.flags(),b.objects(),b.heightSource())

    fun applyToCoordinates(coordinates:Collection<TileCoordinate>?,session:EditorSession?){
        if(coordinates.isNullOrEmpty()||session==null||!session.canEdit())return
        val locals=LinkedHashSet<TileCoordinate>()
        for(c in coordinates){val l=LocalTile.from(c);if(session.world().contains(l))locals.add(l.coordinate())}
        val commands=buildCommands(session,locals)
        if(commands.isNotEmpty())session.execute(CompositeEditCommand("Apply tile properties to selection ("+locals.size+" tiles)",commands))
    }
    private fun sample(event:PointerEvent){
        val ctx=context?:return
        ctx.viewport().tileAt(event.x(),event.y()).ifPresent{center->
            val prev=lastCenter
            val centers=if(prev==null)listOf(center) else brushEngine.interpolateStroke(prev,center,1.0)
            for(stamp in centers){
                val mask=brushEngine.sample(brush,state.brushRadius(),stamp,ctx.session().world(),ctx.session().window());lastMask=mask
                for(s in mask.samples()){visited.add(s.absolute());targetLocals.add(s.local())}
            };lastCenter=center
        }
    }
    private fun buildCommands(session:EditorSession,targets:Collection<TileCoordinate>?):List<EditorCommand>{
        if(targets.isNullOrEmpty())return emptyList()
        val commands=mutableListOf<EditorCommand>();val original=session.world();val predicted=original.copy()
        if(state.applyHeight()){
            val edit=TerrainHeightEdit(original,predicted)
            for(c in targets)edit.setTile(c,state.height())
            commands.addAll(edit.commands{"Paint terrain height at $it"})
        }
        for(c in targets){
            var base=predicted.tile(c).snapshot();val material=materialSnapshot(base)
            if(!sameMaterial(base,material)){commands.add(SetTileMaterialCommand(c,base,material,"Paint tile material at "+c));predicted.tile(c).restore(material,base.heightSource());base=predicted.tile(c).snapshot()}
            val flags=flagsSnapshot(base)
            if(base.flags()!=flags.flags()){commands.add(SetTileFlagsCommand(c,base,flags,"Paint tile flags at "+c));predicted.tile(c).restore(flags,base.heightSource())}
        }
        return commands.toList()
    }
    private fun materialSnapshot(b:TileSnapshot)=TileSnapshot(b.southWestHeight(),b.southEastHeight(),b.northEastHeight(),b.northWestHeight(),
        if(state.applyUnderlay())state.underlayId() else b.underlayId(),if(state.applyOverlay())state.overlayId() else b.overlayId(),
        if(state.applyShape())state.shape() else b.overlayShape(),if(state.applyRotation())state.rotation() else b.overlayRotation(),b.flags(),b.objects(),b.heightSource())
    private fun flagsSnapshot(b:TileSnapshot)=TileSnapshot(b.southWestHeight(),b.southEastHeight(),b.northEastHeight(),b.northWestHeight(),
        b.underlayId(),b.overlayId(),b.overlayShape(),b.overlayRotation(),if(state.applyFlags())state.flags() else b.flags(),b.objects(),b.heightSource())
    private fun sameMaterial(a:TileSnapshot,b:TileSnapshot)=a.underlayId()==b.underlayId()&&a.overlayId()==b.overlayId()&&a.overlayShape()==b.overlayShape()&&a.overlayRotation()==b.overlayRotation()
    private fun clearStroke(){visited.clear();targetLocals.clear();lastMask=null;lastCenter=null}
}
