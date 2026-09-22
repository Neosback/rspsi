package com.rspsi.studio.ui.panels;

import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginDescriptor;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.plugin.StudioPlugin;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native Right-Rail Panel providing RuneLite-inspired plugin discovery,
 * active status toggling, version inspection, and inline settings configuration.
 */
public final class PluginManagerPanel implements StudioPanel {

    public static final String ID = "studio.plugins";

    private final ImString searchQuery = new ImString(64);
    private String selectedPluginIdForConfig = null;
    private String categoryFilter = "All";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Plugins";
    }

    @Override
    public String icon() {
        return StudioIcons.SETTINGS;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.RIGHT;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public void render(StudioPanelContext context) {
        StudioPluginManager studioPlugins = context.studioPlugins();
        EditorPluginLifecycleManager clientLifecycle = context.pluginLifecycle();

        // RuneLite-style: If a plugin is selected for configuration, render its dedicated page
        if (selectedPluginIdForConfig != null && studioPlugins != null) {
            renderPluginConfigPage(context, studioPlugins, selectedPluginIdForConfig);
            return;
        }

        int totalPlugins = (studioPlugins != null ? studioPlugins.allPlugins().size() : 0)
                + (clientLifecycle != null ? clientLifecycle.candidates().size() : 0);

        // Header / Search Bar
        ImGui.inputTextWithHint("##plugin-panel-search", StudioIcons.SEARCH + "  Filter plugins...",
                searchQuery, ImGuiInputTextFlags.None);

        String query = searchQuery.get().trim().toLowerCase();

        renderCategoryFilters();
        ImGui.separator();

        // 1. Studio UI & Tool Plugins
        if (studioPlugins != null) {
            List<StudioPlugin> allStudio = studioPlugins.allPlugins();
            if (!allStudio.isEmpty()) {
                ImGui.textColored(0xFF38BDF8, StudioIcons.VIEWPORT + "  STUDIO EXTENSIONS & TOOLS (" + allStudio.size() + ")");

                for (StudioPlugin plugin : allStudio) {
                    if (!matchesStudioCategory(plugin)) continue;
                    String pid = plugin.id();
                    String pname = plugin.name();
                    String pdesc = plugin.description();

                    if (!query.isEmpty()
                            && !pid.toLowerCase().contains(query)
                            && !pname.toLowerCase().contains(query)
                            && !pdesc.toLowerCase().contains(query)) {
                        continue;
                    }

                    ImGui.pushID(pid);

                    boolean isEnabled = studioPlugins.isEnabled(pid);
                    ImBoolean toggle = new ImBoolean(isEnabled);

                    // Toggle checkbox
                    if (ImGui.checkbox("##toggle-" + pid, toggle)) {
                        studioPlugins.setEnabled(pid, toggle.get());
                    }
                    ImGui.sameLine();

                    // Icon + Name + Version badge
                    ImGui.text(plugin.icon() + " " + pname);
                    ImGui.sameLine();
                    StudioWidgets.badge("v" + plugin.version(), 0.25f, 0.35f, 0.45f);

                    // RuneLite-style Gear icon opens dedicated settings page
                    if (plugin.isConfigurable()) {
                        ImGui.sameLine();
                        if (ImGui.smallButton(StudioIcons.SETTINGS + "##cfg-" + pid)) {
                            selectedPluginIdForConfig = pid;
                        }
                        if (ImGui.isItemHovered()) ImGui.setTooltip("Configure " + pname + "...");
                    }

                    // Description (word-wrapped)
                    if (!pdesc.isBlank()) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0xFF94A3B8);
                        ImGui.textWrapped(pdesc);
                        ImGui.popStyleColor();
                    }

                    ImGui.separator();
                    ImGui.popID();
                }
            }
        }

        renderBrushManagement(context, query);

        // 2. Core Client Engine Plugins
        if (clientLifecycle != null && ("All".equals(categoryFilter) || "Engine".equals(categoryFilter))) {
            List<EditorPlugin> candidates = clientLifecycle.candidates();
            if (!candidates.isEmpty()) {
                ImGui.spacing();
                ImGui.textColored(0xFF38BDF8, StudioIcons.CODE + "  ENGINE PLUGINS (" + candidates.size() + ")");

                for (EditorPlugin plugin : candidates) {
                    EditorPluginDescriptor desc = plugin.descriptor();
                    String id = plugin.id();
                    String name = desc != null ? desc.name() : id;
                    String descText = desc != null ? desc.description() : "";

                    if (!query.isEmpty()
                            && !id.toLowerCase().contains(query)
                            && !name.toLowerCase().contains(query)
                            && !descText.toLowerCase().contains(query)) {
                        continue;
                    }

                    ImGui.pushID("engine-" + id);

                    boolean isEnabled = clientLifecycle.status(id) == EditorPluginLifecycleManager.PluginStatus.ENABLED;
                    ImBoolean toggle = new ImBoolean(isEnabled);

                    if (ImGui.checkbox("##toggle-eng-" + id, toggle)) {
                        clientLifecycle.setEnabled(id, toggle.get());
                    }
                    ImGui.sameLine();
                    ImGui.text(name);

                    if (desc != null && !desc.version().isBlank()) {
                        ImGui.sameLine();
                        StudioWidgets.badge("v" + desc.version(), 0.3f, 0.3f, 0.4f);
                    }

                    if (!descText.isBlank()) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0xFF94A3B8);
                        ImGui.textWrapped(descText);
                        ImGui.popStyleColor();
                    }

                    ImGui.separator();
                    ImGui.popID();
                }
            }
        }
    }

    private void renderCategoryFilters() {
        for (String filter : new String[]{"All", "Tools", "Brushes", "Terrain", "HUD", "Engine"}) {
            boolean selected = filter.equals(categoryFilter);
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
            }
            if (ImGui.smallButton(filter + "##plugin-filter-" + filter)) {
                categoryFilter = filter;
            }
            if (selected) ImGui.popStyleColor();
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private boolean matchesStudioCategory(StudioPlugin plugin) {
        return switch (categoryFilter) {
            case "All" -> true;
            case "Tools" -> plugin instanceof com.rspsi.studio.plugin.StudioToolPlugin;
            case "Terrain" -> plugin instanceof com.rspsi.studio.plugin.StudioToolPlugin tool
                    && "Terrain".equalsIgnoreCase(tool.category());
            case "HUD" -> !(plugin instanceof com.rspsi.studio.plugin.StudioToolPlugin)
                    && plugin.id().toLowerCase().contains("hud");
            case "Brushes", "Engine" -> false;
            default -> true;
        };
    }

    private void renderBrushManagement(StudioPanelContext context, String query) {
        if (context.brushes() == null
                || !("All".equals(categoryFilter) || "Brushes".equals(categoryFilter) || "Terrain".equals(categoryFilter))) {
            return;
        }

        List<EditorBrush> brushes = context.brushes().allBrushes();
        if (brushes.isEmpty()) return;

        ImGui.spacing();
        ImGui.textColored(0xFF38BDF8, StudioIcons.BRUSH + "  BRUSHES (" + brushes.size() + ")");
        ImGui.textDisabled("Brushes are neutral tool capabilities shared by terrain painters and sculptors.");

        for (EditorBrush brush : brushes) {
            if (!query.isEmpty()
                    && !brush.id().toLowerCase().contains(query)
                    && !brush.name().toLowerCase().contains(query)
                    && !brush.description().toLowerCase().contains(query)) {
                continue;
            }

            ImGui.pushID("brush-" + brush.id());
            boolean enabled = context.brushes().isEnabled(brush.id());
            ImBoolean toggle = new ImBoolean(enabled);
            if (ImGui.checkbox("##brush-toggle-" + brush.id(), toggle)) {
                boolean accepted = context.brushes().setEnabled(brush.id(), toggle.get());
                if (!accepted) {
                    context.brushes().setEnabled(brush.id(), true);
                }
            }
            ImGui.sameLine();
            ImGui.text(brush.name());
            for (String capability : context.brushes().capabilityLabels(brush)) {
                ImGui.sameLine();
                StudioWidgets.badge(capability, 0.25f, 0.35f, 0.45f);
            }

            if (!brush.description().isBlank()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF94A3B8);
                ImGui.textWrapped(brush.description());
                ImGui.popStyleColor();
            }
            if (enabled && context.brushes().enabledBrushes().size() == 1) {
                ImGui.textDisabled("Required: at least one brush must remain enabled.");
            }
            ImGui.separator();
            ImGui.popID();
        }
    }

    private static String surfaceLabel(com.rspsi.studio.plugin.StudioToolPlugin.ToolSurface surface) {
        return switch (surface) {
            case BOTTOM_BAR -> "Bottom Bar";
            case FLOATING_TOOLBAR -> "Floating Tool Rail";
            case TOOL_RAIL -> "Left Tool Rail";
        };
    }

    private void renderPluginConfigPage(StudioPanelContext context, StudioPluginManager studioPlugins, String pluginId) {
        var opt = studioPlugins.plugin(pluginId);
        if (opt.isEmpty()) {
            selectedPluginIdForConfig = null;
            return;
        }

        StudioPlugin plugin = opt.get();

        // 1. Navigation Breadcrumb Bar
        if (ImGui.button(StudioIcons.CHEVRON_LEFT + "  Back to Plugins##back-btn", -1.0f, 24.0f)) {
            selectedPluginIdForConfig = null;
            return;
        }

        ImGui.spacing();

        // 2. Header with Icon, Name, and Badges
        ImGui.textColored(0xFF38BDF8, plugin.icon() + "  " + plugin.name());
        ImGui.sameLine();
        StudioWidgets.badge("v" + plugin.version(), 0.20f, 0.40f, 0.60f);
        ImGui.sameLine();
        StudioWidgets.badge(plugin.author(), 0.25f, 0.35f, 0.45f);

        // Enable / Disable toggle switch
        boolean isEnabled = studioPlugins.isEnabled(pluginId);
        ImBoolean toggle = new ImBoolean(isEnabled);
        if (ImGui.checkbox("Enabled##cfg-enable-" + pluginId, toggle)) {
            studioPlugins.setEnabled(pluginId, toggle.get());
        }

        // Word-wrapped description
        if (!plugin.description().isBlank()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0xFF94A3B8);
            ImGui.textWrapped(plugin.description());
            ImGui.popStyleColor();
        }

        // Tool placement: which chrome surfaces this tool's button appears on. Lets the user
        // customize layout (e.g. a tool on both the bottom bar and the floating toolbar) or
        // restrict a tool to fewer surfaces, without editing code.
        if (plugin instanceof com.rspsi.studio.plugin.StudioToolPlugin toolPlugin) {
            ImGui.separator();
            ImGui.spacing();
            ImGui.textColored(0xFF38BDF8, "Placement");
            Set<com.rspsi.studio.plugin.StudioToolPlugin.ToolSurface> active =
                    new HashSet<>(studioPlugins.effectiveSurfaces(toolPlugin));

            boolean changed = false;
            for (var surface : com.rspsi.studio.plugin.StudioToolPlugin.ToolSurface.values()) {
                // The Left Tool Rail exists only to surface Brush Settings while a brush
                // tool is active - offering it as a placement option for anything else
                // would let a user "enable" it and get nothing but an empty rail.
                if (surface == com.rspsi.studio.plugin.StudioToolPlugin.ToolSurface.TOOL_RAIL
                        && !toolPlugin.isBrushTool()) {
                    continue;
                }
                ImBoolean surfaceToggle = new ImBoolean(active.contains(surface));
                if (ImGui.checkbox(surfaceLabel(surface) + "##surf-" + pluginId + "-" + surface, surfaceToggle)) {
                    if (surfaceToggle.get()) active.add(surface); else active.remove(surface);
                    changed = true;
                }
            }
            if (changed) {
                studioPlugins.setSurfaceOverride(pluginId, active);
            }
            if (studioPlugins.hasSurfaceOverride(pluginId)) {
                ImGui.sameLine();
                if (ImGui.smallButton("Reset##surf-reset-" + pluginId)) {
                    studioPlugins.resetSurfaceOverride(pluginId);
                }
            }
        }

        ImGui.separator();
        ImGui.spacing();

        // 3. Plugin-specific Settings Panel (Full Panel Width, Sandboxed)
        try {
            plugin.renderSettings(context);
        } catch (Throwable t) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0xFFEF4444);
            ImGui.text(StudioIcons.BUG_REPORT + "  Plugin Settings Error:");
            ImGui.textWrapped(t.getMessage() != null ? t.getMessage() : t.toString());
            ImGui.popStyleColor();
        }
    }
}
