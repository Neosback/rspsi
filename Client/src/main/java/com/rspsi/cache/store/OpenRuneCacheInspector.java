package com.rspsi.cache.store;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.workspace.CacheDecoderSummary;
import com.rspsi.cache.workspace.CacheDecoderSummary.IndexEntry;
import dev.openrune.OsrsCacheProvider;
import dev.openrune.cache.filestore.definition.SpriteDecoder;
import dev.openrune.definition.type.SpriteType;
import dev.openrune.filesystem.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static dev.openrune.cache.ArchiveIndexKt.*;

/**
 * Validates and decodes all cache indices and definitions to verify that the
 * selected FileStore cache is 100% understood and read correctly.
 */
public final class OpenRuneCacheInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRuneCacheInspector.class);

    private static final Map<Integer, String> INDEX_NAMES = Map.ofEntries(
            Map.entry(ANIMATIONS, "Animations (Frames)"),
            Map.entry(SKELETONS, "Skeletons"),
            Map.entry(CONFIGS, "Configs (Game Definitions)"),
            Map.entry(INTERFACES, "Interfaces & Components"),
            Map.entry(SOUNDEFFECTS, "Sound Effects (Synth/Wave)"),
            Map.entry(MAPS, "Maps & Regions"),
            Map.entry(MUSIC_TRACKS, "Music Tracks (MIDI)"),
            Map.entry(MODELS, "3D Models"),
            Map.entry(SPRITES, "Sprites & Graphics"),
            Map.entry(TEXTURES, "Textures & Materials"),
            Map.entry(BINARY, "Binary Data"),
            Map.entry(MUSIC_JINGLES, "Music Jingles"),
            Map.entry(CLIENTSCRIPT, "ClientScripts (CS2)"),
            Map.entry(FONTS, "Fonts"),
            Map.entry(VORBIS, "Vorbis Audio Samples"),
            Map.entry(MUSIC_PATCHES, "Music Patches (SoundFont)"),
            Map.entry(NOT_USED, "Reserved / Unused"),
            Map.entry(DEFAULTS, "Client Defaults"),
            Map.entry(WORLDMAP_GEOGRAPHY, "World Map Geography"),
            Map.entry(WORLDMAPAREAS, "World Map Areas"),
            Map.entry(WORLDMAP_GROUND, "World Map Ground"),
            Map.entry(DBTABLEINDEX, "DB Table Index"),
            Map.entry(ANIMAYAS, "Animayas (Skeletal)"),
            Map.entry(INTERFACES2, "Interfaces 2"),
            Map.entry(GAMEVALS, "GameVals / Entities")
    );

    private OpenRuneCacheInspector() {}

    public static CacheDecoderSummary inspect(Cache cache, int revision,
                                              String backendName,
                                              DefinitionProvider definitions) {
        Objects.requireNonNull(cache, "cache");
        List<String> failures = new ArrayList<>();

        // 1. Index archive counting
        int totalArchives = 0;
        List<IndexEntry> indexEntries = new ArrayList<>();
        int[] indexIds = cache.indices();
        Arrays.sort(indexIds);

        Map<Integer, Integer> archiveCountByIndex = new HashMap<>();
        for (int idx : indexIds) {
            int count = 0;
            try {
                int[] archives = cache.archives(idx);
                count = archives == null ? 0 : archives.length;
            } catch (RuntimeException ex) {
                failures.add("Index " + idx + " scan error: " + ex.getMessage());
            }
            archiveCountByIndex.put(idx, count);
            totalArchives += count;
            String name = INDEX_NAMES.getOrDefault(idx, "Index " + idx);
            indexEntries.add(new IndexEntry(idx, name, count));
        }

        // 2. Audio counts from indices
        int soundEffects = archiveCountByIndex.getOrDefault(SOUNDEFFECTS, 0);
        int vorbis = archiveCountByIndex.getOrDefault(VORBIS, 0);
        int musicTracks = archiveCountByIndex.getOrDefault(MUSIC_TRACKS, 0);
        int musicJingles = archiveCountByIndex.getOrDefault(MUSIC_JINGLES, 0);
        int musicPatches = archiveCountByIndex.getOrDefault(MUSIC_PATCHES, 0);

        // 3. Models & Maps
        int models = archiveCountByIndex.getOrDefault(MODELS, 0);
        int maps = archiveCountByIndex.getOrDefault(MAPS, 0);
        int interfaces = archiveCountByIndex.getOrDefault(INTERFACES, 0);
        int clientScripts = archiveCountByIndex.getOrDefault(CLIENTSCRIPT, 0);
        int fonts = archiveCountByIndex.getOrDefault(FONTS, 0);
        int worldMapAreas = archiveCountByIndex.getOrDefault(WORLDMAPAREAS, 0);
        int dbTables = archiveCountByIndex.getOrDefault(DBTABLEINDEX, 0);

        // 4. Sprites decoder
        int spriteGroups = 0;
        int totalSubSprites = 0;
        try {
            Map<Integer, SpriteType> sprites = new HashMap<>();
            new SpriteDecoder().load(cache, sprites);
            spriteGroups = sprites.size();
            for (SpriteType st : sprites.values()) {
                if (st != null && st.getSprites() != null) {
                    totalSubSprites += st.getSprites().length;
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("SpriteDecoder failed: {}", ex.getMessage());
            failures.add("SpriteDecoder: " + ex.getMessage());
        }

        // 5. Definitions: reuse eager loads if available, otherwise decode
        int objectCount = definitions != null ? definitions.objectIds().size() : 0;
        int underlayCount = definitions != null ? definitions.underlayIds().size() : 0;
        int overlayCount = definitions != null ? definitions.overlayIds().size() : 0;
        int textureCount = definitions != null ? definitions.textureIds().size() : 0;
        int mapSceneCount = definitions != null ? definitions.mapSceneIds().size() : 0;

        if (objectCount == 0) {
            objectCount = safeDecodeCount("ObjectDecoder", failures, () -> {
                Map<Integer, dev.openrune.definition.type.ObjectType> map = new HashMap<>();
                new OsrsCacheProvider.ObjectDecoder(revision).load(cache, map);
                return map.size();
            });
        }
        if (underlayCount == 0) {
            underlayCount = safeDecodeCount("UnderlayDecoder", failures, () -> {
                Map<Integer, dev.openrune.definition.type.UnderlayType> map = new HashMap<>();
                new OsrsCacheProvider.UnderlayDecoder().load(cache, map);
                return map.size();
            });
        }
        if (overlayCount == 0) {
            overlayCount = safeDecodeCount("OverlayDecoder", failures, () -> {
                Map<Integer, dev.openrune.definition.type.OverlayType> map = new HashMap<>();
                new OsrsCacheProvider.OverlayDecoder().load(cache, map);
                return map.size();
            });
        }
        if (textureCount == 0) {
            textureCount = safeDecodeCount("TextureDecoder", failures, () -> {
                return OpenRuneTextureDefinitionDecoder.decode(cache, revision).definitions().size();
            });
        }

        // 6. Additional game definition decoders
        int itemCount = safeDecodeCount("ItemDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.ItemType> map = new HashMap<>();
            new OsrsCacheProvider.ItemDecoder(revision).load(cache, map);
            return map.size();
        });

        int npcCount = safeDecodeCount("NPCDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.NpcType> map = new HashMap<>();
            new OsrsCacheProvider.NPCDecoder(revision).load(cache, map);
            return map.size();
        });

        int sequenceCount = safeDecodeCount("SequenceDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.SequenceType> map = new HashMap<>();
            new OsrsCacheProvider.SequenceDecoder(revision).load(cache, map);
            return map.size();
        });

        int spotAnimCount = safeDecodeCount("SpotAnimDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.SpotAnimType> map = new HashMap<>();
            new OsrsCacheProvider.SpotAnimDecoder(revision).load(cache, map);
            return map.size();
        });

        int identityKitCount = safeDecodeCount("IdentityKitDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.IdentityKitType> map = new HashMap<>();
            new OsrsCacheProvider.IdentityKitDecoder(revision).load(cache, map);
            return map.size();
        });

        int inventoryCount = safeDecodeCount("InventoryDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.InventoryType> map = new HashMap<>();
            new OsrsCacheProvider.InventoryDecoder().load(cache, map);
            return map.size();
        });

        int varbitCount = safeDecodeCount("VarBitDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.VarBitType> map = new HashMap<>();
            new OsrsCacheProvider.VarBitDecoder().load(cache, map);
            return map.size();
        });

        int enumCount = safeDecodeCount("EnumDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.EnumType> map = new HashMap<>();
            new OsrsCacheProvider.EnumDecoder().load(cache, map);
            return map.size();
        });

        int structCount = safeDecodeCount("StructDecoder", failures, () -> {
            Map<Integer, dev.openrune.definition.type.StructType> map = new HashMap<>();
            new OsrsCacheProvider.StructDecoder().load(cache, map);
            return map.size();
        });

        boolean passed = failures.isEmpty();
        LOGGER.info("Cache inspection complete: {} indices, {} archives, {} definitions, {} audio tracks, status: {}",
                indexIds.length, totalArchives, objectCount + itemCount + npcCount,
                soundEffects + vorbis + musicTracks, passed ? "ALL PASS" : "DIAGNOSTICS");

        return new CacheDecoderSummary(
                revision,
                backendName,
                indexIds.length,
                totalArchives,
                soundEffects,
                vorbis,
                musicTracks,
                musicJingles,
                musicPatches,
                spriteGroups,
                totalSubSprites,
                models,
                textureCount,
                mapSceneCount,
                fonts,
                maps,
                underlayCount,
                overlayCount,
                worldMapAreas,
                objectCount,
                itemCount,
                npcCount,
                sequenceCount,
                spotAnimCount,
                identityKitCount,
                inventoryCount,
                varbitCount,
                enumCount,
                structCount,
                interfaces,
                clientScripts,
                dbTables,
                indexEntries,
                passed,
                failures
        );
    }

    private static int safeDecodeCount(String name, List<String> failures, java.util.function.IntSupplier decode) {
        try {
            return decode.getAsInt();
        } catch (RuntimeException ex) {
            LOGGER.warn("{} execution failed: {}", name, ex.getMessage());
            failures.add(name + ": " + ex.getMessage());
            return 0;
        }
    }
}
