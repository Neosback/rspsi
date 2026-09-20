package com.rspsi.studio.ui;

import com.rspsi.editor.plugin.EditorPanelRegistration;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.ui.panels.HeightToolPanel;
import com.rspsi.studio.ui.panels.KnowledgePanel;
import com.rspsi.studio.ui.panels.MapSettingsPanel;
import com.rspsi.studio.ui.panels.MinimapPanel;
import com.rspsi.studio.ui.panels.ObjectViewerPanel;
import com.rspsi.studio.ui.panels.OutlinerPanel;
import com.rspsi.studio.ui.panels.PluginManagerPanel;
import com.rspsi.studio.ui.panels.TileBrushPanel;
import com.rspsi.studio.ui.panels.TilePainterPalette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates Studio panels across layout slots (Right Sidebar, Bottom Drawer)
 * and supports user-driven slot overrides.
 */
public final class StudioPanelManager {
    private final Map<String, StudioPanel> panels = new LinkedHashMap<>();
    private final Map<String, DockRegion> userOverrides = new LinkedHashMap<>();
    private final Map<String, Boolean> userVisibility = new LinkedHashMap<>();

    private String activeRightPanelId = TileBrushPanel.ID;
    private String activeBottomPanelId = TilePainterPalette.ID;

    public StudioPanelManager() {
        // Register built-in standard panels
        register(new TileBrushPanel());
        register(new MinimapPanel());
        register(new ObjectViewerPanel());
        register(new OutlinerPanel());
        register(new KnowledgePanel());
        register(new MapSettingsPanel());
        register(new PluginManagerPanel());
        register(new TilePainterPalette());
        register(new HeightToolPanel());
    }

    public void register(StudioPanel panel) {
        Objects.requireNonNull(panel, "panel");
        panels.put(panel.id(), panel);
    }

    public void unregister(String panelId) {
        panels.remove(panelId);
    }

    public Optional<StudioPanel> panel(String id) {
        return Optional.ofNullable(panels.get(id));
    }

    public List<StudioPanel> allPanels() {
        return List.copyOf(panels.values());
    }

    public DockRegion effectiveRegion(StudioPanel panel) {
        return userOverrides.getOrDefault(panel.id(), panel.preferredRegion());
    }

    public boolean isVisible(StudioPanel panel) {
        return userVisibility.getOrDefault(panel.id(), true);
    }

    public void setRegionOverride(String panelId, DockRegion region) {
        StudioPanel panel = panels.get(panelId);
        if (panel != null && panel.allowedRegions().contains(region)) {
            userOverrides.put(panelId, region);
        }
    }

    public void resetRegionOverride(String panelId) {
        userOverrides.remove(panelId);
    }

    public void setVisibility(String panelId, boolean visible) {
        userVisibility.put(panelId, visible);
    }

    public List<StudioPanel> panelsForRegion(DockRegion region) {
        return panels.values().stream()
                .filter(p -> effectiveRegion(p) == region && isVisible(p))
                .sorted(Comparator.comparingInt(StudioPanel::order).thenComparing(StudioPanel::id))
                .toList();
    }

    public String activeRightPanelId() {
        return activeRightPanelId;
    }

    public void setActiveRightPanelId(String id) {
        if (panels.containsKey(id)) {
            this.activeRightPanelId = id;
        }
    }

    public String activeBottomPanelId() {
        return activeBottomPanelId;
    }

    public void setActiveBottomPanelId(String id) {
        if (panels.containsKey(id)) {
            this.activeBottomPanelId = id;
        }
    }

    /**
     * Synchronizes dynamic panel registrations from plugin host into manager.
     */
    public void syncPluginContributions(List<EditorPanelRegistration> pluginPanels) {
        if (pluginPanels == null) return;
        for (EditorPanelRegistration reg : pluginPanels) {
            if (!panels.containsKey(reg.id())) {
                register(new PluginStudioPanelAdapter(reg));
            }
        }
    }

    private static final class PluginStudioPanelAdapter implements StudioPanel {
        private final EditorPanelRegistration reg;

        PluginStudioPanelAdapter(EditorPanelRegistration reg) {
            this.reg = reg;
        }

        @Override public String id() { return reg.id(); }
        @Override public String title() { return reg.title(); }
        @Override public String icon() { return reg.icon(); }
        @Override public DockRegion preferredRegion() { return reg.preferredRegion(); }
        @Override public java.util.Set<DockRegion> allowedRegions() { return reg.allowedRegions(); }
        @Override public int order() { return reg.order(); }

        @Override
        public void render(StudioPanelContext context) {
            imgui.ImGui.textDisabled("Plugin panel: " + reg.title());
        }
    }
}
