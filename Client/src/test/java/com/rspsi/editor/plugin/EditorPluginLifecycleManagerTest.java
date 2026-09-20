package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.plugins.server.openrune.OpenRuneServerPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.rspsi.editor.plugin.EditorPluginLifecycleManager.HostFactory;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager.PluginStatus;

import static org.junit.jupiter.api.Assertions.*;

class EditorPluginLifecycleManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void persistedStateSurvivesManagerRecreation() throws Exception {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginStateStore store = EditorPluginStateStore.load(file);
        store.replaceDisabled(List.of(TogglePlugin.ID));

        EditorPluginStateStore reloaded = EditorPluginStateStore.load(file);
        assertFalse(reloaded.isEnabled(TogglePlugin.ID));
        assertTrue(reloaded.isEnabled("other.plugin"));
    }

    @Test
    void malformedStateFileBehavesAsAllEnabled() throws Exception {
        Path file = tempDir.resolve("plugins.json");
        Files.writeString(file, "{ not json ]");

        EditorPluginStateStore store = EditorPluginStateStore.load(file);
        assertTrue(store.isEnabled(TogglePlugin.ID));
        assertTrue(store.disabledIds().isEmpty());
    }

    @Test
    void managerSkipsUserDisabledPluginsAndPersistsIntent() {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginStateStore store = EditorPluginStateStore.load(file);
        store.replaceDisabled(List.of(TogglePlugin.ID));
        List<String> initialized = new ArrayList<>();

        EditorPluginLifecycleManager manager = EditorPluginLifecycleManager.start(
                List.of(new TogglePlugin(), new PlainPlugin()),
                store,
                session(),
                EmptyAssetRepository.INSTANCE,
                null,
                candidates -> {
                    candidates.forEach(plugin -> initialized.add(plugin.id()));
                    return EditorPluginHost.initialize(candidates, session(),
                            EmptyAssetRepository.INSTANCE);
                });

        assertEquals(List.of(PlainPlugin.ID), initialized);
        assertEquals(PluginStatus.DISABLED, manager.status(TogglePlugin.ID));
        assertEquals(PluginStatus.ENABLED, manager.status(PlainPlugin.ID));
        manager.close();
    }

    @Test
    void disablingAPluginCascadesToDependents() {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginLifecycleManager manager = start(file);

        EditorPluginLifecycleManager.RebuildResult result =
                manager.setEnabled(TerrainLikePlugin.ID, false);

        assertTrue(result.initializedIds().contains(PlainPlugin.ID));
        assertFalse(result.initializedIds().contains(TerrainLikePlugin.ID));
        assertFalse(result.initializedIds().contains(ObjectLikePlugin.ID),
                "dependent plugin must be cascade-disabled");
        assertEquals(PluginStatus.DISABLED, manager.status(TerrainLikePlugin.ID));
        assertEquals(PluginStatus.CASCADE_DISABLED, manager.status(ObjectLikePlugin.ID));
        assertEquals(List.of(ObjectLikePlugin.ID), manager.dependentsOf(TerrainLikePlugin.ID));
        assertTrue(manager.state().disabledIds().contains(TerrainLikePlugin.ID));
        assertFalse(manager.state().disabledIds().contains(ObjectLikePlugin.ID),
                "cascade must not be persisted as user intent");
        assertFalse(Files.exists(file) && read(file).contains(ObjectLikePlugin.ID));
        manager.close();
    }

    @Test
    void enablingADependencyReevaluatesCascadeDisabledDependents() {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginLifecycleManager manager = start(file);
        manager.setEnabled(TerrainLikePlugin.ID, false);

        manager.setEnabled(TerrainLikePlugin.ID, true);

        assertEquals(PluginStatus.ENABLED, manager.status(TerrainLikePlugin.ID));
        assertEquals(PluginStatus.ENABLED, manager.status(ObjectLikePlugin.ID));
        assertTrue(manager.host().plugins().stream()
                .anyMatch(plugin -> plugin.id().equals(ObjectLikePlugin.ID)));
        manager.close();
    }

    @Test
    void enableAllRestoresEveryCandidateAndClearsPersistedIntent() {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginLifecycleManager manager = start(file);
        manager.setEnabled(TerrainLikePlugin.ID, false);
        assertFalse(manager.allEnabled());

        EditorPluginLifecycleManager.RebuildResult result = manager.enableAll();

        assertTrue(result.skippedIds().isEmpty());
        assertTrue(manager.allEnabled());
        assertTrue(manager.state().disabledIds().isEmpty());
        manager.close();
    }

    @Test
    void unknownPluginIdsAreRejected() {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginLifecycleManager manager = start(file);

        assertThrows(IllegalArgumentException.class, () -> manager.setEnabled("nope", false));
        assertThrows(IllegalArgumentException.class, () -> manager.status("nope"));
        manager.close();
    }

    @Test
    void closeReleasesTheActiveHostOnlyOnce() {
        Path file = tempDir.resolve("plugins.json");
        EditorPluginLifecycleManager manager = start(file);
        EditorPluginHost host = manager.host();

        manager.close();
        assertNull(manager.host());

        // The host itself is already closed; closing again must be a no-op.
        manager.close();
        assertNotSame(host, manager.host());
    }

    @Test
    void closeReleasesOwnedDiscoveryResourcesAfterTheHost() {
        Path file = tempDir.resolve("plugins.json");
        List<String> closeOrder = new ArrayList<>();
        AutoCloseable owned = () -> closeOrder.add("discovery");
        EditorPluginLifecycleManager manager = EditorPluginLifecycleManager.start(
                List.of(new PlainPlugin()),
                EditorPluginStateStore.load(file),
                session(),
                EmptyAssetRepository.INSTANCE,
                null,
                candidates -> EditorPluginHost.initialize(candidates, session(),
                        EmptyAssetRepository.INSTANCE),
                owned);

        manager.close();
        manager.close();

        assertEquals(List.of("discovery"), closeOrder,
                "owned discovery resources must be released exactly once");
    }

    @Test
    void pluginSettingsSurviveAnUnrelatedPluginToggle() {
        Path file = tempDir.resolve("plugins.json");
        SettingsStore sharedSettings = new SettingsStore(EditorSettingKeys.registry());
        EditorPluginLifecycleManager manager = EditorPluginLifecycleManager.start(
                List.of(new TerrainLikePlugin(), new ObjectLikePlugin(), new SettingsOwningPlugin()),
                EditorPluginStateStore.load(file),
                session(),
                EmptyAssetRepository.INSTANCE,
                null,
                candidates -> EditorPluginHost.initialize(candidates, session(), EmptyAssetRepository.INSTANCE,
                        null, sharedSettings, new EditorTaskService(), new EditorNotificationService()));

        assertEquals(3, manager.host().context().settingsService().get(SettingsOwningPlugin.WIDTH));
        assertEquals(ContributionOwner.plugin(SettingsOwningPlugin.ID),
                manager.host().context().settingsService().ownerOf(SettingsOwningPlugin.WIDTH.id()).orElseThrow());

        assertDoesNotThrow(() -> manager.setEnabled(TerrainLikePlugin.ID, false));

        assertEquals(3, manager.host().context().settingsService().get(SettingsOwningPlugin.WIDTH));
        assertEquals(ContributionOwner.plugin(SettingsOwningPlugin.ID),
                manager.host().context().settingsService().ownerOf(SettingsOwningPlugin.WIDTH.id()).orElseThrow());

        manager.close();
    }

    @Test
    void openRuneServerPluginSettingsSurviveTogglesOfItselfAndOtherPlugins() {
        Path file = tempDir.resolve("plugins.json");
        SettingsStore sharedSettings = new SettingsStore(EditorSettingKeys.registry());
        EditorPluginLifecycleManager manager = EditorPluginLifecycleManager.start(
                List.of(new OpenRuneServerPlugin(), new PlainPlugin()),
                EditorPluginStateStore.load(file),
                session(),
                EmptyAssetRepository.INSTANCE,
                null,
                candidates -> EditorPluginHost.initialize(candidates, session(), EmptyAssetRepository.INSTANCE,
                        null, sharedSettings, new EditorTaskService(), new EditorNotificationService()));

        assertEquals(ContributionOwner.plugin(OpenRuneServerPlugin.PLUGIN_ID),
                manager.host().context().settingsService().ownerOf(OpenRuneServerPlugin.ENABLED.id()).orElseThrow());

        assertDoesNotThrow(() -> manager.setEnabled(PlainPlugin.ID, false));
        assertEquals(ContributionOwner.plugin(OpenRuneServerPlugin.PLUGIN_ID),
                manager.host().context().settingsService().ownerOf(OpenRuneServerPlugin.ENABLED.id()).orElseThrow());

        assertDoesNotThrow(() -> manager.setEnabled(OpenRuneServerPlugin.PLUGIN_ID, false));
        assertTrue(manager.host().context().settingsService().ownerOf(OpenRuneServerPlugin.ENABLED.id()).isEmpty());

        assertDoesNotThrow(() -> manager.setEnabled(OpenRuneServerPlugin.PLUGIN_ID, true));
        assertEquals(ContributionOwner.plugin(OpenRuneServerPlugin.PLUGIN_ID),
                manager.host().context().settingsService().ownerOf(OpenRuneServerPlugin.ENABLED.id()).orElseThrow());

        manager.close();
    }

    private EditorPluginLifecycleManager start(Path file) {
        EditorPluginStateStore store = EditorPluginStateStore.load(file);
        return EditorPluginLifecycleManager.start(
                List.of(new TerrainLikePlugin(), new ObjectLikePlugin(), new PlainPlugin()),
                store,
                session(),
                EmptyAssetRepository.INSTANCE,
                null,
                candidates -> EditorPluginHost.initialize(candidates, session(),
                        EmptyAssetRepository.INSTANCE));
    }

    private static EditorSession session() {
        return new EditorSession(new WorldModel(1, 1, 1));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class PlainPlugin implements EditorPlugin {
        static final String ID = "test.plain";

        @Override public String id() { return ID; }
    }

    private static final class TerrainLikePlugin implements EditorPlugin {
        static final String ID = "test.terrain";

        @Override public String id() { return ID; }
    }

    private static final class ObjectLikePlugin implements EditorPlugin {
        static final String ID = "test.objects";

        @Override
        public EditorPluginDescriptor descriptor() {
            return new EditorPluginDescriptor(ID, "Objects", "1", List.of(TerrainLikePlugin.ID));
        }

        @Override public String id() { return ID; }
    }

    private static final class TogglePlugin implements EditorPlugin {
        static final String ID = "test.toggle";

        @Override public String id() { return ID; }
    }

    private static final class SettingsOwningPlugin implements EditorPlugin {
        static final String ID = "test.settings-owner";
        static final SettingKey<Integer> WIDTH = new SettingKey<>("test.settings-owner.width", Integer.class);

        @Override public String id() { return ID; }

        @Override
        public void initialize(EditorPluginContext context) {
            context.api(this).setting(WIDTH.id(), 3)
                    .category("Test").label("Width").description("Width in tiles")
                    .register();
        }
    }
}
