package com.rspsi.studio.ui

import com.rspsi.cache.map.MapIndexEntry
import com.rspsi.cache.workspace.LoadedOsrsCacheSession
import com.rspsi.editor.model.WorldLocation
import com.rspsi.studio.WorkspaceManager
import com.rspsi.studio.theme.StudioDrawColors
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.theme.StudioPalette
import com.rspsi.studio.theme.StudioWidgets
import imgui.ImColor
import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive 2D OSRS world map and region navigator, rendered as a full
 * workspace tab rather than a floating window.
 *
 * The world map is a navigation surface, so per UI_WORKSPACE_CONTRACT it lives
 * in the workspace tab strip: the user should not have to hunt for it behind
 * a modal window, and closing it should be a tab close rather than an X.
 *
 * Two things this view is careful about:
 *
 * - **The region index is cached.** [com.rspsi.cache.map.MapIndexTable.entries]
 *   allocates and sorts a fresh list per call, and discovery can probe tens of
 *   thousands of archive names. Calling it every frame made the map appear to
 *   hang, so the snapshot is rebuilt only when the cache identity changes.
 * - **Drawing happens after the item rect exists.** ImGui draw commands must be
 *   submitted against a draw list whose clip rect is already established, and
 *   the interactive item must be registered before we test hover/active.
 * - **Terrain is a cache-wide raster, not per-region work.** The map index only
 *   says which regions exist; [WorldMapOverview] bakes the actual colours and
 *   the view blits that one texture between the availability grid and the
 *   region outlines.
 */
class WorldMapView {

    /** Eased zoom in screen pixels per region; see [SmoothZoom]. */
    private val zoom = SmoothZoom(DEFAULT_SCALE, MIN_SCALE, MAX_SCALE)
    private val scale: Double get() = zoom.current

    /** Canvas-relative point (0..1) that stays fixed while an eased zoom settles. */
    private var zoomAnchorX = 0.5
    private var zoomAnchorY = 0.5
    private var centreRegionX: Double = 50.5
    private var centreRegionY: Double = 50.5
    private var selectedRegionX: Int = 50
    private var selectedRegionY: Int = 50
    private var activePlane: Int = 0
    private var showGrid: Boolean = true
    private var showLabels: Boolean = true

    private val jumpInput = ImString(64)

    /** Cache-wide terrain raster; see the class note on baking. */
    private val overview = WorldMapOverview()

    /** Sharp per-region tiles for close zoom, bounded by an LRU. */
    private val detail = WorldMapDetailTiles()

    /** The cache session of the current frame, for detail tile requests. */
    private var session: LoadedOsrsCacheSession? = null

    /** Cached snapshot of the cache's map index; see the class note on caching. */
    private var cachedRegions: List<MapIndexEntry> = emptyList()
    private var cachedRegionKeys: Set<Int> = emptySet()
    private var cachedFor: String? = null

    private var regionCount: Int = 0
    private var indexLoadFailed: Boolean = false

    /** Focuses the map on the region the editor is currently on. */
    fun focusRegion(regionX: Int, regionY: Int) {
        if (regionX !in 0..255 || regionY !in 0..255) return
        centreRegionX = regionX + 0.5
        centreRegionY = regionY + 0.5
        selectedRegionX = regionX
        selectedRegionY = regionY
    }

    fun focusCurrentTile(tileX: Int, tileY: Int) = focusRegion(tileX shr 6, tileY shr 6)

    /** Drops the cached index so the next frame re-reads it from the session. */
    fun invalidate() {
        overview.reset()
        detail.clear()
        cachedFor = null
        cachedRegions = emptyList()
        cachedRegionKeys = emptySet()
        regionCount = 0
        indexLoadFailed = false
    }

