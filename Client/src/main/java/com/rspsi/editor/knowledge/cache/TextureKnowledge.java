package com.rspsi.editor.knowledge.cache;

import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.Objects;

/**
 * Layer 1 Cache Fact: Exact, zero-heuristics properties of a cache texture.
 */
public record TextureKnowledge(
        int id,
        int averageHsl,
        int animationSpeed,
        int animationDirection,
        boolean transparent,
        int brightness
) {
    public static TextureKnowledge from(int id, TextureDefinitionView view) {
        Objects.requireNonNull(view, "view");
        return new TextureKnowledge(
                id,
                view.averageHsl(),
                view.animationSpeed(),
                view.animationDirection(),
                view.transparent(),
                128 // standard OSRS default brightness scale
        );
    }
}
