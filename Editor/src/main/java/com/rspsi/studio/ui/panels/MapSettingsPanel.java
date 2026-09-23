package com.rspsi.studio.ui.panels;

import com.rspsi.editor.render.BackfacePolicy;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.render.SceneVisibilityPolicy;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.SettingRows;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.EnumSet;
import java.util.Set;

/**
 * Map Settings (gear icon), laid out like Displee's settings panel: label on
 * the left, control on the right, no horizontal scrolling.
 *
 * <p>Every live control is bound to a typed render setting. Controls from
 * Displee's panel that Studio does not implement yet are listed greyed out
 * with the reason, instead of being checkboxes that silently do nothing.</p>
 */
public final class MapSettingsPanel implements StudioPanel {
    public static final String ID = "studio.settings";

    private static final String[] PLANES = {"Level 0", "Level 1", "Level 2", "Level 3"};
    private static final String[] PLANE_MODES = {"All levels", "Authored level", "Effective level", "Client traversal"};
    private static final SceneVisibilityPolicy.PlaneSelection[] PLANE_MODE_VALUES = {
            SceneVisibilityPolicy.PlaneSelection.ALL,
            SceneVisibilityPolicy.PlaneSelection.AUTHORED_PLANE,
            SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE,
            SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL};
    private static final String[] MSAA = {"Off", "2x", "4x", "8x"};
    private static final int[] MSAA_VALUES = {0, 2, 4, 8};

    @Override public String id() { return ID; }
    @Override public String title() { return "Map Settings"; }
    @Override public String icon() { return StudioIcons.TUNE; }
    @Override public DockRegion preferredRegion() { return DockRegion.RIGHT; }
    @Override public Set<DockRegion> allowedRegions() { return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM); }
    @Override public int order() { return 40; }

    @Override
    public void render(StudioPanelContext context) {
        SettingsStore settings = context.settings();

        if (SettingRows.begin("Terrain", true)) {
            ImInt plane = new ImInt(settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE));
            if (SettingRows.combo("Player level", plane, PLANES)) {
                settings.set(RenderSettingKeys.ACTIVE_PLANE, plane.get());
            }
            ImInt mode = new ImInt(indexOf(settings.snapshot().get(RenderSettingKeys.PLANE_SELECTION)));
            if (SettingRows.combo("Levels shown", mode, PLANE_MODES)) {
                settings.set(RenderSettingKeys.PLANE_SELECTION, PLANE_MODE_VALUES[mode.get()]);
            }
            toggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain surfaces");
            toggle(settings, RenderSettingKeys.BRIDGE_TILES_VISIBLE, "Bridge tiles");
            toggle(settings, RenderSettingKeys.HIDDEN_TILES_VISIBLE, "Hidden tiles");
            toggle(settings, RenderSettingKeys.ROOFS_VISIBLE, "Roofs");
            SettingRows.notImplemented("Textures", "Terrain textures cannot be switched off yet.");
            SettingRows.notImplemented("Blending", "Scene-wide underlay blending toggle is not wired; "
                    + "the Tile Inspector preview has a per-tile blending switch.");
            SettingRows.notImplemented("High water detail", "No water detail levels yet.");
            SettingRows.notImplemented("Lighting detail", "Low-detail lighting is not simulated yet.");
            SettingRows.notImplemented("Shadows", "Terrain shadow toggle is not wired yet.");
            SettingRows.notImplemented("Grid helper", "Tile grid overlay is not implemented yet.");
            SettingRows.end();
        }

        if (SettingRows.begin("Objects", true)) {
            toggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Show objects");
            toggle(settings, RenderSettingKeys.WALLS_VISIBLE, "Walls");
            toggle(settings, RenderSettingKeys.WALL_DECORATIONS_VISIBLE, "Wall decorations");
            toggle(settings, RenderSettingKeys.GROUND_OBJECTS_VISIBLE, "Game objects");
            toggle(settings, RenderSettingKeys.GROUND_DECORATIONS_VISIBLE, "Ground decorations");
            toggle(settings, RenderSettingKeys.INVISIBLE_OBJECTS_VISIBLE, "Invisible objects (collision only)");
            SettingRows.notImplemented("Textures", "Object textures cannot be switched off yet.");
            SettingRows.notImplemented("Scenery shadows", "Object shadow modes are not implemented yet.");
            SettingRows.notImplemented("Animate", "Animation always runs; a pause toggle is not wired yet.");
            SettingRows.end();
        }

        if (SettingRows.begin("Display", true)) {
            float[] brightness = {settings.snapshot().get(RenderSettingKeys.BRIGHTNESS).floatValue()};
            if (SettingRows.sliderFloat("Brightness", brightness, 0.0f, 4.0f, "%.2f")) {
                settings.set(RenderSettingKeys.BRIGHTNESS, (double) brightness[0]);
            }
            float[] exposure = {settings.snapshot().get(RenderSettingKeys.EXPOSURE).floatValue()};
            if (SettingRows.sliderFloat("Exposure", exposure, -8.0f, 8.0f, "%.1f")) {
                settings.set(RenderSettingKeys.EXPOSURE, (double) exposure[0]);
            }
            int[] fog = {settings.snapshot().get(RenderSettingKeys.FOG_DEPTH_TILES)};
            if (SettingRows.sliderInt("Fog depth (tiles, 0 = off)", fog, 0, 200)) {
                settings.set(RenderSettingKeys.FOG_DEPTH_TILES, fog[0]);
            }
            ImInt msaa = new ImInt(msaaIndex(settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES)));
            if (SettingRows.combo("Anti-aliasing", msaa, MSAA)) {
                settings.set(RenderSettingKeys.MSAA_SAMPLES, MSAA_VALUES[msaa.get()]);
            }
            SettingRows.end();
        }

        if (SettingRows.begin("Minimap & world map", false)) {
            SettingRows.notImplemented("Collision spots", "Minimap collision spots are not implemented yet.");
            SettingRows.notImplemented("Map square ids", "World map square labels are not implemented yet.");
            SettingRows.notImplemented("Map square grid", "World map square grid is not implemented yet.");
            SettingRows.end();
        }

        if (SettingRows.begin("Diagnostics", false)) {
            toggle(settings, RenderSettingKeys.WIREFRAME, "Wireframe");
            toggle(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision overlay");
            BackfacePolicy.NativeCullingMode[] modes = BackfacePolicy.NativeCullingMode.values();
            String[] labels = new String[modes.length];
            int current = 0;
            for (int i = 0; i < modes.length; i++) {
                labels[i] = modes[i].toString();
                if (modes[i] == settings.snapshot().get(RenderSettingKeys.NATIVE_CULLING_MODE)) current = i;
            }
            ImInt culling = new ImInt(current);
            if (SettingRows.combo("Backface culling", culling, labels)) {
                settings.set(RenderSettingKeys.NATIVE_CULLING_MODE, modes[culling.get()]);
            }
            SettingRows.end();
        }
    }

    private static void toggle(SettingsStore settings, SettingKey<Boolean> key, String label) {
        ImBoolean value = new ImBoolean(settings.snapshot().get(key));
        if (SettingRows.checkbox(label, value)) {
            settings.set(key, value.get());
        }
    }

    private static int indexOf(SceneVisibilityPolicy.PlaneSelection selection) {
        for (int i = 0; i < PLANE_MODE_VALUES.length; i++) if (PLANE_MODE_VALUES[i] == selection) return i;
        return 2;
    }

    private static int msaaIndex(int samples) {
        for (int i = 0; i < MSAA_VALUES.length; i++) if (MSAA_VALUES[i] == samples) return i;
        return 0;
    }
}
