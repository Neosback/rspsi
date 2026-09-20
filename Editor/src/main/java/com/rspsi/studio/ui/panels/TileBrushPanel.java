package com.rspsi.studio.ui.panels;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tile brush configuration panel matching Displee's Tile/Brush panel.
 */
public final class TileBrushPanel implements StudioPanel {
    public static final String ID = "studio.tile-brush";

    private final ImInt brushType = new ImInt(0);
    private final ImInt brushSize = new ImInt(1);
    private final ImInt overlayInput = new ImInt(0);
    private final ImInt underlayInput = new ImInt(0);
    private final ImInt shapeInput = new ImInt(0);
    private final ImInt rotationInput = new ImInt(0);
    private final ImInt heightInput = new ImInt(0);

    private static final String[] BRUSH_TYPES = {"Square", "Circle"};
    private static final String[] SHAPE_NAMES = {
            "Shape 0 (Full)", "Shape 1 (Diagonal)", "Shape 2 (Left)", "Shape 3 (Right)",
            "Shape 4 (Corner TL)", "Shape 5 (Corner TR)", "Shape 6 (Corner BR)", "Shape 7 (Corner BL)",
            "Shape 8 (Inv TL)", "Shape 9 (Inv TR)", "Shape 10 (Inv BR)", "Shape 11 (Inv BL)"
    };
    private static final String[] ROTATION_NAMES = {"North (0)", "East (1)", "South (2)", "West (3)"};

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Tile Brush";
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
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void render(StudioPanelContext context) {
        SettingsStore settings = context.settings();
        LoadedOsrsCacheSession cache = context.cache();

        // 1. Brush section
        if (ImGui.collapsingHeader("Brush", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.combo("Type##brush-type", brushType, BRUSH_TYPES);
            if (ImGui.inputInt("Size##brush-size", brushSize)) {
                if (brushSize.get() < 1) brushSize.set(1);
                if (brushSize.get() > 32) brushSize.set(32);
                settings.set(EditorSettingKeys.TERRAIN_HEIGHT_RADIUS, brushSize.get());
            }
        }

        // 2. Tile attributes
        if (ImGui.collapsingHeader("Tile", ImGuiTreeNodeFlags.DefaultOpen)) {
            int curOverlay = settings.snapshot().get(EditorSettingKeys.TERRAIN_OVERLAY);
            overlayInput.set(curOverlay);
            if (ImGui.inputInt("Overlay##tile-overlay", overlayInput)) {
                settings.set(EditorSettingKeys.TERRAIN_OVERLAY, Math.max(0, overlayInput.get()));
            }

            int curUnderlay = settings.snapshot().get(EditorSettingKeys.TERRAIN_UNDERLAY);
            underlayInput.set(curUnderlay);
            if (ImGui.inputInt("Underlay##tile-underlay", underlayInput)) {
                settings.set(EditorSettingKeys.TERRAIN_UNDERLAY, Math.max(0, underlayInput.get()));
            }

            int curShape = settings.snapshot().get(EditorSettingKeys.TERRAIN_OVERLAY_SHAPE);
            shapeInput.set(curShape);
            if (ImGui.combo("Shape##tile-shape", shapeInput, SHAPE_NAMES)) {
                settings.set(EditorSettingKeys.TERRAIN_OVERLAY_SHAPE, shapeInput.get());
            }

            int curRot = settings.snapshot().get(EditorSettingKeys.TERRAIN_OVERLAY_ROTATION);
            rotationInput.set(curRot);
            if (ImGui.combo("Rotation##tile-rot", rotationInput, ROTATION_NAMES)) {
                settings.set(EditorSettingKeys.TERRAIN_OVERLAY_ROTATION, rotationInput.get());
            }

            int curHeight = settings.snapshot().get(EditorSettingKeys.TERRAIN_HEIGHT_DELTA);
            heightInput.set(curHeight);
            if (ImGui.inputInt("Height##tile-height", heightInput)) {
                settings.set(EditorSettingKeys.TERRAIN_HEIGHT_DELTA, heightInput.get());
            }

            // Tile color preview
            ImGui.textDisabled("Preview");
            float previewSize = 72.0f;
            float px = ImGui.getCursorScreenPos().x;
            float py = ImGui.getCursorScreenPos().y;

            int underColor = 0xFF2A2A2A;
            if (cache != null && curUnderlay > 0) {
                var uOpt = cache.bundle().definitions().underlay(curUnderlay);
                if (uOpt.isPresent()) {
                    int rgb = uOpt.get().rgb();
                    underColor = 0xFF000000 | rgb;
                }
            }
            int overColor = 0xFF4A4A4A;
            if (cache != null && curOverlay > 0) {
                var oOpt = cache.bundle().definitions().overlay(curOverlay);
                if (oOpt.isPresent()) {
                    int rgb = oOpt.get().rgb();
                    overColor = 0xFF000000 | rgb;
                }
            }

            imgui.ImDrawList draw = ImGui.getWindowDrawList();
            draw.addRectFilled(px, py, px + previewSize, py + previewSize, underColor, 4.0f);
            if (curOverlay > 0) {
                draw.addTriangleFilled(px, py, px + previewSize, py, px, py + previewSize, overColor);
            }
            draw.addRect(px, py, px + previewSize, py + previewSize, 0xFF666666, 4.0f);

            ImGui.dummy(previewSize, previewSize);
            ImGui.sameLine();
            ImGui.beginGroup();
            ImGui.text("Underlay: " + (curUnderlay > 0 ? "#" + curUnderlay : "None"));
            ImGui.text("Overlay:  " + (curOverlay > 0 ? "#" + curOverlay : "None"));
            ImGui.text("Shape:    " + curShape);
            ImGui.endGroup();
        }

        // 3. Map tiles from active scene
        if (ImGui.collapsingHeader("Map tiles", ImGuiTreeNodeFlags.DefaultOpen)) {
            WorldDocument world = context.session() != null ? context.session().world() : null;
            if (world == null) {
                ImGui.textDisabled("No active map loaded.");
            } else {
                Set<Integer> mapOverlays = new LinkedHashSet<>();
                Set<Integer> mapUnderlays = new LinkedHashSet<>();
                int plane = settings.snapshot().get(com.rspsi.editor.render.RenderSettingKeys.ACTIVE_PLANE);

                for (int x = 0; x < Math.min(64, world.width()); x += 4) {
                    for (int y = 0; y < Math.min(64, world.length()); y += 4) {
                        TileSnapshot s = world.tile(plane, x, y).snapshot();
                        if (s.overlayId() > 0) mapOverlays.add(s.overlayId());
                        if (s.underlayId() > 0) mapUnderlays.add(s.underlayId());
                    }
                }

                ImGui.textDisabled("Used Overlays (" + mapOverlays.size() + "):");
                for (int oId : mapOverlays) {
                    if (ImGui.smallButton("Overlay #" + oId + "##sel-o-" + oId)) {
                        settings.set(EditorSettingKeys.TERRAIN_OVERLAY, oId);
                    }
                    ImGui.sameLine();
                }
                ImGui.newLine();

                ImGui.textDisabled("Used Underlays (" + mapUnderlays.size() + "):");
                for (int uId : mapUnderlays) {
                    if (ImGui.smallButton("Underlay #" + uId + "##sel-u-" + uId)) {
                        settings.set(EditorSettingKeys.TERRAIN_UNDERLAY, uId);
                    }
                    ImGui.sameLine();
                }
                ImGui.newLine();
            }
        }
    }
}
