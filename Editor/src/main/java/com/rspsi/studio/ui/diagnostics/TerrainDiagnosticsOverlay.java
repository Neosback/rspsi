package com.rspsi.studio.ui.diagnostics;

import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.terrain.TerrainFace;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * Opt-in terrain diagnostics overlay. It projects the canonical terrain mesh
 * for the hovered tile and distinguishes underlay, overlay, bridge and object
 * diagnostics without modifying renderer depth state.
 */
public final class TerrainDiagnosticsOverlay implements StudioPlugin {
    public static final String ID = "studio.terrain-diagnostics";
    private final ImBoolean visible = new ImBoolean(false);
    private final ImBoolean labels = new ImBoolean(true);
    private final ImBoolean normals = new ImBoolean(false);
    private final TerrainMeshBuilder meshes = new TerrainMeshBuilder();

    @Override public String id() { return ID; }
    @Override public String name() { return "Terrain Diagnostics"; }
    @Override public String description() {
        return "Face/material diagnostics for authored terrain, bridges and hovered objects.";
    }
    @Override public String icon() { return StudioIcons.BUG_REPORT; }

    @Override
    public void renderOverlay(ImDrawList ignored, StudioPanelContext context) {
        if (!visible.get() || context.viewport() == null || context.session() == null) return;
        var pick = context.viewport().lastPick().orElse(null);
        if (pick == null) return;

        TileCoordinate absolute = pick.tile();
        TileCoordinate local = localCoordinate(context, absolute);
        if (local == null) return;

        var snapshot = context.session().world().tile(local).snapshot();
        var mesh = meshes.build(snapshot);
        var draw = context.viewport().createOverlayDraw();
        float baseX = absolute.x() * 128.0f;
        float baseZ = absolute.y() * 128.0f;

        for (int faceId = 0; faceId < mesh.faces().size(); faceId++) {
            TerrainFace face = mesh.faces().get(faceId);
            var a = mesh.vertices().get(face.a());
            var b = mesh.vertices().get(face.b());
            var c = mesh.vertices().get(face.c());
            int color = face.material() == 0 ? 0x22D3EEFF : 0xFACC15FF;

            draw.line(baseX + a.x(), a.height(), baseZ + a.y(),
                    baseX + b.x(), b.height(), baseZ + b.y(), color, 2.0f);
            draw.line(baseX + b.x(), b.height(), baseZ + b.y(),
                    baseX + c.x(), c.height(), baseZ + c.y(), color, 2.0f);
            draw.line(baseX + c.x(), c.height(), baseZ + c.y(),
                    baseX + a.x(), a.height(), baseZ + a.y(), color, 2.0f);

            float cx = baseX + (a.x() + b.x() + c.x()) / 3.0f;
            float cy = (a.height() + b.height() + c.height()) / 3.0f;
            float cz = baseZ + (a.y() + b.y() + c.y()) / 3.0f;

            if (labels.get()) {
                draw.worldLabel((face.material() == 0 ? "UNDERLAY" : "OVERLAY")
                                + " FACE " + faceId + "  bias=0",
                        cx, cy, cz, color, 0x0F172ACC);
            }

            if (normals.get()) {
                float ux = b.x() - a.x();
                float uy = b.height() - a.height();
                float uz = b.y() - a.y();
                float vx = c.x() - a.x();
                float vy = c.height() - a.height();
                float vz = c.y() - a.y();
                float nx = uy * vz - uz * vy;
                float ny = uz * vx - ux * vz;
                float nz = ux * vy - uy * vx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 0.0001f) {
                    nx /= len; ny /= len; nz /= len;
                    draw.arrow(cx, cy, cz, cx + nx * 48.0f, cy + ny * 48.0f, cz + nz * 48.0f,
                            0xFFFFFFFF);
                }
            }
        }

        if ((snapshot.flags() & OsrsTileFlags.BRIDGE) != 0) {
            draw.tileOutline(absolute, 0xE879F9FF);
            if (labels.get()) draw.tileLabel("BRIDGE FACE", absolute);
        }

        if (pick.objectHit()) {
            draw.tileOutline(absolute, 0x22C55EFF);
            if (labels.get()) {
                draw.worldLabel("OBJECT FACE #" + pick.objectId(),
                        baseX + 64.0f, snapshot.southWestHeight(), baseZ + 64.0f,
                        0x22C55EFF, 0x0F172ACC);
            }
        }
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.checkbox("Show terrain diagnostics##terrain-diag-visible", visible);
        ImGui.checkbox("Show face IDs and depth bias##terrain-diag-labels", labels);
        ImGui.checkbox("Show face normals##terrain-diag-normals", normals);
        ImGui.textDisabled("Cyan = underlay, yellow = overlay, magenta = bridge, green = object.");
    }

    private static TileCoordinate localCoordinate(StudioPanelContext context, TileCoordinate absolute) {
        var world = context.session().world();
        if (world.contains(absolute)) return absolute;
        if (absolute.x() < 0 || absolute.y() < 0 || absolute.plane() < 0) return null;
        WorldTileAddress address = WorldTileAddress.of(absolute.x(), absolute.y(), absolute.plane());
        return world.contains(absolute.plane(), address.regionLocalX(), address.regionLocalY())
                ? new TileCoordinate(absolute.plane(), address.regionLocalX(), address.regionLocalY())
                : null;
    }
}
