package com.rspsi.osrs.rules.loc;

import com.rspsi.cache.definition.ObjectDefinitionView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client-compatible selection of object-definition model IDs for one loc model source type.
 *
 * <p>When a definition has no model-type array, the client treats all model IDs
 * as the shape-10 model set. When model types are present, only entries whose
 * type matches the requested source type are selected.</p>
 */
public final class LocModelSelection {
    private LocModelSelection() {
    }

    public static List<Integer> select(ObjectDefinitionView definition, int sourceType) {
        Objects.requireNonNull(definition, "definition");
        int[] ids = definition.modelIds();
        int[] types = definition.modelTypes();
        List<Integer> selected = new ArrayList<>();

        if (types.length == 0) {
            if (sourceType == 10) {
                for (int id : ids) selected.add(id);
            }
            return List.copyOf(selected);
        }

        for (int index = 0; index < Math.min(ids.length, types.length); index++) {
            if (types[index] == sourceType) selected.add(ids[index]);
        }
        return List.copyOf(selected);
    }
}