    fun render(
        cache: LoadedOsrsCacheSession?,
        currentRegionX: Int,
        currentRegionY: Int,
        currentPlane: Int,
        navigator: Consumer<WorldLocation>,
        workspaces: WorkspaceManager?,
        openDashboard: Runnable?,
        openMapEditor: Runnable?,
        openInterfaceStudio: Runnable?,
        openObjectStudio: Runnable?,
        closeWorkspace: Consumer<WorkspaceManager.Workspace>?,
    ) {
        val mainViewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(mainViewport.getPosX(), mainViewport.getPosY())
        ImGui.setNextWindowSize(mainViewport.getSizeX(), mainViewport.getSizeY())
        val flags = ImGuiWindowFlags.NoDecoration or
            ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoSavedSettings or
            ImGuiWindowFlags.NoBringToFrontOnFocus or
            ImGuiWindowFlags.MenuBar

        if (!ImGui.begin("World Map Workspace", flags)) {
            ImGui.end()
            return
        }

        if (ImGui.beginMenuBar()) {
            if (workspaces != null) {
                StudioWidgets.workspaceTabs(
                    workspaces, openDashboard, openMapEditor,
                    openInterfaceStudio, openObjectStudio,
                    this::openWorldMapWorkspace, closeWorkspace,
                )
            }
            ImGui.endMenuBar()
        }

        session = cache
        ensureIndex(cache)
        overview.pumpUpload()
        detail.pumpUpload()
        requestOverview(cache)

        renderToolbar(currentRegionX, currentRegionY, navigator)
        ImGui.separator()
        renderCanvas(currentRegionX, currentRegionY, currentPlane, navigator)

        ImGui.end()
    }

    /** Re-reads the cached region index when the session identity changes. */
    private fun ensureIndex(cache: LoadedOsrsCacheSession?) {
        val identity = try {
            cache?.identity()?.toString() ?: "no-cache"
        } catch (_: RuntimeException) {
            "unreadable-cache"
        }
        if (identity == cachedFor) return

        cachedFor = identity
        try {
            val index = cache?.bundle()?.project()?.maps()?.index()
            val entries: List<MapIndexEntry> = index?.entries() ?: emptyList()
            cachedRegions = entries
            cachedRegionKeys = entries.mapTo(HashSet(entries.size.coerceAtLeast(16))) {
                (it.regionX() shl 8) or it.regionY()
            }
            regionCount = entries.size
            indexLoadFailed = false
        } catch (_: RuntimeException) {
            cachedRegions = emptyList()
            cachedRegionKeys = emptySet()
            regionCount = 0
            indexLoadFailed = true
        }
    }

    /**
     * Asks the overview raster to bake this cache, centred on wherever the user
     * is looking so the first painted pixels are the ones on screen.
     */
    private fun requestOverview(cache: LoadedOsrsCacheSession?) {
        if (cache == null || cachedRegions.isEmpty()) return
        val project = try {
            cache.bundle().project()
        } catch (_: RuntimeException) {
            return
        }
        val store = try {
            cache.store()
        } catch (_: RuntimeException) {
            null
        }
        overview.request(
            cachedFor ?: return,
            store,
            cachedRegions,
            project.maps(),
            project.definitions(),
            centreRegionX.toInt().coerceIn(0, 255),
            centreRegionY.toInt().coerceIn(0, 255),
        )
    }

    /**
     * Blits the terrain raster over the availability grid.
     *
     * The raster spans the whole region bounding box at
     * [WorldMapOverview.tilesPerPixel] world tiles per texel. Texels the bake
     * has not reached stay transparent, so regions still baking keep reading as
     * present-but-empty through the grid underneath.
     */
    private fun drawTerrain(
        dl: ImDrawList,
        canvasMinX: Float,
        canvasMaxY: Float,
        originRx: Double,
        originRy: Double,
    ) {
        val textureId = overview.textureId()
        val rasterWidth = overview.rasterWidth()
        val rasterHeight = overview.rasterHeight()
        if (textureId == 0 || rasterWidth <= 0 || rasterHeight <= 0) return

        val step = overview.tilesPerPixel()
        val minTileX = overview.originRegionX() * REGION_TILES
        val minTileY = overview.originRegionY() * REGION_TILES
        val maxTileX = minTileX + rasterWidth * step
        val maxTileY = minTileY + rasterHeight * step

        val left = canvasMinX + ((minTileX / REGION_TILES - originRx) * scale).toFloat()
        val right = canvasMinX + ((maxTileX / REGION_TILES - originRx) * scale).toFloat()
        val top = canvasMaxY - ((maxTileY / REGION_TILES - originRy) * scale).toFloat()
        val bottom = canvasMaxY - ((minTileY / REGION_TILES - originRy) * scale).toFloat()
        if (right <= left || bottom <= top) return
        dl.addImage(textureId.toLong(), left, top, right, bottom, 0f, 0f, 1f, 1f)
    }

