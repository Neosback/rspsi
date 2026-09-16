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

    // The 4x4 masks and rotation permutations are the client-side shaped-tile
    // raster contract used by TSPS. They are kept here as data, not coupled to
    // either the legacy renderer or a frontend image API.
    private static final int[][] TILE_SHAPE = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1},
            {1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1},
            {0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0},
            {1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1},
            {1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1}
    };

    private static final int[][] TILE_ROTATION = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {12, 8, 4, 0, 13, 9, 5, 1, 14, 10, 6, 2, 15, 11, 7, 3},
            {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0},
            {3, 7, 11, 15, 2, 6, 10, 14, 1, 5, 9, 13, 0, 4, 8, 12}
    };

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

    /**
     * Builds a TSPS-compatible shaped-tile raster at four pixels per tile.
     * This is intentionally additive: {@link #build(WorldDocument, int,
     * DefinitionProvider)} remains the stable one-pixel semantic baseline.
     * Colors use the OSRS HSL palette and radius-5 underlay blend when the
     * neutral definitions provide the required metadata, with deterministic
     * RGB fallback for incomplete definitions. Palette/mapscene parity remains
     * separate verification work.
     */
    public MinimapImage buildShaped(WorldDocument document, int plane,
                                    DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        if (plane < 0 || plane >= document.planes()) {
            throw new IllegalArgumentException("Plane outside document: " + plane);
        }

        int width = document.width() * 4;
        int height = document.length() * 4;
        int[] pixels = new int[width * height];
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                TileSnapshot tile = document.tile(plane, x, y).snapshot();
                int underlay = blendedOsrsUnderlay(document, plane, x, y, definitions);
                int overlay = osrsColor(definitions.overlay(tile.overlayId()), tile.overlayId(), false);
                int shape = tile.overlayId() == 0 ? 0 : tile.overlayShape() + 1;
                if (shape < 0 || shape >= TILE_SHAPE.length) {
                    throw new IllegalArgumentException("Encoded overlay shape must be between 0 and 11");
                }
                int rotation = tile.overlayRotation();
                if (rotation < 0 || rotation >= TILE_ROTATION.length) {
                    throw new IllegalArgumentException("Tile rotation must be between 0 and 3");
                }
                if ((tile.flags() & OsrsTileFlags.BLOCK_MAP_SQUARE) != 0) {
                    fillTile(pixels, width, x, y, BLOCKED_COLOR);
                    continue;
                }
                int[] mask = TILE_SHAPE[shape];
                int[] permutation = TILE_ROTATION[rotation];
                for (int row = 0; row < 4; row++) {
                    for (int column = 0; column < 4; column++) {
                        int maskIndex = row * 4 + column;
                        int rgb = mask[permutation[maskIndex]] == 0 ? underlay : overlay;
                        pixels[(y * 4 + row) * width + x * 4 + column] = rgb;
                    }
                }
            }
        }
        return new MinimapImage(plane, width, height, pixels);
    }

    private static void fillTile(int[] pixels, int width, int tileX, int tileY, int color) {
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                pixels[(tileY * 4 + row) * width + tileX * 4 + column] = color;
            }
        }
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

    /** Uses the OSRS radius-5 HSL blend when neutral definitions provide it. */
    private static int blendedOsrsUnderlay(WorldDocument document, int plane, int x, int y,
                                           DefinitionProvider definitions) {
        int weightedHue = 0;
        int hueMultiplier = 0;
        int saturation = 0;
        int luminance = 0;
        int samples = 0;
        for (int sampleX = Math.max(0, x - 5); sampleX <= Math.min(document.width() - 1, x + 5); sampleX++) {
            for (int sampleY = Math.max(0, y - 5); sampleY <= Math.min(document.length() - 1, y + 5); sampleY++) {
                TileSnapshot sample = document.tile(plane, sampleX, sampleY).snapshot();
                if (sample.underlayId() <= 0) continue;
                java.util.Optional<FloorDefinitionView> definition = definitions.underlay(sample.underlayId());
                if (definition.isEmpty() || definition.get().chroma() <= 0) continue;
                FloorDefinitionView floor = definition.get();
                weightedHue += floor.weightedHue();
                hueMultiplier += floor.chroma();
                saturation += floor.saturation();
                luminance += floor.luminance();
                samples++;
            }
        }
        if (samples > 0 && hueMultiplier > 0) {
            int hue = weightedHue * 256 / hueMultiplier;
            int hsl = packHsl(hue, saturation / samples, luminance / samples);
            return 0xFF000000 | hslToRgb(adjustUnderlayLight(hsl, 96));
        }
        return blendedUnderlay(document, plane, x, y, definitions);
    }

    private static int osrsColor(java.util.Optional<FloorDefinitionView> definition,
                                  int id, boolean underlay) {
        if (definition.isPresent()) {
            FloorDefinitionView floor = definition.get();
            boolean hasHsl = floor.hue() != 0 || floor.saturation() != 0
                    || floor.luminance() != 0 || floor.chroma() > 0;
            if (hasHsl) {
                int hsl = packHsl(floor.hue(), floor.saturation(), floor.luminance());
                return 0xFF000000 | hslToRgb(adjustOverlayLight(hsl, 96));
            }
            return 0xFF000000 | (floor.rgb() & 0xFFFFFF);
        }
        return color(definition, id, underlay);
    }

    private static int packHsl(int hue, int saturation, int luminance) {
        if (luminance > 179) saturation /= 2;
        if (luminance > 192) saturation /= 2;
        if (luminance > 217) saturation /= 2;
        if (luminance > 243) saturation /= 2;
        return (hue / 4 << 10) + (saturation / 32 << 7) + luminance / 2;
    }

    private static int adjustUnderlayLight(int hsl, int light) {
        light = (hsl & 127) * light >> 7;
        return (hsl & 0xFF80) + clampLight(light);
    }

    private static int adjustOverlayLight(int hsl, int light) {
        light = (hsl & 127) * light >> 7;
        return (hsl & 0xFF80) + clampLight(light);
    }

    private static int clampLight(int light) {
        return Math.max(2, Math.min(126, light));
    }

    /** Port of the RuneScape HSL palette used by TSPS for scene colors. */
    private static int hslToRgb(int hsl) {
        double hue = 0.0078125 + ((hsl >> 10) & 63) / 64.0;
        double saturation = 0.0625 + ((hsl >> 7) & 7) / 8.0;
        double luminance = (hsl & 127) / 128.0;
        double red = luminance;
        double green = luminance;
        double blue = luminance;
        if (saturation != 0.0) {
            double max = luminance < 0.5
                    ? luminance * (1.0 + saturation)
                    : luminance + saturation - luminance * saturation;
            double min = 2.0 * luminance - max;
            red = hueChannel(min, max, hue + 1.0 / 3.0);
            green = hueChannel(min, max, hue);
            blue = hueChannel(min, max, hue - 1.0 / 3.0);
        }
        return (brighten(red) << 16) | (brighten(green) << 8) | brighten(blue);
    }

    private static double hueChannel(double min, double max, double value) {
        if (value < 0.0) value++;
        if (value > 1.0) value--;
        if (6.0 * value < 1.0) return min + (max - min) * 6.0 * value;
        if (2.0 * value < 1.0) return max;
        if (3.0 * value < 2.0) return min + (max - min) * (2.0 / 3.0 - value) * 6.0;
        return min;
    }

    private static int brighten(double channel) {
        int raw = (int) (channel * 256.0);
        return (int) (Math.pow(raw / 256.0, 0.8) * 256.0);
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
