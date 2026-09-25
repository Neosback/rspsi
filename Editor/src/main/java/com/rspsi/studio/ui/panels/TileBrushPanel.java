package com.rspsi.studio.ui.panels;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.api.runtime.SimulatedClient;
import com.rspsi.editor.inspector.ObjectReport;
import com.rspsi.studio.ui.PropertyGrid;
import com.rspsi.editor.model.FloorId;
import com.rspsi.editor.render.TerrainRenderVertex;
import com.rspsi.editor.render.TerrainRenderFace;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.render.TilePreviewBuilder;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.plugin.builtin.TileInfoHudPlugin;
import com.rspsi.studio.ui.OverlayTextureCache;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tile Inspector: shows which underlay/overlay tile types are in use across the current
 * selection (made with Single Select / Multi Select on the floating tool rail), with a
 * visual swatch per distinct type rather than a per-tile dump.
 */
public final class TileBrushPanel implements StudioPanel {
    public static final String ID = "studio.tile-brush";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Tile Inspector";
    }

    @Override
    public String icon() {
        return StudioIcons.TILE;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.RIGHT;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return java.util.EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public float preferredRightSidebarWidth() {
        return 420.0f;
    }

    @Override
    public void render(StudioPanelContext context) {
        LoadedOsrsCacheSession cache = context.cache();
        var session = context.session();
        Set<TileCoordinate> selected = (session != null && session.selection() != null)
                ? session.selection().selectedCoordinates() : Set.of();
        WorldDocument world = session != null ? session.world() : null;

        // Tile Inspector owns nested scrolling so its visual preview never disappears.
        ImGui.setScrollY(0.0f);

        if (world == null) {
            ImGui.textDisabled("No active map loaded.");
            return;
        }
        if (selected.isEmpty()) {
            StudioWidgets.section("Tile Inspector");
            ImGui.textDisabled("No tile selected.");
            ImGui.textWrapped("Use Single Select or Multi Select on the floating tool rail to inspect tiles.");
            return;
        }

        ImGui.textColored(StudioPalette.ACCENT, selected.size() + " tile(s) selected");
        float clearWidth = ImGui.calcTextSize("Clear").x + ImGui.getStyle().getFramePaddingX() * 2.0f;
        float offset = ImGui.getContentRegionAvailX() - clearWidth;
        if (offset > 0.0f) ImGui.sameLine(ImGui.getCursorPosX() + offset);
        if (ImGui.smallButton("Clear##tile-insp-clr")) {
            session.selection().clear();
            return;
        }
        ImGui.dummy(1.0f, 4.0f);

        if (selected.size() == 1) {
            TileInspection inspection = resolveInspection(context, world, selected.iterator().next());
            if (inspection == null) {
                ImGui.textDisabled("Selected tile is outside the loaded map.");
                return;
            }
            renderPinnedTileSummary(cache, world, inspection);

            ImGui.separator();
            float detailsHeight = Math.max(120.0f, ImGui.getContentRegionAvailY());
            ImGui.beginChild("##tile-inspector-details", 0.0f, detailsHeight, false);
            ImGui.setScrollX(0.0f);
            renderSingleTileDetails(context, cache, inspection);

            ImGui.dummy(1.0f, 8.0f);
            if (ImGui.collapsingHeader("Region surfaces")) {
                renderRegionSurvey(cache, context, world);
            }
            ImGui.endChild();
            return;
        }

        ImGui.textDisabled(selected.size()
                + " tiles selected. Region/selection summaries stay scrollable below.");
        ImGui.separator();
        ImGui.beginChild("##tile-inspector-multi", 0.0f,
                Math.max(120.0f, ImGui.getContentRegionAvailY()), false);
        renderRegionSurvey(cache, context, world);
        ImGui.endChild();
    }

    private TileInspection resolveInspection(
            StudioPanelContext context,
            WorldDocument world,
            TileCoordinate coord) {
        if (coord.plane() < 0 || coord.plane() >= world.planes()) return null;

        int localX;
        int localY;
        if (world.contains(coord)) {
            localX = coord.x();
            localY = coord.y();
        } else {
            WorldTileAddress address = WorldTileAddress.of(coord.x(), coord.y(), coord.plane());
            localX = address.regionLocalX();
            localY = address.regionLocalY();
            if (!world.contains(coord.plane(), localX, localY)) return null;
        }

        TileSnapshot snapshot = world.tile(coord.plane(), localX, localY).snapshot();
        int effectivePlane = world.effectivePlane(coord.plane(), localX, localY);
        WorldTile worldTile = context.session().coordinates()
                .toWorld(new LocalTile(effectivePlane, localX, localY));
        List<GpuDrawCommand> drawCommands = commandsForTile(
                context, worldTile.plane(), worldTile.x(), worldTile.y());
        return new TileInspection(coord, localX, localY, effectivePlane, snapshot, drawCommands);
    }

    private void renderPinnedTileSummary(
            LoadedOsrsCacheSession cache,
            WorldDocument world,
            TileInspection inspection) {
        TileSnapshot snapshot = inspection.snapshot();

        if (StudioWidgets.beginPropertyTable("tile-summary")) {
            StudioWidgets.propertyRow("Location",
                    inspection.coordinate().x() + ", " + inspection.coordinate().y()
                            + " · plane " + inspection.coordinate().plane());
            if (inspection.effectivePlane() != inspection.coordinate().plane()) {
                StudioWidgets.propertyRow("Scene plane",
                        inspection.effectivePlane() + " · bridge-adjusted");
            }
            StudioWidgets.propertyRow("Shape / rotation",
                    snapshot.overlayShape() + " · " + snapshot.overlayRotation()
                            + " (" + snapshot.overlayRotation() * 90 + "°)");
            StudioWidgets.propertyRow("Heights",
                    snapshot.southWestHeight() + " / " + snapshot.southEastHeight()
                            + " / " + snapshot.northEastHeight() + " / " + snapshot.northWestHeight());
            StudioWidgets.propertyRow("Flags",
                    "0x" + Integer.toHexString(snapshot.flags()).toUpperCase()
                            + " · " + flagSummary(snapshot.flags()));
            StudioWidgets.endPropertyTable();
        }

        ImGui.dummy(1.0f, 5.0f);
        ImGui.checkbox("Blend neighboring underlays##tile-preview-blend", previewBlending);
        ImGui.sameLine();
        if (ImGui.smallButton("Copy##tile-insp-copy")) {
            ImGui.setClipboardText(tileInfoText(
                    cache,
                    inspection.coordinate(),
                    inspection.effectivePlane(),
                    snapshot,
                    inspection.drawCommands()));
        }

        ImGui.dummy(1.0f, 4.0f);
        var packet = cache == null ? java.util.Optional.<TerrainRenderPacket>empty()
                : tilePreviews.build(
                        world,
                        cache.bundle().definitions(),
                        inspection.coordinate().plane(),
                        inspection.localX(),
                        inspection.localY(),
                        previewBlending.get()
                                ? TilePreviewBuilder.Mode.BLENDED
                                : TilePreviewBuilder.Mode.UNBLENDED);
        if (packet.isPresent()) {
            float previewSize = Math.min(150.0f, Math.max(96.0f, ImGui.getContentRegionAvailX() * 0.42f));
            drawTilePacket(cache, packet.orElseThrow(), previewSize);
        } else {
            ImGui.textDisabled("No rendered floor surface for this tile.");
        }
    }

    private void renderSingleTileDetails(
            StudioPanelContext context,
            LoadedOsrsCacheSession cache,
            TileInspection inspection) {
        TileSnapshot snapshot = inspection.snapshot();

        StudioWidgets.section("Floor definitions");
        renderFloorDefinitionDetail(cache, "Underlay", snapshot.underlayId(), true);
        ImGui.spacing();
        renderFloorDefinitionDetail(cache, "Overlay", snapshot.overlayId(), false);

        ImGui.dummy(1.0f, 8.0f);
        renderObjectsOnTile(context, cache, snapshot.objects());

        ImGui.dummy(1.0f, 8.0f);
        renderDrawCommandsSection(inspection.drawCommands());

        ImGui.dummy(1.0f, 8.0f);
        renderInspectionHudSettings(context);
    }

    private static void renderInspectionHudSettings(StudioPanelContext context) {
        if (!ImGui.collapsingHeader("Inspection HUD")) return;
        context.studioPlugins().plugin(TileInfoHudPlugin.ID)
                .filter(TileInfoHudPlugin.class::isInstance)
                .map(TileInfoHudPlugin.class::cast)
                .ifPresentOrElse(
                        hud -> hud.renderSettings(context),
                        () -> ImGui.textDisabled("Inspection HUD plugin is unavailable."));
    }

    private record TileInspection(
            TileCoordinate coordinate,
            int localX,
            int localY,
            int effectivePlane,
            TileSnapshot snapshot,
            List<GpuDrawCommand> drawCommands) {
    }

    /**
     * Real submitted {@code GpuDrawCommand}s for this exact tile - texture, submission pass,
     * priority, and depth bias are exactly what decides z-fighting/coplanar ordering, so
     * showing the actual values traces a render glitch back to real data instead of guessing.
     */
    private static List<GpuDrawCommand> commandsForTile(StudioPanelContext context, int plane, int x, int y) {
        if (context == null || context.viewport() == null) return List.of();
        GpuUploadPlan plan = context.viewport().lastPlan();
        if (plan == null) return List.of();
        List<GpuDrawCommand> matches = new java.util.ArrayList<>();
        for (GpuDrawCommand command : plan.commands()) {
            if (command.tile().plane() == plane && command.tile().worldX() == x && command.tile().worldY() == y) {
                matches.add(command);
            }
        }
        return matches;
    }

    private void renderDrawCommandsSection(List<GpuDrawCommand> commands) {
        if (!ImGui.collapsingHeader("Render Commands (z-fighting/depth debug) (" + commands.size() + ")")) {
            return;
        }
        if (commands.isEmpty()) {
            ImGui.textDisabled("No draw commands matched this tile in the last rendered frame.");
            return;
        }
        for (GpuDrawCommand command : commands) {
            ImGui.text(command.layer() + " / " + command.pass()
                    + "   texture=" + (command.textureId() >= 0 ? String.valueOf(command.textureId()) : "none")
                    + "   priority=" + command.priority()
                    + "   depthBias=" + command.depthBias()
                    + "   renderMode=" + command.renderMode()
                    + (command.objectId() >= 0 ? "   objectId=" + command.objectId() : ""));
        }
        ImGui.textDisabled("Coplanar faces with the same priority/depth bias are the classic z-fighting cause; "
                + "a decoration that should sit above its wall needs a higher depthBias, not just a higher priority.");
    }

    private String tileInfoText(LoadedOsrsCacheSession cache, TileCoordinate coord, int effectivePlane,
                                TileSnapshot snapshot, List<GpuDrawCommand> commands) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tile ").append(coord.x()).append(',').append(coord.y())
                .append(" plane ").append(coord.plane());
        if (effectivePlane != coord.plane()) sb.append(" (effective plane ").append(effectivePlane).append(")");
        sb.append('\n');
        sb.append("Height SW/SE/NE/NW: ").append(snapshot.southWestHeight()).append('/')
                .append(snapshot.southEastHeight()).append('/').append(snapshot.northEastHeight())
                .append('/').append(snapshot.northWestHeight()).append('\n');
        sb.append("Flags: 0x").append(Integer.toHexString(snapshot.flags()))
                .append(" [").append(flagSummary(snapshot.flags())).append("]\n");
        sb.append("Underlay: #").append(snapshot.underlayId());
        appendFloorDefinitionText(sb, cache, snapshot.underlayId(), true);
        sb.append('\n');
        sb.append("Overlay: #").append(snapshot.overlayId());
        appendFloorDefinitionText(sb, cache, snapshot.overlayId(), false);
        sb.append('\n');
        sb.append("Shape: ").append(snapshot.overlayShape()).append(" Rotation: ").append(snapshot.overlayRotation())
                .append('\n');
        sb.append("Objects: ").append(snapshot.objects().size()).append('\n');
        for (GpuDrawCommand command : commands) {
            sb.append("  Command: ").append(command.layer()).append('/').append(command.pass())
                    .append(" texture=").append(command.textureId())
                    .append(" priority=").append(command.priority())
                    .append(" depthBias=").append(command.depthBias())
                    .append(" renderMode=").append(command.renderMode()).append('\n');
        }
        return sb.toString();
    }

    private void appendFloorDefinitionText(StringBuilder sb, LoadedOsrsCacheSession cache, int id, boolean underlay) {
        if (id <= 0 || cache == null) return;
        int definitionId = FloorId.definitionId(id);
        var def = underlay ? cache.bundle().definitions().underlay(definitionId)
                : cache.bundle().definitions().overlay(definitionId);
        sb.append(" (definition ").append(definitionId).append(')');
        if (def.isEmpty()) {
            sb.append(" (no definition found)");
            return;
        }
        FloorDefinitionView view = def.get();
        sb.append(" rgb=0x").append(Integer.toHexString(view.rgb() & 0xFFFFFF))
                .append(" hue=").append(view.hue()).append(" sat=").append(view.saturation())
                .append(" lum=").append(view.luminance());
        if (!underlay) sb.append(" texture=").append(view.texture());
    }

    /**
     * Dear ImGui's {@code ImDrawList} primitives ({@code addRectFilled}/{@code addRect}/
     * {@code addTriangleFilled}/{@code addText}) take a raw native {@code ImU32}, which Dear
     * ImGui packs as {@code 0xAABBGGRR} (blue and red swapped from the usual "0xAARRGGBB" most
     * people write by hand) - {@code ImGui.textColored}/style-color calls are a different, Java
     * side API that already does this conversion for you, which is why those colors look right
     * while a raw 0xAARRGGBB int hand-fed straight into {@code ImDrawList} renders with red and
     * blue swapped. This was the real cause of the tile swatches/preview looking wrong compared
     * to the actual 3D viewport - every color below needs this conversion before it reaches
     * {@code ImDrawList}.
     */
    private static int toDrawListColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * The color this floor definition actually renders as in the 3D scene. The definition's own
     * {@code rgb()} field is a separate, decoder-computed "average" color - the real renderer
     * never uses it; it packs hue/saturation/luminance the same way
     * {@code TerrainAppearanceBuilder} does and converts through
     * {@code OsrsTerrainColorMath.packedHslToRgb(packed, 0.6)} (the same palette the GL renderer
     * itself uses). Using {@code rgb()} for these swatches was the source of the swatch/preview
     * colors not matching the actual 3D viewport - this reproduces the real pipeline instead.
     */
    private static int floorDisplayRgb(FloorDefinitionView view) {
        int packed = OsrsTerrainColorMath.packHsl(view.hue(), view.saturation(), view.luminance());
        int rgb = OsrsTerrainColorMath.packedHslToRgb(packed, 0.6);
        if (rgb == OsrsTerrainColorMath.INVALID_HSL_COLOR) {
            return 0xFF000000 | (view.rgb() & 0xFFFFFF);
        }
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static String flagSummary(int flags) {
        java.util.List<String> set = new java.util.ArrayList<>();
        if (OsrsTileFlags.isBlocked(flags)) set.add("Blocked");
        if (OsrsTileFlags.hasBridge(flags)) set.add("Bridge");
        if (OsrsTileFlags.removesRoofs(flags)) set.add("Removes Roofs");
        if ((flags & OsrsTileFlags.MINIMAP_HIDDEN) != 0) set.add("Minimap Hidden");
        return set.isEmpty() ? "none set" : String.join(", ", set);
    }

    /** Full floor-definition detail (not just the swatch color) - the raw HSL/texture inputs
     * that decide how this tile actually blends and renders, for debugging a miscolored or
     * mis-textured tile back to its source data. */
    private void renderFloorDefinitionDetail(LoadedOsrsCacheSession cache, String label, int id, boolean underlay) {
        drawSwatch(cache, id, underlay, 20.0f, null);
        ImGui.sameLine(0.0f, 6.0f);
        if (id <= 0 || cache == null) {
            ImGui.text(label + ": None");
            return;
        }
        int definitionId = FloorId.definitionId(id);
        var def = underlay ? cache.bundle().definitions().underlay(definitionId)
                : cache.bundle().definitions().overlay(definitionId);
        if (def.isEmpty()) {
            ImGui.textColored(StudioDrawColors.abgr(0xFFEF4444), label + ": #" + definitionId + "  (no definition found - dangling id)");
            return;
        }
        FloorDefinitionView view = def.get();
        ImGui.text(label + ": #" + definitionId + "   rgb=0x" + Integer.toHexString(view.rgb() & 0xFFFFFF)
                + "  hue=" + view.hue() + " sat=" + view.saturation() + " lum=" + view.luminance());
        if (!underlay) {
            ImGui.sameLine();
            ImGui.textDisabled("  texture=" + (view.texture() >= 0 ? String.valueOf(view.texture()) : "none"));
        }
        if (view.secondaryRgb() >= 0) {
            ImGui.textDisabled("  secondary rgb=0x" + Integer.toHexString(view.secondaryRgb() & 0xFFFFFF)
                    + " hue=" + view.secondaryHue() + " sat=" + view.secondarySaturation()
                    + " lum=" + view.secondaryLuminance());
        }
    }

    /**
     * Object Inspector: every object on the selected tile, each expandable into its full
     * {@link ObjectReport} - definition, collision, and appearance data straight from
     * the cache, for tracking a misconfigured type/rule or a rendering bug back to its source.
     */
    private void renderObjectsOnTile(
            StudioPanelContext context,
            LoadedOsrsCacheSession cache,
            java.util.List<WorldObject> objects) {
        StudioWidgets.section("Objects on tile");
        if (objects.isEmpty()) {
            ImGui.textDisabled("No objects on this tile.");
            return;
        }
        if (cache == null) {
            ImGui.textDisabled("No cache loaded. Object IDs are available but definitions cannot be resolved.");
        }

        int index = 0;
        for (WorldObject object : objects) {
            ImGui.pushID("tile-object-" + index);
            String objectName = cache == null
                    ? "Object #" + object.id()
                    : cache.bundle().definitions().object(object.id())
                            .map(def -> {
                                String name = def.displayName();
                                return name == null || name.isBlank() || "null".equalsIgnoreCase(name)
                                        ? "Object #" + object.id()
                                        : name + " (#" + object.id() + ")";
                            })
                            .orElse("Object #" + object.id());

            ImGui.textColored(StudioPalette.ACCENT, objectName);
            ImGui.textDisabled(object.category().displayName()
                    + " · shape " + object.type()
                    + " · rotation " + object.rotation());

            float gap = 6.0f;
            float width = Math.max(110.0f, (ImGui.getContentRegionAvailX() - gap) * 0.5f);
            if (StudioWidgets.buttonSecondary("Open in Object Viewer", width, 26.0f)) {
                context.navigation().inspectObject(object);
            }
            ImGui.sameLine(0.0f, gap);
            if (StudioWidgets.buttonSecondary("Edit object...", width, 26.0f)) {
                context.navigation().editObject(object);
            }

            if (cache != null && ImGui.collapsingHeader("Definition details##" + index)) {
                ObjectReport report = ObjectReport.forPlacement(
                        object,
                        cache.bundle().definitions(),
                        context.simulation() == null
                                ? com.rspsi.cache.definition.ObjectVarState.freshAccount()
                                : new SimulatedClient(
                                        context.simulation(),
                                        cache.bundle().definitions()));
                PropertyGrid.render("tile-obj-" + index, report);
            }
            ImGui.separator();
            ImGui.popID();
            index++;
        }
    }

    private final imgui.type.ImBoolean previewBlending = new imgui.type.ImBoolean(true);
    private final TilePreviewBuilder tilePreviews = new TilePreviewBuilder();

    /**
     * Every floor surface authored at this x,y, one preview per plane, drawn
     * from the exact terrain packet the renderer submits (lit colours and the
     * real texture), so a bridge deck and the ground under it, or stacked
     * floors, are shown one under the other instead of fighting in one box.
     */
    private void renderTilePreviewStack(LoadedOsrsCacheSession cache, WorldDocument world,
                                        int selectedPlane, int x, int y) {
        if (cache == null) {
            ImGui.textDisabled("No cache loaded - cannot build the tile preview.");
            return;
        }
        TilePreviewBuilder.Mode mode = previewBlending.get()
                ? TilePreviewBuilder.Mode.BLENDED : TilePreviewBuilder.Mode.UNBLENDED;
        boolean any = false;
        for (int plane = 0; plane < world.planes(); plane++) {
            var packet = tilePreviews.build(world, cache.bundle().definitions(), plane, x, y, mode);
            if (packet.isEmpty()) continue;
            any = true;
            int effective = world.effectivePlane(plane, x, y);
            String label = "Plane " + plane + (effective != plane ? "  (renders on scene plane " + effective + ")" : "")
                    + (plane == selectedPlane ? "  - selected" : "");
            if (plane == selectedPlane) {
                ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), label);
            } else {
                ImGui.textDisabled(label);
            }
            drawTilePacket(cache, packet.orElseThrow(), 96.0f);
        }
        if (!any) ImGui.textDisabled("No floor is drawn at this position on any plane.");
    }

    /** Draws one terrain packet as the renderer shades it (north up). */
    private static void drawTilePacket(LoadedOsrsCacheSession cache, TerrainRenderPacket packet, float size) {
        float x = ImGui.getCursorScreenPos().x;
        float y = ImGui.getCursorScreenPos().y;
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(x, y, x + size, y + size, toDrawListColor(0xFF0B0F17), 2.0f);
        for (TerrainRenderFace face : packet.faces()) {
            TerrainRenderVertex a = packet.vertices().get(face.a());
            TerrainRenderVertex b = packet.vertices().get(face.b());
            TerrainRenderVertex c = packet.vertices().get(face.c());
            float ax = x + a.x() / 128.0f * size, ay = y + size - a.y() / 128.0f * size;
            float bx = x + b.x() / 128.0f * size, by = y + size - b.y() / 128.0f * size;
            float cx = x + c.x() / 128.0f * size, cy = y + size - c.y() / 128.0f * size;
            int texture = face.textureId() >= 0 ? OverlayTextureCache.handleFor(cache, face.textureId()) : 0;
            if (texture != 0) {
                // Textured faces: texture RGB x the 7-bit tile light, as the renderer does.
                int light = ((a.packedHsl() & 0x7F) + (b.packedHsl() & 0x7F) + (c.packedHsl() & 0x7F)) / 3;
                int grey = Math.min(255, light * 255 / 127);
                int tint = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
                draw.addImageQuad(texture, ax, ay, bx, by, cx, cy, cx, cy,
                        a.x() / 128.0f, 1.0f - a.y() / 128.0f, b.x() / 128.0f, 1.0f - b.y() / 128.0f,
                        c.x() / 128.0f, 1.0f - c.y() / 128.0f, c.x() / 128.0f, 1.0f - c.y() / 128.0f,
                        toDrawListColor(tint));
            } else {
                draw.addTriangleFilled(ax, ay, bx, by, cx, cy,
                        toDrawListColor(averageRgb(a.packedHsl(), b.packedHsl(), c.packedHsl())));
            }
        }
        draw.addRect(x, y, x + size, y + size,
                StudioPalette.draw(StudioPalette.BORDER_STRONG), 2.0f, 0, 1.5f);
        draw.addText(StudioFonts.icon(), 18,
                x + size - 20.0f, y + 4.0f,
                StudioPalette.draw(StudioPalette.TEXT), StudioIcons.EXPLORE);
        ImGui.dummy(size, size);
    }

    private static int averageRgb(int... packedHsl) {
        int r = 0, g = 0, b = 0;
        for (int hsl : packedHsl) {
            int rgb = OsrsTerrainColorMath.packedHslToRgb(hsl, 0.6);
            if (rgb == OsrsTerrainColorMath.INVALID_HSL_COLOR) rgb = 0;
            r += (rgb >> 16) & 0xFF;
            g += (rgb >> 8) & 0xFF;
            b += rgb & 0xFF;
        }
        int n = packedHsl.length;
        return 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    private void renderRegionSurvey(LoadedOsrsCacheSession cache, StudioPanelContext context, WorldDocument world) {
        int plane = context.settings() != null
                ? context.settings().snapshot().get(com.rspsi.editor.render.RenderSettingKeys.ACTIVE_PLANE) : 0;

        Map<Integer, Integer> underlayCounts = new LinkedHashMap<>();
        Map<Integer, Integer> overlayCounts = new LinkedHashMap<>();
        for (int x = 0; x < Math.min(64, world.width()); x += 2) {
            for (int y = 0; y < Math.min(64, world.length()); y += 2) {
                TileSnapshot snapshot = world.tile(plane, x, y).snapshot();
                if (snapshot.underlayId() > 0) {
                    underlayCounts.merge(snapshot.underlayId(), 1, Integer::sum);
                }
                if (snapshot.overlayId() > 0) {
                    overlayCounts.merge(snapshot.overlayId(), 1, Integer::sum);
                }
            }
        }

        ImGui.textDisabled("Underlays in use (" + underlayCounts.size() + "):");
        renderSwatchGrid(cache, underlayCounts, true);

        ImGui.spacing();
        ImGui.textDisabled("Overlays in use (" + overlayCounts.size() + "):");
        renderSwatchGrid(cache, overlayCounts, false);
    }

    private void renderSwatchGrid(LoadedOsrsCacheSession cache, Map<Integer, Integer> counts, boolean underlay) {
        if (counts.isEmpty()) {
            ImGui.textDisabled("  (none)");
            return;
        }

        float swatchSize = 28.0f;
        float availWidth = ImGui.getContentRegionAvailX();
        int perRow = Math.max(1, (int) (availWidth / (swatchSize + 4.0f)));

        int shown = 0;
        for (var entry : counts.entrySet()) {
            int id = entry.getKey();
            String tooltip = "#" + FloorId.definitionId(id) + "  -  " + entry.getValue() + " tile(s)";
            drawSwatch(cache, id, underlay, swatchSize, tooltip);

            shown++;
            if (shown % perRow != 0) {
                ImGui.sameLine();
            }
        }
        if (shown % perRow != 0) {
            ImGui.newLine();
        }
    }

    /**
     * Draws one tile-type swatch. Overlays with a real OSRS ground texture render that texture
     * (true "what you see is what you get" preview); everything else - underlays (which OSRS
     * never gives a texture, only a color) and untextured overlays - falls back to a flat color
     * rectangle from the definition's average RGB.
     */
    private void drawSwatch(LoadedOsrsCacheSession cache, int id, boolean underlay, float size, String tooltip) {
        int rgb = 0xFF2A2A2A;
        int textureHandle = 0;
        if (cache != null && id > 0) {
            int definitionId = FloorId.definitionId(id);
            var def = underlay ? cache.bundle().definitions().underlay(definitionId)
                    : cache.bundle().definitions().overlay(definitionId);
            if (def.isPresent()) {
                rgb = floorDisplayRgb(def.get());
                if (!underlay && def.get().texture() >= 0) {
                    textureHandle = OverlayTextureCache.handleFor(cache, def.get().texture());
                }
            }
        }

        float x = ImGui.getCursorScreenPos().x;
        float y = ImGui.getCursorScreenPos().y;
        if (textureHandle > 0) {
            ImGui.image(textureHandle, size, size);
        } else {
            ImDrawList draw = ImGui.getWindowDrawList();
            draw.addRectFilled(x, y, x + size, y + size, toDrawListColor(rgb), 3.0f);
            draw.addRect(x, y, x + size, y + size, toDrawListColor(0xFF666666), 3.0f);
            ImGui.dummy(size, size);
        }
        if (tooltip != null && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
    }
}
