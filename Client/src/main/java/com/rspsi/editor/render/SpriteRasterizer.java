package com.rspsi.editor.render;

import com.rspsi.cache.definition.MapSceneSpriteView;

import java.util.Objects;

/** Deterministic ARGB sprite composition used by minimap/editor previews. */
public final class SpriteRasterizer {
    private SpriteRasterizer() { }

    /**
     * Alpha-composites non-transparent source pixels into the destination.
     * Fully opaque indexed-cache sprites therefore follow the same fast path
     * as RuneLite's map-scene composition while real ARGB sprites retain
     * partial alpha correctly.
     */
    public static void blit(MapSceneSpriteView sprite, int[] destination,
                            int destinationWidth, int destinationHeight,
                            int originX, int originY) {
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(destination, "destination");
        if (destinationWidth <= 0 || destinationHeight <= 0
                || destination.length < destinationWidth * destinationHeight) {
            throw new IllegalArgumentException("Invalid destination raster");
        }
        int[] source = sprite.argb();
        for (int y = 0; y < sprite.height(); y++) {
            int dy = originY + y;
            if (dy < 0 || dy >= destinationHeight) continue;
            for (int x = 0; x < sprite.width(); x++) {
                int dx = originX + x;
                if (dx < 0 || dx >= destinationWidth) continue;
                int argb = source[y * sprite.width() + x];
                int alpha = argb >>> 24;
                if (alpha == 0) continue;
                int index = dy * destinationWidth + dx;
                if (alpha == 255) {
                    destination[index] = argb;
                } else {
                    destination[index] = composite(argb, destination[index]);
                }
            }
        }
    }

    private static int composite(int source, int destination) {
        int a = source >>> 24;
        int inv = 255 - a;
        int sr = (source >> 16) & 255;
        int sg = (source >> 8) & 255;
        int sb = source & 255;
        int dr = (destination >> 16) & 255;
        int dg = (destination >> 8) & 255;
        int db = destination & 255;
        int da = destination >>> 24;
        int outA = a + da * inv / 255;
        int outR = (sr * a + dr * inv) / 255;
        int outG = (sg * a + dg * inv) / 255;
        int outB = (sb * a + db * inv) / 255;
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }
}
