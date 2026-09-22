package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderTextureResourceTest {
    @Test
    void collectsReferencedTerrainAndModelTexturesWithDecodedPixels() {
        TerrainRenderVertex vertex = new TerrainRenderVertex(0, 0, 0, 0, 0, 0);
        TerrainRenderPacket terrain = new TerrainRenderPacket(
                new TileCoordinate(0, 0, 0),
                List.of(vertex, vertex, vertex),
                List.of(new TerrainRenderFace(0, 1, 2, 1, 4, 255, 0)),
                0, 0, 4, 1, 2, false, false, 2);
        ModelTriangle triangle = new ModelTriangle(0, 1, 2, 7, 7, 7,
                8, 0, 0, 0);
        ModelRenderPacket model = new ModelRenderPacket(
                new TileCoordinate(0, 0, 0), 99, com.rspsi.editor.model.ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 0, 0, 0, 0, 0),
                        new ModelVertex(1, 0, 0, 0, 0, 0, 0, 0),
                        new ModelVertex(0, 1, 0, 0, 0, 0, 0, 0)),
                List.of(triangle), List.of(), -1,
                0, 0, 0, 1, 1, 1, false, false);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<TextureDefinitionView> texture(int id) {
                return Optional.of(new TextureDefinitionView(id, false, id, 0x123456,
                        0, 0, false));
            }
            @Override public Optional<int[]> texturePixels(int id, double brightness, int textureSize) {
                return Optional.of(new int[]{id, id + 1, id + 2, id + 3});
            }
        };

        Map<Integer, RenderTextureResource> resources = RenderTextureResourceBuilder.build(
                definitions, LightingProfile.osrs(), List.of(terrain), List.of(model));

        assertEquals(List.of(4, 8), resources.keySet().stream().sorted().toList());
        assertTrue(resources.get(4).hasPixels());
        assertEquals(2, resources.get(4).width());
        assertArrayEquals(new int[]{4, 5, 6, 7}, resources.get(4).pixels());
        assertEquals(RenderTextureResource.PixelStatus.AVAILABLE, resources.get(8).pixelStatus());
    }

    @Test
    void reportsUnavailableAndInvalidProviderResultsWithoutThrowing() {
        TextureDefinitionView definition = new TextureDefinitionView(4, false, 4, 0,
                0, 0, false);
        assertFalse(RenderTextureResource.unavailable(4, definition, "missing").hasPixels());
        RenderTextureResource invalid = RenderTextureResource.from(4, definition, 128,
                new int[]{1, 2, 3});
        assertEquals(RenderTextureResource.PixelStatus.INVALID, invalid.pixelStatus());
        assertTrue(invalid.diagnostic().contains("square"));
    }

    @Test
    void averageColorFallbackIsGpuRenderableButNotDecodedCoverage() {
        TextureDefinitionView definition = new TextureDefinitionView(6, false, 6,
                0x345678, 0, 0, false);

        RenderTextureResource fallback = RenderTextureResource.averageColorFallback(
                6, definition, "missing sprite");

        assertFalse(fallback.hasPixels());
        assertTrue(fallback.hasGpuPixels());
        assertEquals(RenderTextureResource.PixelStatus.AVERAGE_COLOR_FALLBACK,
                fallback.pixelStatus());
        assertArrayEquals(new int[]{0x345678}, fallback.pixels());
    }

    @Test
    void argbPixelsWithPartialAlphaDeclareAnAlphaChannel() {
        TextureDefinitionView definition = new TextureDefinitionView(17, true, 17, 0x112233,
                0, 0, false);

        // Water-style texture: opaque, half and fully transparent texels.
        RenderTextureResource water = new RenderTextureResource(17, definition, 2, 2,
                new int[]{0xFF3A5F9E, 0x803A5F9E, 0x003A5F9E, 0x003A5F9E},
                RenderTextureResource.PixelStatus.AVAILABLE, "");

        assertTrue(water.usesAlphaChannel());
        assertEquals(0xFF, RenderTextureResource.alphaOf(0xFF3A5F9E));
        assertEquals(0x80, RenderTextureResource.alphaOf(0x803A5F9E));
        assertEquals(0x00, RenderTextureResource.alphaOf(0x003A5F9E));
    }

    @Test
    void plainRgbAndFullyOpaquePixelsKeepTheBinaryCutoutConvention() {
        TextureDefinitionView definition = new TextureDefinitionView(4, false, 4, 0,
                0, 0, false);

        // Plain RGB assembled as (r << 16) | (g << 8) | b leaves the top byte
        // zero, which must not be mistaken for a transparent alpha channel.
        RenderTextureResource plain = new RenderTextureResource(4, definition, 1, 2,
                new int[]{0x000000, 0x1A2B3C},
                RenderTextureResource.PixelStatus.AVAILABLE, "");
        RenderTextureResource opaque = new RenderTextureResource(4, definition, 1, 2,
                new int[]{0xFF1A2B3C, 0xFF445566},
                RenderTextureResource.PixelStatus.AVAILABLE, "");

        assertFalse(plain.usesAlphaChannel());
        assertFalse(opaque.usesAlphaChannel());
    }

    @Test
    void fallbackResourcesNeverClaimAnAlphaChannel() {
        TextureDefinitionView definition = new TextureDefinitionView(6, true, 6,
                0x345678, 0, 0, false);

        assertFalse(RenderTextureResource.averageColorFallback(6, definition, "missing sprite")
                .usesAlphaChannel());
    }
}
