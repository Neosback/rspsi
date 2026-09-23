package com.rspsi.api.runtime;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which player variables the placed objects of a map depend on: every
 * multiloc's varbit or varp, the states it can show, and how many placements
 * use it. Drives the Player State panel.
 */
public final class VarDependencies {
    private VarDependencies() {
    }

    public enum Kind {
        VARBIT,
        VARP
    }

    /** One state index a variable can hold and what the objects show for it. */
    public record State(int value, String label) {
    }

    public record Dependency(Kind kind, int id, int placements, Set<Integer> objectIds, List<State> states) {
        public Dependency {
            objectIds = Set.copyOf(objectIds);
            states = List.copyOf(states);
        }

        public String key() {
            return (kind == Kind.VARBIT ? "varbit " : "varp ") + id;
        }
    }

    /** Dependencies of every placed multiloc in {@code document}, most-used first. */
    public static List<Dependency> scan(WorldDocument document, DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Map<String, Builder> byVar = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    for (WorldObject object : document.tile(plane, x, y).objects()) {
                        definitions.object(object.id())
                                .filter(ObjectDefinitionView::hasTransforms)
                                .ifPresent(definition -> record(byVar, definition, definitions));
                    }
                }
            }
        }
        return byVar.values().stream().map(Builder::build)
                .sorted(Comparator.comparingInt(Dependency::placements).reversed()
                        .thenComparing(Dependency::key))
                .toList();
    }

    private static void record(Map<String, Builder> byVar, ObjectDefinitionView definition,
                               DefinitionProvider definitions) {
        Kind kind;
        int id;
        if (definition.varbit() != -1) {
            kind = Kind.VARBIT;
            id = definition.varbit();
        } else if (definition.varp() != -1) {
            kind = Kind.VARP;
            id = definition.varp();
        } else {
            return;
        }
        Builder builder = byVar.computeIfAbsent(kind + ":" + id, key -> new Builder(kind, id));
        builder.placements++;
        builder.objectIds.add(definition.id());
        int[] transforms = definition.transforms();
        // The last entry is the default the client falls back to; states are 0..n-2.
        for (int value = 0; value < transforms.length - 1; value++) {
            builder.labels.computeIfAbsent(value, key -> new LinkedHashSet<>())
                    .add(label(transforms[value], definitions));
        }
    }

    private static String label(int objectId, DefinitionProvider definitions) {
        if (objectId < 0) return "nothing";
        return definitions.object(objectId)
                .map(value -> value.hasDisplayName() ? value.displayName() : "#" + objectId)
                .orElse("#" + objectId);
    }

    private static final class Builder {
        private final Kind kind;
        private final int id;
        private int placements;
        private final Set<Integer> objectIds = new LinkedHashSet<>();
        private final Map<Integer, Set<String>> labels = new LinkedHashMap<>();

        private Builder(Kind kind, int id) {
            this.kind = kind;
            this.id = id;
        }

        private Dependency build() {
            List<State> states = new ArrayList<>();
            labels.forEach((value, names) -> states.add(new State(value, String.join(" / ", names))));
            return new Dependency(kind, id, placements, objectIds, states);
        }
    }
}
