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
        RAISE("Raise (+)", 1, "terrain.raise"),
        LOWER("Lower (-)", -1, "terrain.lower"),
        FLATTEN("Flatten (=)", 0, "terrain.flatten"),
        SMOOTH("Smooth (~)", 0, "terrain.smooth"),
        BLEND("Blend", 0, "terrain.blend"),
        TERRACE("Terrace", 0, "terrain.terrace"),
        SET_VALUE("Set Value =", 0, "terrain.flatten");

        private final String label;
        private final int direction;
        private final String engineToolId;

        HeightMode(String label, int direction, String engineToolId) {
            this.label = label;
            this.direction = direction;
            this.engineToolId = engineToolId;
        }

        public String label() { return label; }
        public int direction() { return direction; }
        public String engineToolId() { return engineToolId; }
    }

    private HeightMode mode = HeightMode.RAISE;
    private final ImInt brushRadius = new ImInt(1);
    private final ImInt stepRate = new ImInt(32);
    private final ImInt targetHeight = new ImInt(0);
    private final ImInt terraceStep = new ImInt(16);
    private final ImInt blendStrength = new ImInt(50);
    private final ImInt edgeThreshold = new ImInt(56);
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

        // Sync mode with active tool if active tool matches an engine tool
        if (context.activeToolId() != null) {
            for (HeightMode m : HeightMode.values()) {
                if (m.engineToolId().equals(context.activeToolId())) {
                    this.mode = m;
                    break;
                }
            }
        }

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
                if (context.activateTool() != null) {
                    context.activateTool().accept(m.engineToolId());
                }
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
        if (context.brushes() != null) {
            ImInt rad = new ImInt(context.brushes().brushRadius());
            if (ImGui.sliderInt("Brush Radius", rad.getData(), 0, 16)) {
                context.brushes().setBrushRadius(rad.get());
                brushRadius.set(rad.get());
            }
        } else {
            ImGui.sliderInt("Brush Radius", brushRadius.getData(), 0, 16);
        }

        if (mode == HeightMode.TERRACE) {
            ImGui.setNextItemWidth(colW);
            ImGui.sliderInt("Terrace Step", terraceStep.getData(), 2, 96);
        } else if (mode == HeightMode.BLEND) {
            ImGui.setNextItemWidth(colW);
            ImGui.sliderInt("Blend Strength (%)", blendStrength.getData(), 0, 100);
        } else {
            ImGui.setNextItemWidth(colW);
            ImGui.sliderInt("Step Rate", stepRate.getData(), 4, 128);
        }
        ImGui.endGroup();

        ImGui.sameLine(0.0f, 24.0f);
        ImGui.beginGroup();
        if (mode == HeightMode.FLATTEN || mode == HeightMode.SET_VALUE) {
            ImGui.setNextItemWidth(colW);
            ImGui.inputInt("Target Height", targetHeight);
        } else if (mode == HeightMode.BLEND) {
            ImGui.setNextItemWidth(colW);
            ImGui.sliderInt("Cliff Threshold", edgeThreshold.getData(), 8, 128);
        } else if (mode == HeightMode.TERRACE) {
            ImGui.textDisabled("Terracing snaps terrain into uniform stepped plateaus.");
        } else {
            ImGui.setNextItemWidth(colW);
            if (ImGui.combo("Falloff", new ImInt(falloff), FALLOFF_NAMES)) {
                // falloff updated
            }
        }

        // Quick Preset Deltas
        if (mode == HeightMode.RAISE || mode == HeightMode.LOWER) {
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
        } else if (mode == HeightMode.TERRACE) {
            ImGui.textDisabled("Quick Steps:");
            ImGui.sameLine();
            if (ImGui.smallButton("4##qt4")) terraceStep.set(4);
            ImGui.sameLine();
            if (ImGui.smallButton("8##qt8")) terraceStep.set(8);
            ImGui.sameLine();
            if (ImGui.smallButton("16##qt16")) terraceStep.set(16);
            ImGui.sameLine();
            if (ImGui.smallButton("32##qt32")) terraceStep.set(32);
        }
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
        if (session == null || !session.canEdit() || coords.isEmpty()) return;
        List<EditorCommand> commands = new ArrayList<>();
        int delta = mode.direction() * stepRate.get();

        for (TileCoordinate c : coords) {
            TileSnapshot before = session.world().tile(c).snapshot();
            int sw, se, ne, nw;
            if (mode == HeightMode.FLATTEN || mode == HeightMode.SET_VALUE) {
                int target = targetHeight.get();
                sw = target; se = target; ne = target; nw = target;
            } else if (mode == HeightMode.TERRACE) {
                int step = Math.max(2, Math.min(96, terraceStep.get()));
                sw = Math.round((float) before.southWestHeight() / step) * step;
                se = Math.round((float) before.southEastHeight() / step) * step;
                ne = Math.round((float) before.northEastHeight() / step) * step;
                nw = Math.round((float) before.northWestHeight() / step) * step;
            } else if (mode == HeightMode.BLEND) {
                int strength = blendStrength.get();
                int thresh = edgeThreshold.get();
                int avg = (before.southWestHeight() + before.southEastHeight()
                        + before.northEastHeight() + before.northWestHeight()) / 4;
                sw = Math.abs(avg - before.southWestHeight()) <= thresh
                        ? before.southWestHeight() + (avg - before.southWestHeight()) * strength / 100
                        : before.southWestHeight();
                se = Math.abs(avg - before.southEastHeight()) <= thresh
                        ? before.southEastHeight() + (avg - before.southEastHeight()) * strength / 100
                        : before.southEastHeight();
                ne = Math.abs(avg - before.northEastHeight()) <= thresh
                        ? before.northEastHeight() + (avg - before.northEastHeight()) * strength / 100
                        : before.northEastHeight();
                nw = Math.abs(avg - before.northWestHeight()) <= thresh
                        ? before.northWestHeight() + (avg - before.northWestHeight()) * strength / 100
                        : before.northWestHeight();
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
        if (session == null || !session.canEdit() || coords.isEmpty()) return;
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
