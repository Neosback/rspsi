package com.rspsi.editor.render;

import com.rspsi.editor.settings.SettingInvalidation;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingSpec;
import com.rspsi.editor.settings.SettingConsumerCatalog;
import com.rspsi.editor.settings.SettingsRegistry;

import java.util.List;
import java.util.Set;

/** Canonical renderer setting identities and their registry. */
public final class RenderSettingKeys {
    private RenderSettingKeys() {}

    public static final SettingKey<RenderProfile> PROFILE =
            new SettingKey<>("renderer.profile", RenderProfile.class);
    public static final SettingKey<Boolean> TERRAIN_VISIBLE = bool("viewport.scene.terrain.visible");
    public static final SettingKey<Boolean> OBJECTS_VISIBLE = bool("viewport.scene.objects.visible");
    public static final SettingKey<Boolean> WALLS_VISIBLE = bool("viewport.scene.walls.visible");
    public static final SettingKey<Boolean> WALL_DECORATIONS_VISIBLE = bool("viewport.scene.wall-decorations.visible");
    public static final SettingKey<Boolean> GROUND_OBJECTS_VISIBLE = bool("viewport.scene.ground-objects.visible");
    public static final SettingKey<Boolean> GROUND_DECORATIONS_VISIBLE = bool("viewport.scene.ground-decorations.visible");
    public static final SettingKey<Boolean> ROOFS_VISIBLE = bool("viewport.scene.roofs.visible");
    public static final SettingKey<Boolean> BRIDGE_TILES_VISIBLE = bool("viewport.scene.bridges.visible");
    public static final SettingKey<Boolean> HIDDEN_TILES_VISIBLE = bool("viewport.scene.hidden-tiles.visible");
    public static final SettingKey<Boolean> COLLISION_VISIBLE = bool("viewport.debug.collision.visible");
    public static final SettingKey<Boolean> INVISIBLE_OBJECTS_VISIBLE = bool("viewport.scene.invisible-objects.visible");
    public static final SettingKey<Boolean> WIREFRAME = bool("viewport.debug.wireframe");
    public static final SettingKey<BackfacePolicy.NativeCullingMode> NATIVE_CULLING_MODE =
            new SettingKey<>("viewport.debug.native-culling", BackfacePolicy.NativeCullingMode.class);
    public static final SettingKey<Integer> ACTIVE_PLANE =
            new SettingKey<>("viewport.scene.active-plane", Integer.class);
    public static final SettingKey<SceneVisibilityPolicy.PlaneSelection> PLANE_SELECTION =
            new SettingKey<>("viewport.scene.plane-selection", SceneVisibilityPolicy.PlaneSelection.class);
    public static final SettingKey<Double> BRIGHTNESS =
            new SettingKey<>("renderer.brightness", Double.class);
    public static final SettingKey<Double> EXPOSURE =
            new SettingKey<>("renderer.exposure", Double.class);
    public static final SettingKey<Integer> MSAA_SAMPLES =
            new SettingKey<>("renderer.opengl.msaa", Integer.class);
    public static final SettingKey<Integer> FOG_DEPTH_TILES =
            new SettingKey<>("viewport.presentation.fog-depth-tiles", Integer.class);
    public static final SettingKey<Integer> FOG_COLOR =
            new SettingKey<>("viewport.presentation.fog-color", Integer.class);
    public static final SettingKey<Boolean> HUD_TILE_INSPECTOR_VISIBLE = bool("viewport.hud.tile-inspector.visible");
    public static final SettingKey<Boolean> HUD_TOOL_CONTROLS_VISIBLE = bool("viewport.hud.tool-controls.visible");

