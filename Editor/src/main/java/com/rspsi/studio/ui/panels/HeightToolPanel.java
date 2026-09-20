package com.rspsi.studio.ui.panels;

import com.rspsi.editor.ChangeHeightCommand;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Advanced Height Editing console hosted in the bottom drawer or right sidebar.
 */
public final class HeightToolPanel implements StudioPanel {
    public static final String ID = "studio.height-tool";
    public static HeightToolPanel INSTANCE;

    public HeightToolPanel() {
        INSTANCE = this;
    }

    public enum HeightMode {
        RAISE("Raise (+)", 1),
        LOWER("Lower (-)", -1),
        FLATTEN("Flatten (=)", 0),
        SMOOTH("Smooth (~)", 0),
        SET_VALUE("Set Value =", 0);

        private final String label;
        private final int direction;

        HeightMode(String label, int direction) {
            this.label = label;
            this.direction = direction;
        }

        public String label() { return label; }
        public int direction() { return direction; }
    }

    private HeightMode mode = HeightMode.RAISE;
    private final ImInt brushRadius = new ImInt(1);
    private final ImInt stepRate = new ImInt(32);
    private final ImInt targetHeight = new ImInt(0);
    private int falloff = 0; // 0: None, 1: Linear, 2: Smooth
    private static final String[] FALLOFF_NAMES = {"None (Flat block)", "Linear falloff", "Smooth (Cosine)"};

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Height Editor";
    }

    @Override
    public String icon() {
        return StudioIcons.HEIGHT;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.BOTTOM;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT);
    }

    @Override
    public int order() {
        return 6;
    }

    public HeightMode mode() { return mode; }
    public int brushRadius() { return brushRadius.get(); }
    public int stepRate() { return stepRate.get(); }
    public int targetHeight() { return targetHeight.get(); }

    @Override
    public void render(StudioPanelContext context) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 3.0f);

        // 1. Height Mode Selector
        ImGui.textDisabled("Mode:");
        ImGui.sameLine();
        for (HeightMode m : HeightMode.values()) {
            boolean isCur = this.mode == m;
            if (isCur) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.20f, 0.45f, 0.85f, 1.0f));
            }
            if (ImGui.button(m.label() + "##hm-" + m.name())) {
                this.mode = m;
            }
            if (isCur) {
                ImGui.popStyleColor();
            }
            ImGui.sameLine();
        }

        ImGui.newLine();
        ImGui.separator();

        // 2. Settings Row
        float colW = 200.0f;
        ImGui.beginGroup();
        ImGui.setNextItemWidth(colW);
        ImGui.sliderInt("Brush Radius", brushRadius.getData(), 0, 16);

        ImGui.setNextItemWidth(colW);
        ImGui.sliderInt("Step Rate", stepRate.getData(), 4, 128);
        ImGui.endGroup();

        ImGui.sameLine(0.0f, 24.0f);
        ImGui.beginGroup();
        if (mode == HeightMode.FLATTEN || mode == HeightMode.SET_VALUE) {
            ImGui.setNextItemWidth(colW);
            ImGui.inputInt("Target Height", targetHeight);
        } else {
            ImGui.setNextItemWidth(colW);
            if (ImGui.combo("Falloff", new ImInt(falloff), FALLOFF_NAMES)) {
                // falloff updated
            }
        }

        // Quick Preset Deltas
        ImGui.textDisabled("Quick Steps:");
        ImGui.sameLine();
        if (ImGui.smallButton("8##qs8")) stepRate.set(8);
        ImGui.sameLine();
        if (ImGui.smallButton("16##qs16")) stepRate.set(16);
        ImGui.sameLine();
        if (ImGui.smallButton("32##qs32")) stepRate.set(32);
        ImGui.sameLine();
        if (ImGui.smallButton("64##qs64")) stepRate.set(64);
        ImGui.sameLine();
        if (ImGui.smallButton("128##qs128")) stepRate.set(128);
        ImGui.endGroup();

        ImGui.sameLine(0.0f, 24.0f);
        ImGui.beginGroup();
        // Action Button: Apply to Selection
        EditorSession session = context.session();
        Set<TileCoordinate> selected = session != null ? session.selection().selectedCoordinates() : Set.of();
        int selCount = selected.size();

        String applyLabel = selCount > 0
                ? "Apply to " + selCount + " Selected Tile" + (selCount > 1 ? "s" : "")
                : "Apply to Selection (None)";

        boolean canApply = selCount > 0 && session != null;
        if (!canApply) ImGui.beginDisabled();
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(0.18f, 0.55f, 0.35f, 1.0f));
        if (ImGui.button(applyLabel + "##apply-h-sel", 220.0f, 28.0f)) {
            applyHeightToSelection(session, selected);
        }
        ImGui.popStyleColor();
        if (!canApply) ImGui.endDisabled();

        if (ImGui.button("Level Selection to 0##lvl0", 220.0f, 22.0f)) {
            if (session != null && !selected.isEmpty()) {
                flattenSelectionTo(session, selected, 0);
            }
        }
        ImGui.endGroup();

        ImGui.popStyleVar();
    }

    private void applyHeightToSelection(EditorSession session, Set<TileCoordinate> coords) {
        if (session == null || coords.isEmpty()) return;
        List<EditorCommand> commands = new ArrayList<>();
        int delta = mode.direction() * stepRate.get();

        for (TileCoordinate c : coords) {
            TileSnapshot before = session.world().tile(c).snapshot();
            int sw, se, ne, nw;
            if (mode == HeightMode.FLATTEN || mode == HeightMode.SET_VALUE) {
                int target = targetHeight.get();
                sw = target; se = target; ne = target; nw = target;
            } else if (mode == HeightMode.SMOOTH) {
                int avg = (before.southWestHeight() + before.southEastHeight()
                        + before.northEastHeight() + before.northWestHeight()) / 4;
                sw = (before.southWestHeight() + avg) / 2;
                se = (before.southEastHeight() + avg) / 2;
                ne = (before.northEastHeight() + avg) / 2;
                nw = (before.northWestHeight() + avg) / 2;
            } else {
                sw = before.southWestHeight() + delta;
                se = before.southEastHeight() + delta;
                ne = before.northEastHeight() + delta;
                nw = before.northWestHeight() + delta;
            }
            TileSnapshot after = new TileSnapshot(sw, se, ne, nw,
                    before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                    before.flags(), before.objects());
            if (!before.equals(after)) {
                commands.add(new ChangeHeightCommand(c, before, after, "Modify height at " + c));
            }
        }

        if (!commands.isEmpty()) {
            session.execute(new CompositeEditCommand(mode.label() + " selection (" + commands.size() + " tiles)", commands));
        }
    }

    private void flattenSelectionTo(EditorSession session, Set<TileCoordinate> coords, int height) {
        if (session == null || coords.isEmpty()) return;
        List<EditorCommand> commands = new ArrayList<>();
        for (TileCoordinate c : coords) {
            TileSnapshot before = session.world().tile(c).snapshot();
            TileSnapshot after = new TileSnapshot(height, height, height, height,
                    before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                    before.flags(), before.objects());
            if (!before.equals(after)) {
                commands.add(new ChangeHeightCommand(c, before, after, "Level height at " + c));
            }
        }
        if (!commands.isEmpty()) {
            session.execute(new CompositeEditCommand("Level selection to " + height, commands));
        }
    }
}
