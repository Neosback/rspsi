package com.rspsi.editor.render;

import com.rspsi.cache.AssetCategory;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.assets.AssetRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Editor-facing OSRS texture animation service.
 *
 * <p>It uses the same direction table as TextureAnimation but can answer from
 * definition metadata without forcing texture-pixel decode or GPU upload.</p>
 */
public final class TextureAnimationService {
    public static final int DEFAULT_TEXTURE_SIZE = 128;

    private final AssetRepository assets;

    public TextureAnimationService(AssetRepository assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    public Optional<State> state(int textureId, int clientCycle) {
        return state(textureId, clientCycle, DEFAULT_TEXTURE_SIZE);
    }

    public Optional<State> state(int textureId, int clientCycle, int textureSize) {
        if (clientCycle < 0) throw new IllegalArgumentException("clientCycle cannot be negative");
        if (textureSize <= 0) throw new IllegalArgumentException("textureSize must be positive");
        return assets.texture(textureId).map(definition ->
                state(definition, clientCycle, textureSize));
    }

    public List<Integer> animatedTextureIds() {
        return assets.ids(AssetCategory.TEXTURES).stream()
                .filter(id -> assets.texture(id)
                        .map(def -> def.animationDirection() >= 1
                                && def.animationDirection() <= 4
                                && def.animationSpeed() != 0)
                        .orElse(false))
                .toList();
    }

    public static State state(TextureDefinitionView definition,
                              int clientCycle, int textureSize) {
        Objects.requireNonNull(definition, "definition");
        if (clientCycle < 0) throw new IllegalArgumentException("clientCycle cannot be negative");
        if (textureSize <= 0) throw new IllegalArgumentException("textureSize must be positive");
        int direction = definition.animationDirection();
        int speed = definition.animationSpeed();
        TextureAnimation.UvOffset offset =
                TextureAnimation.offset(definition, clientCycle, textureSize, textureSize);
        return new State(definition.id(), direction, speed, offset.u(), offset.v());
    }

    public record State(int textureId, int direction, int speed, float u, float v) {
        public boolean animated() {
            return direction >= 1 && direction <= 4 && speed != 0;
        }
    }
}
