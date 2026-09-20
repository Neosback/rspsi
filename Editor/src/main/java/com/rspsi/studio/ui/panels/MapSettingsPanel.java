package com.rspsi.studio.ui.panels;

import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.render.SceneVisibilityPolicy;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.EnumSet;
import java.util.Set;

/**
 * Native Map Settings panel mirroring Displee's Settings panel (Gear icon).
 * Houses all map-specific view, terrain, object, minimap, and player options.
 */
public final class MapSettingsPanel implements StudioPanel {
    public static final String ID = "studio.settings";

    private final ImBoolean showCollisionSpots = new ImBoolean(false);
    private final ImBoolean showMapSquareIds = new ImBoolean(true);
    private final ImBoolean showMapSquareGrid = new ImBoolean(true);
    private final ImBoolean highWaterDetail = new ImBoolean(true);
    private final ImBoolean lightingDetail = new ImBoolean(true);
    private final ImBoolean shadows = new ImBoolean(true);
    private final ImBoolean terrainTextures = new ImBoolean(true);
    private final ImBoolean blending = new ImBoolean(true);
    private final ImBoolean showGridHelper = new ImBoolean(false);
    private final ImBoolean animateObjects = new ImBoolean(true);
    private final ImBoolean highlightPlayerTile = new ImBoolean(true);

    private final ImInt emptyTilesSelection = new ImInt(0);
    private final ImInt invisibleTilesSelection = new ImInt(0);
    private final ImInt hiddenTilesSelection = new ImInt(0);
    private final ImInt sceneryShadowSelection = new ImInt(0);

    private static final String[] TILE_LEVEL_OPTIONS = {"All visible levels", "Current level only", "None"};
    private static final String[] SHADOW_OPTIONS = {"None", "Simple", "Full"};

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Map Settings";
    }

    @Override
    public String icon() {
        return StudioIcons.TUNE;
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
        return 40;
    }

    @Override
    public void render(StudioPanelContext context) {
        SettingsStore settings = context.settings();

        // 1. Map settings
        if (ImGui.collapsingHeader("Map settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textDisabled("Size: 64 x 64 tiles");
        }

        // 2. Minimap settings
        if (ImGui.collapsingHeader("Minimap settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.checkbox("Show collision spots", showCollisionSpots);
        }

        // 3. World map settings
        if (ImGui.collapsingHeader("World map settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.checkbox("Show map square ids", showMapSquareIds);
            ImGui.checkbox("Show map square grid", showMapSquareGrid);
        }

        // 4. Terrain settings
        if (ImGui.collapsingHeader("Terrain settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            int currentPlane = settings.snapshot().get(RenderSettingKeys.ACTIVE_PLANE);
            ImInt planeVal = new ImInt(currentPlane);
            String[] planes = {"Level 0", "Level 1", "Level 2", "Level 3"};
            if (ImGui.combo("Player level", planeVal, planes)) {
                settings.set(RenderSettingKeys.ACTIVE_PLANE, planeVal.get());
            }

            boolean showAll = settings.snapshot().get(RenderSettingKeys.PLANE_SELECTION)
                    == SceneVisibilityPolicy.PlaneSelection.ALL;
            ImBoolean showAllVal = new ImBoolean(showAll);
            if (ImGui.checkbox("Show all levels", showAllVal)) {
                settings.set(RenderSettingKeys.PLANE_SELECTION,
                        showAllVal.get() ? SceneVisibilityPolicy.PlaneSelection.ALL
                                : SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE);
            }

            ImGui.checkbox("High water detail", highWaterDetail);
            ImGui.checkbox("Lighting detail", lightingDetail);
            ImGui.checkbox("Shadows", shadows);

            toggleSetting(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain surfaces");
            ImGui.checkbox("Textures##terrain-tex", terrainTextures);
            ImGui.checkbox("Blending", blending);
            ImGui.checkbox("Show grid helper", showGridHelper);

            if (ImGui.treeNode("Show tile settings")) {
                ImGui.combo("Show empty tiles", emptyTilesSelection, TILE_LEVEL_OPTIONS);
                ImGui.combo("Show invisible tiles", invisibleTilesSelection, TILE_LEVEL_OPTIONS);
                ImGui.combo("Show hidden tiles", hiddenTilesSelection, TILE_LEVEL_OPTIONS);
                toggleSetting(settings, RenderSettingKeys.HIDDEN_TILES_VISIBLE, "Display hidden tiles");
                toggleSetting(settings, RenderSettingKeys.BRIDGE_TILES_VISIBLE, "Display bridge tiles");
                toggleSetting(settings, RenderSettingKeys.ROOFS_VISIBLE, "Display roofs");
                ImGui.treePop();
            }
        }

        // 5. Object settings
        if (ImGui.collapsingHeader("Object settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            toggleSetting(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Show objects");
            toggleSetting(settings, RenderSettingKeys.WALLS_VISIBLE, "Show walls");
            toggleSetting(settings, RenderSettingKeys.WALL_DECORATIONS_VISIBLE, "Show wall decorations");
            toggleSetting(settings, RenderSettingKeys.GROUND_OBJECTS_VISIBLE, "Show ground objects");
            toggleSetting(settings, RenderSettingKeys.GROUND_DECORATIONS_VISIBLE, "Show ground decorations");
            ImGui.combo("Scenery shadows", sceneryShadowSelection, SHADOW_OPTIONS);
            ImGui.checkbox("Animate", animateObjects);
        }

        // 6. Player settings
        if (ImGui.collapsingHeader("Player settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.checkbox("Highlight player tile", highlightPlayerTile);
        }

        // 7. Extra view & debug settings
        if (ImGui.collapsingHeader("Diagnostics & Culling", ImGuiTreeNodeFlags.None)) {
            toggleSetting(settings, RenderSettingKeys.WIREFRAME, "Wireframe mode");
            toggleSetting(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision spots overlay");

            if (context.viewport() != null) {
                ImGui.textDisabled("BACKFACES");
                String[] cullLabels = {"Off", "Cull CCW", "Cull CW"};
                for (int m = 0; m < cullLabels.length; m++) {
                    if (m > 0) ImGui.sameLine();
                    if (ImGui.radioButton(cullLabels[m], context.viewport().cullMode() == m)) {
                        context.viewport().setCullMode(m);
                    }
                }
            }
        }
    }

    private static void toggleSetting(SettingsStore settings, SettingKey<Boolean> key, String label) {
        boolean val = settings.snapshot().get(key);
        ImBoolean imVal = new ImBoolean(val);
        if (ImGui.checkbox(label + "##" + key.id(), imVal)) {
            settings.set(key, imVal.get());
        }
    }
}
