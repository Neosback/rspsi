package com.rspsi.editor.tool

import com.rspsi.editor.CompositeEditCommand
import com.rspsi.editor.EditorCommand
import com.rspsi.editor.SetTerrainHeightCommand
import com.rspsi.editor.SetTileMaterialCommand
import com.rspsi.editor.input.PointerButton
import com.rspsi.editor.input.PointerEvent
import com.rspsi.editor.model.LocalTile
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.TileSnapshot
import com.rspsi.editor.model.WorldDocument
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.render.OverlayDraw
import com.rspsi.editor.terrain.TerrainVertexLattice
import com.rspsi.editor.tool.spline.SplineBrushStyle
import com.rspsi.editor.tool.spline.SplinePath
import java.util.LinkedHashSet
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Interactive spline path authoring tool with atomic undoable builds. */
class SplinePathTool : EditorTool {
    private val path = SplinePath()
    private var style = SplineBrushStyle.SMOOTH
    private var overlayId = 1
    private var underlayId = 0
    private var paintUnderlay = false
    private var dragPointIndex = -1
    private var selectedPointIndex = -1
    private var hoveredTile: WorldTile? = null
    private var context: ToolContext? = null

    fun path() = path
    fun style() = style
    fun setStyle(style: SplineBrushStyle) { this.style = style }
    fun width() = path.widthTiles()
    fun setWidth(width: Int) = path.setWidthTiles(width)
    fun overlayId() = overlayId
    fun setOverlayId(value: Int) { overlayId = value.coerceAtLeast(0) }
    fun underlayId() = underlayId
    fun setUnderlayId(value: Int) { underlayId = value.coerceAtLeast(0) }
    fun paintUnderlay() = paintUnderlay
    fun setPaintUnderlay(value: Boolean) { paintUnderlay = value }
    fun selectedPointIndex() = selectedPointIndex
    fun hoveredTile() = hoveredTile
    override fun id() = ID

    override fun activate(context: ToolContext) { this.context = context }
    override fun deactivate() { dragPointIndex = -1; hoveredTile = null }

    override fun pointerMove(event: PointerEvent) {
        hoveredTile = context?.worldTileAt(event.x(), event.y())?.orElse(null)
    }

    override fun pointerDown(event: PointerEvent) {
        val ctx = context ?: return
        val tile = ctx.worldTileAt(event.x(), event.y()).orElse(null) ?: return
        hoveredTile = tile
        val near = path.findPointNear(tile.x(), tile.y(), tile.plane(), 1)
        if (event.button() == PointerButton.SECONDARY || event.button() == PointerButton.PRIMARY && event.alt()) {
            if (near >= 0) {
                path.removePoint(near)
                selectedPointIndex = minOf(selectedPointIndex, path.size() - 1)
                dragPointIndex = -1
            }
            return
        }
        if (event.button() != PointerButton.PRIMARY) return
        if (near >= 0 && !event.shift()) {
            selectedPointIndex = near
            dragPointIndex = near
        } else {
            path.addPoint(tile.x(), tile.y(), tile.plane())
            selectedPointIndex = path.size() - 1
            dragPointIndex = selectedPointIndex
        }
    }

    override fun pointerDrag(event: PointerEvent) {
        val ctx = context ?: return
        if (dragPointIndex !in 0 until path.size()) return
        val tile = ctx.worldTileAt(event.x(), event.y()).orElse(null) ?: return
        hoveredTile = tile
        path.movePoint(dragPointIndex, tile.x(), tile.y())
    }

    override fun pointerUp(event: PointerEvent) { dragPointIndex = -1 }

    private fun sampleTileHeight(plane: Int, x: Int, y: Int): Float {
        val ctx = context ?: return 0f
        val local = ctx.local(WorldTile(plane, x, y)).orElse(null) ?: return 0f
        if (!ctx.session().world().contains(local)) return 0f
        val s = ctx.session().world().tile(local.coordinate()).snapshot()
        return (s.southWestHeight() + s.southEastHeight() + s.northEastHeight() + s.northWestHeight()) / 4f
    }

