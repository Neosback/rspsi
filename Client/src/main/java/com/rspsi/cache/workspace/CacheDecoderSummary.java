package com.rspsi.cache.workspace;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable report summarizing decoded cache contents across all FileStore
 * decoders and cache index archives.
 */
public record CacheDecoderSummary(
        int revision,
        String backendName,
        int totalIndices,
        int totalArchives,
        // Audio
        int soundEffects,
        int vorbisSounds,
        int musicTracks,
        int musicJingles,
        int musicPatches,
        // Visuals
        int spriteGroups,
        int totalSubSprites,
        int models,
        int textures,
        int mapScenes,
        int fonts,
        // World & Environment
        int maps,
        int underlays,
        int overlays,
        int worldMapAreas,
        // Game Definitions
        int objects,
        int items,
        int npcs,
        int sequences,
        int spotAnims,
        int identityKits,
        int inventories,
        // Engine & Scripting
        int varbits,
        int enums,
        int structs,
        int interfaces,
        int clientScripts,
        int dbTables,
        // Indices breakdown
        List<IndexEntry> indices,
        // Diagnostics
        boolean allDecodersPassed,
        List<String> failures
) {
    public CacheDecoderSummary {
        backendName = backendName == null ? "OpenRune FileStore" : backendName;
        indices = indices == null ? List.of() : List.copyOf(indices);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public record IndexEntry(int id, String name, int archiveCount) {
        public IndexEntry {
            name = Objects.requireNonNull(name, "name");
        }
    }

    public int totalAudioCount() {
        return soundEffects + vorbisSounds + musicTracks + musicJingles + musicPatches;
    }

    public int totalDefinitionsCount() {
        return objects + items + npcs + sequences + spotAnims + identityKits + inventories
                + underlays + overlays + textures;
    }

    public static CacheDecoderSummary empty() {
        return new CacheDecoderSummary(
                0, "None", 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                Collections.emptyList(),
                true,
                Collections.emptyList()
        );
    }
}
