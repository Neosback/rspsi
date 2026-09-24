package com.rspsi.editor.render;

import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.Objects;

/** Client-cycle UV displacement for an OSRS animated texture. */
public final class TextureAnimation {
    /** RuneLite GPU uploads {@code client.getGameCycle() & 127} to the shader. */
    public static final int CLIENT_CYCLE_MASK = 127;

    private TextureAnimation() {
    }

    /**
     * Returns the normalized UV displacement for the supplied client cycle.
     *
     * <p>The direction table and cycle phase match RuneLite GPU:
     * 1 = negative V, 2 = negative U, 3 = positive V, 4 = positive U.
     * RuneLite stores signed pixels-per-cycle and multiplies by
     * {@code (gameCycle & 127) / 128}. RSPSi stores the equivalent normalized
     * UV displacement directly. Width/height are explicit so a 64px client
     * texture advances one 64px source texel per speed step even though the
     * native texture array upscales it to 128px.</p>
     */
    public static UvOffset offset(TextureDefinitionView definition, int clientCycle,
                                  int width, int height) {
        Objects.requireNonNull(definition, "definition");
        if (clientCycle < 0) throw new IllegalArgumentException("clientCycle cannot be negative");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture dimensions must be positive");
        }

        UvOffset rate = rate(definition, width, height);
        int phase = clientCycle & CLIENT_CYCLE_MASK;
        return new UvOffset(rate.u() * phase, rate.v() * phase);
    }

    /**
     * Returns normalized UV displacement per client cycle.
     *
     * <p>This is the static value suitable for a GPU texture-state table:
     * the shader multiplies it by {@code clientCycle & 127}. Keeping the
     * rate calculation here makes the CPU reference and native shader paths
     * share one direction/dimension contract.</p>
     */
    public static UvOffset rate(TextureDefinitionView definition, int width, int height) {
        Objects.requireNonNull(definition, "definition");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture dimensions must be positive");
        }

        int direction = definition.animationDirection();
        int speed = definition.animationSpeed();
        if (direction < 1 || direction > 4 || speed == 0) return UvOffset.ZERO;

        float horizontal = speed / (float) width;
        float vertical = speed / (float) height;
        return switch (direction) {
            case 1 -> new UvOffset(0.0f, -vertical);
            case 2 -> new UvOffset(-horizontal, 0.0f);
            case 3 -> new UvOffset(0.0f, vertical);
            case 4 -> new UvOffset(horizontal, 0.0f);
            default -> UvOffset.ZERO;
        };
    }

    /** Returns the static native animation rate for a decoded renderer texture. */
    public static UvOffset rate(RenderTextureResource texture) {
        Objects.requireNonNull(texture, "texture");
        if (!texture.hasPixels() || texture.width() <= 0 || texture.height() <= 0) {
            return UvOffset.ZERO;
        }
        return rate(texture.definition(), texture.width(), texture.height());
    }

    /** Returns the native offset for a decoded renderer texture. */
    public static UvOffset offset(RenderTextureResource texture, int clientCycle) {
        Objects.requireNonNull(texture, "texture");
        if (!texture.hasPixels() || texture.width() <= 0 || texture.height() <= 0) {
            return UvOffset.ZERO;
        }
        return offset(texture.definition(), clientCycle, texture.width(), texture.height());
    }

    public record UvOffset(float u, float v) {
        public static final UvOffset ZERO = new UvOffset(0.0f, 0.0f);
    }
}
