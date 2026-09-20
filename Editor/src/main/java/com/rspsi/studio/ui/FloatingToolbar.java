package com.rspsi.studio.ui;

import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modern floating frosted-glass vertical tool rail rendered directly over the 3D viewport canvas.
 * Powered by Google Material Icons, draggable via a sleek top grip, and dynamically driven
 * by registered {@link StudioToolPlugin}s.
 */
public final class FloatingToolbar {

    private boolean collapsed = false;
    private float offsetX = 20.0f;
    private float offsetY = 20.0f;

    private float boundMinX;
    private float boundMinY;
    private float boundMaxX;
    private float boundMaxY;
    private boolean hovered = false;

    public boolean isHovered() {
        return hovered;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public void toggleCollapsed() {
        this.collapsed = !this.collapsed;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
    }

    public void resetPosition() {
        this.offsetX = 20.0f;
        this.offsetY = 20.0f;
    }

    public boolean contains(float x, float y) {
        return x >= boundMinX && x < boundMaxX && y >= boundMinY && y < boundMaxY;
    }

    public void render(float baseStartX, float baseStartY,
                       Consumer<String> activateTool, String activeToolId) {
        render(null, baseStartX, baseStartY, activateTool, activeToolId);
    }

    public void render(StudioPanelContext context,
                       float baseStartX, float baseStartY,
                       Consumer<String> activateTool, String activeToolId) {

        float startX = baseStartX + offsetX;
        float startY = baseStartY + offsetY;

        this.hovered = false;

        float btnSize = 34.0f;
        float padding = 5.0f;
        float railWidth = btnSize + (padding * 2.0f);

        ImDrawList dl = ImGui.getWindowDrawList();

        if (collapsed) {
            // Minimal collapsed pill button to re-expand
            float h = 32.0f;
            this.boundMinX = startX;
            this.boundMinY = startY;
            this.boundMaxX = startX + railWidth;
            this.boundMaxY = startY + h;

            dl.addRectFilled(boundMinX, boundMinY, boundMaxX, boundMaxY, 0xDD181B22, 6.0f);
            dl.addRect(boundMinX, boundMinY, boundMaxX, boundMaxY, 0x6064748B, 6.0f, 0, 1.0f);

            ImGui.setCursorScreenPos(startX + padding, startY + 2.0f);
            ImGui.pushFont(StudioFonts.icon(), 0.0f);
            if (ImGui.button(StudioIcons.BRUSH + "##ftb-expand", btnSize, 28.0f)) {
                collapsed = false;
            }
            ImGui.popFont();
            if (ImGui.isItemHovered()) {
                this.hovered = true;
                ImGui.setTooltip("Expand Flying Tool Rail");
            }
            return;
        }

        // Get tools list from StudioPluginManager if available, filtered to only those placed
        // on this surface (a tool may opt out of the floating toolbar via surfaces()/the
        // Plugin Manager's per-tool surface override).
        StudioPluginManager plugins = context != null ? context.studioPlugins() : null;
        List<StudioToolPlugin> toolPlugins = plugins != null
                ? plugins.toolPlugins().stream()
                        .filter(t -> plugins.effectiveSurfaces(t).contains(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR))
                        .toList()
                : null;

        int toolCount = (toolPlugins != null && !toolPlugins.isEmpty()) ? toolPlugins.size() : 5;
        // Extra height for grip
        float extraHeight = 18.0f;
        float totalHeight = extraHeight + (toolCount * (btnSize + 4.0f)) + (padding * 2.0f);

        this.boundMinX = startX;
        this.boundMinY = startY;
        this.boundMaxX = startX + railWidth;
        this.boundMaxY = startY + totalHeight;

        // 1. Sleek Frosted Glass Capsule Background & Shadow
        dl.addRectFilled(boundMinX + 2.0f, boundMinY + 2.0f, boundMaxX + 2.0f, boundMaxY + 2.0f, 0x60000000, 8.0f);
        dl.addRectFilled(boundMinX, boundMinY, boundMaxX, boundMaxY, 0xDC181B22, 8.0f);
        dl.addRect(boundMinX, boundMinY, boundMaxX, boundMaxY, 0x5064748B, 8.0f, 0, 1.0f);

        float curY = startY + padding;

        // 2. Minimal Top Drag Grip Handle
        ImGui.setCursorScreenPos(startX, curY);
        ImGui.invisibleButton("##ftb_grip", railWidth, 14.0f);
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left, 1.0f)) {
            offsetX += ImGui.getIO().getMouseDeltaX();
            offsetY += ImGui.getIO().getMouseDeltaY();
            this.hovered = true;
        }
        if (ImGui.isItemHovered()) {
            this.hovered = true;
            ImGui.setTooltip("Drag to reposition Flying Tool Rail (Right-click for options)");
        }

