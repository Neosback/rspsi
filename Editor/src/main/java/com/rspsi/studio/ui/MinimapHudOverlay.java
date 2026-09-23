package com.rspsi.studio.ui;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.studio.NativeSceneViewport;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import com.rspsi.studio.ui.hud.ViewportHudManager;

/**
 * Authentic circular OSRS Minimap HUD overlay rendered in the top-right of the 3D viewport.
 * Features:
 * - Circular stone frame (minimap-frame.png)
 * - Rotating compass tracking camera yaw with North-reset on click
 * - Interactive World Map toggle button
 * - Live radar terrain with camera center indicator
 */
public final class MinimapHudOverlay {

    public static final float HUD_WIDTH = 181.0f;
    public static final float HUD_HEIGHT = 165.0f;
    private static final float RADAR_CENTER_X = 98.5f;
    private static final float RADAR_CENTER_Y = 82.5f;
    private static final float RADAR_RADIUS = 74.0f;

    private Runnable onWorldMapClick;

    private final MinimapTextureService textureService = new MinimapTextureService();
    private final SpriteTextureCache iconTextures = new SpriteTextureCache();
    private java.util.List<com.rspsi.editor.minimap.MinimapIcons.Icon> icons;
    private WorldDocument iconWorld;
    private int iconPlane = -1;
    private EditorSession listenedSession;

    public void setOnWorldMapClick(Runnable callback) {
        this.onWorldMapClick = callback;
    }

    public void render(StudioPanelContext context, float vpX, float vpY, float vpWidth, float vpHeight) {
        NativeSceneViewport viewport = context.viewport();
        if (viewport == null || context.huds() == null) return;

        context.huds().register("studio.minimap-hud", ViewportHudManager.Quadrant.TOP_RIGHT, 10);
        var placement = context.huds().place("studio.minimap-hud", HUD_WIDTH, HUD_HEIGHT);
        if (placement == null) return;
        float hudX = placement.x();
        float hudY = placement.y();

        ImGui.setNextWindowPos(hudX, hudY, ImGuiCond.Always);
        ImGui.setNextWindowSize(HUD_WIDTH, HUD_HEIGHT, ImGuiCond.Always);

        int flags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoBackground
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoScrollbar;

        if (!ImGui.begin("##minimap_hud_overlay", flags)) {
            ImGui.end();
            return;
        }

        ImDrawList dl = ImGui.getWindowDrawList();
        float radarX = hudX + RADAR_CENTER_X;
        float radarY = hudY + RADAR_CENTER_Y;

        // 1. Radar background (black circle)
        dl.addCircleFilled(radarX, radarY, RADAR_RADIUS, 0xFF0A0D12, 48);

        // 2. Terrain radar rendering (authentic OSRS minimap texture fan)
        renderRadarTerrain(dl, context, radarX, radarY);

        // 3. Center player indicator (white dot with black drop-shadow)
        dl.addRectFilled(radarX - 1.5f, radarY - 1.5f, radarX + 2.5f, radarY + 2.5f, 0xFFFFFFFF);
        dl.addRect(radarX - 2.5f, radarY - 2.5f, radarX + 3.5f, radarY + 3.5f, 0xFF000000, 0.0f, 0, 1.0f);

        // 4. Stone Minimap Frame texture
        BrandingAssets.Texture frame = BrandingAssets.minimapFrame();
        if (frame != null && frame.isValid()) {
            dl.addImage(frame.id, hudX, hudY, hudX + HUD_WIDTH, hudY + HUD_HEIGHT);
        }

        // 5. Rotating Compass (tracking camera yaw, clicking resets to North)
        renderCompass(dl, viewport, hudX, hudY);

        // 6. World Map button
        renderWorldMapButton(dl, hudX, hudY);

        ImGui.end();
    }

