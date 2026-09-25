package com.rspsi.studio;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginDescriptor;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager.PluginStatus;
import com.rspsi.editor.plugin.runtime.PluginEcosystemService;
import com.rspsi.editor.plugin.runtime.PluginRepositoryEntry;
import com.rspsi.editor.plugin.runtime.SemanticVersion;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Native Dear ImGui Plugin Manager dialog.
 *
 * <p>Provides visibility into discovered plugins, active status toggles,
 * dependency hierarchies, descriptor metadata, and live contribution counts.</p>
 */
public final class PluginManagerWindow {
    private boolean open;
    private final ImString searchQuery = new ImString(128);
    private final ImString repositoryUrl = new ImString(512);
    private PluginEcosystemService ecosystem;
    private Runnable rescanPlugins;
    private PluginEcosystemService.RepositoryRefresh repositoryRefresh;
    private boolean showRepositories;
    private String repositoryStatus = "";
    private String selectedStudioPluginId;

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void toggle() {
        this.open = !this.open;
    }

    public void setEcosystem(PluginEcosystemService ecosystem, Runnable rescanPlugins) {
        this.ecosystem = ecosystem;
        this.rescanPlugins = rescanPlugins;
    }

    public void render(
            EditorPluginLifecycleManager pluginLifecycle,
            StudioPluginManager studioPlugins,
            StudioPanelContext panelContext) {
        if (!open) return;
        if (pluginLifecycle == null && studioPlugins == null) return;

        StudioWidgets.windowBackdrop("plugins");
        ImGui.setNextWindowSize(880.0f, 600.0f, ImGuiCond.Appearing);
        ImBoolean pOpen = new ImBoolean(open);
        if (!ImGui.begin(StudioIcons.EXTENSION + "  Plugins##studio-plugins", pOpen, ImGuiWindowFlags.NoCollapse)) {
            open = pOpen.get();
            ImGui.end();
            return;
        }
        open = pOpen.get();

        // Top bar
        ImGui.setNextItemWidth(360.0f);
        ImGui.inputTextWithHint("##plugin-search", StudioIcons.SEARCH + "  Filter plugins...",
                searchQuery, ImGuiInputTextFlags.None);
        ImGui.sameLine();
        ImGui.beginDisabled(pluginLifecycle == null);
        if (ImGui.button("Enable All")) {
            pluginLifecycle.enableAll();
        }
        ImGui.sameLine();
        if (ImGui.button("Reload Active")) {
            pluginLifecycle.reload();
        }
        ImGui.endDisabled();
        if (ecosystem != null) {
            ImGui.sameLine();
            if (ImGui.button(showRepositories ? "Hide Repositories" : "Repositories")) {
                showRepositories = !showRepositories;
            }
            ImGui.sameLine();
            if (ImGui.button("Rescan JARs") && rescanPlugins != null) {
                ImGui.end();
                rescanPlugins.run();
                return;
            }
        }

        ImGui.separator();

        if (showRepositories && ecosystem != null && pluginLifecycle != null) {
            boolean installed = renderRepositories(pluginLifecycle);
            ImGui.separator();
            if (installed && rescanPlugins != null) {
                ImGui.end();
                rescanPlugins.run();
                return;
            }
        }

        if (selectedStudioPluginId != null && studioPlugins != null && panelContext != null) {
            renderStudioPluginSettings(studioPlugins, panelContext);
            ImGui.end();
            return;
        }

        String query = searchQuery.get().trim().toLowerCase();
        List<EditorPlugin> candidates = pluginLifecycle == null ? List.of() : pluginLifecycle.candidates();

        ImGui.beginChild("plugin-list", 0.0f, -ImGui.getFrameHeightWithSpacing(), true);
        if (studioPlugins != null) {
            renderStudioPlugins(studioPlugins, query);
            if (!studioPlugins.allPlugins().isEmpty() && !candidates.isEmpty()) {
                ImGui.separatorText("Editor plugins");
            }
        }
        for (EditorPlugin plugin : candidates) {
            EditorPluginDescriptor descriptor = plugin.descriptor();
            String id = plugin.id();
            String name = descriptor != null ? descriptor.name() : id;
            String desc = descriptor != null ? descriptor.description() : "";

            if (!query.isEmpty() && !id.toLowerCase().contains(query)
                    && !name.toLowerCase().contains(query)
                    && !desc.toLowerCase().contains(query)) {
                continue;
            }

            ImGui.pushID(id);
            PluginStatus status = pluginLifecycle.status(id);
            boolean isEnabled = status == PluginStatus.ENABLED;

            // Header line: Checkbox / Name / Version / Status Badge
            ImBoolean toggle = new ImBoolean(isEnabled);
            if (ImGui.checkbox("##toggle", toggle)) {
                pluginLifecycle.setEnabled(id, toggle.get());
            }
            ImGui.sameLine();
            ImGui.text(name);
            ImGui.sameLine();
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.textDisabled(id);
            ImGui.popFont();

            ImGui.sameLine(0.0f, 10.0f);
            renderStatusBadge(status);

            if (descriptor != null && !descriptor.version().isBlank()) {
                ImGui.sameLine();
                StudioWidgets.badge("v" + descriptor.version(), 0.3f, 0.4f, 0.5f);
            }

            if (descriptor != null && !descriptor.authors().isEmpty()) {
                ImGui.sameLine();
                ImGui.textDisabled("by " + String.join(", ", descriptor.authors()));
            }

            // Description
            if (!desc.isBlank()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.68f, 0.74f, 1.0f);
                ImGui.textWrapped(desc);
                ImGui.popStyleColor();
            }

            // Dependencies
            if (descriptor != null && !descriptor.dependencies().isEmpty()) {
                ImGui.textDisabled("Dependencies: " + String.join(", ", descriptor.dependencies()));
            }
            if (descriptor != null && !descriptor.optionalDependencies().isEmpty()) {
                ImGui.textDisabled("Optional: " + String.join(", ", descriptor.optionalDependencies()));
            }
            if (descriptor != null) {
                ImGui.textDisabled("Plugin API " + descriptor.apiVersion()
                        + "  |  Minimum Studio " + descriptor.minimumStudioVersion());
                if (!descriptor.tags().isEmpty()) {
                    ImGui.textDisabled("Tags: " + String.join(", ", descriptor.tags()));
                }
                if (!descriptor.permissions().isEmpty()) {
                    ImGui.textDisabled("Permissions: " + descriptor.permissions().stream()
                            .map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(", ")));
                }
            }

