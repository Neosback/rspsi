package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.PluginContext;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingSpec;

import java.util.Set;

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
            com.rspsi.editor.plugin.ContributionOwner owner = context.owner();
            context.settingsService().register(owner, SettingSpec.of(ENABLED, false, SettingScope.PROJECT, cat,
                    "Enable OpenRune", "Enables OpenRune server project integration.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(PROJECT_PATH, "", SettingScope.PROJECT, cat,
                    "Project Root", "Path to the OpenRune server project repository.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(SYMBOLS, true, SettingScope.PROJECT, cat,
                    "GameVals & Symbols", "Loads symbolic names from .data/gamevals and gamevals.toml.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(CONTENT_INDEX, true, SettingScope.PROJECT, cat,
                    "Content Script Index", "Indexes Kotlin content scripts for source references.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(NPC_SPAWNS, true, SettingScope.PROJECT, cat,
                    "Server NPC Spawns", "Displays server-defined NPC spawns in the editor.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(INTERFACES, true, SettingScope.PROJECT, cat,
                    "Interfaces", "Integrates OpenRune UI and component references.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(CS2, true, SettingScope.PROJECT, cat,
                    "ClientScripts", "Links CS2 source files.", Set.of()));
            context.settingsService().register(owner, SettingSpec.of(CACHE_BUILD, false, SettingScope.PROJECT, cat,
                    "Cache Build Tooling", "Allows triggering cache builds through OpenRune Gradle tooling.", Set.of()));
        }

        if (context.integrations() != null) {
            context.integrations().registerProvider(provider);
        }
    }
}