    override fun renderOverlay(draw: OverlayDraw) {
        hoveredTile?.let { h ->
            draw.tileOutline(h, 0x38BDF8EE)
            draw.tileFilled(h, 0x38BDF822)
            if (path.isEmpty) {
                val x = h.x() * 128f + 64f
                val z = h.y() * 128f + 64f
                val y = sampleTileHeight(h.plane(), h.x(), h.y()) - 10f
                draw.circle(x, y, z, 36f, 0x34D399FF, 2.5f)
                draw.worldLabel("Click to place Start (P1)", x, y - 20f, z, -1, 0x059669EE)
            }
        }
        if (path.isEmpty) return
        val points = path.points()
        points.forEachIndexed { i, p ->
            val x = p.x * 128f + 64f
            val z = p.y * 128f + 64f
            val y = sampleTileHeight(p.plane, p.x, p.y) - 6f
            val selected = i == selectedPointIndex
            draw.circle(x, y, z, 40f, if (selected) 0xFBBF24FF else 0x38BDF8FF, if (selected) 3.5f else 2.5f)
            draw.worldLabel("P" + (i + 1), x, y, z, -1, if (selected) 0xD97706EE else 0x0284C7EE)
        }
        val hovered = hoveredTile
        if (hovered != null && points.isNotEmpty() && dragPointIndex < 0) {
            val last = points.last()
            if (last.plane == hovered.plane() && (last.x != hovered.x() || last.y != hovered.y())) {
                val x1 = last.x * 128f + 64f; val z1 = last.y * 128f + 64f
                val y1 = sampleTileHeight(last.plane, last.x, last.y) - 6f
                val x2 = hovered.x() * 128f + 64f; val z2 = hovered.y() * 128f + 64f
                val y2 = sampleTileHeight(hovered.plane(), hovered.x(), hovered.y()) - 6f
                draw.line(x1, y1, z1, x2, y2, z2, 0x38BDF8CC, 2.5f)
                draw.circle(x2, y2, z2, 28f, 0x38BDF8AA, 2f)
                draw.worldLabel("P" + (points.size + 1), x2, y2, z2, 0xFFE2E8F0, 0x0F172ACC)
            }
        }
        val plane = points[0].plane
        if (points.size == 1 && hovered != null && dragPointIndex < 0 &&
            (points[0].x != hovered.x() || points[0].y != hovered.y())) {
            val preview = SplinePath()
            preview.setWidthTiles(path.widthTiles())
            preview.addPoint(points[0].x, points[0].y, plane)
            preview.addPoint(hovered.x(), hovered.y(), plane)
            drawFootprint(draw, preview.rasterize(0.4f), plane, 0x34D39944, 0x34D399AA, 0x38BDF844, 0x38BDF888)
            return
        }
        if (points.size < 2) return
        val curve = path.evaluate(0.35f)
        for (i in 0 until curve.size / 2 - 1) {
            val x1 = curve[i * 2] * 128f + 64f; val z1 = curve[i * 2 + 1] * 128f + 64f
            val y1 = sampleTileHeight(plane, curve[i * 2].roundToInt(), curve[i * 2 + 1].roundToInt()) - 4f
            val x2 = curve[(i + 1) * 2] * 128f + 64f; val z2 = curve[(i + 1) * 2 + 1] * 128f + 64f
            val y2 = sampleTileHeight(plane, curve[(i + 1) * 2].roundToInt(), curve[(i + 1) * 2 + 1].roundToInt()) - 4f
            draw.line(x1, y1, z1, x2, y2, z2, 0xFBBF24FF, 3.5f)
        }
        drawFootprint(draw, path.rasterize(0.4f), plane, 0x34D39955, 0x34D399CC, 0x38BDF855, 0x38BDF8AA)
    }

    private fun drawFootprint(draw: OverlayDraw, footprint: Set<Long>, plane: Int,
        shapedFill: Int, shapedOutline: Int, fill: Int, outline: Int) {
        for (packed in footprint) {
            val x = SplinePath.unpackX(packed); val y = SplinePath.unpackY(packed)
            val shaped = style.shape(SplinePath.neighbourMask(footprint, x, y)) > 0
            val tile = WorldTile(plane, x, y)
            draw.tileFilled(tile, if (shaped) shapedFill else fill)
            draw.tileOutline(tile, if (shaped) shapedOutline else outline)
        }
    }

