package com.rspsi.editor.plugin;

import com.rspsi.editor.CommandHistory;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SelectionModel;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.generation.GeneratorService;
import com.rspsi.editor.knowledge.WorldKnowledgeService;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.settings.SettingsService;
import com.rspsi.editor.settings.SettingsStore;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Universal, stable service interface provided to editor plugins, procedural generators,
 * and workspace tools.
 *
 * <p>All operations pass through this boundary without direct coupling to OpenGL,
 * Dear ImGui, or FileStore internal classes.</p>
 */
public interface PluginContext {

    /** The active editor session. */
    EditorSession session();

    /** The authored world document. */
    default WorldDocument world() {
        return session().world();
    }

    /** Optional access to viewport picking and camera transformation. */
    Optional<EditorSceneAccess> scene();

    /** The active selection model. */
    default SelectionModel selection() {
        return session().selection();
    }

    /** The session command history for undo and redo operations. */
    default CommandHistory history() {
        return session().history();
    }

    /** Convenience command execution helper. */
    default Consumer<EditorCommand> commands() {
        return session()::execute;
    }

    /** Direct settings storage. */
    SettingsStore settings();

    /** Managed settings registration and categorisation service. */
    SettingsService settingsService();

    /** Asset repository for cache definitions and sprites. */
    AssetRepository assets();

    /** Background task scheduling and progress reporting service. */
    EditorTaskService tasks();

    /** Notification service for alerts, warnings, and messages. */
    EditorNotificationService notifications();

    /** Derived world semantic knowledge service. */
    WorldKnowledgeService knowledge();

    /** Procedural generation and WFC generator management service. */
    GeneratorService generators();

    /** The contribution owner identity for this context. */
    ContributionOwner owner();

    /** Universal symbolic identifier mapping service. */
    default com.rspsi.editor.symbols.SymbolService symbols() {
        return null;
    }

    /** Server source code cross-reference service. */
    default com.rspsi.editor.integration.reference.ReferenceService references() {
        return null;
    }

    /** Server NPC spawn service. */
    default com.rspsi.editor.integration.npc.NpcSpawnService spawns() {
        return null;
    }

    /** Studio multi-clock simulation engine. */
    default com.rspsi.editor.simulation.SimulationEngine simulation() {
        return null;
    }

    /** Server ecosystem integration service. */
    default com.rspsi.editor.integration.ServerIntegrationService integrations() {
        return null;
    }

    /** Tracks a resource for automatic host cleanup upon unload. */
    <T extends AutoCloseable> T track(T resource);
}
