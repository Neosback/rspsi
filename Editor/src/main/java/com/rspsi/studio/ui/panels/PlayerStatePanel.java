package com.rspsi.studio.ui.panels;

import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.api.runtime.SimulatedClient;
import com.rspsi.api.runtime.VarDependencies;
import com.rspsi.cache.definition.VarbitDefinitionView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.SettingRows;
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

        ImGui.textWrapped("Shows the map as this player sees it. Variables pick the state of "
                + "multi-state objects; changes rebuild the scene and are never saved.");
        if (ImGui.button("Reset to fresh account", -Float.MIN_VALUE, 0.0f)) {
            client.resetVars();
        }
        ImGui.spacing();
        renderMapDependencies(context, client);
        renderManualVars(client, definitions);
    }

    private void renderMapDependencies(StudioPanelContext context, SimulatedClient client) {
        WorldDocument world = context.session() == null ? null : context.session().world();
        if (world != null && world != scannedWorld) {
            dependencies = VarDependencies.scan(world, client.definitions());
            scannedWorld = world;
        }
        if (!SettingRows.begin("Objects in this map (" + dependencies.size() + ")###player-map-vars", true)) {
            return;
        }
        if (world == null) {
            SettingRows.value("Map", "Open a map to see which variables its objects use.");
        } else if (dependencies.isEmpty()) {
            SettingRows.value("Map", "No multi-state objects in this map.");
        }
        for (VarDependencies.Dependency dependency : dependencies) {
            int current = dependency.kind() == VarDependencies.Kind.VARBIT
                    ? client.getVarbitValue(dependency.id()) : client.getVarpValue(dependency.id());
            List<Integer> values = new java.util.ArrayList<>();
            List<String> labels = new java.util.ArrayList<>();
            for (VarDependencies.State state : dependency.states()) {
                values.add(state.value());
                labels.add(state.value() + ": " + state.label());
            }
            // The default entry is reached by a value past the last state; a
            // narrow varbit (e.g. one bit) cannot hold such a value at all.
            int defaultValue = dependency.states().size();
            boolean representable = dependency.kind() == VarDependencies.Kind.VARP
                    || client.definitions().varbit(dependency.id())
                    .map(varbit -> defaultValue <= varbit.mask()).orElse(false);
            if (representable) {
                values.add(defaultValue);
                labels.add(defaultValue + ": default");
            }
            int selected = values.indexOf(current);
            if (selected < 0) {
                values.add(current);
                labels.add(current + ": (no state)");
                selected = values.size() - 1;
            }
            ImInt choice = new ImInt(selected);
            String label = dependency.key() + "  (" + dependency.placements() + " placed)";
            if (SettingRows.combo(label, choice, labels.toArray(String[]::new)) && choice.get() != selected) {
                set(client, dependency, values.get(choice.get()));
            }
        }
        if (SettingRows.button("Map changed?", "Rescan") && world != null) {
            dependencies = VarDependencies.scan(world, client.definitions());
        }
        SettingRows.end();
    }

    private void renderManualVars(SimulatedClient client, com.rspsi.cache.definition.DefinitionProvider definitions) {
        if (!SettingRows.begin("Set any variable", false)) return;
        SettingRows.inputInt("Varbit id", varbitId);
        var layout = definitions.varbit(varbitId.get());
        SettingRows.value("Layout", layout.map(PlayerStatePanel::describe).orElse("no such varbit"));
        SettingRows.inputInt("Varbit value (now " + client.getVarbitValue(varbitId.get()) + ")", varbitValue);
        if (SettingRows.button("Apply varbit", "Set") && layout.isPresent()) {
            client.setVarbit(varbitId.get(), varbitValue.get());
        }
        SettingRows.inputInt("Varp id", varpId);
        SettingRows.inputInt("Varp value (now " + client.getVarpValue(varpId.get()) + ")", varpValue);
        if (SettingRows.button("Apply varp", "Set")) {
            client.setVarpValue(varpId.get(), varpValue.get());
        }
        SettingRows.end();
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