    private void renderRadarTerrain(ImDrawList dl, StudioPanelContext context, float radarX, float radarY) {
        NativeSceneViewport viewport = context.viewport();
        EditorSession session = context.session();
        if (session == null || viewport == null) return;
        if (session != listenedSession) {
            listenedSession = session;
            listenedSession.addChangeListener(changed -> {
                textureService.markDirty();
                icons = null;
            });
        }

        WorldDocument world = session.world();
        int activePlane = context.settings().snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
        if (activePlane < 0 || activePlane >= world.planes()) activePlane = 0;

        int texId = 0;
        if (context.cache() != null) {
            texId = textureService.textureForPlane(world, activePlane, context.cache().bundle().definitions());
        }

        // Camera coordinates are absolute world-space. The minimap texture is
        // document-local, so subtract the session WorldWindow exactly once.
        float camTileX = viewport.navigation().camera().x() / 128.0f
                - session.window().originX();
        float camTileZ = viewport.navigation().camera().z() / 128.0f
                - session.window().originY();
        float yaw = viewport.navigation().camera().yaw();

        float cosY = (float) Math.cos(-yaw);
        float sinY = (float) Math.sin(-yaw);

        float docW = Math.max(1.0f, world.width());
        float docL = Math.max(1.0f, world.length());

        float uCenter = camTileX / docW;
        float vCenter = (docL - camTileZ) / docL;

        float scale = 2.6f; // pixels per tile on radar
        float uvRadius = (RADAR_RADIUS / scale) / docW;

        if (texId > 0) {
            int segments = 32;
            float step = (float) (Math.PI * 2.0 / segments);

            for (int i = 0; i < segments; i++) {
                float a0 = i * step;
                float a1 = (i + 1) * step;

                float sa0 = (float) Math.sin(a0);
                float ca0 = -(float) Math.cos(a0);
                float sa1 = (float) Math.sin(a1);
                float ca1 = -(float) Math.cos(a1);

                float p0x = radarX + sa0 * RADAR_RADIUS;
                float p0y = radarY + ca0 * RADAR_RADIUS;
                float p1x = radarX + sa1 * RADAR_RADIUS;
                float p1y = radarY + ca1 * RADAR_RADIUS;

                float u0 = uCenter + (sa0 * cosY - ca0 * sinY) * uvRadius;
                float v0 = vCenter + (sa0 * sinY + ca0 * cosY) * uvRadius;
                float u1 = uCenter + (sa1 * cosY - ca1 * sinY) * uvRadius;
                float v1 = vCenter + (sa1 * sinY + ca1 * cosY) * uvRadius;

                dl.addImageQuad(texId,
                        radarX, radarY,
                        p0x, p0y,
                        p1x, p1y,
                        radarX, radarY,
                        uCenter, vCenter,
                        u0, v0,
                        u1, v1,
                        uCenter, vCenter);
            }
        }
        if (context.cache() != null) {
            renderMapIcons(dl, context, world, activePlane, radarX, radarY,
                    uCenter, vCenter, cosY, sinY, scale * docW, docW, docL);
        }
    }

    /**
     * Map-function icons (bank, shop, altar...) at their objects' tiles, placed
     * with the inverse of the terrain fan's rotation so they stay on their
     * tiles as the camera turns, and clipped to the radar circle.
     */
    private void renderMapIcons(ImDrawList dl, StudioPanelContext context, WorldDocument world, int plane,
                                float radarX, float radarY, float uCenter, float vCenter,
                                float cosY, float sinY, float pixelsPerUv, float docW, float docL) {
        var definitions = context.cache().bundle().definitions();
        if (icons == null || iconWorld != world || iconPlane != plane) {
            icons = com.rspsi.editor.minimap.MinimapIcons.locate(world, definitions, plane);
            iconWorld = world;
            iconPlane = plane;
        }
        for (var icon : icons) {
            float du = (icon.x() + 0.5f) / docW - uCenter;
            float dv = (docL - (icon.y() + 0.5f)) / docL - vCenter;
            float px = radarX + (cosY * du + sinY * dv) * pixelsPerUv;
            float py = radarY + (-sinY * du + cosY * dv) * pixelsPerUv;
            float dx = px - radarX;
            float dy = py - radarY;
            if (dx * dx + dy * dy > (RADAR_RADIUS - 6.0f) * (RADAR_RADIUS - 6.0f)) continue;
            SpriteTextureCache.Texture texture = iconTextures.get(definitions, icon.spriteId());
            if (texture == null) continue;
            float halfW = texture.width() * 0.5f;
            float halfH = texture.height() * 0.5f;
            dl.addImage(texture.id(), px - halfW, py - halfH, px + halfW, py + halfH);
        }
    }

