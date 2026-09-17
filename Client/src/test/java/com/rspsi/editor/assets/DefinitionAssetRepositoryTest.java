package com.rspsi.editor.assets;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionAssetRepositoryTest {
    @Test
    void searchesNeutralAssetDescriptorsByNameIdAndType() {
        DefinitionAssetRepository assets = new DefinitionAssetRepository(new Definitions());

        assertEquals(1, assets.search("castle").size());
        assertEquals(12, assets.search("castle").get(0).id());
        assertEquals(1, assets.search("12").size());
        assertEquals(12, assets.search("12").get(0).id());
        assertEquals(1, assets.search("overlay").size());
    }

    @Test
    void missingNamesUseStableFallbacksAndUnknownTypesStayEmpty() {
        DefinitionAssetRepository assets = new DefinitionAssetRepository(new Definitions());

        assertEquals("Object 13", assets.get(13, "object").orElseThrow().name());
        assertTrue(assets.get(12, "npc").isEmpty());
    }

    @Test
    void symbolicNamesRemainOptionalAndAreSearchable() {
        SymbolicNameProvider names = (type, id) ->
                type.equals("object") && id == 12
                        ? Optional.of("loc.castle_wall")
                        : Optional.empty();
        DefinitionAssetRepository assets = new DefinitionAssetRepository(new Definitions(), names);

        AssetDescriptor descriptor = assets.get(12, "object").orElseThrow();
        assertEquals("Castle wall", descriptor.name());
        assertEquals(Optional.of("loc.castle_wall"), descriptor.symbolicName());
        assertTrue(descriptor.details().contains("Size: 1 × 1"));
        assertEquals(List.of(descriptor), assets.search("loc.castle_wall"));
    }

    @Test
    void symbolicProviderValuesAreNormalized() {
        DefinitionAssetRepository assets = new DefinitionAssetRepository(new Definitions(),
                (type, id) -> type.equals("object") && id == 12 ? Optional.of("  loc.castle_wall  ") : null);

        assertEquals(Optional.of("loc.castle_wall"), assets.get(12, "object").orElseThrow().symbolicName());
    }

    @Test
    void repeatedSearchesReuseTheNeutralDescriptorIndex() {
        CountingDefinitions definitions = new CountingDefinitions();
        DefinitionAssetRepository assets = new DefinitionAssetRepository(definitions);

        assets.search("castle");
        assets.search("12");

        assertEquals(2, definitions.objectLookups);
        assertEquals(1, definitions.overlayLookups);
    }

    private static class Definitions implements DefinitionProvider {
        @Override public Optional<ObjectDefinitionView> object(int id) {
            if (id == 12) return Optional.of(new ObjectDefinitionView(12, "Castle wall", 1, 1, List.of(), new int[0]));
            if (id == 13) return Optional.of(new ObjectDefinitionView(13, null, 1, 1, List.of(), new int[0]));
            return Optional.empty();
        }
        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) {
            return id == 4 ? Optional.of(new FloorDefinitionView(4, -1, 0, 0, 0, 0, 0, 0)) : Optional.empty();
        }
        @Override public Optional<TextureDefinitionView> texture(int id) { return Optional.empty(); }
        @Override public List<Integer> objectIds() { return List.of(12, 13); }
        @Override public List<Integer> overlayIds() { return List.of(4); }
    }

    private static final class CountingDefinitions extends Definitions {
        private int objectLookups;
        private int overlayLookups;

        @Override public Optional<ObjectDefinitionView> object(int id) {
            objectLookups++;
            return super.object(id);
        }

        @Override public Optional<FloorDefinitionView> overlay(int id) {
            overlayLookups++;
            return super.overlay(id);
        }
    }
}