            // Dependents warning if disabling
            List<String> dependents = pluginLifecycle.dependentsOf(id);
            if (!dependents.isEmpty()) {
                ImGui.textDisabled("Required by: " + String.join(", ", dependents));
            }

            ImGui.separator();
            ImGui.popID();
        }
        ImGui.endChild();

        if (ImGui.button("Close##plugin-close")) {
            open = false;
        }

        ImGui.end();
    }

    private boolean renderRepositories(EditorPluginLifecycleManager pluginLifecycle) {
        ImGui.text("Plugin repositories");
        ImGui.textDisabled("Feeds use the versioned RSPSi manifest contract. Downloads are staged and SHA-256 verified before install.");

        ImGui.setNextItemWidth(470.0f);
        ImGui.inputTextWithHint("##plugin-repository-url",
                "https://example.com/rspsi-plugins.json", repositoryUrl,
                ImGuiInputTextFlags.None);
        ImGui.sameLine();
        if (ImGui.button("Add Repository")) {
            try {
                URI uri = URI.create(repositoryUrl.get().trim());
                ecosystem.addRepository(uri);
                repositoryUrl.clear();
                repositoryStatus = "Repository added.";
            } catch (RuntimeException failure) {
                repositoryStatus = "Repository error: " + rootMessage(failure);
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Refresh Feeds")) {
            try {
                repositoryRefresh = ecosystem.refreshRepositories();
                repositoryStatus = "Loaded " + repositoryRefresh.repositories().size()
                        + " feed(s); " + repositoryRefresh.failures().size() + " failed.";
            } catch (RuntimeException failure) {
                repositoryStatus = "Refresh failed: " + rootMessage(failure);
            }
        }

        for (URI repository : ecosystem.repositories()) {
            ImGui.pushID(repository.toString());
            ImGui.textDisabled(repository.toString());
            ImGui.sameLine();
            if (ImGui.smallButton("Remove")) {
                ecosystem.removeRepository(repository);
                repositoryRefresh = null;
            }
            ImGui.popID();
        }

        if (!repositoryStatus.isBlank()) {
            ImGui.textWrapped(repositoryStatus);
        }
        if (repositoryRefresh == null) return false;

        Map<String, SemanticVersion> installedVersions = installedVersions(pluginLifecycle);
        Map<String, PluginRepositoryEntry> newest = new LinkedHashMap<>();
        repositoryRefresh.repositories().forEach(repository ->
                repository.plugins().forEach(entry ->
                        newest.merge(entry.manifest().id(), entry, (first, second) ->
                                second.manifest().version().compareTo(first.manifest().version()) > 0
                                        ? second : first)));

        if (!repositoryRefresh.failures().isEmpty()) {
            repositoryRefresh.failures().forEach((uri, failure) ->
                    ImGui.textWrapped("Feed failed: " + uri + " - " + failure));
        }

        for (PluginRepositoryEntry entry : newest.values()) {
            ImGui.pushID("repo-release-" + entry.manifest().id());
            SemanticVersion installed = installedVersions.get(entry.manifest().id());
            boolean newer = installed == null
                    || entry.manifest().version().compareTo(installed) > 0;
            String state = installed == null ? "not installed"
                    : newer ? "installed " + installed : "up to date";
            ImGui.text(entry.manifest().name() + "  v" + entry.manifest().version());
            ImGui.sameLine();
            ImGui.textDisabled(state);
            if (newer) {
                ImGui.sameLine();
                if (ImGui.smallButton(installed == null ? "Install" : "Update")) {
                    try {
                        ecosystem.install(entry);
                        repositoryStatus = entry.manifest().name() + " v"
                                + entry.manifest().version()
                                + " installed. Rescanning plugins...";
                        ImGui.popID();
                        return true;
                    } catch (RuntimeException failure) {
                        repositoryStatus = "Install failed: " + rootMessage(failure);
                    }
                }
            }
            ImGui.popID();
        }
        return false;
    }

    private void renderStudioPlugins(StudioPluginManager plugins, String query) {
        List<StudioPlugin> values = plugins.allPlugins();
        if (values.isEmpty()) return;

        ImGui.separatorText("Studio UI & tools");
        for (StudioPlugin plugin : values) {
            String haystack = (plugin.id() + " " + plugin.name() + " " + plugin.description())
                    .toLowerCase();
            if (!query.isBlank() && !haystack.contains(query)) continue;

            ImGui.pushID("studio-" + plugin.id());
            ImBoolean enabled = new ImBoolean(plugins.isEnabled(plugin.id()));
            if (ImGui.checkbox("##enabled", enabled)) {
                plugins.setEnabled(plugin.id(), enabled.get());
            }
            ImGui.sameLine();
            ImGui.text(plugin.name());
            ImGui.sameLine();
            StudioWidgets.badge("v" + plugin.version(), 0.24f, 0.34f, 0.45f);
            ImGui.sameLine();
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.textDisabled(plugin.id());
            ImGui.popFont();

            if (plugin.isConfigurable()) {
                ImGui.sameLine();
                if (ImGui.smallButton("Configure")) {
                    selectedStudioPluginId = plugin.id();
                }
            }
            if (!plugin.description().isBlank()) {
                ImGui.textWrapped(plugin.description());
            }
            ImGui.separator();
            ImGui.popID();
        }
    }

    private void renderStudioPluginSettings(
            StudioPluginManager plugins,
            StudioPanelContext context) {
        StudioPlugin plugin = plugins.plugin(selectedStudioPluginId).orElse(null);
        if (plugin == null) {
            selectedStudioPluginId = null;
            return;
        }

        if (StudioWidgets.buttonGhost("Back to Plugins", 120.0f, 28.0f)) {
            selectedStudioPluginId = null;
            return;
        }
        ImGui.dummy(1.0f, 8.0f);
        StudioWidgets.heading(plugin.name(), plugin.id() + " · v" + plugin.version());
        if (!plugin.description().isBlank()) {
            ImGui.textWrapped(plugin.description());
            ImGui.dummy(1.0f, 6.0f);
        }

        ImBoolean enabled = new ImBoolean(plugins.isEnabled(plugin.id()));
        if (ImGui.checkbox("Enabled##studio-plugin-enabled", enabled)) {
            plugins.setEnabled(plugin.id(), enabled.get());
        }

        if (plugin instanceof StudioToolPlugin tool) {
            ImGui.separatorText("Tool placement");
            Set<StudioToolPlugin.ToolSurface> active =
                    new HashSet<>(plugins.effectiveSurfaces(tool));
            boolean changed = false;
            for (StudioToolPlugin.ToolSurface surface : StudioToolPlugin.ToolSurface.values()) {
                if (surface == StudioToolPlugin.ToolSurface.TOOL_RAIL && !tool.isBrushTool()) {
                    continue;
                }
                ImBoolean value = new ImBoolean(active.contains(surface));
                if (ImGui.checkbox(surfaceLabel(surface) + "##surface-" + surface, value)) {
                    if (value.get()) active.add(surface);
                    else active.remove(surface);
                    changed = true;
                }
            }
            if (changed) plugins.setSurfaceOverride(plugin.id(), active);
            if (plugins.hasSurfaceOverride(plugin.id())
                    && StudioWidgets.buttonGhost("Reset placement", 120.0f, 26.0f)) {
                plugins.resetSurfaceOverride(plugin.id());
            }
        }

        if (plugin.isConfigurable()) {
            ImGui.separatorText("Settings");
            plugin.renderSettings(context);
        }
    }

    private static String surfaceLabel(StudioToolPlugin.ToolSurface surface) {
        return switch (surface) {
            case BOTTOM_BAR -> "Primary Tool Rail";
            case FLOATING_TOOLBAR -> "Viewport Quick Toolbar";
            case TOOL_RAIL -> "Brush Shelf";
        };
    }

    private static Map<String, SemanticVersion> installedVersions(
            EditorPluginLifecycleManager lifecycle) {
        Map<String, SemanticVersion> result = new LinkedHashMap<>();
        for (EditorPlugin plugin : lifecycle.candidates()) {
            try {
                result.put(plugin.id(), SemanticVersion.parse(plugin.descriptor().version()));
            } catch (RuntimeException ignored) {
                // A legacy plugin may use a non-semver version. It remains usable,
                // but a repository cannot safely determine whether it is newer.
            }
        }
        return result;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String value = current.getMessage();
        return value == null || value.isBlank()
                ? current.getClass().getSimpleName() : value;
    }

    private static void renderStatusBadge(PluginStatus status) {
        switch (status) {
            case ENABLED -> StudioWidgets.badge("ACTIVE", 0.2f, 0.7f, 0.3f);
            case DISABLED -> StudioWidgets.badge("DISABLED", 0.7f, 0.3f, 0.2f);
            case CASCADE_DISABLED -> StudioWidgets.badge("BLOCKED BY DEPENDENCY", 0.8f, 0.6f, 0.1f);
        }
    }
}
