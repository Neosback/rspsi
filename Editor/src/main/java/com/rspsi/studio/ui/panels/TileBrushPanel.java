package com.rspsi.studio.ui.panels;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTileAddress;
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
                    renderSingleTileDetail(cache, world, selected.iterator().next());
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

    private void renderSingleTileDetail(LoadedOsrsCacheSession cache, WorldDocument world, TileCoordinate coord) {
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

        ImGui.text("Coordinate: " + coord.x() + ", " + coord.y() + "  (plane " + coord.plane() + ")");
        ImGui.text("Height (SW/SE/NE/NW): " + snapshot.southWestHeight() + " / " + snapshot.southEastHeight()
                + " / " + snapshot.northEastHeight() + " / " + snapshot.northWestHeight());
        ImGui.text("Flags (raw): 0x" + Integer.toHexString(snapshot.flags()));
        if (!snapshot.objects().isEmpty()) {
            ImGui.text("Objects on tile: " + snapshot.objects().size());
        }

        ImGui.spacing();
        renderLiveTilePreview(cache, snapshot);
        ImGui.sameLine(0.0f, 16.0f);
        ImGui.beginGroup();
        ImGui.text("Underlay: " + (snapshot.underlayId() > 0 ? "#" + snapshot.underlayId() : "None"));
        ImGui.text("Overlay: " + (snapshot.overlayId() > 0 ? "#" + snapshot.overlayId() : "None"));
        ImGui.text("Shape: " + snapshot.overlayShape() + "  Rotation: " + snapshot.overlayRotation());
        ImGui.endGroup();
    }

    /** Draws exact authored topology using the canonical TerrainMeshBuilder. */
    private void renderLiveTilePreview(LoadedOsrsCacheSession cache, TileSnapshot snapshot) {
        float size = 96.0f;
        float x = ImGui.getCursorScreenPos().x;
        float y = ImGui.getCursorScreenPos().y;
        ImDrawList draw = ImGui.getWindowDrawList();

        int underlayRgb = 0xFF2A2A2A;
        if (cache != null && snapshot.underlayId() > 0) {
            var def = cache.bundle().definitions().underlay(snapshot.underlayId());
            if (def.isPresent()) underlayRgb = 0xFF000000 | def.get().rgb();
        }

        int overlayRgb = 0xFF4A4A4A;
        if (cache != null && snapshot.overlayId() > 0) {
            var def = cache.bundle().definitions().overlay(snapshot.overlayId());
            if (def.isPresent()) overlayRgb = 0xFF000000 | def.get().rgb();
        }

        var mesh = new TerrainMeshBuilder().build(snapshot);
        draw.addRectFilled(x, y, x + size, y + size, underlayRgb, 2.0f);
        for (var face : mesh.faces()) {
            if (face.material() == 1 && snapshot.overlayId() <= 0) continue;
            int color = face.material() == 1 ? overlayRgb : underlayRgb;
            var a = mesh.vertices().get(face.a());
            var b = mesh.vertices().get(face.b());
            var c = mesh.vertices().get(face.c());
            draw.addTriangleFilled(
                    x + a.x() / 128.0f * size, y + size - a.y() / 128.0f * size,
                    x + b.x() / 128.0f * size, y + size - b.y() / 128.0f * size,
                    x + c.x() / 128.0f * size, y + size - c.y() / 128.0f * size,
                    color);
        }

        draw.addRect(x, y, x + size, y + size, 0xFF64748B, 2.0f, 0, 1.5f);
        draw.addText(x + size / 2.0f - 10.0f, y - 15.0f, 0xFFE2E8F0, "N ↑");
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
            String tooltip = "#" + id + "  -  " + entry.getValue() + " tile(s)";
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
            var def = underlay ? cache.bundle().definitions().underlay(id) : cache.bundle().definitions().overlay(id);
            if (def.isPresent()) {
                rgb = 0xFF000000 | def.get().rgb();
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
            draw.addRectFilled(x, y, x + size, y + size, rgb, 3.0f);
            draw.addRect(x, y, x + size, y + size, 0xFF666666, 3.0f);
            ImGui.dummy(size, size);
        }
        if (tooltip != null && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
    }
}
