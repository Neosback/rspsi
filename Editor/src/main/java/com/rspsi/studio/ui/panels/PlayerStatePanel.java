package com.rspsi.studio.ui.panels;

import com.rspsi.api.runtime.SimulatedClient;
import com.rspsi.api.runtime.VarDependencies;
import com.rspsi.cache.definition.VarbitDefinitionView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Simulated player: the game state the map is shown in. Player variables
 * (varbits and varps) decide which state multi-state objects show - bushes,
 * doors, quest scenery. Changing one rebuilds the scene with the new state.
 * Nothing here is saved into the map.
 */
public final class PlayerStatePanel implements StudioPanel {
    public static final String ID = "studio.player-state";

    private final ImInt varbitId = new ImInt(0);
    private final ImInt varbitValue = new ImInt(0);
    private final ImInt varpId = new ImInt(0);
    private final ImInt varpValue = new ImInt(0);
    private WorldDocument scannedWorld;
    private List<VarDependencies.Dependency> dependencies = List.of();

    @Override public String id() { return ID; }
    @Override public String title() { return "Player State"; }
    @Override public String icon() { return StudioIcons.PLAYER; }
    @Override public DockRegion preferredRegion() { return DockRegion.RIGHT; }
    @Override public Set<DockRegion> allowedRegions() { return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM); }
    @Override public int order() { return 36; }

    @Override
    public void render(StudioPanelContext context) {
        if (context.cache() == null || context.simulation() == null) {
            ImGui.textDisabled("Load a cache to simulate a player.");
            return;
        }
        var definitions = context.cache().bundle().definitions();
        SimulatedClient client = new SimulatedClient(context.simulation(), definitions);

        ImGui.textWrapped("The map is shown as this player sees it. Player variables decide which "
                + "state multi-state objects show (bushes, doors, quest scenery). Changes rebuild "
                + "the scene and are never saved into the map.");
        if (ImGui.button("Reset to fresh account")) {
            client.resetVars();
        }
        ImGui.sameLine();
        ImGui.textDisabled("(every variable 0)");

        ImGui.spacing();
        ImGui.separator();
        renderMapDependencies(context, client);

        ImGui.spacing();
        ImGui.separator();
        renderManualVars(client, definitions);
    }

    private void renderMapDependencies(StudioPanelContext context, SimulatedClient client) {
        WorldDocument world = context.session() == null ? null : context.session().world();
        if (world == null) {
            ImGui.textDisabled("Open a map to see which variables its objects use.");
            return;
        }
        if (world != scannedWorld) {
            dependencies = VarDependencies.scan(world, client.definitions());
            scannedWorld = world;
        }
        ImGui.textColored(0xFF38BDF8, "Variables used by objects in this map (" + dependencies.size() + ")");
        if (ImGui.smallButton("Rescan##player-rescan")) {
            dependencies = VarDependencies.scan(world, client.definitions());
        }
        if (dependencies.isEmpty()) {
            ImGui.textDisabled("No multi-state objects in this map.");
            return;
        }
        for (VarDependencies.Dependency dependency : dependencies) {
            int current = dependency.kind() == VarDependencies.Kind.VARBIT
                    ? client.getVarbitValue(dependency.id()) : client.getVarpValue(dependency.id());
            ImGui.pushID(dependency.key());
            ImGui.spacing();
            ImGui.text(dependency.key() + " = " + current);
            ImGui.sameLine();
            ImGui.textDisabled(dependency.placements() + " placement(s)");
            for (VarDependencies.State state : dependency.states()) {
                boolean active = state.value() == current;
                if (active) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0xFF2563EB);
                if (ImGui.smallButton(state.value() + ": " + state.label())) {
                    set(client, dependency, state.value());
                }
                if (active) ImGui.popStyleColor();
            }
            // The default entry is reached by a value past the last state; a
            // narrow varbit (e.g. one bit) cannot hold such a value at all.
            int defaultValue = dependency.states().size();
            boolean representable = dependency.kind() == VarDependencies.Kind.VARP
                    || client.definitions().varbit(dependency.id())
                    .map(varbit -> defaultValue <= varbit.mask()).orElse(false);
            if (representable) {
                boolean onDefault = current >= defaultValue;
                if (onDefault) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0xFF2563EB);
                if (ImGui.smallButton("default (out of range)")) {
                    set(client, dependency, defaultValue);
                }
                if (onDefault) ImGui.popStyleColor();
            }
            ImGui.popID();
        }
    }

    private void renderManualVars(SimulatedClient client, com.rspsi.cache.definition.DefinitionProvider definitions) {
        ImGui.textColored(0xFF38BDF8, "Set any variable");
        ImGui.pushItemWidth(90.0f);
        ImGui.inputInt("varbit##manual-varbit", varbitId);
        ImGui.sameLine();
        ImGui.inputInt("value##manual-varbit-value", varbitValue);
        ImGui.popItemWidth();
        ImGui.sameLine();
        var layout = definitions.varbit(varbitId.get());
        if (ImGui.button("Set##manual-varbit-set") && layout.isPresent()) {
            client.setVarbit(varbitId.get(), varbitValue.get());
        }
        ImGui.textDisabled(layout.map(PlayerStatePanel::describe)
                .orElse("No varbit " + varbitId.get() + " in this cache")
                + "   current = " + client.getVarbitValue(varbitId.get()));

        ImGui.pushItemWidth(90.0f);
        ImGui.inputInt("varp##manual-varp", varpId);
        ImGui.sameLine();
        ImGui.inputInt("value##manual-varp-value", varpValue);
        ImGui.popItemWidth();
        ImGui.sameLine();
        if (ImGui.button("Set##manual-varp-set")) {
            client.setVarpValue(varpId.get(), varpValue.get());
        }
        ImGui.textDisabled("current = " + client.getVarpValue(varpId.get()));
    }

    private static void set(SimulatedClient client, VarDependencies.Dependency dependency, int value) {
        if (dependency.kind() == VarDependencies.Kind.VARBIT) {
            client.setVarbit(dependency.id(), value);
        } else {
            client.setVarpValue(dependency.id(), value);
        }
    }

    private static String describe(VarbitDefinitionView varbit) {
        return "bits " + varbit.leastSignificantBit() + ".." + varbit.mostSignificantBit()
                + " of varp " + varbit.varp();
    }
}
