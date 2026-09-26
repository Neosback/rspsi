package com.rspsi.editor.core.module;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.tool.CompositeTilePainterTool;
import com.rspsi.editor.tool.SplinePathTool;
import com.rspsi.editor.ui.DockRegion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CorePathAndTilePainterModuleCompatibilityTest {

    @Test
    void pathModuleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        assertModuleSurface(
                CorePathModule.class,
                "rspsi.tools.path.spline",
                17);
    }

    @Test
    void tilePainterModuleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        assertModuleSurface(
                CoreTilePainterModule.class,
                "rspsi.tools.terrain.painter",
                11);
    }

    @Test
    void pathModuleRegistersExactToolAndManagedSurfaces() {
        Fixture fixture = fixture();
        new CorePathModule().install(fixture.context());

        List<EditorToolRegistration> tools = fixture.registry().toolRegistrations();
        assertEquals(1, tools.size());
        assertTool(
                tools.get(0),
                SplinePathTool.ID,
                "Spline Path",
                "Terrain",
                "Paint",
                "PATH",
                "P",
                45);
        assertInstanceOf(
                SplinePathTool.class,
                fixture.registry().createTool(SplinePathTool.ID));

        List<UiSurfaceContribution> surfaces = fixture.registry().uiSurfaceContributions();
        assertEquals(2, surfaces.size());
        assertSurface(
                surfaces.get(0),
                "studio.path-context",
                "Path Builder",
                "path",
                UiSurfaceContribution.SurfaceType.BOTTOM_CONTEXT,
                DockRegion.BOTTOM,
                Set.of(DockRegion.BOTTOM, DockRegion.RIGHT),
                UiSurfaceContribution.SizeClass.EXPANDED,
                true,
                true,
                SplinePathTool.ID,
                15);
        assertSurface(
                surfaces.get(1),
                "studio.path-hud",
                "Path HUD",
                "path",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY,
                Set.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false,
                true,
                SplinePathTool.ID,
                35);

        assertDoesNotThrow(fixture.registry()::validateReferences);
    }

    @Test
    void tilePainterModuleRegistersExactToolAndManagedSurfaces() {
        Fixture fixture = fixture();
        new CoreTilePainterModule().install(fixture.context());

        List<EditorToolRegistration> tools = fixture.registry().toolRegistrations();
        assertEquals(1, tools.size());
        assertTool(
                tools.get(0),
                "terrain.tile-painter",
                "Tile painter",
                "Terrain",
                "Paint",
                "BRUSH",
                "2",
                15);
        assertInstanceOf(
                CompositeTilePainterTool.class,
                fixture.registry().createTool("terrain.tile-painter"));

        List<UiSurfaceContribution> surfaces = fixture.registry().uiSurfaceContributions();
        assertEquals(2, surfaces.size());
        assertSurface(
                surfaces.get(0),
                "studio.tile-palette",
                "Tile Painter",
                "palette",
                UiSurfaceContribution.SurfaceType.BOTTOM_CONTEXT,
                DockRegion.BOTTOM,
                Set.of(DockRegion.BOTTOM, DockRegion.RIGHT),
                UiSurfaceContribution.SizeClass.EXPANDED,
                true,
                true,
                "terrain.tile-painter",
                5);
        assertSurface(
                surfaces.get(1),
                "studio.tile-painter-hud",
                "Tile Painter HUD",
                "brush",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY,
                Set.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false,
                true,
                "terrain.tile-painter",
                30);

        assertDoesNotThrow(fixture.registry()::validateReferences);
    }

    private static void assertModuleSurface(
            Class<?> moduleType,
            String expectedId,
            int expectedOrder) throws Exception {
        int modifiers = moduleType.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertTrue(Modifier.isPublic(
                moduleType.getDeclaredConstructor().getModifiers()));

        var idField = moduleType.getDeclaredField("ID");
        int idModifiers = idField.getModifiers();
        assertTrue(Modifier.isPublic(idModifiers));
        assertTrue(Modifier.isStatic(idModifiers));
        assertTrue(Modifier.isFinal(idModifiers));
        assertEquals(expectedId, idField.get(null));

        var module = assertInstanceOf(
                com.rspsi.editor.core.CoreEditorModule.class,
                moduleType.getDeclaredConstructor().newInstance());
        assertEquals(expectedId, module.id());
        assertEquals(expectedOrder, module.order());
        assertEquals("0.1.0", module.version());
    }

    private static void assertTool(
            EditorToolRegistration registration,
            String id,
            String label,
            String category,
            String group,
            String icon,
            String shortcut,
            int order) {
        assertEquals(id, registration.id());
        assertEquals(label, registration.label());
        assertEquals(category, registration.category());
        assertEquals(group, registration.toolGroup());
        assertEquals(icon, registration.icon());
        assertEquals(shortcut, registration.shortcut());
        assertEquals(order, registration.order());
    }

    private static void assertSurface(
            UiSurfaceContribution surface,
            String id,
            String title,
            String icon,
            UiSurfaceContribution.SurfaceType type,
            DockRegion preferredRegion,
            Set<DockRegion> allowedRegions,
            UiSurfaceContribution.SizeClass sizeClass,
            boolean movable,
            boolean closable,
            String associatedToolId,
            int priority) {
        assertEquals(id, surface.id());
        assertEquals(title, surface.title());
        assertEquals(icon, surface.icon());
        assertEquals(type, surface.type());
        assertEquals(preferredRegion, surface.preferredRegion());
        assertEquals(allowedRegions, surface.allowedRegions());
        assertEquals(sizeClass, surface.sizeClass());
        assertEquals(movable, surface.movable());
        assertEquals(closable, surface.closable());
        assertEquals(associatedToolId, surface.associatedToolId());
        assertEquals(priority, surface.priority());
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
