package com.rspsi.studio.theme;

import com.rspsi.studio.WorkspaceManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
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
                    StudioPalette.draw(StudioPalette.ACCENT_MUTED), 4.0f);
            // A solid edge marker reads as "current" even at a glance.
            drawList.addRectFilled(x, y + 3.0f, x + 3.0f, y + height - 3.0f,
                    StudioPalette.draw(StudioPalette.ACCENT), 2.0f);
        } else if (hovered) {
            drawList.addRectFilled(x, y, x + width, y + height,
                    StudioPalette.draw(StudioPalette.FIELD_HOVER), 4.0f);
        }

        int tint = StudioPalette.draw(selected ? StudioPalette.TEXT : StudioPalette.TEXT_MUTED);

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
        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT_MUTED);
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
                StudioPalette.draw(StudioPalette.PANEL_ELEVATED), 3.0f);
        drawList.addRectFilled(x, y, x + 3.0f, y + height,
                StudioPalette.draw(StudioPalette.ACCENT), 2.0f);
        drawList.addText(x + 10.0f, y + (height - ImGui.getTextLineHeight()) * 0.5f,
                StudioPalette.draw(StudioPalette.TEXT), label);
        ImGui.dummy(width, height);
        ImGui.dummy(1.0f, 2.0f);
    }

    private static final java.util.Map<String, Float> ANIMATIONS = new java.util.HashMap<>();

    /** Delta-time smoothed animation lerp for fluid 60fps micro-interactions. */
    public static float lerp(String key, float target, float speed) {
        float dt = Math.max(1.0f / 120.0f, Math.min(1.0f / 15.0f, ImGui.getIO().getDeltaTime()));
        float current = ANIMATIONS.getOrDefault(key, target);
        float updated = current + (target - current) * Math.min(1.0f, speed * dt);
        if (Math.abs(updated - target) < 0.001f) {
            updated = target;
        }
        ANIMATIONS.put(key, updated);
        return updated;
    }

    private static int lerpColor(int colA, int colB, float t) {
        int rA = colA & 0xFF;
        int gA = (colA >> 8) & 0xFF;
        int bA = (colA >> 16) & 0xFF;
        int aA = (colA >> 24) & 0xFF;

        int rB = colB & 0xFF;
        int gB = (colB >> 8) & 0xFF;
        int bB = (colB >> 16) & 0xFF;
        int aB = (colB >> 24) & 0xFF;

        int r = Math.clamp((int) (rA + (rB - rA) * t), 0, 255);
        int g = Math.clamp((int) (gA + (gB - gA) * t), 0, 255);
        int b = Math.clamp((int) (bA + (bB - bA) * t), 0, 255);
        int a = Math.clamp((int) (aA + (aB - aA) * t), 0, 255);

        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * Begins an elevated, rounded card container with 1px subtle border.
     */
    public static void beginCard(String id, float width, float height) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, StudioPalette.PANEL_BG);
        ImGui.pushStyleColor(ImGuiCol.Border, StudioPalette.BORDER);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 8.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14.0f, 12.0f);
        ImGui.beginChild("##card-" + id, width, height, true, imgui.flag.ImGuiWindowFlags.None);
    }

    public static void endCard() {
        ImGui.endChild();
        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    /**
     * Modern iOS / Linear-style capsule toggle switch with smooth delta-time animated knob.
     */
    public static boolean toggleSwitch(String id, boolean state) {
        return toggleSwitch(id, state, null);
    }

    public static boolean toggleSwitch(String id, boolean state, String label) {
        float width = 36.0f;
        float height = 20.0f;
        float radius = height * 0.5f;

        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();

        boolean clicked = ImGui.invisibleButton("##switch-" + id, width, height);
        boolean newState = clicked ? !state : state;
        boolean hovered = ImGui.isItemHovered();

        float anim = lerp("switch-" + id, newState ? 1.0f : 0.0f, 16.0f);

        ImDrawList drawList = ImGui.getWindowDrawList();
        int colOff = StudioPalette.draw(StudioPalette.BORDER);
        int colOn = StudioPalette.draw(StudioPalette.ACCENT);
        int colBg = lerpColor(colOff, colOn, anim);
        drawList.addRectFilled(x, y, x + width, y + height, colBg, radius);

        int borderColor = StudioPalette.draw(hovered
                ? StudioPalette.ACCENT_HOVER : StudioPalette.BORDER_STRONG);
        drawList.addRect(x, y, x + width, y + height, borderColor, radius, 0, 1.0f);

        float knobPadding = 2.5f;
        float knobRadius = radius - knobPadding;
        float knobMinX = x + radius;
        float knobMaxX = x + width - radius;
        float knobX = knobMinX + (knobMaxX - knobMinX) * anim;
        float knobY = y + radius;

        drawList.addCircleFilled(knobX, knobY, knobRadius, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 1.0f));

        if (label != null && !label.isEmpty()) {
            ImGui.sameLine(0.0f, 8.0f);
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted(label);
        }

        return newState;
    }

    /** Primary brand CTA button using the Studio blue interaction token. */
    public static boolean buttonPrimary(String label, float width, float height) {
        ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_ACTIVE);
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 1.0f));
        boolean clicked = ImGui.button(label, width, height);
        ImGui.popStyleColor(4);
        return clicked;
    }

    /** Secondary neutral surface button with subtle 1px border. */
    public static boolean buttonSecondary(String label, float width, float height) {
        ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.PANEL_ELEVATED);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.FIELD_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_SOFT);
        ImGui.pushStyleColor(ImGuiCol.Border, StudioPalette.BORDER);
        boolean clicked = ImGui.button(label, width, height);
        ImGui.popStyleColor(4);
        return clicked;
    }

    /** Ghost button: transparent background until hovered. */
    public static boolean buttonGhost(String label, float width, float height) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0x00000000);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.FIELD_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_SOFT);
        boolean clicked = ImGui.button(label, width, height);
        ImGui.popStyleColor(3);
        return clicked;
    }

    /** Rounded pill badge for status and tags. */
    public static void pill(String label, int bgColor, int textColor) {
        ImVec2 size = ImGui.calcTextSize(label);
        float padX = 8.0f;
        float padY = 2.5f;
        float width = size.x + padX * 2.0f;
        float height = size.y + padY * 2.0f;
        float radius = height * 0.5f;

        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(x, y, x + width, y + height, bgColor, radius);
        drawList.addText(x + padX, y + padY, textColor, label);

        ImGui.dummy(width, height);
    }

    public static void badge(String label, float r, float g, float b) {
        pill(label, ImGui.getColorU32(r, g, b, 0.22f), ImGui.getColorU32(r, g, b, 1.0f));
    }

    public static boolean toggle(String label, boolean value) {
        return toggleSwitch(label, value, label);
    }

    public static void searchHint(String hint) {
        ImGui.textDisabled(StudioIcons.SEARCH + "  " + hint);
    }

    public static void info(String text) {
        ImGui.textDisabled(StudioIcons.INFO + "  " + text);
    }

    /** Compact two-column inspection table for labels and values. */
    public static boolean beginPropertyTable(String id) {
        if (!ImGui.beginTable("##properties-" + id, 2,
                ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.RowBg)) {
            return false;
        }
        ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthStretch, 0.40f);
        ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch, 0.60f);
        return true;
    }

    public static void propertyRow(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.textWrapped(value == null ? "" : value);
    }

    public static void propertyRowMono(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textWrapped(value == null ? "" : value);
        ImGui.popFont();
    }

    public static void endPropertyTable() {
        ImGui.endTable();
    }

    /**
     * The persistent workspace tab strip shared by every workspace view (Content Studio,
     * Map Studio, Interface Studio, Object Studio).
     *
     * <p>Only workspaces {@code workspaces} reports as open are shown, so a workspace
     * stays available as a tab once entered instead of being torn down on navigation.
     * Content Studio is always present and has no close button; every other tab gets one.</p>
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
            ImGui.pushStyleColor(ImGuiCol.Button,
                    isActive ? StudioPalette.ACCENT : StudioPalette.CHROME_BG);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered,
                    isActive ? StudioPalette.ACCENT_HOVER : StudioPalette.FIELD_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive,
                    isActive ? StudioPalette.ACCENT_ACTIVE : StudioPalette.ACCENT_SOFT);
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
            if (ImGui.button(workspaceLabel(workspace) + "##ws-" + workspace)) {
                runWorkspaceCallback(workspace, openDashboard, openMapEditor, openInterfaceStudio, openObjectStudio);
            }
            ImGui.popStyleColor(4);

            if (workspace != WorkspaceManager.Workspace.DASHBOARD
                    && ImGui.beginPopupContextItem("##ws-context-" + workspace)) {
                if (ImGui.menuItem("Close") && closeWorkspace != null) {
                    closeWorkspace.accept(workspace);
                }
                ImGui.endPopup();
            }
        }
        ImGui.popStyleVar(2);
    }

    private static String workspaceLabel(WorkspaceManager.Workspace workspace) {
        return switch (workspace) {
            case DASHBOARD -> "Content Studio";
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
