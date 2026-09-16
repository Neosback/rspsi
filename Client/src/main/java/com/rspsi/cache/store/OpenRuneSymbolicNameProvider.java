package com.rspsi.cache.store;

import com.rspsi.editor.assets.SymbolicNameProvider;
import dev.openrune.definition.constants.ConstantProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Translates the loaded OpenRune RSCM/GameVal reverse tables into the
 * editor's neutral symbolic-name contract.
 *
 * <p>Names are optional because mappings are an independently loaded data
 * set. A cache can therefore remain usable when no RSCM/GameVal directory is
 * present. The returned key is the exact backend key, preserving provenance
 * rather than inventing a second naming convention in RSPSi.</p>
 */
public final class OpenRuneSymbolicNameProvider implements SymbolicNameProvider {
    private static final Map<String, List<String>> TABLES = Map.of(
            "object", List.of("objects", "object", "locs", "loc"),
            "underlay", List.of("underlays", "underlay"),
            "overlay", List.of("overlays", "overlay"),
            "texture", List.of("textures", "texture"),
            "model", List.of("models", "model"));

    @Override
    public Optional<String> name(String type, int id) {
        if (type == null || id < 0) return Optional.empty();
        List<String> tableNames = TABLES.get(type.trim().toLowerCase(java.util.Locale.ROOT));
        if (tableNames == null) return Optional.empty();

        Map<String, Map<String, Integer>> mappings = ConstantProvider.INSTANCE.getMappings();
        for (String table : tableNames) {
            if (!mappings.containsKey(table)) continue;
            return reverse(mappings.get(table), id);
        }
        return Optional.empty();
    }

    private static Optional<String> reverse(Map<String, Integer> table, int id) {
        if (table == null) return Optional.empty();
        return table.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() == id)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
