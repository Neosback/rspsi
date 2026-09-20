package com.rspsi.studio.ui;

import com.rspsi.editor.plugin.EditorPanelRegistration;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
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
    private final Map<String, UiSurfaceContribution> managedSurfaces = new LinkedHashMap<>();

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
        if (panel == null || !panel.allowedRegions().contains(region)) return;

        UiSurfaceContribution surface = managedSurfaces.get(panelId);
        if (surface == null || surface.associatedToolId().isBlank()) {
            userOverrides.put(panelId, region);
            return;
        }

        // Paired tool surfaces move together. This keeps a tool button and its
        // contextual shelf from being split across unrelated layout regions.
        for (UiSurfaceContribution contribution : managedSurfaces.values()) {
            if (!surface.associatedToolId().equals(contribution.associatedToolId())) continue;
            StudioPanel paired = panels.get(contribution.id());
            if (paired != null && paired.allowedRegions().contains(region)) {
                userOverrides.put(contribution.id(), region);
            }
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

    public Optional<DockRegion> managedRegionForTool(String toolId) {
        if (toolId == null || toolId.isBlank()) return Optional.empty();
        return managedSurfaces.values().stream()
                .filter(surface -> toolId.equals(surface.associatedToolId()))
                .filter(surface -> surface.type() != UiSurfaceContribution.SurfaceType.VIEWPORT_HUD)
                .map(surface -> panels.get(surface.id()))
                .filter(java.util.Objects::nonNull)
                .map(this::effectiveRegion)
                .findFirst();
    }

    public Optional<String> associatedToolId(String panelId) {
        UiSurfaceContribution surface = managedSurfaces.get(panelId);
        if (surface == null || surface.associatedToolId().isBlank()) return Optional.empty();
        return Optional.of(surface.associatedToolId());
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
     * Synchronizes neutral managed surfaces declared by EditorPlugins.
     * Viewport HUDs are consumed by ViewportHudManager; panel-like surfaces
     * are projected into controlled Studio slots.
     */
    public void syncUiSurfaces(List<UiSurfaceContribution> contributions) {
        if (contributions == null) return;
        for (UiSurfaceContribution contribution : contributions) {
            managedSurfaces.put(contribution.id(), contribution);
            if (contribution.type() == UiSurfaceContribution.SurfaceType.VIEWPORT_HUD) continue;
            if (!panels.containsKey(contribution.id())) {
                register(new ManagedSurfacePanelAdapter(contribution));
            }
            userVisibility.putIfAbsent(contribution.id(), true);
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

    private static final class ManagedSurfacePanelAdapter implements StudioPanel {
        private final UiSurfaceContribution contribution;

        private ManagedSurfacePanelAdapter(UiSurfaceContribution contribution) {
            this.contribution = contribution;
        }

        @Override public String id() { return contribution.id(); }
        @Override public String title() { return contribution.title(); }
        @Override public String icon() { return contribution.icon(); }
        @Override public DockRegion preferredRegion() { return contribution.preferredRegion(); }
        @Override public java.util.Set<DockRegion> allowedRegions() { return contribution.allowedRegions(); }
        @Override public int order() { return contribution.priority(); }

        @Override
        public void render(StudioPanelContext context) {
            imgui.ImGui.textDisabled("Managed plugin surface: " + contribution.title());
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
