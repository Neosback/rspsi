package com.rspsi.editor.render;

import com.rspsi.cache.definition.MapSceneSpriteView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpriteRasterizerTest {
    @Test
    void clipsAndCompositesArgbPixels() {
        int[] target = new int[4 * 4];
        java.util.Arrays.fill(target, 0xFF000000);
        MapSceneSpriteView sprite = new MapSceneSpriteView(
                1, 2, 2, 0, 0,
                new int[]{0xFFFFFFFF, 0x00000000, 0x80FF0000, 0xFF00FF00});

        SpriteRasterizer.blit(sprite, target, 4, 4, 1, 1);

        assertEquals(0xFFFFFFFF, target[1 + 1 * 4]);
        assertEquals(0xFF000000, target[2 + 1 * 4]);
        assertEquals(0xFF800000, target[1 + 2 * 4]);
        assertEquals(0xFF00FF00, target[2 + 2 * 4]);
    }
}
