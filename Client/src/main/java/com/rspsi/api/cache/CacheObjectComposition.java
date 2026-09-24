package com.rspsi.api.cache;

import com.rspsi.api.ObjectComposition;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.definition.ObjectDefinitionResolver;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectVarState;

import java.util.List;
import java.util.Optional;

/**
 * {@link ObjectComposition} over a cache object definition. {@link #getImpostor()}
 * resolves the multiloc with the client's rule for the given var state
 * ({@link ObjectDefinitionResolver}, after {@code ObjectComposition.transform}).
 */
public final class CacheObjectComposition implements ObjectComposition {
    private final ObjectDefinitionView definition;
    private final DefinitionProvider definitions;
    private final ObjectVarState varState;

    public CacheObjectComposition(ObjectDefinitionView definition, DefinitionProvider definitions,
                                  ObjectVarState varState) {
        this.definition = definition;
        this.definitions = definitions;
        this.varState = varState;
    }

    public static Optional<ObjectComposition> of(int objectId, DefinitionProvider definitions, ObjectVarState varState) {
        return definitions.object(objectId).map(def -> new CacheObjectComposition(def, definitions, varState));
    }

    @Override public int getId() { return definition.id(); }

    @Override
    public String getName() {
        String name = definition.name();
        return name == null || name.isBlank() ? "null" : name;
    }

    @Override
    public String[] getActions() {
        List<String> actions = definitions.objectActions(definition.id()).orElse(List.of());
        String[] result = new String[5];
        for (int i = 0; i < Math.min(5, actions.size()); i++) result[i] = actions.get(i);
        return result;
    }

    @Override public int getMapSceneId() { return definition.mapSceneId(); }

    @Override public int getMapIconId() { return definitions.objectMapElement(definition.id()).orElse(-1); }

    @Override
    public int[] getImpostorIds() {
        return definition.transforms().length == 0 ? null : definition.transforms();
    }

    @Override
    public ObjectComposition getImpostor() {
        if (definition.transforms().length == 0) return this;
        return new ObjectDefinitionResolver(definitions, varState).resolveEditorDisplay(definition.id())
                .displayDefinition()
                .<ObjectComposition>map(display -> new CacheObjectComposition(display, definitions, varState))
                .orElse(null);
    }

    @Override
    public int getAccessBitMask() {
        return definitions.objectAppearance(definition.id()).map(appearance -> appearance.clipMask()).orElse(0);
    }

    @Override public int getVarbitId() { return definition.varbit(); }

    @Override public int getVarPlayerId() { return definition.varp(); }

    @Override public int getSizeX() { return definition.width(); }

    @Override public int getSizeY() { return definition.length(); }

    @Override
    public int getIntValue(int paramId, int defaultValue) {
        return param(paramId).map(param -> {
            try {
                return Integer.parseInt(param.value());
            } catch (NumberFormatException notInt) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Override
    public String getStringValue(int paramId) {
        return param(paramId).filter(param -> param.type() == ObjectDefinitionRawView.ValueType.STRING)
                .map(ObjectDefinitionRawView.Param::value).orElse(null);
    }

    private Optional<ObjectDefinitionRawView.Param> param(int paramId) {
        return definitions.objectRaw(definition.id()).flatMap(raw -> raw.params().stream()
                .filter(param -> param.id() == paramId).findFirst());
    }
}
