package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginApiTest {

    @Test
    void testFluentSettingRegistrationAndAccess() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        AtomicBoolean flag = new AtomicBoolean();

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "test.fluent.plugin";
            }

            @Override
            public void initialize(EditorPluginContext context) {
                PluginApi api = context.api(this);
                BoundSetting<Integer> speed = api.setting("test.speed", 42)
                        .category("Movement")
                        .order(10)
                        .range(1, 100)
                        .label("Speed")
                        .description("Navigation speed")
                        .register();

                assertEquals(42, speed.get());
                speed.set(99);
                assertEquals(99, speed.get());

                assertEquals("Movement", speed.spec().category());
                assertEquals(10, speed.spec().order());
                assertEquals(ContributionOwner.plugin(id()),
                        context.settingsService().ownerOf("test.speed").orElseThrow());

                flag.set(true);
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(List.of(testPlugin), session, new EmptyAssets());
        assertTrue(flag.get(), "plugin should initialize");

        // Verify still accessible via host
        assertEquals(99, host.context().settingsService().get(
                new com.rspsi.editor.settings.SettingKey<>("test.speed", Integer.class)));

        // Unload host
        host.close();

        // Verify setting was automatically cleaned up
        assertThrows(IllegalArgumentException.class, () ->
                host.context().settingsService().get(
                        new com.rspsi.editor.settings.SettingKey<>("test.speed", Integer.class)));
        assertTrue(host.context().settingsService().ownerOf("test.speed").isEmpty());
    }

    @Test
    void testFluentOverlayRegistration() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        List<WorldTile> outlinedTiles = new ArrayList<>();

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "test.overlay.plugin";
            }

            @Override
            public void initialize(EditorPluginContext context) {
                PluginApi api = context.api(this);
                api.sceneOverlay("test.overlay", (snapshot, draw) ->
                        draw.tileOutline(new WorldTile(0, 10, 20)));
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(List.of(testPlugin), session, new EmptyAssets());
        EditorOverlayRegistration reg = host.registry().overlayRegistrations().stream()
                .filter(r -> "test.overlay".equals(r.id()))
                .findFirst()
                .orElseThrow();

        EditorSceneOverlay overlay = host.registry().createOverlay(reg.id());
        overlay.render(null, new OverlayDraw() {
            @Override
            public void tileOutline(WorldTile tile) {
                outlinedTiles.add(tile);
            }
        });

        assertEquals(1, outlinedTiles.size());
        assertEquals(new WorldTile(0, 10, 20), outlinedTiles.get(0));

        host.close();
        assertTrue(host.registry().overlayRegistrations().isEmpty());
    }

    @Test
    void testFluentHudRegistrationCarriesMovementAndOpacityPolicy() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "test.hud.plugin";
            }

            @Override
            public void initialize(EditorPluginContext context) {
                context.api(this)
                        .hud("test.hud")
                        .label("Test HUD")
                        .movable(true)
                        .opacity(0.42f)
                        .width(180.0f)
                        .content(new com.rspsi.editor.overlay.OverlayComponent.Text("Hello"))
                        .register();
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(
                List.of(testPlugin), session, new EmptyAssets());

        var hud = host.context().services().overlays().contribution("test.hud");
        assertNotNull(hud);
        assertTrue(hud.movable());
        assertEquals(0.42f, hud.defaultOpacity(), 0.001f);
        assertEquals(180.0f, hud.preferredWidth(), 0.001f);

        host.close();
    }

    @Test
    void testFluentMenuAndShortcutRegistration() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        AtomicBoolean actionRan = new AtomicBoolean();

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "test.menu.plugin";
            }

            @Override
            public void initialize(EditorPluginContext context) {
                PluginApi api = context.api(this);
                api.menu("test.my.menu")
                        .path("Tools", "Custom")
                        .label("Run Custom Action")
                        .action(() -> actionRan.set(true))
                        .register();

                api.shortcut("test.my.shortcut")
                        .key("K")
                        .ctrl()
                        .action(() -> actionRan.set(true))
                        .register();
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(List.of(testPlugin), session, new EmptyAssets());

        // Test menu registration and underlying command
        assertEquals(1, host.registry().menuRegistrations().size());
        EditorMenuRegistration menuReg = host.registry().menuRegistrations().get(0);
        assertEquals("test.my.menu", menuReg.id());
        assertEquals(List.of("Tools", "Custom"), menuReg.path());

        com.rspsi.editor.EditorCommand cmd = host.registry().createCommand(menuReg.commandId());
        assertNotNull(cmd);
        cmd.apply(session);
        assertTrue(actionRan.get());

        // Test shortcut dispatch
        actionRan.set(false);
        EditorKeyEvent keyEvent = new EditorKeyEvent("K", true, false, false, true, false, false);
        boolean handled = host.registry().dispatchShortcut(host.context(), keyEvent);
        assertTrue(handled);
        assertTrue(actionRan.get());

        host.close();
        assertTrue(host.registry().menuRegistrations().isEmpty());
        assertTrue(host.registry().shortcutRegistrations().isEmpty());
    }

    @Test
    void testFluentStatusAndInspectorRegistration() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "test.status.plugin";
            }

            @Override
            public void initialize(EditorPluginContext context) {
                PluginApi api = context.api(this);
                api.statusItem("test.fps", () -> "60 FPS");
                api.inspector("test.insp", "Stats", "Diagnostics", ctx ->
                        List.of(new EditorInspectorField("f1", "Field 1", "Val 1")));
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(List.of(testPlugin), session, new EmptyAssets());

        var statusItems = host.registry().statusItems(host.context());
        assertEquals(1, statusItems.size());
        assertEquals("60 FPS", statusItems.get(0).value());

        var inspectorFields = host.registry().inspect(host.context());
        assertEquals(1, inspectorFields.size());
        assertEquals("Field 1", inspectorFields.get(0).label());
        assertEquals("Val 1", inspectorFields.get(0).value());

        host.close();
    }


    @Test
    void testMapToolBuilderCarriesFirstClassStudioMetadata() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        AtomicInteger density = new AtomicInteger(35);

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "test.map-tool.plugin";
            }

            @Override
            public void initialize(EditorPluginContext context) {
                context.api(this)
                        .mapTool("test.biome-painter")
                        .label("Biome Painter")
                        .category("Terrain")
                        .group("terrain")
                        .icon("forest")
                        .shortcut("B")
                        .order(40)
                        .surfaces(ToolUiDescriptor.ToolSurface.BOTTOM_BAR)
                        .brushUi(ToolUiDescriptor.BrushUiMode.SHARED_SETTINGS)
                        .capabilities(
                                ToolUiDescriptor.ToolCapability.TILE_TARGET,
                                ToolUiDescriptor.ToolCapability.WORLD_READ,
                                ToolUiDescriptor.ToolCapability.WORLD_EDIT,
                                ToolUiDescriptor.ToolCapability.PREVIEW)
                        .contextDrawer(ctx -> List.of(EditorSetting.integer(
                                "density",
                                "Density",
                                1,
                                100,
                                density::get,
                                density::set)))
                        .factory(() -> new NoOpTool("test.biome-painter"))
                        .register();
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(
                List.of(testPlugin), session, new EmptyAssets());

        EditorToolRegistration registration = host.registry().toolRegistrations().stream()
                .filter(tool -> "test.biome-painter".equals(tool.id()))
                .findFirst()
                .orElseThrow();

        assertEquals("Biome Painter", registration.label());
        assertEquals("Terrain", registration.category());
        assertEquals("terrain", registration.toolGroup());
        assertEquals("forest", registration.icon());
        assertEquals("B", registration.shortcut());
        assertEquals(40, registration.order());

        ToolUiDescriptor ui = registration.ui();
        assertEquals(ToolUiDescriptor.BrushUiMode.SHARED_SETTINGS, ui.brushUiMode());
        assertTrue(ui.appearsOn(ToolUiDescriptor.ToolSurface.BOTTOM_BAR));
        assertFalse(ui.appearsOn(ToolUiDescriptor.ToolSurface.FLOATING_TOOLBAR));
        assertTrue(ui.has(ToolUiDescriptor.ToolCapability.BRUSH_FOOTPRINT));
        assertTrue(ui.has(ToolUiDescriptor.ToolCapability.TILE_TARGET));
        assertTrue(ui.has(ToolUiDescriptor.ToolCapability.WORLD_EDIT));
        assertTrue(ui.has(ToolUiDescriptor.ToolCapability.CONTEXT_DRAWER));
        assertTrue(ui.hasContextDrawerContent());

        List<EditorSetting> drawerSettings =
                host.registry().settingsForTool(host.context(), "test.biome-painter");
        assertEquals(1, drawerSettings.size());
        assertEquals("Density", drawerSettings.get(0).label());
        drawerSettings.get(0).setValue(72);
        assertEquals(72, density.get());

        assertEquals("test.biome-painter", host.registry().createTool(registration.id()).id());
        host.close();
    }

    private static final class NoOpTool implements EditorTool {
        private final String id;

        private NoOpTool(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void activate(ToolContext context) {
        }

        @Override
        public void deactivate() {
        }

        @Override
        public void pointerDown(PointerEvent event) {
        }

        @Override
        public void pointerDrag(PointerEvent event) {
        }

        @Override
        public void pointerUp(PointerEvent event) {
        }
    }

    private static final class EmptyAssets implements AssetRepository {
        @Override
        public List<AssetDescriptor> search(String query) {
            return List.of();
        }

        @Override
        public Optional<AssetDescriptor> get(int id, String type) {
            return Optional.empty();
        }
    }
}