        // Pill grip graphic
        float gripW = 18.0f;
        float gripH = 3.0f;
        float gripX = startX + (railWidth - gripW) * 0.5f;
        float gripY = curY + 5.0f;
        dl.addRectFilled(gripX, gripY, gripX + gripW, gripY + gripH, 0x8094A3B8, 2.0f);

        // Right-click context menu on grip
        if (ImGui.beginPopupContextItem("ftb_options_ctx")) {
            ImGui.textColored(0xFF38BDF8, "Flying Tool Rail");
            ImGui.separator();
            if (ImGui.menuItem("Reset Position")) resetPosition();
            if (ImGui.menuItem("Minimize Toolbar")) collapsed = true;
            ImGui.endPopup();
        }

        curY += 16.0f;

        // 3. Render Tool Buttons
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2.0f, 2.0f);

        if (toolPlugins != null && !toolPlugins.isEmpty()) {
            for (StudioToolPlugin tool : toolPlugins) {
                boolean isActive = tool.toolIds().contains(activeToolId) || tool.id().equals(activeToolId);

                curY = renderToolButton(startX + padding, curY, btnSize, tool.icon(),
                        tool.toolId(), tool.name(), tool.shortcut(), isActive, activateTool);
            }
        } else {
            // Built-in fallback tool set
            String[][] fallback = {
                    {"selection.box", StudioIcons.SELECT, "Tile Selection", "1"},
                    {"terrain.tile-painter", StudioIcons.BRUSH, "Tile Painter", "2"},
                    {"terrain.raise", StudioIcons.HEIGHT, "Height Sculptor", "3"},
                    {"terrain.smooth", StudioIcons.PATH, "Path Builder", "4"},
                    {"object.place", StudioIcons.OBJECT, "Object Spawner", "5"}
            };
            for (String[] t : fallback) {
                boolean isActive = t[0].equals(activeToolId);
                curY = renderToolButton(startX + padding, curY, btnSize, t[1],
                        t[0], t[2], t[3], isActive, activateTool);
            }
        }

        ImGui.popStyleVar(2);

        // Check if mouse is within toolbar bounds
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        if (contains(mouseX, mouseY)) {
            this.hovered = true;
        }
    }

    private float renderToolButton(float x, float y, float size, String icon,
                                   String toolId, String name, String shortcut,
                                   boolean isActive, Consumer<String> activateTool) {
        ImGui.setCursorScreenPos(x, y);

        if (isActive) {
            // Also cover Hovered/Active so the button stays visibly "on" immediately on click
            // instead of only after the mouse moves away (ImGui's hover/press colors otherwise
            // paint over the plain Button color while the cursor sits on top of it).
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(0.28f, 0.53f, 0.92f, 1.0f));
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(0.16f, 0.40f, 0.78f, 1.0f));
            ImGui.pushStyleColor(ImGuiCol.Text, 0xFFFFFFFF);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.15f, 0.18f, 0.24f, 0.85f));
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(0.22f, 0.26f, 0.34f, 0.9f));
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(0.12f, 0.15f, 0.20f, 0.9f));
            ImGui.pushStyleColor(ImGuiCol.Text, 0xFFCBD5E1);
        }

        ImGui.pushFont(StudioFonts.icon(), 0.0f);
        if (ImGui.button(icon + "##ftb-tool-" + toolId, size, size)) {
            if (activateTool != null) activateTool.accept(toolId);
        }
        ImGui.popFont();

        ImGui.popStyleColor(4);

        if (ImGui.isItemHovered()) {
            this.hovered = true;
            String shortcutSuffix = shortcut == null || shortcut.isEmpty() ? "" : " [" + shortcut + "]";
            ImGui.setTooltip(name + shortcutSuffix);
        }

        return y + size + 4.0f;
    }
}
