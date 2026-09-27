package com.rspsi.studio.ui;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.studio.PreferencesWindow;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Program-owned main menu bar (File, Edit, View, Cache, Server, Help).
 * Clean, decoupled from workspace tabs to eliminate UI collisions.
 */
public final class StudioMenuBar {

    public void render(LoadedOsrsCacheSession cache,
                       EditorPluginHost editorHost,
                       ServerIntegrationService integrations,
                       boolean showServerSpawns,
                       Consumer<Boolean> setShowServerSpawns,
                       PreferencesWindow preferencesWindow,
                       Runnable openIntegrationCenter,
                       Runnable openCommandPalette,
                       Runnable resetLayout,
                        boolean drawerVisible,
                        Runnable toggleDrawer,
                        boolean hudVisible,
                        Runnable toggleHud,
                        boolean leftRailVisible,
                        Runnable toggleLeftRail,
                        boolean worldMapOpen,
                        Runnable toggleWorldMap) {
        if (!ImGui.beginMainMenuBar()) return;

        // 1. File Menu
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem(StudioIcons.SETTINGS + "  Preferences...", "Ctrl+,", preferencesWindow != null && preferencesWindow.isOpen())) {
                if (preferencesWindow != null) preferencesWindow.toggle();
            }
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.CLOSE + "  Exit")) {
                // Application exit handled via window close
            }
            ImGui.endMenu();
        }

        // 2. Edit Menu
        if (ImGui.beginMenu("Edit")) {
            EditorSession session = editorHost != null
                    ? editorHost.context().session() : null;
            boolean canUndo = session != null && session.history().canUndo();
            boolean canRedo = session != null && session.history().canRedo();
            if (ImGui.menuItem(StudioIcons.UNDO + "  Undo", "Ctrl+Z", false, canUndo) && session != null) session.undo();
            if (ImGui.menuItem(StudioIcons.REDO + "  Redo", "Ctrl+Shift+Z", false, canRedo) && session != null) session.redo();
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.REFRESH + "  Reset Layout")) {
                if (resetLayout != null) resetLayout.run();
            }
            ImGui.endMenu();
        }

        // 3. View Menu (Program-level window/view toggles)
        if (ImGui.beginMenu("View")) {
            if (ImGui.menuItem(StudioIcons.MAP + "  World Map...", "Ctrl+M", worldMapOpen)) {
                if (toggleWorldMap != null) toggleWorldMap.run();
            }
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.TUNE + "  Context Drawer", "Ctrl+Space", drawerVisible)) {
                if (toggleDrawer != null) toggleDrawer.run();
            }
            if (ImGui.menuItem(StudioIcons.VIEWPORT + "  Tile Information HUD", null, hudVisible)) {
                if (toggleHud != null) toggleHud.run();
            }
            if (ImGui.menuItem(StudioIcons.BRUSH + "  Left Brush Rail (always show)", null, leftRailVisible)) {
                if (toggleLeftRail != null) toggleLeftRail.run();
            }
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.SEARCH + "  Command Palette", "Ctrl+P")) {
                if (openCommandPalette != null) openCommandPalette.run();
            }
            ImGui.endMenu();
        }

        // 4. Cache Menu
        if (ImGui.beginMenu("Cache")) {
            if (cache != null) {
                ImGui.menuItem(StudioIcons.FOLDER + "  Revision " + cache.identity().revision(), null, true, false);
                if (cache.identity().subRevision() != null) {
                    ImGui.menuItem(StudioIcons.FOLDER_OPEN + "  Sub-revision " + cache.identity().subRevision(), null, true, false);
                }
            } else {
                ImGui.textDisabled("No cache loaded");
            }
            ImGui.endMenu();
        }

        // 5. Server Menu
        if (ImGui.beginMenu("Server")) {
            if (openIntegrationCenter != null) {
                if (ImGui.menuItem(StudioIcons.TERMINAL + "  Integration Center...")) openIntegrationCenter.run();
            }
            boolean connected = integrations != null && integrations.isConnected();
            if (connected) {
                var session = integrations.activeSession().orElseThrow();
                ImGui.textDisabled("Project-owned: " + session.provider().name());
                ImGui.textDisabled("Close the Studio project to disconnect this integration.");
            } else {
                ImGui.textDisabled("No server project connected");
            }
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.CHECK + "  Show Server NPC Spawns", null,
                    showServerSpawns, connected)) {
                boolean next = !showServerSpawns;
                if (next) {
                    try {
                        var promoted = integrations.ensureCapabilities(IntegrationCapability.NPC_SPAWNS);
                        next = promoted.activeCapabilities().contains(IntegrationCapability.NPC_SPAWNS);
                    } catch (RuntimeException ignored) {
                        next = false;
                    }
                }
                if (setShowServerSpawns != null) setShowServerSpawns.accept(next);
            }
            if (connected && !showServerSpawns) {
                ImGui.textDisabled("Spawn data loads lazily when enabled.");
            }
            ImGui.endMenu();
        }

        // 7. Help Menu
        if (ImGui.beginMenu("Help")) {
            ImGui.menuItem(StudioIcons.INFO + "  OpenRune Content Studio", null, true, false);
            ImGui.endMenu();
        }

        ImGui.endMainMenuBar();
    }
}
