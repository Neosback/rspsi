package com.rspsi.studio.ui;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioPalette;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/**
 * Modernized right sidebar featuring a slim vertical icon rail (44px)
 * and an active panel container that supports pinned dual-panel split mode,
 * with horizontal scroll locking and automatic text wrapping.
 */
public final class StudioRightSidebar {

    public static final float RAIL_WIDTH = 44.0f;

    private static final int SIDEBAR_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings;

    private String pinnedTopPanelId = null;

    public String pinnedTopPanelId() {
        return pinnedTopPanelId;
    }

    public void setPinnedTopPanelId(String panelId) {
        this.pinnedTopPanelId = panelId;
    }

    public float preferredWidth(StudioPanelManager panelManager) {
        if (panelManager == null) return 390.0f;
        String activeId = panelManager.activeRightPanelId();
        return panelManager.panel(activeId)
                .map(StudioPanel::preferredRightSidebarWidth)
                .orElse(390.0f);
    }

    public void render(StudioPanelManager panelManager,
                       StudioPanelContext context,
                       float x, float y, float width, float height) {
        float effectiveWidth = Math.max(320.0f, width);

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(effectiveWidth, height, ImGuiCond.Always);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4.0f, 4.0f);
        ImGui.begin("StudioRightSidebar", SIDEBAR_FLAGS);

        float totalH = Math.max(100.0f, height - 12.0f);

        List<StudioPanel> rightPanels = panelManager.panelsForRegion(DockRegion.RIGHT);
        if (rightPanels.isEmpty()) {
            ImGui.textDisabled("No panels docked in right sidebar.");
            ImGui.end();
            ImGui.popStyleVar();
            return;
        }

        String initialActiveId = panelManager.activeRightPanelId();
        String activeId = rightPanels.stream().anyMatch(p -> p.id().equals(initialActiveId))
                ? initialActiveId : rightPanels.get(0).id();
        panelManager.setActiveRightPanelId(activeId);

