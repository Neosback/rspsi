package com.rspsi.studio;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginDescriptor;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager.PluginStatus;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;

/**
 * Native Dear ImGui Plugin Manager dialog.
 *
 * <p>Provides visibility into discovered plugins, active status toggles,
 * dependency hierarchies, descriptor metadata, and live contribution counts.</p>
 */
public final class PluginManagerWindow {
    private boolean open;
    private final ImString searchQuery = new ImString(128);

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void toggle() {
        this.open = !this.open;
    }

    public void render(EditorPluginLifecycleManager pluginLifecycle) {
        if (!open) return;
        if (pluginLifecycle == null) return;

        ImGui.setNextWindowSize(720.0f, 480.0f, ImGuiCond.Appearing);
        ImBoolean pOpen = new ImBoolean(open);
        if (!ImGui.begin(StudioIcons.OBJECT + "  Plugins##studio-plugins", pOpen, ImGuiWindowFlags.NoCollapse)) {
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
        if (ImGui.button("Enable All")) {
            pluginLifecycle.enableAll();
        }
        ImGui.sameLine();
        if (ImGui.button("Reload Plugins")) {
            pluginLifecycle.reload();
        }

        ImGui.separator();

        String query = searchQuery.get().trim().toLowerCase();
        List<EditorPlugin> candidates = pluginLifecycle.candidates();

        ImGui.beginChild("plugin-list", 0.0f, -ImGui.getFrameHeightWithSpacing(), true);
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

    private static void renderStatusBadge(PluginStatus status) {
        switch (status) {
            case ENABLED -> StudioWidgets.badge("ACTIVE", 0.2f, 0.7f, 0.3f);
            case DISABLED -> StudioWidgets.badge("DISABLED", 0.7f, 0.3f, 0.2f);
            case CASCADE_DISABLED -> StudioWidgets.badge("BLOCKED BY DEPENDENCY", 0.8f, 0.6f, 0.1f);
        }
    }
}
