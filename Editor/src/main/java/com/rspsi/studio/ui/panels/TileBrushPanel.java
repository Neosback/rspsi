package com.rspsi.studio.ui.panels;

import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.inspector.ObjectDefinitionSummary;
import com.rspsi.editor.inspector.ObjectInspectorSnapshot;
import com.rspsi.editor.model.FloorId;
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
    public void render(StudioPanelContext context) {
        LoadedOsrsCacheSession cache = context.cache();
        var session = context.session();
        Set<TileCoordinate> selected = (session != null && session.selection() != null)
                ? session.selection().selectedCoordinates() : Set.of();
        WorldDocument world = session != null ? session.world() : null;

        if (ImGui.collapsingHeader("Tile Inspector", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (world == null) {
                ImGui.textDisabled("No active map loaded.");
            } else if (selected.isEmpty()) {
                ImGui.textDisabled("No tile selected.");
                ImGui.textDisabled("Use Single Select or Multi Select on the floating tool rail to inspect tiles.");
            } else {
                ImGui.textColored(0xFF38BDF8, selected.size() + " tile(s) selected");
                ImGui.sameLine(0.0f, 12.0f);
                if (ImGui.smallButton("Clear Selection##tile-insp-clr")) {
                    session.selection().clear();
                }
                ImGui.spacing();

                if (selected.size() == 1) {
                    renderSingleTileDetail(context, cache, world, selected.iterator().next());
                } else {
                    ImGui.textDisabled(selected.size() + " tiles selected - see the region survey below for the distinct types in use.");
                }
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        if (ImGui.collapsingHeader("Tiles Used In This Region", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (world == null) {
                ImGui.textDisabled("No active map loaded.");
            } else {
                renderRegionSurvey(cache, context, world);
            }
        }
    }

    private void renderSingleTileDetail(StudioPanelContext context, LoadedOsrsCacheSession cache,
                                        WorldDocument world, TileCoordinate coord) {
        if (coord.plane() < 0 || coord.plane() >= world.planes()) {
            ImGui.textDisabled("Selected tile's plane is outside the loaded map.");
            return;
        }
        int localX;
        int localY;
        if (world.contains(coord)) {
            localX = coord.x();
            localY = coord.y();
        } else {
            WorldTileAddress address = WorldTileAddress.of(coord.x(), coord.y(), coord.plane());
            localX = address.regionLocalX();
            localY = address.regionLocalY();
            if (!world.contains(coord.plane(), localX, localY)) {
                ImGui.textDisabled("Selected tile is outside the loaded map.");
                return;
            }
        }
        TileSnapshot snapshot = world.tile(coord.plane(), localX, localY).snapshot();
        int effectivePlane = world.effectivePlane(coord.plane(), localX, localY);
        // GpuDrawCommand tiles carry absolute OSRS world coordinates, not this
        // document's local ones, and reflect the bridge-adjusted render plane -
        // both conversions are required or every lookup here silently misses.
        WorldTile worldTile = context.session().coordinates().toWorld(new LocalTile(effectivePlane, localX, localY));
        List<GpuDrawCommand> drawCommands = commandsForTile(context, worldTile.plane(), worldTile.x(), worldTile.y());

        if (ImGui.smallButton("Copy Tile Info##tile-insp-copy")) {
            ImGui.setClipboardText(tileInfoText(cache, coord, effectivePlane, snapshot, drawCommands));
        }

        ImGui.text("Coordinate: " + coord.x() + ", " + coord.y() + "  (plane " + coord.plane() + ")");
        if (effectivePlane != coord.plane()) {
            ImGui.sameLine();
            ImGui.textColored(0xFFF59E0B, "  (renders as plane " + effectivePlane + " - bridge on plane 1)");
        }
        ImGui.text("Height (SW/SE/NE/NW): " + snapshot.southWestHeight() + " / " + snapshot.southEastHeight()
                + " / " + snapshot.northEastHeight() + " / " + snapshot.northWestHeight());

        int flags = snapshot.flags();
        ImGui.text("Flags (raw): 0x" + Integer.toHexString(flags));
        ImGui.sameLine(0.0f, 8.0f);
        ImGui.textDisabled("[" + flagSummary(flags) + "]");

        ImGui.spacing();
        renderLiveTilePreview(cache, snapshot);
        ImGui.sameLine(0.0f, 16.0f);
        ImGui.beginGroup();
        renderFloorDefinitionDetail(cache, "Underlay", snapshot.underlayId(), true);
        ImGui.spacing();
        renderFloorDefinitionDetail(cache, "Overlay", snapshot.overlayId(), false);
        ImGui.text("Shape: " + snapshot.overlayShape() + "  Rotation: " + snapshot.overlayRotation());
        ImGui.endGroup();

        ImGui.spacing();
        renderDrawCommandsSection(drawCommands);

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        renderObjectsOnTile(cache, snapshot.objects());
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
            ImGui.textColored(0xFFEF4444, label + ": #" + definitionId + "  (no definition found - dangling id)");
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
     * {@link ObjectInspectorSnapshot} - definition, collision, and appearance data straight from
     * the cache, for tracking a misconfigured type/rule or a rendering bug back to its source.
     */
    private void renderObjectsOnTile(LoadedOsrsCacheSession cache, java.util.List<WorldObject> objects) {
        if (!ImGui.collapsingHeader("Objects On Tile (" + objects.size() + ")",
                objects.isEmpty() ? 0 : ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        if (objects.isEmpty()) {
            ImGui.textDisabled("No objects on this tile.");
            return;
        }
        if (cache == null) {
            ImGui.textDisabled("No cache loaded - cannot resolve object definitions.");
            return;
        }
        for (WorldObject object : objects) {
            ImGui.pushID(object.id() * 4 + object.rotation());
            ObjectInspectorSnapshot snapshot = ObjectInspectorSnapshot.capture(object, cache.bundle().definitions());
            String label = "#" + object.id() + "  " + snapshot.definition().map(ObjectDefinitionSummary::name)
                    .filter(name -> !name.isBlank()).orElse("(unnamed)")
                    + "   [" + snapshot.categoryName() + "]";
            if (ImGui.treeNode(label)) {
                if (ImGui.smallButton("Copy Object Info##obj-copy")) {
                    ImGui.setClipboardText(objectInfoText(object, snapshot));
                }
                renderObjectInspectorDetail(object, snapshot);
                ImGui.treePop();
            }
            ImGui.popID();
        }
    }

    private void renderObjectInspectorDetail(WorldObject object, ObjectInspectorSnapshot snapshot) {
        ImGui.textColored(0xFF38BDF8, "Placement");
        ImGui.text("Id: " + snapshot.id() + "   Type: " + snapshot.type() + "   Rotation: " + snapshot.rotation());
        ImGui.text("Position: " + snapshot.x() + ", " + snapshot.y() + "  (plane " + snapshot.plane() + ")");
        ImGui.text("Category: " + snapshot.categoryName() + "   Shape: " + snapshot.shapeName());
        if (snapshot.category() == com.rspsi.editor.model.ObjectCategory.WALL
                || snapshot.category() == com.rspsi.editor.model.ObjectCategory.WALL_DECOR) {
            ImGui.text("Wall orientation A/B: 0x" + Integer.toHexString(object.wallOrientationA())
                    + " / 0x" + Integer.toHexString(object.wallOrientationB()));
        }

        ImGui.spacing();
        ImGui.textColored(0xFF38BDF8, "Definition");
        if (snapshot.definition().isEmpty()) {
            ImGui.textColored(0xFFEF4444, "No definition found for id " + snapshot.id() + " - dangling object id.");
        } else {
            ObjectDefinitionSummary def = snapshot.definition().get();
            ImGui.text("Name: " + (def.name().isBlank() ? "(none)" : def.name()));
            ImGui.text("Size: " + def.width() + " x " + def.length());
            ImGui.text("Model ids: " + def.modelIds());
            if (!def.modelTypes().isEmpty()) {
                ImGui.text("Model types: " + def.modelTypes());
            }
            ImGui.text("Actions: " + (def.actions().isEmpty() ? "(none)" : String.join(", ", def.actions())));
        }

        ImGui.spacing();
        ImGui.textColored(0xFF38BDF8, "Resolution");
        var resolution = snapshot.resolution();
        if (resolution.renderableGeometryReady()) {
            ImGui.text(resolution.diagnosticSummary());
        } else {
            ImGui.textColored(0xFFEF4444, resolution.diagnosticSummary());
        }
        if (resolution.transformed()) {
            ImGui.text("Transform path: " + resolution.transformPath());
            resolution.displayDefinition().ifPresent(display ->
                    ImGui.text("Display definition: " + display.name() + " (#" + display.id() + ")"));
        }
        if (!resolution.selectedModelIds().isEmpty()) {
            ImGui.text("Selected models: " + resolution.selectedModelIds());
        }

        ImGui.spacing();
        ImGui.textColored(0xFF38BDF8, "Collision");
        if (snapshot.collision().isEmpty()) {
            ImGui.textDisabled("No collision data.");
        } else {
            var collision = snapshot.collision().get();
            ImGui.text("Block walk: " + collision.blockWalk() + "   Block projectile: " + collision.blockProjectile()
                    + "   Breaks routefinding: " + collision.breakRouteFinding());
        }

        ImGui.spacing();
        ImGui.textColored(0xFF38BDF8, "Appearance");
        if (snapshot.appearance().isEmpty()) {
            ImGui.textDisabled("No appearance data.");
        } else {
            ObjectAppearanceView a = snapshot.appearance().get();
            ImGui.text("Animation id: " + (a.animationId() >= 0 ? String.valueOf(a.animationId()) : "none")
                    + "   Randomize start: " + a.randomizeAnimStart() + "   Delay update: " + a.delayAnimationUpdate());
            ImGui.text("Scale: " + a.scaleX() + ", " + a.scaleY() + ", " + a.scaleZ()
                    + "   Offset: " + a.offsetX() + ", " + a.offsetY() + ", " + a.offsetZ());
            ImGui.text("Contoured ground: " + a.contouredGround() + " (type " + a.contourGroundType()
                    + ", param " + a.contourGroundParameter() + ")   Obstructs ground: " + a.obstructsGround());
            ImGui.text("Decor displacement: " + a.decorDisplacement() + "   Rotated: " + a.rotated());
            ImGui.text("Casts shadow: " + a.castsShadow() + "   Occludes: " + a.occludes());
            ImGui.text("Merge normals: " + a.mergeNormals() + "   Non-flat shading: " + a.nonFlatShading());
            ImGui.text("Ambient: " + a.ambient() + "   Contrast: " + a.contrast());
            ImGui.text("Model clipped: " + a.modelClipped() + "   Clip mask: 0x" + Integer.toHexString(a.clipMask()));
            if (!a.recolors().isEmpty()) {
                ImGui.text("Recolors: " + a.recolors());
            }
            if (!a.retextures().isEmpty()) {
                ImGui.text("Retextures: " + a.retextures());
            }
        }
        // The visible model's scale/offset/recolors come from the display
        // definition; only call it out when it differs from the placed one.
        snapshot.displayAppearance()
                .filter(display -> !snapshot.appearance().equals(java.util.Optional.of(display)))
                .ifPresent(d -> {
                    ImGui.textColored(0xFF38BDF8, "Display appearance (visible model)");
                    ImGui.text("Scale: " + d.scaleX() + ", " + d.scaleY() + ", " + d.scaleZ()
                            + "   Offset: " + d.offsetX() + ", " + d.offsetY() + ", " + d.offsetZ());
                    if (!d.recolors().isEmpty()) {
                        ImGui.text("Recolors: " + d.recolors());
                    }
                    if (!d.retextures().isEmpty()) {
                        ImGui.text("Retextures: " + d.retextures());
                    }
                });
    }

    private String objectInfoText(WorldObject object, ObjectInspectorSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Object #").append(snapshot.id()).append(" type=").append(snapshot.type())
                .append(" rotation=").append(snapshot.rotation()).append('\n');
        sb.append("Position: ").append(snapshot.x()).append(',').append(snapshot.y())
                .append(" plane ").append(snapshot.plane()).append('\n');
        sb.append("Category: ").append(snapshot.categoryName()).append("  Shape: ").append(snapshot.shapeName())
                .append('\n');
        sb.append("Wall orientation A/B: 0x").append(Integer.toHexString(object.wallOrientationA()))
                .append(" / 0x").append(Integer.toHexString(object.wallOrientationB())).append('\n');
        snapshot.definition().ifPresentOrElse(def -> sb.append("Definition: name=").append(def.name())
                        .append(" size=").append(def.width()).append('x').append(def.length())
                        .append(" modelIds=").append(def.modelIds())
                        .append(" actions=").append(def.actions()).append('\n'),
                () -> sb.append("Definition: NOT FOUND (dangling id)\n"));
        sb.append("Resolution: ").append(snapshot.resolution().diagnosticSummary())
                .append(" transformPath=").append(snapshot.resolution().transformPath())
                .append(" selectedModels=").append(snapshot.resolution().selectedModelIds()).append('\n');
        snapshot.collision().ifPresent(c -> sb.append("Collision: blockWalk=").append(c.blockWalk())
                .append(" blockProjectile=").append(c.blockProjectile())
                .append(" breaksRouteFinding=").append(c.breakRouteFinding()).append('\n'));
        snapshot.appearance().ifPresent(a -> sb.append("Appearance: animationId=").append(a.animationId())
                .append(" scale=").append(a.scaleX()).append(',').append(a.scaleY()).append(',').append(a.scaleZ())
                .append(" offset=").append(a.offsetX()).append(',').append(a.offsetY()).append(',').append(a.offsetZ())
                .append(" castsShadow=").append(a.castsShadow()).append(" occludes=").append(a.occludes())
                .append(" clipMask=0x").append(Integer.toHexString(a.clipMask())).append('\n'));
        return sb.toString();
    }

    /** Draws exact authored topology using the canonical TerrainMeshBuilder. */
    private void renderLiveTilePreview(LoadedOsrsCacheSession cache, TileSnapshot snapshot) {
        float size = 96.0f;
        float x = ImGui.getCursorScreenPos().x;
        float y = ImGui.getCursorScreenPos().y;
        ImDrawList draw = ImGui.getWindowDrawList();

        int underlayRgb = 0xFF2A2A2A;
        if (cache != null && snapshot.underlayId() > 0) {
            var def = cache.bundle().definitions().underlay(FloorId.definitionId(snapshot.underlayId()));
            if (def.isPresent()) underlayRgb = floorDisplayRgb(def.get());
        }

        int overlayRgb = 0xFF4A4A4A;
        if (cache != null && snapshot.overlayId() > 0) {
            var def = cache.bundle().definitions().overlay(FloorId.definitionId(snapshot.overlayId()));
            if (def.isPresent()) overlayRgb = floorDisplayRgb(def.get());
        }

        var mesh = new TerrainMeshBuilder().build(snapshot);
        draw.addRectFilled(x, y, x + size, y + size, toDrawListColor(underlayRgb), 2.0f);
        for (var face : mesh.faces()) {
            if (face.material() == 1 && snapshot.overlayId() <= 0) continue;
            int color = toDrawListColor(face.material() == 1 ? overlayRgb : underlayRgb);
            var a = mesh.vertices().get(face.a());
            var b = mesh.vertices().get(face.b());
            var c = mesh.vertices().get(face.c());
            draw.addTriangleFilled(
                    x + a.x() / 128.0f * size, y + size - a.y() / 128.0f * size,
                    x + b.x() / 128.0f * size, y + size - b.y() / 128.0f * size,
                    x + c.x() / 128.0f * size, y + size - c.y() / 128.0f * size,
                    color);
        }

        draw.addRect(x, y, x + size, y + size, toDrawListColor(0xFF64748B), 2.0f, 0, 1.5f);
        draw.addText(x + size / 2.0f - 10.0f, y - 15.0f, toDrawListColor(0xFFE2E8F0), "N");
        ImGui.dummy(size, size);
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
