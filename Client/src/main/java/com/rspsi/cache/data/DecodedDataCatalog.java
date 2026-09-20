package com.rspsi.cache.data;

import com.rspsi.cache.AssetCategory;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.cache.workspace.CacheDecoderSummary;
import com.rspsi.editor.assets.AssetRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Neutral catalog of everything the cache pipeline knows how to decode.
 *
 * <p>A family can be visible from decoder metadata before a neutral typed
 * provider exists. That distinction lets Studio expose coverage honestly while
 * plugins and corpus extractors consume typed families as adapters are added.</p>
 */
public final class DecodedDataCatalog {
    private final Map<String, DecodedDataFamily> families = new LinkedHashMap<>();
    private final Map<String, DecodedDataProvider<?>> providers = new LinkedHashMap<>();

    public static DecodedDataCatalog fromAssets(AssetRepository assets) {
        Objects.requireNonNull(assets, "assets");
        DecodedDataCatalog catalog = new DecodedDataCatalog();
        catalog.registerAssetProviders(assets);
        return catalog;
    }

    public static DecodedDataCatalog fromSummary(CacheDecoderSummary summary,
                                                 AssetRepository assets) {
        Objects.requireNonNull(summary, "summary");
        DecodedDataCatalog catalog = fromAssets(assets);
        catalog.mergeSummary(summary);
        return catalog;
    }

    public synchronized void mergeSummary(CacheDecoderSummary summary) {
        Objects.requireNonNull(summary, "summary");
        family("sound-effects", "Sound effects", DecodedDataFamily.Group.AUDIO, summary.soundEffects());
        family("vorbis-sounds", "Vorbis sounds", DecodedDataFamily.Group.AUDIO, summary.vorbisSounds());
        family("music-tracks", "Music tracks", DecodedDataFamily.Group.AUDIO, summary.musicTracks());
        family("music-jingles", "Music jingles", DecodedDataFamily.Group.AUDIO, summary.musicJingles());
        family("music-patches", "Music patches", DecodedDataFamily.Group.AUDIO, summary.musicPatches());

        family("sprites", "Sprite groups", DecodedDataFamily.Group.VISUAL, summary.spriteGroups());
        family("models", "Models", DecodedDataFamily.Group.VISUAL, summary.models());
        family("textures", "Textures", DecodedDataFamily.Group.VISUAL, summary.textures());
        family("map-scenes", "Map scene sprites", DecodedDataFamily.Group.VISUAL, summary.mapScenes());
        family("fonts", "Fonts", DecodedDataFamily.Group.VISUAL, summary.fonts());

        family("maps", "Map squares", DecodedDataFamily.Group.WORLD, summary.maps());
        family("underlays", "Underlays", DecodedDataFamily.Group.WORLD, summary.underlays());
        family("overlays", "Overlays", DecodedDataFamily.Group.WORLD, summary.overlays());
        family("world-map-areas", "World map areas", DecodedDataFamily.Group.WORLD, summary.worldMapAreas());

        family("objects", "Objects", DecodedDataFamily.Group.DEFINITIONS, summary.objects());
        family("items", "Items", DecodedDataFamily.Group.DEFINITIONS, summary.items());
        family("npcs", "NPCs", DecodedDataFamily.Group.DEFINITIONS, summary.npcs());
        family("sequences", "Sequences", DecodedDataFamily.Group.DEFINITIONS, summary.sequences());
        family("spot-anims", "Spot animations", DecodedDataFamily.Group.DEFINITIONS, summary.spotAnims());
        family("identity-kits", "Identity kits", DecodedDataFamily.Group.DEFINITIONS, summary.identityKits());
        family("inventories", "Inventories", DecodedDataFamily.Group.DEFINITIONS, summary.inventories());

        family("varbits", "Varbits", DecodedDataFamily.Group.ENGINE, summary.varbits());
        family("enums", "Enums", DecodedDataFamily.Group.ENGINE, summary.enums());
        family("structs", "Structs", DecodedDataFamily.Group.ENGINE, summary.structs());
        family("interfaces", "Interfaces", DecodedDataFamily.Group.ENGINE, summary.interfaces());
        family("client-scripts", "Client scripts", DecodedDataFamily.Group.ENGINE, summary.clientScripts());
        family("db-tables", "DB tables", DecodedDataFamily.Group.ENGINE, summary.dbTables());
    }