    fun buildPath(): Boolean {
        val ctx = context ?: return false
        if (path.size() < 2) return false
        val footprint = path.rasterize(0.4f)
        if (footprint.isEmpty()) return false
        val plane = path.points()[0].plane
        val world = ctx.session().world()
        val predicted = world.copy()
        val commands = mutableListOf<EditorCommand>()
        if (style == SplineBrushStyle.RAMP) buildRamp(ctx, world, predicted, footprint, plane, commands)

        for (packed in footprint) {
            val wx = SplinePath.unpackX(packed); val wy = SplinePath.unpackY(packed)
            val local = ctx.local(WorldTile(plane, wx, wy)).orElse(null) ?: continue
            val coord = local.coordinate()
            val mask = SplinePath.neighbourMask(footprint, wx, wy)
            val base = predicted.tile(coord).snapshot()
            val after = TileSnapshot(
                base.southWestHeight(), base.southEastHeight(), base.northEastHeight(), base.northWestHeight(),
                if (paintUnderlay) underlayId else base.underlayId(),
                if (overlayId > 0) overlayId else base.overlayId(),
                style.shape(mask), style.rotation(mask), base.flags(), base.objects(), base.heightSource())
            if (!sameMaterial(base, after)) {
                commands.add(SetTileMaterialCommand(coord, base, after, "Paint path material at " + coord))
                predicted.tile(coord).restore(after, base.heightSource())
            }
        }
        if (commands.isEmpty()) return false
        ctx.session().execute(CompositeEditCommand(
            "Build Spline Path (" + style.displayName() + ", " + commands.size + " tiles)", commands))
        clear()
        return true
    }

    private fun buildRamp(ctx: ToolContext, world: WorldDocument, predicted: WorldDocument,
        footprint: Set<Long>, plane: Int, commands: MutableList<EditorCommand>) {
        val start = path.points().first(); val end = path.points().last()
        val startHeight = averageHeight(ctx, world, WorldTile(plane, start.x, start.y), 0)
        val endHeight = averageHeight(ctx, world, WorldTile(plane, end.x, end.y), startHeight + 128)
        val lattice = TerrainVertexLattice(predicted)
        val changed = LinkedHashSet<TileCoordinate>()
        val total = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat().coerceAtLeast(1f)
        for (packed in footprint) {
            val wx = SplinePath.unpackX(packed); val wy = SplinePath.unpackY(packed)
            val local = ctx.local(WorldTile(plane, wx, wy)).orElse(null) ?: continue
            val distance = hypot((wx - start.x).toDouble(), (wy - start.y).toDouble()).toFloat()
            val progress = (distance / total).coerceIn(0f, 1f)
            val target = (startHeight + (endHeight - startHeight) * progress).roundToInt()
            changed.addAll(lattice.setHeight(local.plane(), local.x(), local.y(), target))
            changed.addAll(lattice.setHeight(local.plane(), local.x() + 1, local.y(), target))
            changed.addAll(lattice.setHeight(local.plane(), local.x() + 1, local.y() + 1, target))
            changed.addAll(lattice.setHeight(local.plane(), local.x(), local.y() + 1, target))
        }
        for (coord in changed) {
            val before = world.tile(coord).snapshot(); val after = predicted.tile(coord).snapshot()
            if (before != after) commands.add(SetTerrainHeightCommand(
                coord, before, after, before.heightSource(), after.heightSource(), "Ramp path height at " + coord))
        }
    }

    private fun averageHeight(ctx: ToolContext, world: WorldDocument, tile: WorldTile, fallback: Int): Int {
        val local: LocalTile = ctx.local(tile).orElse(null) ?: return fallback
        val s = world.tile(local.coordinate()).snapshot()
        return (s.southWestHeight() + s.southEastHeight() + s.northEastHeight() + s.northWestHeight()) / 4
    }

    fun clear() { path.clear(); dragPointIndex = -1; selectedPointIndex = -1 }

    private fun sameMaterial(a: TileSnapshot, b: TileSnapshot) =
        a.underlayId() == b.underlayId() && a.overlayId() == b.overlayId() &&
            a.overlayShape() == b.overlayShape() && a.overlayRotation() == b.overlayRotation()

    companion object { const val ID = "path.spline" }
}
