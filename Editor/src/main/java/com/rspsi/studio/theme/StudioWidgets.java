package com.rspsi.studio.theme;

import com.rspsi.studio.WorkspaceManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;

import java.util.function.Consumer;

/** Small, consistent controls used by the native OpenRune Studio shell. */
public final class StudioWidgets {
    private StudioWidgets() {
    }

    public static boolean iconButton(String id, String icon, String label, String shortcut) {
        boolean clicked = ImGui.button(icon + "  " + label + "##" + id);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip(label + (shortcut == null || shortcut.isBlank()
                    ? "" : "  (" + shortcut + ")"));
        }
        return clicked;
    }

    /**
     * A rail entry: icon over a short caption, filling the rail's width.
     *
     * <p>The caption is deliberately not hidden behind a hover tooltip. A
     * bare icon strip asks the user to memorise three glyphs before they can
     * do anything; naming the tools costs a few points of rail width and
     * removes that entirely.</p>
     */
    public static boolean railButton(String id, String icon, String label, boolean selected,
                                     String shortcut) {
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        int iconSize = 20;
        float captionHeight = ImGui.getTextLineHeight();
        float height = iconSize + captionHeight + 14.0f;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##" + id, width, height);
        boolean hovered = ImGui.isItemHovered();

        ImDrawList drawList = ImGui.getWindowDrawList();
        if (selected) {
            drawList.addRectFilled(x, y, x + width, y + height,
                    ImGui.getColorU32(0.16f, 0.32f, 0.52f, 1.0f), 4.0f);
            // A solid edge marker reads as "current" even at a glance.
            drawList.addRectFilled(x, y + 3.0f, x + 3.0f, y + height - 3.0f,
                    ImGui.getColorU32(0.35f, 0.66f, 0.98f, 1.0f), 2.0f);
        } else if (hovered) {
            drawList.addRectFilled(x, y, x + width, y + height,
                    ImGui.getColorU32(0.17f, 0.19f, 0.23f, 1.0f), 4.0f);
        }

        int tint = selected
                ? ImGui.getColorU32(1.0f, 1.0f, 1.0f, 1.0f)
                : ImGui.getColorU32(0.72f, 0.76f, 0.83f, 1.0f);

        ImGui.pushFont(StudioFonts.icon(), (float) iconSize);
        ImVec2 glyph = ImGui.calcTextSize(icon);
        ImGui.popFont();
        drawList.addText(StudioFonts.icon(), iconSize,
                x + Math.max(0.0f, (width - glyph.x) * 0.5f), y + 6.0f, tint, icon);

        String caption = railCaption(label);
        ImVec2 captionSize = ImGui.calcTextSize(caption);
        drawList.addText(x + Math.max(0.0f, (width - captionSize.x) * 0.5f),
                y + iconSize + 8.0f, tint, caption);

        if (hovered) {
            ImGui.setItemTooltip(label + (shortcut == null || shortcut.isBlank()
                    ? "" : "  (" + shortcut + ")"));
        }
        return clicked;
    }

    /** Rail captions are one short word; the tooltip carries the full name. */
    private static String railCaption(String label) {
        int cut = label.indexOf(' ');
        String first = cut < 0 ? label : label.substring(0, cut);
        return first.length() <= 9 ? first : first.substring(0, 9);
    }

    /**
     * Labels a control on its own line and lets the control span the panel.
     *
     * <p>Dear ImGui puts a widget's label to its right, which in a fixed
     * inspector column pushes long setting names past the edge - that is why
     * "Selection quarter tile" was rendering clipped. Naming the field above
     * it means the label can never be the thing that overflows.</p>
     */
    public static void fieldLabel(String label) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.74f, 0.80f, 1.0f);
        ImGui.textWrapped(label);
        ImGui.popStyleColor();
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
    }

    public static void heading(String label, String hint) {
        section(label);
        if (hint != null && !hint.isBlank()) ImGui.textDisabled(hint);
    }

    /**
     * A full-width filled bar labeling the section below it — the same
     * "solid blue bar" idiom used throughout Displee's map editor, instead
     * of a bare label and a hairline rule that barely separates sections.
     */
    public static void section(String label) {
        ImGui.dummy(1.0f, 4.0f);
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float height = ImGui.getFrameHeight();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(x, y, x + width, y + height,
                ImGui.getColorU32(0.23f, 0.32f, 0.42f, 1.0f), 2.0f);
        drawList.addText(x + 8.0f, y + (height - ImGui.getTextLineHeight()) * 0.5f,
                ImGui.getColorU32(0.90f, 0.93f, 0.97f, 1.0f), "> " + label);
        ImGui.dummy(width, height);
        ImGui.dummy(1.0f, 2.0f);
    }

    public static void badge(String label, float r, float g, float b) {
        ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, 0.22f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r, g, b, 0.32f);
        ImGui.smallButton(label);
        ImGui.popStyleColor(2);
    }

    public static boolean toggle(String label, boolean value) {
        ImBoolean state = new ImBoolean(value);
        return ImGui.checkbox(label, state) ? state.get() : value;
    }

    public static void searchHint(String hint) {
        ImGui.textDisabled(StudioIcons.SEARCH + "  " + hint);
    }

    public static void info(String text) {
        ImGui.textDisabled(StudioIcons.INFO + "  " + text);
    }

    /**
     * The persistent workspace tab strip shared by every workspace view (Dashboard,
     * Map Studio, Interface Studio, Object Studio).
     *
     * <p>Only workspaces {@code workspaces} reports as open are shown, so a workspace
     * stays available as a tab once entered instead of being torn down on navigation.
     * Dashboard is always present and has no close button; every other tab gets one.</p>
     */
    public static void workspaceTabs(WorkspaceManager workspaces,
                                     Runnable openDashboard,
                                     Runnable openMapEditor,
                                     Runnable openInterfaceStudio,
                                     Runnable openObjectStudio,
                                     Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        WorkspaceManager.Workspace active = workspaces.active();
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10.0f, 3.0f);
        boolean first = true;
        for (WorkspaceManager.Workspace workspace : workspaces.open()) {
            if (!first) ImGui.sameLine();
            first = false;

            boolean isActive = workspace == active;
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.18f, 0.38f, 0.65f, 1.0f));
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 1.0f));
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.13f, 0.15f, 0.18f, 0.85f));
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(0.65f, 0.68f, 0.75f, 1.0f));
            }
            if (ImGui.button(workspaceLabel(workspace) + "##ws-" + workspace)) {
                runWorkspaceCallback(workspace, openDashboard, openMapEditor, openInterfaceStudio, openObjectStudio);
            }
            ImGui.popStyleColor(2);

            if (workspace != WorkspaceManager.Workspace.DASHBOARD) {
                ImGui.sameLine(0.0f, 2.0f);
                if (ImGui.smallButton("x##ws-close-" + workspace) && closeWorkspace != null) {
                    closeWorkspace.accept(workspace);
                }
            }
        }
        ImGui.popStyleVar(2);
    }

    private static String workspaceLabel(WorkspaceManager.Workspace workspace) {
        return switch (workspace) {
            case DASHBOARD -> "Dashboard";
            case MAP_EDITOR -> "Map Studio";
            case INTERFACE_STUDIO -> "Interface Studio";
            case OBJECT_STUDIO -> "Object Studio";
        };
    }

    private static void runWorkspaceCallback(WorkspaceManager.Workspace workspace,
                                             Runnable openDashboard, Runnable openMapEditor,
                                             Runnable openInterfaceStudio, Runnable openObjectStudio) {
        switch (workspace) {
            case DASHBOARD -> { if (openDashboard != null) openDashboard.run(); }
            case MAP_EDITOR -> { if (openMapEditor != null) openMapEditor.run(); }
            case INTERFACE_STUDIO -> { if (openInterfaceStudio != null) openInterfaceStudio.run(); }
            case OBJECT_STUDIO -> { if (openObjectStudio != null) openObjectStudio.run(); }
        }
    }
}
