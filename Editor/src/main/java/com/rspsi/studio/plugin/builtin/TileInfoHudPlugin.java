package com.rspsi.studio.plugin.builtin;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.PickResult;
import com.rspsi.studio.NativeSceneViewport;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanelContext;
import com.rspsi.studio.ui.hud.ViewportHudManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.Optional;

/**
 * Built-in StudioPlugin providing an authentic floating Tile Information HUD badge
 * over the 3D viewport with full user configurability.
 */
public final class TileInfoHudPlugin implements StudioPlugin {

    public static final String ID = "studio.tile-info-hud";

    private final ImBoolean showCoordinates = new ImBoolean(true);
    private final ImBoolean showPlane = new ImBoolean(true);
    private final ImBoolean showHeight = new ImBoolean(true);
    private final ImBoolean showObject = new ImBoolean(true);
    private final ImInt anchorCorner = new ImInt(0); // 0=Bottom-Left, 1=Top-Left, 2=Bottom-Right, 3=Top-Right
    private final ImFloat bgAlpha = new ImFloat(0.75f);

    private static final String[] ANCHOR_NAMES = {
            "Bottom-Left",
            "Top-Left",
            "Bottom-Right",
            "Top-Right"
    };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Tile Information HUD";
    }

    @Override
    public String description() {
        return "Floating viewport badge displaying live tile coordinates, elevation, and hovered object details.";
    }

    @Override
    public String version() {
        return "1.1.0";
    }

    @Override
    public String author() {
        return "OpenRune";
    }

    @Override
    public String icon() {
        return StudioIcons.EXPLORE;
    }

    @Override
    public void renderHUD(StudioPanelContext context) {
        NativeSceneViewport viewport = context.viewport();
        if (viewport == null) return;

        Optional<PickResult> lastPick = viewport.lastPick();
        if (lastPick.isEmpty()) return;

        PickResult hit = lastPick.get();
        TileCoordinate coord = hit.tile();
        int height = 0;
        if (context.session() != null && context.session().world().contains(coord)) {
            height = context.session().world().tile(coord).snapshot().southWestHeight();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(StudioIcons.EXPLORE);

        if (showCoordinates.get()) {
            sb.append(String.format("  Tile: (%d, %d)", coord.x(), coord.y()));
        }

        if (showPlane.get()) {
            if (sb.length() > 2) sb.append("  |  ");
            sb.append("Plane ").append(hit.plane());
        }

        if (showHeight.get()) {
            if (sb.length() > 2) sb.append("  |  ");
            sb.append("Height ").append(height);
        }

        if (showObject.get() && hit.objectHit()) {
            LoadedOsrsCacheSession cache = context.cache();
            String objName = "Object #" + hit.objectId();
            if (cache != null) {
                var def = cache.bundle().definitions().object(hit.objectId());
                if (def.isPresent() && !def.get().name().isBlank()) {
                    objName = def.get().name() + " (#" + hit.objectId() + ")";
                }
            }
            if (sb.length() > 2) sb.append("  |  ");
            sb.append(StudioIcons.OBJECT).append(" ").append(objName);
        }

        String text = sb.toString();
        if (text.isBlank() || text.equals(StudioIcons.EXPLORE)) return;

        float padX = 10.0f;
        float padY = 4.0f;
        float textW = ImGui.calcTextSize(text).x;
        float badgeW = textW + padX * 2.0f;
        float badgeH = 22.0f;

        if (context.huds() == null) return;
        ViewportHudManager.Quadrant quadrant = switch (anchorCorner.get()) {
            case 1 -> ViewportHudManager.Quadrant.TOP_LEFT;
            case 2 -> ViewportHudManager.Quadrant.BOTTOM_RIGHT;
            case 3 -> ViewportHudManager.Quadrant.TOP_RIGHT;
            default -> ViewportHudManager.Quadrant.BOTTOM_LEFT;
        };
        context.huds().register(ID, quadrant, 20);
        var placement = context.huds().place(ID, badgeW, badgeH);
        if (placement == null) return;
        float hudX = placement.x();
        float hudY = placement.y();

        ImDrawList dl = ImGui.getWindowDrawList();
        int alphaByte = (int) (Math.max(0.1f, Math.min(1.0f, bgAlpha.get())) * 255.0f);
        int bgColor = (alphaByte << 24) | 0x0F172A;
        int borderColor = (alphaByte << 24) | 0x334155;

        // Semi-transparent rounded pill
        dl.addRectFilled(hudX, hudY, hudX + badgeW, hudY + badgeH, bgColor, 6.0f);
        dl.addRect(hudX, hudY, hudX + badgeW, hudY + badgeH, borderColor, 6.0f, 0, 1.0f);

        // Text
        dl.addText(hudX + padX, hudY + padY, 0xFFE2E8F0, text);
    }

    @Override
    public void renderSettings(StudioPanelContext context) {
        ImGui.textColored(0xFF38BDF8, StudioIcons.TUNE + "  HUD Display Elements");
        ImGui.checkbox("Show Tile Coordinates##hud-coords", showCoordinates);
        ImGui.checkbox("Show Plane##hud-plane", showPlane);
        ImGui.checkbox("Show Elevation / Height##hud-height", showHeight);
        ImGui.checkbox("Show Hovered Object##hud-obj", showObject);

        ImGui.separator();
        ImGui.textColored(0xFF38BDF8, StudioIcons.SETTINGS + "  Layout & Style");
        ImGui.combo("Anchor Position##hud-anchor", anchorCorner, ANCHOR_NAMES);
        ImGui.sliderFloat("Background Opacity##hud-alpha", bgAlpha.getData(), 0.1f, 1.0f, "%.2f");

        if (ImGui.button("Reset HUD Defaults##hud-reset")) {
            showCoordinates.set(true);
            showPlane.set(true);
            showHeight.set(true);
            showObject.set(true);
            anchorCorner.set(0);
            bgAlpha.set(0.75f);
        }
    }
}
