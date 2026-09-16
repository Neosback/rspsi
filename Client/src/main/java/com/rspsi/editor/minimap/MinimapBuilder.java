package com.rspsi.editor.minimap;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.Objects;

/**
 * Builds a deterministic, cache-neutral terrain minimap raster.
 *
 * <p>This is the first semantic baseline for minimap fixtures. It selects an
 * overlay when present, otherwise averages the current underlay with its
 * cardinal neighbors. Missing definitions use a visible deterministic
 * fallback so cache problems do not silently become transparent pixels.</p>
 */
public final class MinimapBuilder {
    private static final int MISSING_COLOR = 0xFF9CA3AF;
    private static final int BLOCKED_COLOR = 0xFF1F2937;

    public MinimapImage build(WorldDocument document, int plane, DefinitionProvider definitions) {
        return build(document, plane, definitions, true);
    }

    public MinimapImage build(WorldDocument document, int plane,
                              DefinitionProvider definitions, boolean blendUnderlays) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        if (plane < 0 || plane >= document.planes()) {
            throw new IllegalArgumentException("Plane outside document: " + plane);
        }
        int[] pixels = new int[document.width() * document.length()];
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                TileSnapshot tile = document.tile(plane, x, y).snapshot();
                int index = y * document.width() + x;
                if ((tile.flags() & OsrsTileFlags.BLOCK_MAP_SQUARE) != 0) {
                    pixels[index] = BLOCKED_COLOR;
                } else if (tile.overlayId() != 0) {
                    pixels[index] = color(definitions.overlay(tile.overlayId()), tile.overlayId(), false);
                } else if (blendUnderlays) {
                    pixels[index] = blendedUnderlay(document, plane, x, y, definitions);
                } else {
                    pixels[index] = color(definitions.underlay(tile.underlayId()), tile.underlayId(), true);
                }
            }
        }
        return new MinimapImage(plane, document.width(), document.length(), pixels);
    }

    private static int blendedUnderlay(WorldDocument document, int plane, int x, int y,
                                       DefinitionProvider definitions) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int samples = 0;
        for (int offset = 0; offset < 5; offset++) {
            int sampleX = x;
            int sampleY = y;
            if (offset == 1) sampleX--;
            if (offset == 2) sampleX++;
            if (offset == 3) sampleY--;
            if (offset == 4) sampleY++;
            if (sampleX < 0 || sampleX >= document.width()
                    || sampleY < 0 || sampleY >= document.length()) continue;
            TileSnapshot sample = document.tile(plane, sampleX, sampleY).snapshot();
            int rgb = color(definitions.underlay(sample.underlayId()), sample.underlayId(), true);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            samples++;
        }
        if (samples == 0) return MISSING_COLOR;
        return 0xFF000000 | ((red / samples) << 16) | ((green / samples) << 8) | (blue / samples);
    }

    private static int color(java.util.Optional<FloorDefinitionView> definition,
                             int id, boolean underlay) {
        if (definition.isPresent()) return 0xFF000000 | (definition.get().rgb() & 0xFFFFFF);
        if (id == 0) return 0xFF000000;
        int seed = id * (underlay ? 73 : 97);
        int red = 64 + Math.floorMod(seed, 128);
        int green = 64 + Math.floorMod(seed * 3, 128);
        int blue = 64 + Math.floorMod(seed * 7, 128);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
