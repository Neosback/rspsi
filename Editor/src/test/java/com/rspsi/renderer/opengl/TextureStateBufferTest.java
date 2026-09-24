package com.rspsi.renderer.opengl;

import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.TextureAnimation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureStateBufferTest {
    @Test
    void entryCarriesRuneLiteAnimationRateWithoutPerDrawPhase() {
        int textureId = 7;
        TextureDefinitionView definition = new TextureDefinitionView(
                textureId, false, textureId, 0,
                4, 3, false);
        RenderTextureResource resource = RenderTextureResource.from(
                textureId, definition, 64, new int[64 * 64]);

        TextureStateBuffer.Entry entry = TextureStateBuffer.entryFor(resource);
        TextureAnimation.UvOffset expected = TextureAnimation.rate(resource);

        assertEquals(1.0f, entry.scaleU());
        assertEquals(1.0f, entry.scaleV());
        assertEquals(expected.u(), entry.animationUPerCycle());
        assertEquals(expected.v(), entry.animationVPerCycle());
        assertEquals(3.0f / 64.0f, entry.animationUPerCycle());
        assertEquals(0.0f, entry.animationVPerCycle());
    }

    @Test
    void staticTextureProducesZeroAnimationRate() {
        int textureId = 8;
        TextureDefinitionView definition = new TextureDefinitionView(
                textureId, false, textureId, 0,
                0, 0, false);
        RenderTextureResource resource = RenderTextureResource.from(
                textureId, definition, 128, new int[128 * 128]);

        TextureStateBuffer.Entry entry = TextureStateBuffer.entryFor(resource);

        assertEquals(0.0f, entry.animationUPerCycle());
        assertEquals(0.0f, entry.animationVPerCycle());
    }

    @Test
    void averageColorFallbackDoesNotAnimateAOnePixelDiagnosticLayer() {
        int textureId = 9;
        TextureDefinitionView definition = new TextureDefinitionView(
                textureId, false, textureId, 0x336699,
                3, 2, false);
        RenderTextureResource resource = RenderTextureResource.averageColorFallback(
                textureId, definition, "fixture");

        assertEquals(TextureStateBuffer.DEFAULT, TextureStateBuffer.entryFor(resource));
    }
}