    public synchronized <T> AutoCloseable registerProvider(DecodedDataProvider<T> provider) {
        Objects.requireNonNull(provider, "provider");
        String id = provider.familyId();
        if (providers.putIfAbsent(id, provider) != null) {
            throw new IllegalArgumentException("Duplicate decoded data provider: " + id);
        }
        DecodedDataFamily existing = families.get(id);
        int count = provider.ids().size();
        if (existing == null) {
            families.put(id, new DecodedDataFamily(id, label(id),
                    DecodedDataFamily.Group.INTEGRATION, count, true));
        } else {
            families.put(id, new DecodedDataFamily(existing.id(), existing.label(),
                    existing.group(), existing.count() >= 0 ? existing.count() : count, true));
        }
        return () -> unregisterProvider(id);
    }

    public synchronized void unregisterProvider(String familyId) {
        if (familyId == null) return;
        providers.remove(familyId);
        DecodedDataFamily family = families.get(familyId);
        if (family != null) {
            families.put(familyId, new DecodedDataFamily(family.id(), family.label(),
                    family.group(), family.count(), false));
        }
    }

    public synchronized List<DecodedDataFamily> families() {
        return List.copyOf(families.values());
    }

    public synchronized Optional<DecodedDataFamily> family(String id) {
        return Optional.ofNullable(families.get(id));
    }

    public synchronized <T> Optional<DecodedDataProvider<T>> provider(String id, Class<T> type) {
        DecodedDataProvider<?> provider = providers.get(id);
        if (provider == null || !type.isAssignableFrom(provider.valueType())) return Optional.empty();
        @SuppressWarnings("unchecked")
        DecodedDataProvider<T> cast = (DecodedDataProvider<T>) provider;
        return Optional.of(cast);
    }

    private void registerAssetProviders(AssetRepository assets) {
        register("objects", ObjectDefinitionView.class,
                () -> assets.ids(AssetCategory.OBJECTS), assets::object,
                "Objects", DecodedDataFamily.Group.DEFINITIONS);
        register("underlays", FloorDefinitionView.class,
                () -> assets.ids(AssetCategory.UNDERLAYS), assets::underlay,
                "Underlays", DecodedDataFamily.Group.WORLD);
        register("overlays", FloorDefinitionView.class,
                () -> assets.ids(AssetCategory.OVERLAYS), assets::overlay,
                "Overlays", DecodedDataFamily.Group.WORLD);
        register("textures", TextureDefinitionView.class,
                () -> assets.ids(AssetCategory.TEXTURES), assets::texture,
                "Textures", DecodedDataFamily.Group.VISUAL);
        register("models", ModelDefinitionView.class,
                () -> assets.ids(AssetCategory.MODELS), assets::model,
                "Models", DecodedDataFamily.Group.VISUAL);
        register("map-scenes", MapSceneSpriteView.class,
                () -> assets.ids(AssetCategory.MAP_SCENES), assets::mapScene,
                "Map scene sprites", DecodedDataFamily.Group.VISUAL);
        register("sequences", SequenceDefinitionView.class,
                () -> assets.ids(AssetCategory.SEQUENCES), assets::sequence,
                "Sequences", DecodedDataFamily.Group.DEFINITIONS);
        register("map-elements", MapElementDefinitionView.class,
                () -> assets.ids(AssetCategory.MAP_ELEMENTS), assets::mapElement,
                "Map elements", DecodedDataFamily.Group.WORLD);
    }

    private <T> void register(String id, Class<T> type, Supplier<List<Integer>> ids,
                              IntFunction<Optional<T>> getter, String label,
                              DecodedDataFamily.Group group) {
        List<Integer> availableIds = List.copyOf(ids.get());
        families.put(id, new DecodedDataFamily(id, label, group,
                availableIds.isEmpty() ? -1 : availableIds.size(), true));
        providers.put(id, new DecodedDataProvider<T>() {
            @Override public String familyId() { return id; }
            @Override public Class<T> valueType() { return type; }
            @Override public List<Integer> ids() { return List.copyOf(ids.get()); }
            @Override public Optional<T> get(int valueId) { return getter.apply(valueId); }
        });
    }

    private void family(String id, String label, DecodedDataFamily.Group group, int count) {
        boolean queryable = providers.containsKey(id);
        DecodedDataFamily existing = families.get(id);
        int effectiveCount = count >= 0 ? count : existing == null ? -1 : existing.count();
        families.put(id, new DecodedDataFamily(id, label, group, effectiveCount, queryable));
    }

    private static String label(String id) {
        String[] pieces = id.replace('_', '-').split("-");
        List<String> words = new ArrayList<>();
        for (String piece : pieces) {
            if (piece.isEmpty()) continue;
            words.add(Character.toUpperCase(piece.charAt(0)) + piece.substring(1));
        }
        return String.join(" ", words);
    }
}
