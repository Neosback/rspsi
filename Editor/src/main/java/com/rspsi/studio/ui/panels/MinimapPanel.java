package com.rspsi.studio.ui.panels;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.NativeSceneViewport;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;

import java.util.EnumSet;
import java.util.Set;

/**
 * Interactive 2D Minimap / Radar Navigator matching Displee's radar tool.
 * Provides a real-time top-down overview of the loaded region document with
 * tile colors, camera frustum indicator, and click-to-teleport navigation.
 */
public final class MinimapPanel implements StudioPanel {
    public static final String ID = "studio.minimap";

    private final ImInt jumpX = new ImInt(32);
    private final ImInt jumpY = new ImInt(32);
    private final com.rspsi.studio.ui.MinimapTextureService textureService = new com.rspsi.studio.ui.MinimapTextureService();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "WorldMap";
    }

    @Override
    public String icon() {
        return StudioIcons.MAP;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.RIGHT;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public void render(StudioPanelContext context) {
        NativeSceneViewport viewport = context.viewport();
        EditorSession session = context.session();
        LoadedOsrsCacheSession cache = context.cache();

        if (viewport == null) {
            ImGui.textDisabled("Viewport not available");
            return;
        }

        int activePlane = context.settings().snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        int cameraWorldX = Math.max(0, (int) Math.floor(viewport.navigation().camera().x() / 128.0f));
        int cameraWorldY = Math.max(0, (int) Math.floor(viewport.navigation().camera().z() / 128.0f));
        WorldTile cameraWorld = new WorldTile(activePlane, cameraWorldX, cameraWorldY);
        LocalTile cameraLocal = session == null ? null
                : session.coordinates().toLocal(cameraWorld).orElse(null);

        int worldW = session != null ? session.world().width() : 64;
        int worldL = session != null ? session.world().length() : 64;

        // 1. Header with absolute camera info & plane switcher
        ImGui.textColored(0xFF38BDF8, String.format(
                "World: (%d, %d)  Pl: %d", cameraWorldX, cameraWorldY, activePlane));
        ImGui.sameLine();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 2.0f);
        for (int p = 0; p < 4; p++) {
            boolean isCur = (p == activePlane);
            if (isCur) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0xFF3B82F6);
            if (ImGui.button("P" + p + "##pl-sw-" + p)) {
                context.settings().set(RenderSettingKeys.ACTIVE_PLANE, p);
            }
            if (isCur) ImGui.popStyleColor();
            if (p < 3) ImGui.sameLine();
        }
        ImGui.popStyleVar();
        ImGui.separator();

        // 2. Interactive Radar Canvas
        float availW = ImGui.getContentRegionAvailX();
        float radarSize = Math.min(availW, 256.0f);
        int maxDim = Math.max(1, Math.max(worldW, worldL));
        float tileSize = radarSize / maxDim;

        float rx = ImGui.getCursorScreenPos().x + (availW - radarSize) * 0.5f;
        float ry = ImGui.getCursorScreenPos().y;

        ImDrawList draw = ImGui.getWindowDrawList();
        int texId = 0;
        if (session != null && cache != null) {
            texId = textureService.textureForPlane(session.world(), activePlane, cache.bundle().definitions());
        }

        if (texId > 0) {
            // Draw authentic OSRS shaped-tile minimap texture with map scene sprites & wall markers
            draw.addImage(texId, rx, ry, rx + radarSize, ry + radarSize, 0.0f, 1.0f, 1.0f, 0.0f);
        } else {
            // Dark radar fallback
            draw.addRectFilled(rx, ry, rx + radarSize, ry + radarSize, 0xFF141F14, 4.0f);
        }

        // Radar border
        draw.addRect(rx, ry, rx + radarSize, ry + radarSize, 0xFF475569, 4.0f, 0, 1.5f);

        // Invisible button to capture mouse interaction over radar
        ImGui.setCursorScreenPos(rx, ry);
        ImGui.invisibleButton("##radar-canvas", radarSize, radarSize);

        if (ImGui.isItemHovered() && (ImGui.isMouseClicked(ImGuiMouseButton.Left) || ImGui.isMouseDragging(ImGuiMouseButton.Left, 1.0f))) {
            float mouseRelX = ImGui.getIO().getMousePosX() - rx;
            float mouseRelY = ImGui.getIO().getMousePosY() - ry;
            int clickedTileX = Math.max(0, Math.min(worldW - 1, (int) (mouseRelX / tileSize)));
            int clickedTileY = Math.max(0, Math.min(worldL - 1, (worldL - 1 - (int) (mouseRelY / tileSize))));

            if (session != null) {
                viewport.navigationService().synchronizeFromCamera(activePlane);
                viewport.navigationService().jumpTo(
                        session.coordinates().toWorld(
                                new LocalTile(activePlane, clickedTileX, clickedTileY)));
            }
        }

        // Render camera frustum & position dot
        float camRelX = cameraLocal == null ? -1.0f : cameraLocal.x() * tileSize + tileSize * 0.5f;
        float camRelY = cameraLocal == null ? -1.0f
                : (worldL - 1 - cameraLocal.y()) * tileSize + tileSize * 0.5f;
        float camScreenX = rx + camRelX;
        float camScreenY = ry + camRelY;

        if (cameraLocal != null && camRelX >= 0 && camRelX <= radarSize
                && camRelY >= 0 && camRelY <= radarSize) {
            float yaw = viewport.navigation().camera().yaw();
            float dirX = (float) Math.sin(yaw);
            float dirY = -(float) Math.cos(yaw);

            // Draw frustum cone
            draw.addTriangleFilled(
                    camScreenX + dirX * 16.0f, camScreenY + dirY * 16.0f,
                    camScreenX - dirY * 7.0f - dirX * 4.0f, camScreenY + dirX * 7.0f - dirY * 4.0f,
                    camScreenX + dirY * 7.0f - dirX * 4.0f, camScreenY - dirX * 7.0f - dirY * 4.0f,
                    0xCC38BDF8
            );
            draw.addCircleFilled(camScreenX, camScreenY, 3.5f, 0xFFFFFFFF);
        }

        ImGui.spacing();

        // 3. Navigation Shortcuts & Coordinate Jump
        ImGui.pushItemWidth(60.0f);
        ImGui.inputInt("X##nav-x", jumpX, 0, 0);
        ImGui.sameLine();
        ImGui.inputInt("Y##nav-y", jumpY, 0, 0);
        ImGui.popItemWidth();
        ImGui.sameLine();

        if (ImGui.button("Jump##jump-coord")) {
            int targetTileX = Math.max(0, Math.min(worldW - 1, jumpX.get()));
            int targetTileY = Math.max(0, Math.min(worldL - 1, jumpY.get()));
            if (session != null) {
                viewport.navigationService().synchronizeFromCamera(activePlane);
                viewport.navigationService().jumpTo(
                        session.coordinates().toWorld(
                                new LocalTile(activePlane, targetTileX, targetTileY)));
            }
        }

        if (ImGui.button("Center on Camera##re-center", availW, 24.0f)) {
            viewport.navigationService().jumpTo(cameraWorld);
        }

        if (session != null && !session.selection().selectedCoordinates().isEmpty()) {
            if (ImGui.button("Center on Selection##sel-center", availW, 24.0f)) {
                var first = session.selection().selectedCoordinates().iterator().next();
                viewport.navigationService().synchronizeFromCamera(activePlane);
                viewport.navigationService().jumpTo(
                        session.coordinates().toWorld(LocalTile.from(first)));
            }
        }

        if (ImGui.button(StudioIcons.REFRESH + " Refresh Minimap##refresh-minimap", availW, 24.0f)) {
            textureService.markDirty();
        }
    }
}
