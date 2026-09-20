package com.rspsi.cache.data;

import com.rspsi.editor.assets.AssetRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DecodedDataCatalogTest {
    @Test
    void distinguishesKnownFamiliesFromTypedProviders() throws Exception {
        DecodedDataCatalog catalog = DecodedDataCatalog.fromAssets(AssetRepository.empty());

        assertTrue(catalog.family("objects").orElseThrow().queryable());
        assertTrue(catalog.provider("objects",
                com.rspsi.cache.definition.ObjectDefinitionView.class).isPresent());

        DecodedDataProvider<String> provider = new DecodedDataProvider<>() {
            @Override public String familyId() { return "custom-biomes"; }
            @Override public Class<String> valueType() { return String.class; }
            @Override public List<Integer> ids() { return List.of(1, 2); }
            @Override public Optional<String> get(int id) {
                return id == 1 ? Optional.of("swamp") : Optional.empty();
            }
        };

        AutoCloseable registration = catalog.registerProvider(provider);
        assertEquals(2, catalog.family("custom-biomes").orElseThrow().count());
        assertEquals("swamp", catalog.provider("custom-biomes", String.class)
                .orElseThrow().get(1).orElseThrow());

        registration.close();
        assertFalse(catalog.family("custom-biomes").orElseThrow().queryable());
    }
}
