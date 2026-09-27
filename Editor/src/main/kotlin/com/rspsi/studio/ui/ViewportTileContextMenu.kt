package com.rspsi.studio.ui

import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.CompositeEditCommand
import com.rspsi.editor.DeleteObjectCommand
import com.rspsi.editor.EditorSession
import com.rspsi.editor.RotateObjectCommand
import com.rspsi.editor.SetTileFlagsCommand
import com.rspsi.editor.brush.BrushCapability
import com.rspsi.editor.model.TileCoordinate
import com.rspsi.editor.model.TileSnapshot
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import com.rspsi.editor.terrain.TerrainHeightEdit
import com.rspsi.editor.tool.CompositeTilePainterTool
import com.rspsi.studio.NativeSceneViewport
import com.rspsi.studio.brush.StudioBrushManager
import com.rspsi.studio.theme.StudioPalette
import com.rspsi.studio.ui.panels.TilePainterPalette
import imgui.ImGui
import imgui.flag.ImGuiMouseButton

/**
 * The right-click menu on a viewport tile: selection, the tile's objects, painter and
 * height shortcuts, and walk/bridge flags.
 *
 * Every action is an undoable command on the session. Height changes go through
 * [TerrainHeightEdit], the same path the height tools use, so shared corners stay
 * consistent with neighbouring tiles.
 */
