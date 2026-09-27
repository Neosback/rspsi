package com.rspsi.studio.ui.panels;

import com.rspsi.editor.render.BackfacePolicy;
import com.rspsi.editor.render.RenderSettingKeys;
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
 * <p>Plane / height visibility follows Terraini's model:
 * <ul>
 *   <li><b>Height level</b> — spinner 0-3: which plane the camera sits on.</li>
 *   <li><b>Show All Height Levels</b> — when on, every plane is rendered
 *       (Terraini {@code allHeightsVisible=true}); when off, only planes
 *       0..height-level are shown ({@code VisiblePlaneWindow.maxPlaneExclusive}).</li>
 *   <li><b>Show Hidden Tiles</b> — highlight tiles flagged hidden (colour 12345678).</li>
 *   <li><b>Show Empty Tiles</b> — draw grey placeholder for tiles with no underlay/overlay.</li>
 * </ul></p>
 */
public final class MapSettingsPanel implements StudioPanel {
    public static final String ID = "studio.settings";

    private static final String[] PLANES = {"Level 0", "Level 1", "Level 2", "Level 3"};
    private static final String[] MSAA = {"Off", "2x", "4x", "8x"};
    private static final int[] MSAA_VALUES = {0, 2, 4, 8};

    @Override public String id() { return ID; }
    @Override public String title() { return "Map Settings"; }
    @Override public String icon() { return StudioIcons.TUNE; }
    @Override public DockRegion preferredRegion() { return DockRegion.RIGHT; }
    @Override public Set<DockRegion> allowedRegions() { return EnumSet.of(DockRegion.RIGHT); }
    @Override public int order() { return 40; }

    @Override
    public void render(StudioPanelContext context) {
        SettingsStore settings = context.settings();

        if (SettingRows.begin("Terrain", true)) {
            // --- Terraini height-level model ---
            ImInt height = new ImInt(settings.snapshot().get(RenderSettingKeys.CURRENT_HEIGHT));
            if (SettingRows.combo("Height level", height, PLANES)) {
                settings.set(RenderSettingKeys.CURRENT_HEIGHT, height.get());
            }
            toggle(settings, RenderSettingKeys.ALL_HEIGHTS_VISIBLE, "Show All Height Levels");
            toggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain surfaces");
            toggle(settings, RenderSettingKeys.BRIDGE_TILES_VISIBLE, "Bridge tiles");
            toggle(settings, RenderSettingKeys.HIDDEN_TILES_VISIBLE, "Show Hidden Tiles");
            toggle(settings, RenderSettingKeys.EMPTY_TILES_VISIBLE, "Show Empty Tiles");
            SettingRows.end();
        }

        if (SettingRows.begin("Objects", true)) {
            toggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Show objects");
            toggle(settings, RenderSettingKeys.WALLS_VISIBLE, "Walls");
            toggle(settings, RenderSettingKeys.WALL_DECORATIONS_VISIBLE, "Wall decorations");
            toggle(settings, RenderSettingKeys.GROUND_OBJECTS_VISIBLE, "Game objects");
            toggle(settings, RenderSettingKeys.GROUND_DECORATIONS_VISIBLE, "Ground decorations");
            toggle(settings, RenderSettingKeys.INVISIBLE_OBJECTS_VISIBLE, "Invisible objects (collision only)");
            toggle(settings, RenderSettingKeys.OBJECT_ANIMATIONS, "Object animations");
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

    private static int msaaIndex(int samples) {
        for (int i = 0; i < MSAA_VALUES.length; i++) if (MSAA_VALUES[i] == samples) return i;
        return 0;
    }
}
