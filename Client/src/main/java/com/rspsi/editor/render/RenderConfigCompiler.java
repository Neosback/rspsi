package com.rspsi.editor.render;

import com.rspsi.editor.settings.SettingsSnapshot;

import java.util.Objects;

/** Compiles one immutable frame configuration from typed settings. */
public final class RenderConfigCompiler {
    public RenderConfig compile(SettingsSnapshot settings) {
        Objects.requireNonNull(settings, "settings");
        return new RenderConfig(
                settings.getOrDefault(RenderSettingKeys.PROFILE, RenderProfile.VANILLA_COMPATIBILITY),
                settings.getOrDefault(RenderSettingKeys.TERRAIN_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.OBJECTS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.WALLS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.WALL_DECORATIONS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.GROUND_OBJECTS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.GROUND_DECORATIONS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.ROOFS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.BRIDGE_TILES_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.HIDDEN_TILES_VISIBLE, false),
                settings.getOrDefault(RenderSettingKeys.COLLISION_VISIBLE, false),
                settings.getOrDefault(RenderSettingKeys.WIREFRAME, false),
                settings.getOrDefault(RenderSettingKeys.ACTIVE_PLANE, 0),
                settings.getOrDefault(RenderSettingKeys.PLANE_SELECTION,
                        SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE),
                settings.getOrDefault(RenderSettingKeys.BRIGHTNESS, 1.0),
                settings.getOrDefault(RenderSettingKeys.EXPOSURE, 0.0),
                settings.getOrDefault(RenderSettingKeys.MSAA_SAMPLES, 0),
                settings.getOrDefault(RenderSettingKeys.FOG_DEPTH_TILES, 0),
                settings.getOrDefault(RenderSettingKeys.FOG_COLOR, 0x101827));
    }

    public RenderConfig defaultConfig() {
        return compile(RenderSettingKeys.registry().defaults());
    }
}