class ViewportTileContextMenu(
    private val objectEditor: ObjectEditorWindow,
    private val brushes: StudioBrushManager,
) {
    private var tile: WorldTile? = null

    /**
     * Opens the menu on a right-click release that was not a camera drag, then draws it
     * while open. Call inside the viewport window.
     */
    fun render(cache: LoadedOsrsCacheSession?, viewport: NativeSceneViewport, session: EditorSession?) {
        if (ImGui.isWindowHovered() && ImGui.isMouseReleased(ImGuiMouseButton.Right) &&
            !ImGui.isMouseDragging(ImGuiMouseButton.Right, DRAG_THRESHOLD)
        ) {
            val localX = ImGui.getIO().mousePosX - viewport.imageOriginX()
            val localY = ImGui.getIO().mousePosY - viewport.imageOriginY()
            viewport.pickAt(localX, localY).ifPresent { pick ->
                tile = pick.tile()
                ImGui.openPopup(POPUP_ID)
            }
        }
        if (ImGui.beginPopup(POPUP_ID)) {
            renderItems(cache, session)
            ImGui.endPopup()
        }
    }

    private fun renderItems(cache: LoadedOsrsCacheSession?, session: EditorSession?) {
        val picked = tile ?: return
        if (session == null) {
            ImGui.textDisabled("No active session")
            return
        }
        ImGui.textColored(StudioPalette.u32(StudioPalette.INFO), "Tile (${picked.x}, ${picked.y}, Pl ${picked.plane})")
        ImGui.separator()
        val local = session.coordinates().toLocal(picked).orElse(null)
        if (local == null) {
            ImGui.textDisabled("Tile is outside the active document")
            return
        }
        val coordinate = local.coordinate()

        if (ImGui.menuItem("Select Tile")) {
            session.selection().clear()
            session.selection().select(coordinate)
        }
        if (ImGui.menuItem("Add to Selection")) session.selection().select(coordinate)
        if (ImGui.menuItem("Clear Selection")) session.selection().clear()

        val snapshot = session.world().tile(local)?.snapshot()
        if (snapshot != null && snapshot.objects().isNotEmpty()) {
            ImGui.separator()
            ImGui.textDisabled("Objects (${snapshot.objects().size}):")
            snapshot.objects().forEach { renderObjectMenu(it, cache, session) }
        }

        ImGui.separator()
        ImGui.textDisabled("Painter:")
        val palette = TilePainterPalette.INSTANCE
        if (ImGui.menuItem("Sample Tile (Eyedropper)") && palette != null) palette.sampleTile(session, coordinate)
        if (ImGui.menuItem("Paint Tile with Active Brush") && palette != null) {
            val tool = CompositeTilePainterTool()
            brushes.activeBrush(TILE_PAINTER, setOf(BrushCapability.SPATIAL_FOOTPRINT))?.let { tool.setBrush(it) }
            tool.bindState(palette.state())
            tool.applyToCoordinates(setOf(coordinate), session)
        }

        if (snapshot == null) return
        ImGui.separator()
        ImGui.textDisabled("Height:")
        if (ImGui.menuItem("Flatten Tile")) {
            val average = (snapshot.southWestHeight() + snapshot.southEastHeight() +
                snapshot.northEastHeight() + snapshot.northWestHeight()) / 4
            applyHeight(session, "Flatten terrain") { it.setTile(coordinate, average) }
        }
        if (ImGui.menuItem("Raise (+$QUICK_HEIGHT_STEP)")) {
            applyHeight(session, "Raise terrain") { it.raiseTile(coordinate, QUICK_HEIGHT_STEP) }
        }
        if (ImGui.menuItem("Lower (-$QUICK_HEIGHT_STEP)")) {
            applyHeight(session, "Lower terrain") { it.raiseTile(coordinate, -QUICK_HEIGHT_STEP) }
        }

        ImGui.separator()
        ImGui.textDisabled("Flags (0x${Integer.toHexString(snapshot.flags())}):")
        flagItem(session, coordinate, snapshot, FLAG_BLOCKED, "Blocked Walk (0x01)", "Toggle blocked flag")
        flagItem(session, coordinate, snapshot, FLAG_BRIDGE, "Bridge Tile (0x02)", "Toggle bridge flag")
    }

    private fun renderObjectMenu(placed: WorldObject, cache: LoadedOsrsCacheSession?, session: EditorSession) {
        val name = cache?.bundle()?.definitions()?.`object`(placed.id)?.map { it.displayName() }?.orElse(null)
            ?: "Object #${placed.id}"
        if (!ImGui.beginMenu("$name (Type ${placed.type}, Rot ${placed.rotation})##ctx-obj-${placed.id}")) return
        if (ImGui.menuItem("Edit object...")) objectEditor.open(placed)
        if (ImGui.menuItem("Rotate +90° (Clockwise)")) {
            session.execute(RotateObjectCommand(placed, (placed.rotation + 1) % 4, "Rotate $name"))
        }
        if (ImGui.menuItem("Rotate -90° (Counter-CW)")) {
            session.execute(RotateObjectCommand(placed, (placed.rotation + 3) % 4, "Rotate $name"))
        }
        if (ImGui.menuItem("Delete Object")) session.execute(DeleteObjectCommand(placed, "Delete $name"))
        ImGui.endMenu()
    }

    private fun flagItem(
        session: EditorSession,
        coordinate: TileCoordinate,
        snapshot: TileSnapshot,
        flag: Int,
        label: String,
        description: String,
    ) {
        val set = (snapshot.flags() and flag) != 0
        if (ImGui.menuItem((if (set) "[x] " else "[ ] ") + label)) {
            session.execute(SetTileFlagsCommand(coordinate, snapshot, snapshot.withFlags(snapshot.flags() xor flag), description))
        }
    }

    /** Runs one [TerrainHeightEdit] as a single undo step. */
    private inline fun applyHeight(session: EditorSession, description: String, change: (TerrainHeightEdit) -> Unit) {
        val edit = TerrainHeightEdit(session.world())
        change(edit)
        val commands = edit.commands { "$description at $it" }
        if (commands.isNotEmpty()) session.execute(CompositeEditCommand(description, commands))
    }

    private companion object {
        const val POPUP_ID = "viewport_tile_context"
        const val TILE_PAINTER = "terrain.tile-painter"
        const val QUICK_HEIGHT_STEP = 32
        const val FLAG_BLOCKED = 0x01
        const val FLAG_BRIDGE = 0x02

        /** Pixels a right-drag may move and still count as a click (camera orbit guard). */
        const val DRAG_THRESHOLD = 3.0f
    }
}