        // 1. Vertical Icon Rail
        ImGui.beginChild("right-sidebar-rail", RAIL_WIDTH, totalH, false, ImGuiWindowFlags.NoScrollbar);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2.0f, 2.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6.0f);

        for (StudioPanel p : rightPanels) {
            boolean isSel = p.id().equals(activeId);
            boolean isPinned = p.id().equals(pinnedTopPanelId);

            if (isSel) {
                ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.ACCENT);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.ACCENT_HOVER);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
            } else if (isPinned) {
                ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.WARNING);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.WARNING);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.WARNING);
                ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.CHROME_BG);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.FIELD_HOVER);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_SOFT);
                ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT_MUTED);
            }

            ImGui.pushFont(StudioFonts.icon(), 0.0f);
            if (ImGui.button(p.icon() + "##p-rail-" + p.id(), 40.0f, 40.0f)) {
                panelManager.setActiveRightPanelId(p.id());
                panelManager.associatedToolId(p.id()).ifPresent(toolId -> {
                    if (context.activateTool() != null) context.activateTool().accept(toolId);
                });
            }
            ImGui.popFont();
            ImGui.popStyleColor(4);

            if (ImGui.isItemHovered()) {
                String tt = p.title() + (isPinned ? " (Pinned Top)" : "");
                ImGui.setTooltip(tt);
            }

            // Right-click context menu to pin or reassign slot
            if (ImGui.beginPopupContextItem("panel-ctx-" + p.id())) {
                ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), p.title());
                ImGui.separator();
                if (isPinned) {
                    if (ImGui.menuItem("Unpin from Top Split")) {
                        pinnedTopPanelId = null;
                    }
                } else {
                    if (ImGui.menuItem("Pin to Top Split")) {
                        pinnedTopPanelId = p.id();
                    }
                }
                if (ImGui.menuItem("Move to Bottom Drawer")) {
                    if (isPinned) pinnedTopPanelId = null;
                    panelManager.setRegionOverride(p.id(), DockRegion.BOTTOM);
                }
                if (ImGui.menuItem("Reset to Default Region")) {
                    panelManager.resetRegionOverride(p.id());
                }
                ImGui.endPopup();
            }
        }

        ImGui.popStyleVar(3);
        ImGui.endChild();

        ImGui.sameLine(0.0f, 4.0f);

        // 2. Panel Content Body (supports split top/bottom mode)
        float contentW = effectiveWidth - RAIL_WIDTH - 12.0f;

        StudioPanel pinnedPanel = (pinnedTopPanelId != null)
                ? panelManager.panel(pinnedTopPanelId).orElse(null)
                : null;

        if (pinnedPanel != null && pinnedPanel.id().equals(activeId)) {
            // If the user selects the pinned panel in the rail, show it full-height
            pinnedPanel = null;
        }

        if (pinnedPanel != null) {
            // Split mode: Top Pinned Half + Bottom Rail Selected Half
            float topH = Math.max(120.0f, (totalH - 24.0f) * 0.46f);
            float botH = Math.max(120.0f, totalH - topH - 16.0f);

            ImGui.beginGroup();

            // --- Top Pinned Section ---
            ImGui.beginChild("right-split-top", contentW, topH, true,
                    pinnedPanel.allowHorizontalScroll() ? ImGuiWindowFlags.HorizontalScrollbar : ImGuiWindowFlags.None);
            if (!pinnedPanel.allowHorizontalScroll()) ImGui.setScrollX(0.0f);
            ImGui.textColored(StudioPalette.WARNING, StudioIcons.PIN + " " + pinnedPanel.title());
            String unpin = StudioIcons.CLOSE + " Unpin";
            ImGui.sameLine(Math.max(0.0f, ImGui.getWindowContentRegionMaxX()
                    - ImGui.calcTextSize(unpin).x - ImGui.getStyle().getFramePaddingX() * 2.0f));
            if (ImGui.smallButton(unpin + "##unpin-top")) {
                pinnedTopPanelId = null;
            }
            ImGui.separator();
            ImGui.pushTextWrapPos(0.0f);
            pinnedPanel.render(context);
            ImGui.popTextWrapPos();
            ImGui.endChild();

            // --- Bottom Active Section ---
            final String finalActiveId = activeId;
            StudioPanel activePanel = rightPanels.stream()
                    .filter(p -> p.id().equals(finalActiveId))
                    .findFirst()
                    .orElse(rightPanels.get(0));

            ImGui.beginChild("right-split-bottom", contentW, botH, true,
                    activePanel.allowHorizontalScroll() ? ImGuiWindowFlags.HorizontalScrollbar : ImGuiWindowFlags.None);
            if (!activePanel.allowHorizontalScroll()) ImGui.setScrollX(0.0f);
            ImGui.textColored(StudioPalette.ACCENT, activePanel.title());
            ImGui.separator();
            ImGui.pushTextWrapPos(0.0f);
            activePanel.render(context);
            ImGui.popTextWrapPos();
            ImGui.endChild();

            ImGui.endGroup();
        } else {
            // Normal Single Panel Mode. Horizontal overflow is opt-in; the default
            // contract is responsive reflow + vertical scrolling only.
            final String finalActiveId = activeId;
            StudioPanel activePanel = rightPanels.stream()
                    .filter(p -> p.id().equals(finalActiveId))
                    .findFirst()
                    .orElse(rightPanels.get(0));
            int contentFlags = activePanel.allowHorizontalScroll()
                    ? ImGuiWindowFlags.HorizontalScrollbar
                    : ImGuiWindowFlags.None;
            ImGui.beginChild("right-sidebar-content", contentW, totalH, false, contentFlags);
            if (!activePanel.allowHorizontalScroll()) ImGui.setScrollX(0.0f);
            ImGui.pushTextWrapPos(0.0f);

            activePanel.render(context);

            ImGui.popTextWrapPos();
            ImGui.endChild();
        }

        ImGui.end();
        ImGui.popStyleVar();
    }
}