    private fun openWorldMapWorkspace() {
        // Already the active workspace; re-focusing keeps the tab press meaningful.
    }

    private fun renderToolbar(
        currentRegionX: Int,
        currentRegionY: Int,
        navigator: Consumer<WorldLocation>,
    ) {
        val areas = overview.areas
        if (areas.isNotEmpty()) {
            val currentArea = overview.currentArea ?: areas.first()
            ImGui.alignTextToFramePadding()
            ImGui.text("Area:")
            ImGui.sameLine()
            ImGui.setNextItemWidth(180.0f)
            if (ImGui.beginCombo("##worldmap_area_combo", currentArea.displayName)) {
                for (area in areas) {
                    val isSelected = area.id == currentArea.id
                    if (ImGui.selectable("${area.displayName}##area_${area.id}", isSelected)) {
                        overview.switchArea(area)
                        focusRegion(area.originRegionX, area.originRegionY)
                    }
                    if (isSelected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
                ImGui.endCombo()
            }
            ImGui.sameLine()
            ImGui.textDisabled("|")
            ImGui.sameLine()
        }

        ImGui.alignTextToFramePadding()
        ImGui.text("Jump:")
        ImGui.sameLine()

        ImGui.setNextItemWidth(170.0f)
        val jumpEnter = ImGui.inputTextWithHint(
            "##jump_input",
            "Region ID, rx,ry, x,y",
            jumpInput,
            ImGuiInputTextFlags.EnterReturnsTrue,
        )
        ImGui.sameLine()
        val jumpClicked = ImGui.button("Go##jump_go")
        if (jumpEnter || jumpClicked) {
            handleJump(jumpInput.get().trim())
        }

        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()

        if (ImGui.button("${StudioIcons.ZOOM_OUT}##zoom_out")) zoomBy(0.75, 0.5, 0.5)
        if (ImGui.isItemHovered()) ImGui.setTooltip("Zoom out")
        ImGui.sameLine()
        if (ImGui.button("${StudioIcons.ZOOM_IN}##zoom_in")) zoomBy(1.33, 0.5, 0.5)
        if (ImGui.isItemHovered()) ImGui.setTooltip("Zoom in")
        ImGui.sameLine()
        if (ImGui.button("${StudioIcons.FULLSCREEN}  Fit##fit_world")) fitAllRegions()
        if (ImGui.isItemHovered()) ImGui.setTooltip("Fit all cache regions in view")
        ImGui.sameLine()
        if (ImGui.button("${StudioIcons.CENTER_FOCUS}  Current##center_current")) {
            focusRegion(currentRegionX, currentRegionY)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Centre on the edited region ($currentRegionX, $currentRegionY)")
        }

        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()

        val grid = ImBoolean(showGrid)
        if (ImGui.checkbox("Grid##show_grid", grid)) showGrid = grid.get()
        ImGui.sameLine()
        val labels = ImBoolean(showLabels)
        if (ImGui.checkbox("Labels##show_labels", labels)) showLabels = labels.get()

        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()

        val canOpen = selectedRegionX in 0..255 && selectedRegionY in 0..255
        if (!canOpen) ImGui.beginDisabled()
        val label = if (canOpen) {
            "${StudioIcons.NAVIGATION}  Open Region $selectedRegionX,$selectedRegionY in Map Studio##open_editor"
        } else {
            "${StudioIcons.NAVIGATION}  Open Region##open_editor"
        }
        if (StudioWidgets.buttonPrimary(label, 260.0f, 0.0f)) {
            navigator.accept(tileLocation(selectedRegionX, selectedRegionY))
        }
        if (!canOpen) ImGui.endDisabled()

        if (overview.building()) {
            ImGui.sameLine()
            val total = max(1, overview.regionTotal())
            ImGui.setNextItemWidth(150.0f)
            ImGui.progressBar(overview.completed().toFloat() / total, 150.0f, 0.0f)
            if (ImGui.isItemHovered()) ImGui.setTooltip(overview.statusText())
        }
    }

    private fun renderCanvas(
        currentRegionX: Int,
        currentRegionY: Int,
        currentPlane: Int,
        navigator: Consumer<WorldLocation>,
    ) {
        val availW = max(100.0f, ImGui.getContentRegionAvailX())
        val availH = max(100.0f, ImGui.getContentRegionAvailY() - 30.0f)

        val canvasMinX = ImGui.getCursorScreenPosX()
        val canvasMinY = ImGui.getCursorScreenPosY()
        val canvasMaxX = canvasMinX + availW
        val canvasMaxY = canvasMinY + availH

        // Register the interactive item first so hover/active are meaningful, and so the
        // window's clip rect is in place before any drawing is queued.
        ImGui.invisibleButton("##worldmap_canvas", availW, availH)
        val isHovered = ImGui.isItemHovered()
        val isActive = ImGui.isItemActive()

        if (isActive) {
            val middle = ImGui.isMouseDown(ImGuiMouseButton.Middle)
            val button = if (middle) ImGuiMouseButton.Middle else ImGuiMouseButton.Left
            if (ImGui.isMouseDragging(button)) {
                val dx = ImGui.getMouseDragDeltaX(button)
                val dy = ImGui.getMouseDragDeltaY(button)
                ImGui.resetMouseDragDelta(button)
                // Screen Y grows downward, world region Y grows upward.
                centreRegionX -= dx / scale
                centreRegionY += dy / scale
                clampCenter()
            }
        }

        if (isHovered) {
            val wheel = ImGui.getIO().mouseWheel
            if (wheel != 0.0f) {
                zoomAnchorX = (ImGui.getMousePosX() - canvasMinX).toDouble() / availW
                zoomAnchorY = (ImGui.getMousePosY() - canvasMinY).toDouble() / availH
                zoom.wheel(wheel)
            }
        }
        advanceZoom(availW.toDouble(), availH.toDouble())

        val halfWReg = (availW / scale) / 2.0
        val halfHReg = (availH / scale) / 2.0
        val originRx = centreRegionX - halfWReg
        val originRy = centreRegionY - halfHReg

        val minVisibleRx = max(0, floor(originRx).toInt())
        val maxVisibleRx = min(255, floor(originRx + halfWReg * 2.0).toInt())
        val minVisibleRy = max(0, floor(originRy).toInt())
        val maxVisibleRy = min(255, floor(originRy + halfHReg * 2.0).toInt())

        var hoverRx = -1
        var hoverRy = -1
        if (isHovered) {
            val mx = ImGui.getMousePosX() - canvasMinX
            val my = canvasMaxY - ImGui.getMousePosY()
            hoverRx = floor(originRx + mx / scale).toInt()
            hoverRy = floor(originRy + my / scale).toInt()
        }

        val dl: ImDrawList = ImGui.getWindowDrawList()
        dl.pushClipRect(canvasMinX, canvasMinY, canvasMaxX, canvasMaxY, true)
        dl.addRectFilled(canvasMinX, canvasMinY, canvasMaxX, canvasMaxY,
            StudioDrawColors.abgr(0xFF0B0E14.toInt()))

        val gridCol = ImColor.rgba(28, 37, 51, 140)
        val regionFill = ImColor.rgba(30, 41, 59, 230)

        // Availability goes down first: the terrain raster covers the regions it
        // has baked and leaves the rest of these fills showing through.
        for (rx in minVisibleRx..maxVisibleRx) {
            for (ry in minVisibleRy..maxVisibleRy) {
                if (!cachedRegionKeys.contains((rx shl 8) or ry)) continue
                val x1 = canvasMinX + ((rx - originRx) * scale).toFloat()
                val y2 = canvasMaxY - ((ry - originRy) * scale).toFloat()
                dl.addRectFilled(x1, y2 - scale.toFloat(), x1 + scale.toFloat(), y2, regionFill)
            }
        }

        drawTerrain(dl, canvasMinX, canvasMaxY, originRx, originRy)

        if (scale >= 24.0 && overview.isAuthentic()) {
            for (rx in minVisibleRx..maxVisibleRx) {
                for (ry in minVisibleRy..maxVisibleRy) {
                    val texId = overview.getGroundTexture(rx, ry)
                    if (texId != 0) {
                        val x1 = canvasMinX + ((rx - originRx) * scale).toFloat()
                        val y2 = canvasMaxY - ((ry - originRy) * scale).toFloat()
                        val x2 = x1 + scale.toFloat()
                        val y1 = y2 - scale.toFloat()
                        dl.addImage(texId.toLong(), x1, y1, x2, y2, 0f, 0f, 1f, 1f)
                    }
                }
            }
        }

        drawDetailTiles(dl, canvasMinX, canvasMaxY, originRx, originRy, currentPlane,
            minVisibleRx..maxVisibleRx, minVisibleRy..maxVisibleRy)

        if (showGrid || showLabels) {
            val regionBorder = ImColor.rgba(51, 65, 85, 200)
            for (rx in minVisibleRx..maxVisibleRx) {
                for (ry in minVisibleRy..maxVisibleRy) {
                    val x1 = canvasMinX + ((rx - originRx) * scale).toFloat()
                    val y2 = canvasMaxY - ((ry - originRy) * scale).toFloat()
                    val x2 = x1 + scale.toFloat()
                    val y1 = y2 - scale.toFloat()

                    if (cachedRegionKeys.contains((rx shl 8) or ry)) {
                        if (showGrid && scale >= 4.0) dl.addRect(x1, y1, x2, y2, regionBorder)
                        if (showLabels && scale >= 22.0) {
                            val label = "$rx,$ry"
                            val size = ImGui.calcTextSize(label)
                            if (size.x < scale) {
                                dl.addText(
                                    x1 + (scale.toFloat() - size.x) * 0.5f,
                                    y1 + (scale.toFloat() - size.y) * 0.5f,
                                    ImColor.rgba(148, 163, 184, 180),
                                    label,
                                )
                            }
                        }
                    } else if (showGrid && scale >= 10.0) {
                        dl.addRect(x1, y1, x2, y2, gridCol)
                    }
                }
            }
        }

        if (currentRegionX in minVisibleRx..maxVisibleRx && currentRegionY in minVisibleRy..maxVisibleRy) {
            val x1 = canvasMinX + ((currentRegionX - originRx) * scale).toFloat()
            val y2 = canvasMaxY - ((currentRegionY - originRy) * scale).toFloat()
            val x2 = x1 + scale.toFloat()
            val y1 = y2 - scale.toFloat()
            val currentCol = ImColor.rgba(0, 229, 255, 255)
            dl.addRect(x1, y1, x2, y2, currentCol, 0.0f, 0, 2.5f)
            if (scale >= 18.0f) dl.addText(x1 + 2.0f, y1 + 2.0f, currentCol, "CURRENT")
        }

        if (selectedRegionX in minVisibleRx..maxVisibleRx && selectedRegionY in minVisibleRy..maxVisibleRy) {
            val x1 = canvasMinX + ((selectedRegionX - originRx) * scale).toFloat()
            val y2 = canvasMaxY - ((selectedRegionY - originRy) * scale).toFloat()
            val x2 = x1 + scale.toFloat()
            val y1 = y2 - scale.toFloat()
            val selectCol = ImColor.rgba(96, 165, 250, 255)
            dl.addRect(x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f, selectCol, 0.0f, 0, 3.0f)
        }

        if (hoverRx in 0..255 && hoverRy in 0..255) {
            val x1 = canvasMinX + ((hoverRx - originRx) * scale).toFloat()
            val y2 = canvasMaxY - ((hoverRy - originRy) * scale).toFloat()
            val x2 = x1 + scale.toFloat()
            val y1 = y2 - scale.toFloat()
            dl.addRect(x1, y1, x2, y2, ImColor.rgba(245, 158, 11, 230), 0.0f, 0, 2.0f)

            if (isHovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                selectedRegionX = hoverRx
                selectedRegionY = hoverRy
            }
            if (isHovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                selectedRegionX = hoverRx
                selectedRegionY = hoverRy
                navigator.accept(tileLocation(hoverRx, hoverRy))
            }
            if (isHovered) {
                val inCache = cachedRegionKeys.contains((hoverRx shl 8) or hoverRy)
                ImGui.setTooltip(
                    "Region ${(hoverRx shl 8) or hoverRy} ($hoverRx, $hoverRy)\n" +
                        "World Tile: (${hoverRx * 64}, ${hoverRy * 64})\n" +
                        "Status: ${if (inCache) "In Cache (m${hoverRx}_$hoverRy)" else "Empty / Unmapped"}\n" +
                        "Click to select  ·  Double-click to open in Map Studio",
                )
            }
        }

        dl.popClipRect()

        if (indexLoadFailed) {
            ImGui.textColored(StudioPalette.u32(StudioPalette.WARNING),
                "Map index could not be read from the cache; no regions are shown.")
        }
        renderStatusBar(hoverRx, hoverRy, currentRegionX, currentRegionY, currentPlane)
    }

    private fun renderStatusBar(
        hoverRx: Int,
        hoverRy: Int,
        currentRegionX: Int,
        currentRegionY: Int,
        currentPlane: Int,
    ) {
        ImGui.separator()
        val hoverText = if (hoverRx in 0..255 && hoverRy in 0..255) {
            "Hover: Region ${(hoverRx shl 8) or hoverRy} ($hoverRx, $hoverRy)  Tile: (${hoverRx * 64}, ${hoverRy * 64})"
        } else {
            "Hover: outside world grid"
        }
        val selText = "Selected: Region ${(selectedRegionX shl 8) or selectedRegionY} " +
            "($selectedRegionX, $selectedRegionY)"
        val zoom = String.format(Locale.ROOT, "Zoom %.1f px/region", scale)

        ImGui.textColored(StudioPalette.u32(StudioPalette.TEXT_MUTED), hoverText)
        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()
        ImGui.textColored(StudioPalette.u32(StudioPalette.INFO), selText)
        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()
        ImGui.textDisabled("$regionCount regions in cache  ·  editing $currentRegionX,$currentRegionY p$currentPlane")
        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()
        val failure = overview.failureText()
        if (failure != null) {
            ImGui.textColored(StudioPalette.u32(StudioPalette.WARNING), "World map unavailable: $failure")
        } else {
            ImGui.textDisabled(overview.statusText())
        }

        ImGui.sameLine()
        val zoomWidth = ImGui.calcTextSize(zoom).x
        val avail = ImGui.getContentRegionAvailX()
        if (avail > zoomWidth + 10f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - zoomWidth)
            ImGui.textDisabled(zoom)
        }
    }

    private fun tileLocation(regionX: Int, regionY: Int): WorldLocation =
        WorldLocation.ofTile((regionX shl 6) + 32, (regionY shl 6) + 32, activePlane)
            ?: WorldLocation.ofRegionId((regionX shl 8) or regionY)
            ?: WorldLocation(0, 0, 0, WorldLocation.Kind.REGION)

    private fun handleJump(query: String) {
        if (query.isBlank()) return

        val cleanQuery = query.trim()
        val matchedArea = overview.areas.find {
            it.displayName.equals(cleanQuery, ignoreCase = true) ||
            it.internalName.equals(cleanQuery, ignoreCase = true) ||
            it.displayName.contains(cleanQuery, ignoreCase = true)
        }
        if (matchedArea != null) {
            overview.switchArea(matchedArea)
            focusRegion(matchedArea.originRegionX, matchedArea.originRegionY)
            return
        }

        val cleaned = query.lowercase(Locale.ROOT)
            .replace(Regex("^(region|regionid|id|r)\\b[:=]?"), "")
            .replace(Regex("[()\\[\\]{}]"), "")
            .trim()
        val parts = cleaned.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
        try {
            var targetRx = -1
            var targetRy = -1
            when (parts.size) {
                1 -> {
                    val id = parts[0].toInt()
                    if (id in 0..0xFFFF) {
                        targetRx = id shr 8
                        targetRy = id and 0xFF
                    }
                }
                2, 3 -> {
                    val a = parts[0].toInt()
                    val b = parts[1].toInt()
                    val (rx, ry) = if (a in 0..255 && b in 0..255) {
                        a to b
                    } else {
                        (a / 64) to (b / 64)
                    }
                    if (rx in 0..255 && ry in 0..255) {
                        targetRx = rx
                        targetRy = ry
                        if (parts.size == 3) {
                            val plane = parts[2].toInt()
                            if (plane in 0..3) activePlane = plane
                        }
                    }
                }
            }
            if (targetRx in 0..255 && targetRy in 0..255) {
                val current = overview.currentArea
                if (current == null || !current.containsRegion(targetRx, targetRy)) {
                    val targetArea = overview.areas.find { it.containsRegion(targetRx, targetRy) }
                    if (targetArea != null) {
                        overview.switchArea(targetArea)
                    }
                }
                focusRegion(targetRx, targetRy)
            }
        } catch (_: NumberFormatException) {
            // Leave the current view untouched on malformed input.
        }
    }

    /** Eases the zoom by [factor], keeping canvas point ([anchorX], [anchorY]) fixed. */
    private fun zoomBy(factor: Double, anchorX: Double, anchorY: Double) {
        zoomAnchorX = anchorX
        zoomAnchorY = anchorY
        zoom.multiply(factor)
    }

    /**
     * Advances the eased zoom one frame and moves the centre so the world point under the
     * zoom anchor stays under it. Uses the real canvas size, so the anchor never drifts.
     */
    private fun advanceZoom(canvasWidth: Double, canvasHeight: Double) {
        val before = scale
        val ratio = zoom.advance(ImGui.getIO().deltaTime)
        if (ratio == 1.0) return
        val after = scale
        // Offset of the anchor from the canvas centre, in screen pixels (y up).
        val offsetX = (zoomAnchorX - 0.5) * canvasWidth
        val offsetY = (0.5 - zoomAnchorY) * canvasHeight
        centreRegionX += offsetX / before - offsetX / after
        centreRegionY += offsetY / before - offsetY / after
        clampCenter()
    }

    /**
     * Draws sharp region tiles once a region is at least [DETAIL_MIN_SCALE] pixels wide,
     * requesting only on-screen regions nearest the centre first.
     */
    private fun drawDetailTiles(
        dl: ImDrawList,
        canvasMinX: Float,
        canvasMaxY: Float,
        originRx: Double,
        originRy: Double,
        plane: Int,
        visibleX: IntRange,
        visibleY: IntRange,
    ) {
        if (scale < DETAIL_MIN_SCALE) return
        val cache = session ?: return
        val visible = ArrayList<Int>()
        for (rx in visibleX) for (ry in visibleY) {
            if ((rx shl 8) or ry in cachedRegionKeys) visible += (rx shl 8) or ry
        }
        visible.sortBy { key ->
            val dx = (key shr 8) + 0.5 - centreRegionX
            val dy = (key and 0xFF) + 0.5 - centreRegionY
            dx * dx + dy * dy
        }
        try {
            detail.request(cache.bundle(), cache.bundle().definitions(), plane, visible)
        } catch (_: RuntimeException) {
            return
        }
        for (key in visible) {
            val rx = key shr 8
            val ry = key and 0xFF
            val texId = detail.textureId(plane, rx, ry)
            if (texId == 0) continue
            val x1 = canvasMinX + ((rx - originRx) * scale).toFloat()
            val y2 = canvasMaxY - ((ry - originRy) * scale).toFloat()
            dl.addImage(texId.toLong(), x1, y2 - scale.toFloat(), x1 + scale.toFloat(), y2, 0f, 0f, 1f, 1f)
        }
    }

    private fun fitAllRegions() {
        if (cachedRegions.isEmpty()) {
            zoom.set(DEFAULT_SCALE)
            centreRegionX = 50.5
            centreRegionY = 50.5
            return
        }
        var minRx = 255
        var maxRx = 0
        var minRy = 255
        var maxRy = 0
        for (entry in cachedRegions) {
            val rx = entry.regionX()
            val ry = entry.regionY()
            if (rx in 0..255 && ry in 0..255) {
                minRx = min(minRx, rx)
                maxRx = max(maxRx, rx)
                minRy = min(minRy, ry)
                maxRy = max(maxRy, ry)
            }
        }
        val spanX = max(1, maxRx - minRx + 2)
        val spanY = max(1, maxRy - minRy + 2)
        centreRegionX = (minRx + maxRx) * 0.5 + 0.5
        centreRegionY = (minRy + maxRy) * 0.5 + 0.5
        zoom.set(600.0 / max(spanX, spanY))
        clampCenter()
    }

    private fun clampCenter() {
        centreRegionX = max(0.0, min(255.0, centreRegionX))
        centreRegionY = max(0.0, min(255.0, centreRegionY))
    }

    private companion object {
        const val MIN_SCALE = 2.0

        /**
         * Sixteen screen pixels per world tile. Past [DETAIL_MIN_SCALE] regions draw from
         * sharp four-pixel-per-tile tiles, so close zoom stays legible.
         */
        const val MAX_SCALE = 1024.0

        /** A region this many pixels wide or more uses detail tiles (1.5 px per tile). */
        const val DETAIL_MIN_SCALE = 96.0
        const val DEFAULT_SCALE = 16.0
        const val REGION_TILES = 64
    }
}
