package com.rspsi.editor;

import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginDescriptor;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorShortcutRegistration;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.EditorInspectorField;
import com.rspsi.editor.core.CoreEditorModules;
import com.rspsi.editor.core.module.CoreTerrainModule;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.editor.ui.PanelDescriptor;
import com.rspsi.editor.ui.WorkspaceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPluginHostTest {
    @Test
    void pluginsRegisterNeutralToolsAndWorkspaceContributions() {
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initialize(
                List.of(new SamplePlugin()), session, new EmptyAssets());

        assertEquals(List.of("sample-tool"), host.registry().toolIds());
        assertEquals(List.of("sample.command"), host.registry().commandRegistrations().stream()
                .map(com.rspsi.editor.plugin.EditorCommandRegistration::id).toList());
        assertEquals("sample command", host.registry().createCommand("sample.command").description());
        assertEquals(3, host.registry().settingsForTool(host.context(), "sample-tool")
                .get(0).value());
        var setting = host.registry().settingsForTool(host.context(), "sample-tool").get(0);
        setting.setValue(6);
        assertEquals(6, setting.value());
        assertThrows(IllegalArgumentException.class, () -> setting.setValue(9));
        assertEquals(1, host.registry().searchProvidedAssets(host.context(), "sample").size());
        assertEquals("ready", host.registry().statusItems(host.context()).get(0).value());
        assertEquals("sample.command", host.registry().menuRegistrations().get(0).commandId());
        assertEquals("sample-tool", host.registry().createTool("sample-tool").id());
        assertEquals("sample-panel", host.registry().panels().get(0).id());
        assertEquals("sample-workspace", host.registry().workspaces().get(0).id());
        assertEquals(List.of(new EditorInspectorField("sample.value", "Value", "ready")),
                host.registry().inspect(host.context()));
        assertEquals(1, host.registry().validate(host.context()).size());
        assertEquals("sample-overlay", host.registry().overlayRegistrations().get(0).id());
        assertTrue(host.registry().dispatchShortcut(host.context(),
                new com.rspsi.editor.input.EditorKeyEvent("K", true, false,
                        true, false, false, false)));
        assertEquals(1, host.registry().shortcutRegistrations().size());
    }

    @Test
    void inputRouterKeepsTextFieldsFromStealingEditorShortcuts() {
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initialize(
                List.of(new SamplePlugin()), session, new EmptyAssets());
        EditorInputRouter router = new EditorInputRouter(host.context(),
                new com.rspsi.editor.tool.EditorToolController());
        var event = new com.rspsi.editor.input.EditorKeyEvent("K", true, false,
                true, false, false, false);

        assertTrue(!router.key(event, true));
        assertTrue(router.key(event, false));
    }

    @Test
    void coreToolsAreRegisteredAsNeutralContributions() {
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initializeWithCoreModules(
                CoreEditorModules.all(), List.of(), session, new EmptyAssets());

        assertTrue(host.plugins().isEmpty());
        assertEquals(CoreEditorModules.ids(), host.coreModuleIds());
        assertEquals(24, host.registry().toolRegistrations().size());
        EditorToolRegistration registration = host.registry().toolRegistrations().get(0);
        assertEquals("terrain.paint-underlay", registration.id());
        assertEquals("Terrain", registration.category());
        assertEquals("paint-underlay", host.registry().createTool(registration.id()).id());
    }

    @Test
    void terrainSettingsBelongToThePluginAndConfigureNewToolInstances() {
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initializeWithCoreModules(
                List.of(new CoreTerrainModule()), List.of(), session, new EmptyAssets());

        var settings = host.registry().settingsForTool(host.context(), "terrain.raise");
        var radius = settings.stream().filter(value -> value.id().equals("terrain.height-radius"))
                .findFirst().orElseThrow();
        radius.setValue(6);

        ChangeHeightTool tool = (ChangeHeightTool) host.registry().createTool("terrain.raise");
        assertEquals(6, tool.radius());
        assertEquals(8, tool.delta());
        assertTrue(settings.stream().anyMatch(value -> value.id().equals("terrain.height-falloff")));
    }

    @Test
    void objectAndSelectionSettingsAlsoConfigurePluginTools() {
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initializeWithCoreModules(
                CoreEditorModules.all(), List.of(), session, new EmptyAssets());

        var objectSettings = host.registry().settingsForTool(host.context(), "object.place");
        objectSettings.stream().filter(value -> value.id().equals("objects.id"))
                .findFirst().orElseThrow().setValue(1234);
        objectSettings.stream().filter(value -> value.id().equals("objects.type"))
                .findFirst().orElseThrow().setValue(10);
        PlaceObjectTool place = (PlaceObjectTool) host.registry().createTool("object.place");
        assertEquals(1234, place.idValue());

        objectSettings.stream().filter(value -> value.id().equals("objects.snap-grid"))
                .findFirst().orElseThrow().setValue(4);
        MoveObjectTool move = (MoveObjectTool) host.registry().createTool("object.move");
        assertEquals(4, move.snapGridSize());

        var selectionSettings = host.registry().settingsForTool(host.context(), "selection.replace");
        selectionSettings.stream().filter(value -> value.id().equals("selection.replacement-id"))
                .findFirst().orElseThrow().setValue(4321);
        ReplaceSelectionTool replace = (ReplaceSelectionTool)
                host.registry().createTool("selection.replace");
        assertEquals(4321, replace.replacementId());
    }

    @Test
    void duplicatePluginIdsAreRejected() {
        EditorPlugin plugin = new SamplePlugin();
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                EditorPluginHost.initialize(List.of(plugin, plugin), session, new EmptyAssets()));
    }

    @Test
    void pluginDependenciesOverrideLoadOrderButRemainDeterministic() {
        List<String> events = new ArrayList<>();
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initialize(
                List.of(new DependentPlugin(events), new LifecyclePlugin("base", events)),
                session, new EmptyAssets());

        assertEquals(List.of("base", "dependent"), host.plugins().stream()
                .map(EditorPlugin::id).toList());
        host.close();
        assertEquals(List.of("dependent", "base"), events);
    }

    @Test
    void missingPluginDependenciesAreRejected() {
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> EditorPluginHost.initialize(
                List.of(new EditorPlugin() {
                    @Override public String id() { return "dependent"; }
                    @Override public EditorPluginDescriptor descriptor() {
                        return new EditorPluginDescriptor("dependent", "Dependent", "1", List.of("missing"));
                    }
                }), session, new EmptyAssets()));
    }

    @Test
    void typedSettingsRejectNonFiniteAndOverflowingNumbers() {
        var integer = com.rspsi.editor.plugin.EditorSetting.integer(
                "count", "Count", 0, 10, () -> 1, ignored -> { });
        var decimal = com.rspsi.editor.plugin.EditorSetting.decimal(
                "ratio", "Ratio", 0, 10, () -> 1.0, ignored -> { });

        assertThrows(IllegalArgumentException.class, () -> integer.setValue((double) Integer.MAX_VALUE + 1));
        assertThrows(IllegalArgumentException.class, () -> decimal.setValue(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> decimal.setValue(Double.POSITIVE_INFINITY));
    }

    @Test
    void duplicateWorkspaceIdsAreRejected() {
        com.rspsi.editor.plugin.EditorPluginRegistry registry =
                new com.rspsi.editor.plugin.EditorPluginRegistry();
        WorkspaceDefinition workspace = new WorkspaceDefinition(
                "same", List.of(new com.rspsi.editor.ui.PanelPlacement(
                        "panel", DockRegion.RIGHT, 0)));
        registry.registerWorkspace(workspace);
        assertThrows(IllegalArgumentException.class, () -> registry.registerWorkspace(workspace));
    }

    @Test
    void closeShutsDownInReverseOrderAndRemovesPluginContributions() {
        List<String> events = new ArrayList<>();
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginHost host = EditorPluginHost.initialize(
                List.of(new LifecyclePlugin("first", events), new LifecyclePlugin("second", events)),
                session, new EmptyAssets());

        assertEquals(List.of("first-tool", "second-tool"), host.registry().toolIds());
        host.close();
        host.close();

        assertEquals(List.of("second", "first"), events);
        assertEquals(List.of(), host.plugins());
        assertEquals(List.of(), host.registry().toolIds());
        assertEquals(List.of(), host.registry().commandRegistrations());
        assertEquals(List.of(), host.registry().toolContextRegistrations());
        assertEquals(List.of(), host.registry().assetProviderRegistrations());
        assertEquals(List.of(), host.registry().statusRegistrations());
        assertEquals(List.of(), host.registry().menuRegistrations());
        assertEquals(List.of(), host.registry().panels());
    }

    @Test
    void failedInitializationShutsDownAlreadyLoadedPlugins() {
        List<String> events = new ArrayList<>();
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));

        assertThrows(IllegalStateException.class, () -> EditorPluginHost.initialize(
                List.of(new LifecyclePlugin("first", events), new FailingPlugin()),
                session, new EmptyAssets()));

        assertEquals(List.of("first"), events);
    }

    @Test
    void pluginResourcesCloseInReverseOrderAndRetainedContextsCannotRegisterAfterClose() {
        List<String> resources = new ArrayList<>();
        EditorSession session = new EditorSession(new com.rspsi.editor.model.WorldModel(1, 1, 1));
        EditorPluginContext[] retained = new EditorPluginContext[1];
        EditorPlugin plugin = new EditorPlugin() {
            @Override public String id() { return "resources"; }

            @Override public void initialize(EditorPluginContext context) {
                retained[0] = context;
                context.track(() -> resources.add("first"));
                context.track(() -> resources.add("second"));
                context.registry().registerTool("resources.tool", SampleTool::new);
            }
        };

        EditorPluginHost host = EditorPluginHost.initialize(List.of(plugin), session, new EmptyAssets());
        host.close();

        assertEquals(List.of("second", "first"), resources);
        assertThrows(IllegalStateException.class,
                () -> retained[0].registry().registerTool("late", SampleTool::new));
        assertThrows(IllegalStateException.class,
                () -> retained[0].track(() -> { }));
    }

    private static final class SamplePlugin implements EditorPlugin {
        @Override
        public String id() {
            return "sample";
        }

        @Override
        public void initialize(com.rspsi.editor.plugin.EditorPluginContext context) {
            context.registry().registerTool("sample-tool", SampleTool::new);
            context.registry().registerCommand(new com.rspsi.editor.plugin.EditorCommandRegistration(
                    "sample.command", "Sample command", "Sample", SampleCommand::new));
            int[] radius = {3};
            context.registry().registerToolContext(new com.rspsi.editor.plugin.EditorToolContextRegistration(
                    "sample-context", "Sample context", List.of("sample-tool"), 0,
                    () -> ignored -> List.of(com.rspsi.editor.plugin.EditorSetting.integer(
                            "sample.radius", "Radius", 1, 8, () -> radius[0], value -> radius[0] = value))));
            context.registry().registerAssetProvider(new com.rspsi.editor.plugin.EditorAssetProviderRegistration(
                    "sample-assets", "Sample assets", List.of("sample"), 0,
                    () -> (ignored, query) -> List.of(new com.rspsi.editor.assets.AssetDescriptor(
                            77, "sample", "Sample asset"))));
            context.registry().registerStatus(new com.rspsi.editor.plugin.EditorStatusRegistration(
                    "sample-status", "Sample status", 0,
                    () -> ignored -> List.of(new com.rspsi.editor.plugin.EditorStatusItem(
                            "sample.status", "State", "ready"))));
            context.registry().registerMenu(new com.rspsi.editor.plugin.EditorMenuRegistration(
                    "sample-menu", List.of("Tools"), "Sample command", "sample.command", 0));
            context.registry().registerPanel(new PanelDescriptor(
                    "sample-panel", DockRegion.RIGHT, null, 120, 120));
            context.registry().registerWorkspace(new WorkspaceDefinition(
                    "sample-workspace",
                    List.of(new com.rspsi.editor.ui.PanelPlacement(
                            "sample-panel", DockRegion.RIGHT, 0))));
            context.registry().registerInspector(new com.rspsi.editor.plugin.EditorInspectorRegistration(
                    "sample-inspector", "Sample", "Diagnostics",
                    () -> ignored -> List.of(new EditorInspectorField("sample.value", "Value", "ready"))));
            context.registry().registerValidator(new com.rspsi.editor.plugin.EditorValidatorRegistration(
                    "sample-validator", "Sample", "Diagnostics",
                    () -> ignored -> List.of(new com.rspsi.editor.validation.ValidationIssue(
                            com.rspsi.editor.validation.ValidationIssue.Severity.WARNING,
                            "SAMPLE", "Sample warning", null))));
            context.registry().registerOverlay(new com.rspsi.editor.plugin.EditorOverlayRegistration(
                    "sample-overlay", "Sample", "Diagnostics", () -> (scene, draw) -> { }));
            context.registry().registerShortcut(new EditorShortcutRegistration(
                    "sample-shortcut", "Sample shortcut", "K", true, false, false, false,
                    () -> (ignored, event) -> true));
        }
    }

    private static final class LifecyclePlugin implements EditorPlugin {
        private final String id;
        private final List<String> events;

        private LifecyclePlugin(String id, List<String> events) {
            this.id = id;
            this.events = events;
        }

        @Override public String id() { return id; }

        @Override public void initialize(com.rspsi.editor.plugin.EditorPluginContext context) {
            context.registry().registerTool(id + "-tool", SampleTool::new);
            context.registry().registerCommand(new com.rspsi.editor.plugin.EditorCommandRegistration(
                    id + ".command", id + " command", "Lifecycle", SampleCommand::new));
            context.registry().registerToolContext(new com.rspsi.editor.plugin.EditorToolContextRegistration(
                    id + "-context", id + " context", List.of(id + "-tool"), 0,
                    () -> ignored -> List.of()));
            context.registry().registerAssetProvider(new com.rspsi.editor.plugin.EditorAssetProviderRegistration(
                    id + "-assets", id + " assets", List.of("test"), 0,
                    () -> (ignored, query) -> List.of()));
            context.registry().registerStatus(new com.rspsi.editor.plugin.EditorStatusRegistration(
                    id + "-status", id + " status", 0,
                    () -> ignored -> List.of()));
            context.registry().registerMenu(new com.rspsi.editor.plugin.EditorMenuRegistration(
                    id + "-menu", List.of("Tools"), id + " command", id + ".command", 0));
            context.registry().registerPanel(new PanelDescriptor(
                    id + "-panel", DockRegion.RIGHT, null, 100, 100));
        }

        @Override public void shutdown(com.rspsi.editor.plugin.EditorPluginContext context) {
            events.add(id);
        }
    }

    private static final class DependentPlugin implements EditorPlugin {
        private final List<String> events;

        private DependentPlugin(List<String> events) { this.events = events; }

        @Override public String id() { return "dependent"; }

        @Override public int loadOrder() { return -100; }

        @Override public EditorPluginDescriptor descriptor() {
            return new EditorPluginDescriptor(id(), "Dependent", "1", List.of("base"));
        }

        @Override public void initialize(EditorPluginContext context) {
            context.registry().registerTool("dependent-tool", SampleTool::new);
        }

        @Override public void shutdown(EditorPluginContext context) { events.add(id()); }
    }

    private static final class FailingPlugin implements EditorPlugin {
        @Override public String id() { return "failing"; }

        @Override public int loadOrder() { return 10; }

        @Override public void initialize(com.rspsi.editor.plugin.EditorPluginContext context) {
            context.registry().registerTool("failing-tool", SampleTool::new);
            throw new IllegalStateException("intentional test failure");
        }
    }

    private static final class SampleTool implements EditorTool {
        @Override public String id() { return "sample-tool"; }
        @Override public void activate(ToolContext context) { }
        @Override public void deactivate() { }
        @Override public void pointerDown(com.rspsi.editor.input.PointerEvent event) { }
        @Override public void pointerDrag(com.rspsi.editor.input.PointerEvent event) { }
        @Override public void pointerUp(com.rspsi.editor.input.PointerEvent event) { }
    }

    private static final class SampleCommand implements EditorCommand {
        @Override public void apply(EditorSession session) { }
        @Override public void undo(EditorSession session) { }
        @Override public String description() { return "sample command"; }
    }

    private static final class EmptyAssets implements AssetRepository {
        @Override public List<com.rspsi.editor.assets.AssetDescriptor> search(String query) {
            return List.of();
        }
        @Override public java.util.Optional<com.rspsi.editor.assets.AssetDescriptor> get(int id, String type) {
            return java.util.Optional.empty();
        }
    }
}
