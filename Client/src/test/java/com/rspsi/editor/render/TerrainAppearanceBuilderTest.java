package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainAppearanceBuilderTest {
    @Test
    void preservesShapeRotationTextureAndPackedHslInputs() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 1, 2, 7, 3, 0, List.of()));

        TerrainAppearance appearance = new TerrainAppearanceBuilder()
                .build(document, definitions())
                .get(new TileCoordinate(0, 0, 0));

        assertEquals((12 << 10) | (5 << 7) | 64, appearance.underlayHsl());
        assertEquals((20 << 10) | (4 << 7) | 80, appearance.overlayHsl());
        assertEquals(9, appearance.textureId());
        assertEquals(0x1234, appearance.textureAverageHsl());
        assertEquals(7, appearance.shape());
        assertEquals(3, appearance.rotation());
    }

    @Test
    void recognizesTheOsrsHiddenOverlaySentinel() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 3, 0, 0, 0, List.of()));

        TerrainAppearance appearance = new TerrainAppearanceBuilder()
                .build(document, definitions())
                .get(new TileCoordinate(0, 0, 0));

        assertTrue(appearance.overlayHidden());
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 0 ? Optional.of(new FloorDefinitionView(
                        0, -1, 0x102030, 12, 5, 64, 96, 8)) : Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 1 ? Optional.of(new FloorDefinitionView(
                        1, 9, 0xA0B0C0, 20, 4, 80, 20, 8,
                        -1, 0, 0, 0)) : id == 2 ? Optional.of(new FloorDefinitionView(
                        2, -1, 0xFF00FF, 0, 0, 0, 0, 1)) : Optional.empty();
            }

            @Override public Optional<TextureDefinitionView> texture(int id) {
                return id == 9 ? Optional.of(new TextureDefinitionView(
                        9, false, 4, 0, 0x1234, 0, 0, false)) : Optional.empty();
            }
        };
    }
}
