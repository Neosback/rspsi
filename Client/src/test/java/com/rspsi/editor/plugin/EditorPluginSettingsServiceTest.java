package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.settings.SettingHandle;
import com.rspsi.editor.settings.SettingInvalidation;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the owned-settings path end to end through the real plugin host: a plugin
 * registers a dynamic setting, tracks the handle for automatic cleanup, and the
 * setting disappears with the rest of the plugin's contributions on host close.
 */
class EditorPluginSettingsServiceTest {
    private static final SettingKey<Integer> PATH_WIDTH =
            new SettingKey<>("plugin.smartroads.path-width", Integer.class);

    @Test
    void pluginOwnedSettingIsUsableWhileLoadedAndRemovedOnUnload() {
        EditorSession session = new EditorSession(new WorldModel(1, 1, 1));
        SmartRoadsPlugin plugin = new SmartRoadsPlugin();

        EditorPluginHost host = EditorPluginHost.initialize(List.of(plugin), session, new EmptyAssets());
        EditorPluginContext context = host.context();

        assertEquals(3, context.settingsService().get(PATH_WIDTH));
        assertEquals(ContributionOwner.plugin("org.example.smartroads"),
                context.settingsService().ownerOf(PATH_WIDTH.id()).orElseThrow());

        host.close();

        assertThrows(IllegalArgumentException.class, () -> context.settingsService().get(PATH_WIDTH));
        assertTrue(context.settingsService().ownerOf(PATH_WIDTH.id()).isEmpty());
    }

    private static final class SmartRoadsPlugin implements EditorPlugin {
        @Override
        public String id() {
            return "org.example.smartroads";
        }

        @Override
        public void initialize(EditorPluginContext context) {
            SettingSpec<Integer> spec = SettingSpec.integer(PATH_WIDTH, 3, 1, 12,
                    SettingScope.VIEWPORT, "Path width", "Width in tiles",
                    Set.of(SettingInvalidation.REDRAW));
            SettingHandle handle = context.settingsService()
                    .register(ContributionOwner.plugin(id()), spec);
            context.track(handle);
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
