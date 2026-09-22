package com.rspsi.studio.ui;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.studio.PluginManagerWindow;
import com.rspsi.studio.PreferencesWindow;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Program-owned main menu bar (File, Edit, View, Cache, Plugins, Server, Help).
 * Clean, decoupled from workspace tabs to eliminate UI collisions.
 */
public final class StudioMenuBar {

    public void render(LoadedOsrsCacheSession cache,
                       EditorPluginLifecycleManager pluginLifecycle,
                       ServerIntegrationService integrations,
                       boolean showServerSpawns,
                       Consumer<Boolean> setShowServerSpawns,
                       PreferencesWindow preferencesWindow,
                       PluginManagerWindow pluginManagerWindow,
                       Runnable openIntegrationCenter,
                       Runnable openCommandPalette,
                       Runnable resetLayout,
                        boolean drawerVisible,
                        Runnable toggleDrawer,
                        boolean hudVisible,
                        Runnable toggleHud,
                        boolean leftRailVisible,
                        Runnable toggleLeftRail) {
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
            EditorSession session = pluginLifecycle != null && pluginLifecycle.host() != null
                    ? pluginLifecycle.host().context().session() : null;
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
            if (ImGui.menuItem(StudioIcons.TUNE + "  Utility Drawer", "Ctrl+Space", drawerVisible)) {
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
            if (integrations != null && integrations.isConnected()) {
                var session = integrations.activeSession().orElseThrow();
                ImGui.textDisabled("Connected: " + session.provider().name());
                if (ImGui.menuItem(StudioIcons.CLOSE + "  Disconnect Server Project")) {
                    integrations.disconnect();
                }
            } else {
                ImGui.textDisabled("No server project connected");
            }
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.CHECK + "  Show Server NPC Spawns", null, showServerSpawns)) {
                if (setShowServerSpawns != null) setShowServerSpawns.accept(!showServerSpawns);
            }
            ImGui.endMenu();
        }

        // 6. Help Menu
        if (ImGui.beginMenu("Help")) {
            ImGui.menuItem(StudioIcons.INFO + "  OpenRune Studio", null, true, false);
            ImGui.separator();
            if (ImGui.menuItem(StudioIcons.PREFAB + "  Plugins...", null, pluginManagerWindow != null && pluginManagerWindow.isOpen())) {
                if (pluginManagerWindow != null) pluginManagerWindow.toggle();
            }
            ImGui.endMenu();
        }

        ImGui.endMainMenuBar();
    }
}
