package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds OSRS HSL/material inputs without exposing cache-library types. */
public final class TerrainAppearanceBuilder {
    private static final int BLEND_RADIUS = 5;

    public Map<TileCoordinate, TerrainAppearance> build(WorldDocument document,
                                                         DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Map<TileCoordinate, TerrainAppearance> result = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    result.put(new TileCoordinate(plane, x, y), appearance(document, definitions,
                            plane, x, y, tile));
                }
            }
        }
        return result;
    }

    private TerrainAppearance appearance(WorldDocument document, DefinitionProvider definitions,
                                         int plane, int x, int y, TileSnapshot tile) {
        int underlayHsl = blendedUnderlay(document, definitions, plane, x, y);
        FloorDefinitionView overlay = tile.overlayId() <= 0
                ? null : definitions.overlay(tile.overlayId() - 1).orElse(null);
        int overlayHsl = overlay == null ? -1 : pack(overlay.hue(), overlay.saturation(), overlay.luminance());
        int secondaryHsl = overlay == null || overlay.secondaryRgb() < 0
                ? -1 : pack(overlay.secondaryHue(), overlay.secondarySaturation(), overlay.secondaryLuminance());
        int textureId = overlay == null ? -1 : overlay.texture();
        int textureHsl = -1;
        if (textureId >= 0) {
            TextureDefinitionView texture = definitions.texture(textureId).orElse(null);
            textureHsl = texture == null ? -1 : texture.averageHsl();
        }
        boolean hidden = overlay != null && (overlay.rgb() & 0xFFFFFF) == 0xFF00FF;
        return new TerrainAppearance(underlayHsl, overlayHsl, secondaryHsl, textureId,
                textureHsl, tile.overlayShape(), tile.overlayRotation() & 3, hidden);
    }

    private int blendedUnderlay(WorldDocument document, DefinitionProvider definitions,
                                int plane, int x, int y) {
        long saturation = 0;
        long luminance = 0;
        long weightedHue = 0;
        long chroma = 0;
        int count = 0;
        for (int dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx++) {
            for (int dy = -BLEND_RADIUS; dy <= BLEND_RADIUS; dy++) {
                int sampleX = x + dx;
                int sampleY = y + dy;
                if (sampleX < 0 || sampleX >= document.width()
                        || sampleY < 0 || sampleY >= document.length()) continue;
                int id = document.tile(plane, sampleX, sampleY).snapshot().underlayId();
                if (id <= 0) continue;
                FloorDefinitionView floor = definitions.underlay(id - 1).orElse(null);
                if (floor == null || floor.chroma() <= 0) continue;
                saturation += floor.saturation();
                luminance += floor.luminance();
                weightedHue += floor.weightedHue();
                chroma += floor.chroma();
                count++;
            }
        }
        if (chroma == 0) return 0;
        int averageHue = clamp((int) Math.round((double) weightedHue / chroma), 0, 63);
        int averageSaturation = clamp((int) Math.round((double) saturation / Math.max(1, count)), 0, 7);
        int averageLuminance = clamp((int) Math.round((double) luminance / Math.max(1, count)), 0, 127);
        return pack(averageHue, averageSaturation, averageLuminance);
    }

    private static int pack(int hue, int saturation, int luminance) {
        return (clamp(hue, 0, 63) << 10)
                | (clamp(saturation, 0, 7) << 7)
                | clamp(luminance, 0, 127);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
