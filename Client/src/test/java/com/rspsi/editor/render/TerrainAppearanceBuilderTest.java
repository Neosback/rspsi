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

        // Floor definitions are blended in the 0..255 source domain, then
        // packed using the client's 6/3/7-bit HSL representation.
        assertEquals((16 << 10) | (6 << 7) | 48, appearance.underlayHsl());
        // Textured overlays use light-only render vertices; their average HSL
        // is retained separately for minimap/fallback use.
        assertEquals(-1, appearance.overlayHsl());
        assertEquals(0x1234, appearance.overlayMinimapHsl());
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
                        0, -1, 0x102030, 64, 192, 96, 64, 256)) : Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 1 ? Optional.of(new FloorDefinitionView(
                        1, 9, 0xA0B0C0, 80, 128, 200, 20, 8,
                        -1, 0, 0, 0)) : id == 2 ? Optional.of(new FloorDefinitionView(
                        2, -1, 0xFF00FF, 0, 0, 0, 0, 1)) : Optional.empty();
            }

            @Override public Optional<TextureDefinitionView> texture(int id) {
                return id == 9 ? Optional.of(new TextureDefinitionView(
                        9, false, 4, 0, 0x1234, 0, 0, false)) : Optional.empty();
            }
        };
    }

    @Test
    void texturedOverlayRecoversHueAndSaturationWhenTheProviderHasNoAverageHsl() {
        int[] pixels = new int[16];
        java.util.Arrays.fill(pixels, 0x62A992);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 0 ? Optional.of(new FloorDefinitionView(
                        0, -1, 0x102030, 64, 192, 96, 64, 256)) : Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                // Tile overlay ids are one-based in the scene document, so the
                // overlay-id-1 tile below resolves this definition.
                return id == 0 ? Optional.of(new FloorDefinitionView(
                        0, 25, 0, 0, 0, 0, 0, 1, -1, 0, 0, 0)) : Optional.empty();
            }

            @Override public Optional<TextureDefinitionView> texture(int id) {
                // A provider that exposes the definition but no average HSL is
                // exactly what the 3.0.2 texture view does today.
                return id == 25 ? Optional.of(new TextureDefinitionView(
                        25, false, 25, 0x0071AD, -1, 0, 0, false)) : Optional.empty();
            }

            @Override public Optional<int[]> texturePixels(int id, double brightness, int size) {
                return id == 25 ? Optional.of(pixels) : Optional.empty();
            }
        };
        WorldDocument document = new WorldDocument(11, 11, 1);
        document.tile(0, 5, 5).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 1, 0, 0, 0, List.of()));

        TerrainAppearance appearance = new TerrainAppearanceBuilder()
                .buildTile(document, definitions, 0, 5, 5);

        assertEquals(25, appearance.textureId());
        assertEquals(TextureAverageColor.packedHslFromRgb(0x62A992) & 0xFF80,
                appearance.textureAverageHsl() & 0xFF80,
                "a textured overlay without a provider average HSL must still carry "
                        + "the texture's hue and saturation, or the palette resolves "
                        + "every texel through the grey axis");
    }

    @Test
    void usesTheAsymmetricClientBlendWindow() {
        WorldDocument document = new WorldDocument(11, 1, 1);
        for (int x = 0; x < 11; x++) {
            document.tile(0, x, 0).restore(new TileSnapshot(
                    0, x, 0, 0, x == 0 ? 1 : 2, 0, 0, 0, 0, List.of()));
        }

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                if (id == 0) {
                    return Optional.of(new FloorDefinitionView(0, -1, 0,
                            0, 128, 96, 0, 256));
                }
                if (id == 1) {
                    return Optional.of(new FloorDefinitionView(1, -1, 0,
                            128, 128, 96, 128, 256));
                }
                return Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        // The window is x-4..x+5. This also guards against accidentally
        // reverting to the symmetric -5..+5 loop.
        TerrainAppearance appearance = new TerrainAppearanceBuilder()
                .build(document, definitions)
                .get(new TileCoordinate(0, 5, 0));

        assertEquals(OsrsTerrainColorMath.packHsl(128, 128, 96), appearance.underlayHsl());
    }
}
