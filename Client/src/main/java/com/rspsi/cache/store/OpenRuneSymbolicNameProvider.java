package com.rspsi.cache.store;

import com.rspsi.editor.assets.SymbolicNameProvider;
import dev.openrune.cache.gameval.GameValElement;
import dev.openrune.cache.gameval.GameValHandler;
import dev.openrune.definition.GameValGroupTypes;
import dev.openrune.definition.constants.ConstantProvider;
import dev.openrune.filesystem.Cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Translates OpenRune GameVal/RSCM/Sym mappings into the editor's neutral
 * symbolic-name contract.
 *
 * <p>Live cache sessions prefer GameVals embedded in the selected cache for
 * groups OpenRune exposes directly. External RSCM/Sym mappings loaded into
 * {@link ConstantProvider} remain a fallback for projects that supply them.
 * This prevents the asset browser from exposing a symbolic-name capability
 * that silently stays empty unless another subsystem happened to initialise a
 * global mapping provider first.</p>
 */
public final class OpenRuneSymbolicNameProvider implements SymbolicNameProvider {
    private static final Map<String, List<String>> TABLES = Map.of(
            "object", List.of("objects", "object", "locs", "loc"),
            "sequence", List.of("sequences", "sequence", "seqs", "seq"),
            "underlay", List.of("underlays", "underlay"),
            "overlay", List.of("overlays", "overlay"),
            "texture", List.of("textures", "texture"),
            "model", List.of("models", "model"));

    private final Map<String, Map<Integer, String>> cacheNames;

    /** Compatibility constructor for callers that only use externally loaded mappings. */
    public OpenRuneSymbolicNameProvider() {
        this.cacheNames = Map.of();
    }

    /** Builds symbolic names from the selected cache's embedded GameVal groups. */
    public OpenRuneSymbolicNameProvider(Cache cache, int revision) {
        if (cache == null) throw new NullPointerException("cache");
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        Map<String, Map<Integer, String>> names = new LinkedHashMap<>();
        loadGameVals(names, "object", "objects", GameValGroupTypes.LOCTYPES, cache, revision);
        loadGameVals(names, "sequence", "sequences", GameValGroupTypes.SEQTYPES, cache, revision);
        this.cacheNames = Map.copyOf(names);
    }

    @Override
    public Optional<String> name(String type, int id) {
        if (type == null || id < 0) return Optional.empty();
        String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);

        Map<Integer, String> cacheTable = cacheNames.get(normalized);
        if (cacheTable != null) {
            String value = cacheTable.get(id);
            if (value != null) return Optional.of(value);
        }

        List<String> tableNames = TABLES.get(normalized);
        if (tableNames == null) return Optional.empty();

        Map<String, Map<String, Integer>> mappings = ConstantProvider.INSTANCE.getMappings();
        for (String table : tableNames) {
            if (!mappings.containsKey(table)) continue;
            Optional<String> value = reverse(mappings.get(table), id);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    private static void loadGameVals(Map<String, Map<Integer, String>> names,
                                     String neutralType,
                                     String prefix,
                                     GameValGroupTypes group,
                                     Cache cache,
                                     int revision) {
        try {
            List<GameValElement> values =
                    GameValHandler.INSTANCE.readGameVal(group, cache, revision);
            if (values.isEmpty()) return;
            Map<Integer, String> reverse = new LinkedHashMap<>();
            for (GameValElement value : values) {
                if (value == null || value.getId() < 0 || value.getName() == null
                        || value.getName().isBlank()) {
                    continue;
                }
                reverse.putIfAbsent(value.getId(), prefix + "." + value.getName());
            }
            if (!reverse.isEmpty()) names.put(neutralType, Map.copyOf(reverse));
        } catch (RuntimeException ignored) {
            // GameVals are optional across cache families/revisions. A missing
            // or unsupported group must not make the cache unusable; external
            // RSCM/Sym mappings can still satisfy the fallback below.
        }
    }

    private static Optional<String> reverse(Map<String, Integer> table, int id) {
        if (table == null) return Optional.empty();
        return table.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() == id)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