    public static SettingsRegistry registry() {
        SettingsRegistry registry = new SettingsRegistry();
        Set<SettingInvalidation> visibility = Set.of(SettingInvalidation.VISIBILITY, SettingInvalidation.REDRAW);
        registry.register(SettingSpec.enumeration(PROFILE, RenderProfile.VANILLA_COMPATIBILITY,
                List.of(RenderProfile.values()), SettingScope.GLOBAL, "Render profile",
                "Select the RuneScape-compatible or editor-oriented render behavior.",
                Set.of(SettingInvalidation.REDRAW, SettingInvalidation.VISIBILITY, SettingInvalidation.LIGHTING)));
        registry.register(SettingSpec.of(TERRAIN_VISIBLE, true, SettingScope.VIEWPORT, "Terrain",
                "Render terrain surfaces.", visibility));
        registry.register(SettingSpec.of(OBJECTS_VISIBLE, true, SettingScope.VIEWPORT, "Objects",
                "Render placed world objects.", visibility));
        registry.register(SettingSpec.of(WALLS_VISIBLE, true, SettingScope.VIEWPORT, "Walls",
                "Render wall objects.", visibility));
        registry.register(SettingSpec.of(WALL_DECORATIONS_VISIBLE, true, SettingScope.VIEWPORT, "Wall decorations",
                "Render wall decorations.", visibility));
        registry.register(SettingSpec.of(GROUND_OBJECTS_VISIBLE, true, SettingScope.VIEWPORT, "Ground objects",
                "Render ground objects.", visibility));
        registry.register(SettingSpec.of(GROUND_DECORATIONS_VISIBLE, true, SettingScope.VIEWPORT, "Ground decorations",
                "Render ground decorations.", visibility));
        registry.register(SettingSpec.of(ROOFS_VISIBLE, true, SettingScope.VIEWPORT, "Roofs",
                "Render roof-related geometry.", visibility));
        registry.register(SettingSpec.of(BRIDGE_TILES_VISIBLE, true, SettingScope.VIEWPORT, "Bridge tiles",
                "Render geometry authored above bridge-effective planes.", visibility));
        registry.register(SettingSpec.of(HIDDEN_TILES_VISIBLE, false, SettingScope.VIEWPORT, "Hidden tiles",
                "Include tiles marked hidden by scene flags.", visibility));
        registry.register(SettingSpec.of(INVISIBLE_OBJECTS_VISIBLE, false, SettingScope.VIEWPORT, "Invisible objects",
                "Show markers for collision-only locs the client draws nothing for "
                        + "(invisible walls and floor blockers).", visibility));
        registry.register(SettingSpec.of(COLLISION_VISIBLE, false, SettingScope.VIEWPORT, "Collision",
                "Show collision diagnostics.", Set.of(SettingInvalidation.REDRAW)));
        registry.register(SettingSpec.of(WIREFRAME, false, SettingScope.VIEWPORT, "Wireframe",
                "Show renderer geometry edges.", Set.of(SettingInvalidation.REDRAW)));
        registry.register(SettingSpec.enumeration(NATIVE_CULLING_MODE,
                BackfacePolicy.defaultMode(),
                List.of(BackfacePolicy.NativeCullingMode.values()), SettingScope.VIEWPORT,
                "Native back-face culling",
                "Client Front is the normal model path and follows RuneLite Model.draw0 winding. "
                        + "Terrain stays two-sided; Two Sided and Reversed Debug remain explicit "
                        + "diagnostic modes for parity investigation.",
                Set.of(SettingInvalidation.REDRAW)));
        registry.register(SettingSpec.integer(ACTIVE_PLANE, 0, 0, 3, SettingScope.VIEWPORT,
                "Active plane", "Plane used by authored, effective, or client traversal projections.", visibility));
        registry.register(SettingSpec.enumeration(PLANE_SELECTION, SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL,
                List.of(SceneVisibilityPolicy.PlaneSelection.values()), SettingScope.VIEWPORT,
                "Plane selection",
                "Choose client traversal, all planes, authored plane, or bridge-effective plane projection.",
                visibility));
        registry.register(SettingSpec.decimal(BRIGHTNESS, 1.0, 0.0, 4.0, SettingScope.VIEWPORT,
                "Brightness", "Frontend exposure multiplier; does not alter authored colors.",
                Set.of(SettingInvalidation.REDRAW)));
        registry.register(SettingSpec.decimal(EXPOSURE, 0.0, -8.0, 8.0, SettingScope.VIEWPORT,
                "Exposure", "Frontend exposure offset; does not alter authored colors.",
                Set.of(SettingInvalidation.REDRAW)));
        registry.register(SettingSpec.integer(MSAA_SAMPLES, 4, 0, 8, SettingScope.GLOBAL,
                "MSAA samples", "Multisample anti-aliasing for the scene framebuffer. "
                        + "GlFramebuffer clamps the requested value to the driver's GL_MAX_SAMPLES "
                        + "and rounds down to a power of two; 0 disables MSAA.",
                Set.of(SettingInvalidation.FRAMEBUFFER)));
        registry.register(SettingSpec.integer(FOG_DEPTH_TILES, 0, 0, 1000, SettingScope.VIEWPORT,
                "Fog depth", "Distance from the scene edge where presentation fog reaches the background.",
                Set.of(SettingInvalidation.REDRAW)));
        registry.register(SettingSpec.integer(FOG_COLOR, 0x101827, 0, 0xFFFFFF, SettingScope.VIEWPORT,
                "Fog color", "Presentation-only RGB color used by scene-edge fog.",
                Set.of(SettingInvalidation.REDRAW)));
        Set<SettingInvalidation> none = Set.of();
        registry.register(SettingSpec.of(HUD_TILE_INSPECTOR_VISIBLE, true, SettingScope.VIEWPORT, "Tile inspector HUD",
                "Show the floating tile inspection card over the viewport.", none));
        registry.register(SettingSpec.of(HUD_TOOL_CONTROLS_VISIBLE, true, SettingScope.VIEWPORT, "Tool controls HUD",
                "Show the floating contextual tool controls card over the viewport.", none));
        return registry;
    }

    /** Declares the render configuration compiler as the consumer of every render key. */
    public static SettingConsumerCatalog consumerCatalog() {
        SettingConsumerCatalog consumers = new SettingConsumerCatalog();
        consumers.register("render-config", PROFILE, TERRAIN_VISIBLE, OBJECTS_VISIBLE,
                WALLS_VISIBLE, WALL_DECORATIONS_VISIBLE, GROUND_OBJECTS_VISIBLE,
                GROUND_DECORATIONS_VISIBLE, ROOFS_VISIBLE, BRIDGE_TILES_VISIBLE,
                HIDDEN_TILES_VISIBLE, INVISIBLE_OBJECTS_VISIBLE, COLLISION_VISIBLE, WIREFRAME, ACTIVE_PLANE,
                PLANE_SELECTION, BRIGHTNESS, EXPOSURE, MSAA_SAMPLES, FOG_DEPTH_TILES,
                FOG_COLOR);
        consumers.register("native-viewport-validation", NATIVE_CULLING_MODE);
        consumers.register("map-studio-viewport-hud", HUD_TILE_INSPECTOR_VISIBLE, HUD_TOOL_CONTROLS_VISIBLE);
        return consumers;
    }

    private static SettingKey<Boolean> bool(String id) {
        return new SettingKey<>(id, Boolean.class);
    }
}
