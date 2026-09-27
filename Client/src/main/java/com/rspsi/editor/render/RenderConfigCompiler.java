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
                settings.getOrDefault(RenderSettingKeys.BRIDGE_TILES_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.HIDDEN_TILES_VISIBLE, false),
                settings.getOrDefault(RenderSettingKeys.EMPTY_TILES_VISIBLE, false),
                settings.getOrDefault(RenderSettingKeys.COLLISION_VISIBLE, false),
                settings.getOrDefault(RenderSettingKeys.WIREFRAME, false),
                settings.getOrDefault(RenderSettingKeys.CURRENT_HEIGHT, 0),
                settings.getOrDefault(RenderSettingKeys.ALL_HEIGHTS_VISIBLE, true),
                settings.getOrDefault(RenderSettingKeys.BRIGHTNESS, 1.0),
                settings.getOrDefault(RenderSettingKeys.EXPOSURE, 0.0),
                settings.getOrDefault(RenderSettingKeys.MSAA_SAMPLES, 0),
                settings.getOrDefault(RenderSettingKeys.FOG_DEPTH_TILES, 0),
                settings.getOrDefault(RenderSettingKeys.FOG_COLOR, 0x101827),
                settings.getOrDefault(RenderSettingKeys.INVISIBLE_OBJECTS_VISIBLE, false),
                settings.getOrDefault(RenderSettingKeys.NATIVE_CULLING_MODE, BackfacePolicy.defaultMode()),
                settings.getOrDefault(RenderSettingKeys.GPU_DEBUG_VIEW, GpuDebugView.NONE));
    }

    public RenderConfig defaultConfig() {
        return compile(RenderSettingKeys.registry().defaults());
    }
}
