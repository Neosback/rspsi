package com.rspsi.api.runtime;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionResolver;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.VarbitDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.simulation.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulatedClientTest {
    /** Bush multiloc shaped like rev-240 object 10586: [Bush, Bank chest, -1] on varbit 3984. */
    private static final DefinitionProvider DEFINITIONS = new DefinitionProvider() {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return switch (id) {
                case 10586 -> Optional.of(new ObjectDefinitionView(id, "null", 1, 1, List.of(),
                        new int[0], new int[0], -1, false, 3984, -1, new int[]{1124, 4483, -1}, -1));
                case 1124 -> Optional.of(new ObjectDefinitionView(id, "Bush", 1, 1, List.of(),
                        new int[]{1565}, new int[]{10}, -1, false));
                case 4483 -> Optional.of(new ObjectDefinitionView(id, "Bank chest", 1, 1, List.of(),
                        new int[]{1566}, new int[]{10}, -1, false));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<VarbitDefinitionView> varbit(int id) {
            // Varbit 3984 lives in bits 4..6 of varp 1000.
            return id == 3984 ? Optional.of(new VarbitDefinitionView(3984, 1000, 4, 6)) : Optional.empty();
        }

        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
    };

    @Test
    void varbitsReadAndWriteTheirBitsOfTheParentVarp() {
        VarbitDefinitionView varbit = new VarbitDefinitionView(1, 50, 4, 6);

        assertEquals(0b101, varbit.read(0b1010000));
        assertEquals(0b0110000 | 0b1111, varbit.write(0b1111, 0b011));
        assertEquals(0b1110000, varbit.write(0, 0b1111), "values are masked to the varbit width");
    }

    @Test
    void setVarbitRewritesOnlyItsBitsInTheSimulationEngine() {
        SimulationEngine engine = new SimulationEngine();
        SimulatedClient client = new SimulatedClient(engine, DEFINITIONS);
        client.setVarpValue(1000, 0b1111);

        client.setVarbit(3984, 1);

        assertEquals(1, client.getVarbitValue(3984));
        assertEquals(0b0011111, client.getVarpValue(1000), "neighbouring bits are untouched");
        assertEquals(1000, client.getVarbit(3984).getIndex());
        assertNull(client.getVarbit(9999));
    }

    @Test
    void multilocFollowsTheSimulatedPlayer() {
        SimulatedClient client = new SimulatedClient(new SimulationEngine(), DEFINITIONS);
        ObjectDefinitionResolver resolver = new ObjectDefinitionResolver(DEFINITIONS, client);

        assertEquals(1124, resolver.resolveEditorDisplay(10586).displayDefinition().orElseThrow().id());
        client.setVarbit(3984, 1);
        assertEquals(4483, resolver.resolveEditorDisplay(10586).displayDefinition().orElseThrow().id());
        client.setVarbit(3984, 5);
        assertEquals(ObjectDefinitionResolver.Status.HIDDEN_IN_VAR_STATE,
                resolver.resolveEditorDisplay(10586).status(), "out of range -> default entry -1");
        client.resetVars();
        assertEquals(0, client.getVarbitValue(3984));
    }

    @Test
    void dependencyScanListsEachVariableWithItsStates() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(10586, 10, 0, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(10586, 10, 1, 0, 1, 0))));

        List<VarDependencies.Dependency> dependencies = VarDependencies.scan(document, DEFINITIONS);

        assertEquals(1, dependencies.size());
        VarDependencies.Dependency bush = dependencies.get(0);
        assertEquals("varbit 3984", bush.key());
        assertEquals(2, bush.placements());
        assertEquals(List.of(new VarDependencies.State(0, "Bush"), new VarDependencies.State(1, "Bank chest")),
                bush.states());
    }
}
