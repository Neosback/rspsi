package com.rspsi.editor.core.module;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.tool.BlendTerrainTool;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.SmoothTerrainTool;
import com.rspsi.editor.tool.TerraceTerrainTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreTerrainModuleCompatibilityTest {

    @Test
    void moduleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        int modifiers = CoreTerrainModule.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertTrue(Modifier.isPublic(
                CoreTerrainModule.class.getDeclaredConstructor().getModifiers()));

        var idField = CoreTerrainModule.class.getDeclaredField("ID");
        int idModifiers = idField.getModifiers();
        assertTrue(Modifier.isPublic(idModifiers));
        assertTrue(Modifier.isStatic(idModifiers));
        assertTrue(Modifier.isFinal(idModifiers));
        assertEquals("rspsi.tools.terrain", idField.get(null));

        CoreTerrainModule module = new CoreTerrainModule();
        assertEquals("rspsi.tools.terrain", module.id());
        assertEquals(10, module.order());
        assertEquals("0.1.0", module.version());
    }

    @Test
    void installRegistersExactTerrainToolInventoryAndMetadata() {
        Fixture fixture = fixture();
        new CoreTerrainModule().install(fixture.context());

        List<EditorToolRegistration> tools = fixture.registry().toolRegistrations();
        assertEquals(10, tools.size());

        assertTool(tools.get(0),
                "terrain.paint-underlay", "Paint underlay", "Terrain", "Paint",
                "PENCIL", "Shift+2", 20);
        assertTool(tools.get(1),
                "terrain.paint-overlay", "Paint overlay", "Terrain", "Paint",
                "BRUSH", "2", 10);
        assertTool(tools.get(2),
                "terrain.flags", "Paint flags", "Terrain", "Paint",
                "FLAGS", null, 30);
        assertTool(tools.get(3),
                "terrain.raise", "Raise height", "Height", "Height",
                "UP", "3", 10);
        assertTool(tools.get(4),
                "terrain.lower", "Lower height", "Height", "Height",
                "DOWN", "Shift+3", 20);
        assertTool(tools.get(5),
                "terrain.flatten", "Flatten terrain", "Height", "Height",
                "FLATTEN", null, 30);
        assertTool(tools.get(6),
                "terrain.smooth", "Smooth terrain", "Height", "Height",
                "SMOOTH", null, 40);
        assertTool(tools.get(7),
                "terrain.blend", "Blend terrain", "Height", "Height",
                "HEIGHT", null, 45);
        assertTool(tools.get(8),
                "terrain.terrace", "Terrace terrain", "Height", "Height",
                "HEIGHT", null, 47);
        assertTool(tools.get(9),
                "terrain.ramp", "Ramp terrain", "Height", "Height",
                "RAMP", null, 50);
    }

    @Test
    void factoriesCreateExpectedToolsAndApplyCurrentSettingsLazily() {
        Fixture fixture = fixture();
        new CoreTerrainModule().install(fixture.context());

        fixture.context().settings().set(EditorSettingKeys.TERRAIN_UNDERLAY, 42);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_OVERLAY, 43);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_OVERLAY_SHAPE, 7);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_OVERLAY_ROTATION, 2);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_HEIGHT_DELTA, 12);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_HEIGHT_RADIUS, 4);
        fixture.context().settings().set(
                EditorSettingKeys.TERRAIN_HEIGHT_FALLOFF,
                ChangeHeightTool.Falloff.SMOOTH);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_FLATTEN_HEIGHT, -128);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_SMOOTH_STRENGTH, 75);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_FLAGS, 123);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_RAMP_START, -50);
        fixture.context().settings().set(EditorSettingKeys.TERRAIN_RAMP_END, 90);

        PaintUnderlayTool underlay = assertInstanceOf(
                PaintUnderlayTool.class,
                fixture.registry().createTool("terrain.paint-underlay"));
        assertEquals(42, underlay.underlayId());

        PaintOverlayTool overlay = assertInstanceOf(
                PaintOverlayTool.class,
                fixture.registry().createTool("terrain.paint-overlay"));
        assertEquals(43, overlay.overlayId());
        assertEquals(7, overlay.shape());
        assertEquals(2, overlay.rotation());

        PaintFlagsTool flags = assertInstanceOf(
                PaintFlagsTool.class,
                fixture.registry().createTool("terrain.flags"));
        assertEquals(123, flags.flags());

        ChangeHeightTool raise = assertInstanceOf(
                ChangeHeightTool.class,
                fixture.registry().createTool("terrain.raise"));
        assertEquals(12, raise.delta());
        assertEquals(4, raise.radius());
        assertEquals(ChangeHeightTool.Falloff.SMOOTH, raise.falloff());

        ChangeHeightTool lower = assertInstanceOf(
                ChangeHeightTool.class,
                fixture.registry().createTool("terrain.lower"));
        assertEquals(-12, lower.delta());
        assertEquals(4, lower.radius());
        assertEquals(ChangeHeightTool.Falloff.SMOOTH, lower.falloff());

        FlattenTerrainTool flatten = assertInstanceOf(
                FlattenTerrainTool.class,
                fixture.registry().createTool("terrain.flatten"));
        assertEquals(-128, flatten.targetHeight());

        SmoothTerrainTool smooth = assertInstanceOf(
                SmoothTerrainTool.class,
                fixture.registry().createTool("terrain.smooth"));
        assertEquals(75, smooth.strengthPercent());

        BlendTerrainTool blend = assertInstanceOf(
                BlendTerrainTool.class,
                fixture.registry().createTool("terrain.blend"));
        assertEquals(75, blend.strengthPercent());
        assertEquals(56, blend.edgeThreshold());

        TerraceTerrainTool terrace = assertInstanceOf(
                TerraceTerrainTool.class,
                fixture.registry().createTool("terrain.terrace"));
        assertEquals(16, terrace.step());

        RampTerrainTool ramp = assertInstanceOf(
                RampTerrainTool.class,
                fixture.registry().createTool("terrain.ramp"));
        assertEquals(-50, ramp.startHeight());
        assertEquals(90, ramp.endHeight());
    }

    @Test
    void installRegistersExactTerrainSettingsContext() {
        Fixture fixture = fixture();
        new CoreTerrainModule().install(fixture.context());

        List<EditorToolContextRegistration> contexts =
                fixture.registry().toolContextRegistrations();
        assertEquals(1, contexts.size());

        EditorToolContextRegistration context = contexts.get(0);
        assertEquals("terrain.context", context.id());
        assertEquals("Terrain settings", context.label());
        assertEquals(0, context.order());
        assertEquals(
                List.of(
                        "terrain.paint-underlay",
                        "terrain.paint-overlay",
                        "terrain.raise",
                        "terrain.lower",
                        "terrain.flatten",
                        "terrain.smooth",
                        "terrain.blend",
                        "terrain.terrace",
                        "terrain.ramp",
                        "terrain.flags"),
                context.toolIds());

        assertEquals(
                List.of(
                        "terrain.underlay",
                        "terrain.overlay",
                        "terrain.overlay-shape",
                        "terrain.overlay-rotation",
                        "terrain.height-delta",
                        "terrain.height-radius",
                        "terrain.height-falloff",
                        "terrain.flatten-height",
                        "terrain.smooth-strength",
                        "terrain.flags",
                        "terrain.ramp-start",
                        "terrain.ramp-end"),
                fixture.registry()
                        .settingsForTool(fixture.context(), "terrain.raise")
                        .stream()
                        .map(com.rspsi.editor.plugin.EditorSetting::id)
                        .toList());

        assertTrue(
                fixture.registry()
                        .settingsForTool(fixture.context(), "not-terrain")
                        .isEmpty());
    }

    private static void assertTool(
            EditorToolRegistration registration,
            String id,
            String label,
            String category,
            String group,
            String icon,
            String shortcut,
            int order
    ) {
        assertEquals(id, registration.id());
        assertEquals(label, registration.label());
        assertEquals(category, registration.category());
        assertEquals(group, registration.toolGroup());
        assertEquals(icon, registration.icon());
        assertEquals(shortcut, registration.shortcut());
        assertEquals(order, registration.order());
    }

    private static Fixture fixture() {
        EditorSession session = new EditorSession(new WorldModel(8, 8, 1));
        EditorPluginRegistry registry = new EditorPluginRegistry();
        EditorPluginContext context = new EditorPluginContext(
                session,
                EmptyAssetRepository.INSTANCE,
                registry);
        return new Fixture(registry, context);
    }

    private record Fixture(
            EditorPluginRegistry registry,
            EditorPluginContext context
    ) {
    }
}