    private void renderCompass(ImDrawList dl, NativeSceneViewport viewport, float hudX, float hudY) {
        BrandingAssets.Texture compassTex = BrandingAssets.compass();
        float compassCenterX = hudX + 21.0f;
        float compassCenterY = hudY + 21.0f;
        float compassRadius = 18.0f;

        float yaw = viewport.navigation().camera().yaw();

        if (compassTex != null && compassTex.isValid()) {
            // Render rotating compass texture using quad fan
            float cosY = (float) Math.cos(-yaw);
            float sinY = (float) Math.sin(-yaw);
            int segments = 24;
            float step = (float) (Math.PI * 2.0 / segments);

            for (int i = 0; i < segments; i++) {
                float a0 = i * step;
                float a1 = (i + 1) * step;

                float sin0 = (float) Math.sin(a0);
                float cos0 = -(float) Math.cos(a0);
                float sin1 = (float) Math.sin(a1);
                float cos1 = -(float) Math.cos(a1);

                float p0x = compassCenterX + (sin0 * cosY - cos0 * sinY) * compassRadius;
                float p0y = compassCenterY + (sin0 * sinY + cos0 * cosY) * compassRadius;
                float p1x = compassCenterX + (sin1 * cosY - cos1 * sinY) * compassRadius;
                float p1y = compassCenterY + (sin1 * sinY + cos1 * cosY) * compassRadius;

                float u0 = 0.5f + sin0 * 0.5f;
                float v0 = 0.5f + cos0 * 0.5f;
                float u1 = 0.5f + sin1 * 0.5f;
                float v1 = 0.5f + cos1 * 0.5f;

                dl.addImageQuad(compassTex.id,
                        compassCenterX, compassCenterY,
                        p0x, p0y,
                        p1x, p1y,
                        compassCenterX, compassCenterY,
                        0.5f, 0.5f,
                        u0, v0,
                        u1, v1,
                        0.5f, 0.5f);
            }
        }

        // Invisible button to capture compass click
        ImGui.setCursorScreenPos(compassCenterX - compassRadius, compassCenterY - compassRadius);
        if (ImGui.invisibleButton("##hud_compass_btn", compassRadius * 2.0f, compassRadius * 2.0f)) {
            ImVec2 mousePos = ImGui.getMousePos();
            float mdx = mousePos.x - compassCenterX;
            float mdy = mousePos.y - compassCenterY;
            if (mdx * mdx + mdy * mdy <= compassRadius * compassRadius) {
                // Reset facing North
                viewport.navigation().north();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset Camera to North");
        }
    }

    private void renderWorldMapButton(ImDrawList dl, float hudX, float hudY) {
        float btnX = hudX + 138.5f;
        float btnY = hudY + 113.0f;
        float btnSize = 32.0f;

        ImGui.setCursorScreenPos(btnX, btnY);
        boolean clicked = ImGui.invisibleButton("##hud_worldmap_btn", btnSize, btnSize);
        boolean hovered = ImGui.isItemHovered();

        BrandingAssets.Texture icon = hovered ? BrandingAssets.worldmapIconHover() : BrandingAssets.worldmapIcon();
        if (icon != null && icon.isValid()) {
            dl.addImage(icon.id, btnX, btnY, btnX + btnSize, btnY + btnSize);
        }

        if (hovered) {
            ImGui.setTooltip("World Map / Minimap Navigator");
        }

        if (clicked && onWorldMapClick != null) {
            onWorldMapClick.run();
        }
    }
}
