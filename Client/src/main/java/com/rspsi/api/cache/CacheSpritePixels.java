package com.rspsi.api.cache;

import com.rspsi.api.SpritePixels;
import com.rspsi.cache.definition.MapSceneSpriteView;

/** {@link SpritePixels} over a decoded cache sprite frame. */
public record CacheSpritePixels(int width, int height, int[] pixels) implements SpritePixels {
    public static CacheSpritePixels of(MapSceneSpriteView sprite) {
        int[] argb = sprite.argb();
        int[] rgb = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            // RuneLite sprites are RGB with 0 as the transparent key; keep true black visible.
            int color = argb[i] & 0xFFFFFF;
            rgb[i] = (argb[i] >>> 24) == 0 ? 0 : (color == 0 ? 1 : color);
        }
        return new CacheSpritePixels(sprite.width(), sprite.height(), rgb);
    }

    @Override public int getWidth() { return width; }

    @Override public int getHeight() { return height; }

    @Override public int[] getPixels() { return pixels.clone(); }
}
