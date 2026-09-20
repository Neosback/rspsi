package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.PluginApi;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingScope;

/**
 * First-party plugin integrating OpenRune Server ecosystem capabilities into OpenRune Studio.
 *
 * <p>Registers the OpenRuneServerProvider with ServerIntegrationService and registers
 * project settings for symbolic names, source references, and NPC spawns.</p>
 */
public final class OpenRuneServerPlugin implements EditorPlugin {
    public static final String PLUGIN_ID = "openrune.server.integration";

    public static final SettingKey<Boolean> ENABLED = new SettingKey<>("integration.openrune.enabled", Boolean.class);
    public static final SettingKey<String> PROJECT_PATH = new SettingKey<>("integration.openrune.project-path", String.class);
    public static final SettingKey<Boolean> SYMBOLS = new SettingKey<>("integration.openrune.symbols", Boolean.class);
    public static final SettingKey<Boolean> CONTENT_INDEX = new SettingKey<>("integration.openrune.content-index", Boolean.class);
    public static final SettingKey<Boolean> NPC_SPAWNS = new SettingKey<>("integration.openrune.npc-spawns", Boolean.class);
    public static final SettingKey<Boolean> INTERFACES = new SettingKey<>("integration.openrune.interfaces", Boolean.class);
    public static final SettingKey<Boolean> CS2 = new SettingKey<>("integration.openrune.cs2", Boolean.class);
    public static final SettingKey<Boolean> CACHE_BUILD = new SettingKey<>("integration.openrune.cache-build", Boolean.class);

    private final OpenRuneServerProvider provider = new OpenRuneServerProvider();

    @Override
    public String id() {
        return PLUGIN_ID;
    }

    @Override
    public com.rspsi.editor.plugin.EditorPluginDescriptor descriptor() {
        return new com.rspsi.editor.plugin.EditorPluginDescriptor(PLUGIN_ID, "OpenRune Server Integration", "1.0.0", java.util.List.of());
    }

    public OpenRuneServerProvider provider() {
        return provider;
    }

    @Override
    public void initialize(com.rspsi.editor.plugin.EditorPluginContext context) {
        if (context.settingsService() != null) {
            String cat = "Server Integration - OpenRune";
            PluginApi api = context.api(this);
            api.setting(ENABLED.id(), false).scope(SettingScope.PROJECT).category(cat)
                    .label("Enable OpenRune").description("Enables OpenRune server project integration.")
                    .register();
            api.setting(PROJECT_PATH.id(), "").scope(SettingScope.PROJECT).category(cat)
                    .label("Project Root").description("Path to the OpenRune server project repository.")
                    .register();
            api.setting(SYMBOLS.id(), true).scope(SettingScope.PROJECT).category(cat)
                    .label("GameVals & Symbols").description("Loads symbolic names from .data/gamevals and gamevals.toml.")
                    .register();
            api.setting(CONTENT_INDEX.id(), true).scope(SettingScope.PROJECT).category(cat)
                    .label("Content Script Index").description("Indexes Kotlin content scripts for source references.")
                    .register();
            api.setting(NPC_SPAWNS.id(), true).scope(SettingScope.PROJECT).category(cat)
                    .label("Server NPC Spawns").description("Displays server-defined NPC spawns in the editor.")
                    .register();
            api.setting(INTERFACES.id(), true).scope(SettingScope.PROJECT).category(cat)
                    .label("Interfaces").description("Integrates OpenRune UI and component references.")
                    .register();
            api.setting(CS2.id(), true).scope(SettingScope.PROJECT).category(cat)
                    .label("ClientScripts").description("Links CS2 source files.")
                    .register();
            api.setting(CACHE_BUILD.id(), false).scope(SettingScope.PROJECT).category(cat)
                    .label("Cache Build Tooling").description("Allows triggering cache builds through OpenRune Gradle tooling.")
                    .register();
        }

        if (context.integrations() != null) {
            context.integrations().registerProvider(provider);
        }
    }
}
