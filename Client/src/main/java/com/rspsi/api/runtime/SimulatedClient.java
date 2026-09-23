package com.rspsi.api.runtime;

import com.rspsi.api.Client;
import com.rspsi.api.VarbitComposition;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectVarState;
import com.rspsi.cache.definition.VarbitDefinitionView;
import com.rspsi.editor.simulation.SimulationEngine;

import java.util.Objects;

/**
 * {@link Client} over the editor's {@link SimulationEngine}: varps live in the
 * engine's runtime state, varbits are read and written through the cache's
 * varbit layouts. Also the {@link ObjectVarState} scene builds resolve
 * multilocs against.
 */
public final class SimulatedClient implements Client, ObjectVarState {
    private final SimulationEngine engine;
    private final DefinitionProvider definitions;

    public SimulatedClient(SimulationEngine engine, DefinitionProvider definitions) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public DefinitionProvider definitions() {
        return definitions;
    }

    @Override
    public int getVarbitValue(int varbitId) {
        return definitions.varbit(varbitId)
                .map(varbit -> varbit.read(getVarpValue(varbit.varp())))
                .orElse(0);
    }

    @Override
    public int getVarpValue(int varpId) {
        return engine.state().varp(varpId);
    }

    @Override
    public void setVarbit(int varbitId, int value) {
        VarbitDefinitionView varbit = definitions.varbit(varbitId).orElseThrow(
                () -> new IllegalArgumentException("No varbit " + varbitId + " in this cache"));
        setVarpValue(varbit.varp(), varbit.write(getVarpValue(varbit.varp()), value));
    }

    @Override
    public void setVarpValue(int varpId, int value) {
        engine.stateProvider().setVarp(varpId, value);
    }

    @Override
    public VarbitComposition getVarbit(int varbitId) {
        return definitions.varbit(varbitId).map(varbit -> (VarbitComposition) new VarbitComposition() {
            @Override public int getIndex() { return varbit.varp(); }
            @Override public int getLeastSignificantBit() { return varbit.leastSignificantBit(); }
            @Override public int getMostSignificantBit() { return varbit.mostSignificantBit(); }
        }).orElse(null);
    }

    @Override
    public void resetVars() {
        engine.stateProvider().reset();
    }

    @Override
    public int varbitValue(int varbitId) {
        return getVarbitValue(varbitId);
    }

    @Override
    public int varpValue(int varpId) {
        return getVarpValue(varpId);
    }
}
