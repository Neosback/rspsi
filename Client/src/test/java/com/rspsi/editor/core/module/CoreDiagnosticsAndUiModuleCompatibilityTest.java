package com.rspsi.editor.core.module;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.debug.DebugColor;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorOverlayRegistration;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorSceneOverlay;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.ui.DockRegion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoreDiagnosticsAndUiModuleCompatibilityTest {

    @Test
    void diagnosticsModuleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        assertModuleSurface(CoreDiagnosticsModule.class, "rspsi.tools.renderer-debug", 40);
        assertEquals(DebugColor.YELLOW, CoreDiagnosticsModule.defaultMarkerColor());
    }

    @Test
    void diagnosticsModuleRegistersExactOverlayContract() {
        Fixture fixture = fixture();
        new CoreDiagnosticsModule().install(fixture.context());

        List<EditorOverlayRegistration> overlays = fixture.registry().overlayRegistrations();
        assertEquals(1, overlays.size());

        EditorOverlayRegistration registration = overlays.get(0);
        assertEquals("renderer-debug.scene-semantics", registration.id());
        assertEquals("Scene semantics", registration.label());
        assertEquals("Renderer diagnostics", registration.category());
        assertFalse(registration.enabledByDefault());

        EditorSceneOverlay overlay =
                fixture.registry().createOverlay("renderer-debug.scene-semantics");
        assertNotNull(overlay);
    }

    @Test
    void uiModuleKeepsJavaFacingIdentityAndOrdering() throws Exception {
        assertModuleSurface(CoreUiModule.class, "rspsi.ui.core-surfaces", 50);
    }

    @Test
    void uiModuleRegistersExactManagedHudSurfaces() {
        Fixture fixture = fixture();
        new CoreUiModule().install(fixture.context());

        List<UiSurfaceContribution> surfaces = fixture.registry().uiSurfaceContributions();
        assertEquals(2, surfaces.size());

        assertSurface(
                surfaces.get(0),
                "studio.minimap-hud",
                "Minimap HUD",
                "map",
                UiSurfaceContribution.SizeClass.EXPANDED,
                10);
        assertSurface(
                surfaces.get(1),
                "studio.tile-info-hud",
                "Tile Information HUD",
                "explore",
                UiSurfaceContribution.SizeClass.COMPACT,
                20);

        assertDoesNotThrow(fixture.registry()::validateReferences);
    }

    private static void assertModuleSurface(
            Class<?> moduleType,
            String expectedId,
            int expectedOrder) throws Exception {
        assertTrue(Modifier.isPublic(moduleType.getModifiers()));
        assertTrue(Modifier.isFinal(moduleType.getModifiers()));
        assertTrue(Modifier.isPublic(moduleType.getDeclaredConstructor().getModifiers()));

        var idField = moduleType.getDeclaredField("ID");
        assertTrue(Modifier.isPublic(idField.getModifiers()));
        assertTrue(Modifier.isStatic(idField.getModifiers()));
        assertTrue(Modifier.isFinal(idField.getModifiers()));
        assertEquals(expectedId, idField.get(null));

        var module = assertInstanceOf(
                com.rspsi.editor.core.CoreEditorModule.class,
                moduleType.getDeclaredConstructor().newInstance());
        assertEquals(expectedId, module.id());
        assertEquals(expectedOrder, module.order());
        assertEquals("0.1.0", module.version());
    }

    private static void assertSurface(
            UiSurfaceContribution surface,
            String id,
            String title,
            String icon,
            UiSurfaceContribution.SizeClass sizeClass,
            int priority) {
        assertEquals(id, surface.id());
        assertEquals(title, surface.title());
        assertEquals(icon, surface.icon());
        assertEquals(UiSurfaceContribution.SurfaceType.VIEWPORT_HUD, surface.type());
        assertEquals(DockRegion.OVERLAY, surface.preferredRegion());
        assertEquals(Set.of(DockRegion.OVERLAY), surface.allowedRegions());
        assertEquals(sizeClass, surface.sizeClass());
        assertFalse(surface.movable());
        assertTrue(surface.closable());
        assertEquals("", surface.associatedToolId());
        assertEquals(priority, surface.priority());
    }

    private static Fixture fixture() {
        EditorSession session = new EditorSession(new WorldModel(8, 8, 2));
        EditorPluginRegistry registry = new EditorPluginRegistry();
        EditorPluginContext context = new EditorPluginContext(
                session,
                EmptyAssetRepository.INSTANCE,
                registry);
        return new Fixture(registry, context);
    }

    private record Fixture(EditorPluginRegistry registry, EditorPluginContext context) {
    }
}
