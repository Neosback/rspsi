package com.rspsi.editor.render;

import java.util.Objects;

/** Client-cycle UV displacement for an OSRS animated texture. */
public final class TextureAnimation {
    private TextureAnimation() {
    }

    /**
     * Returns the normalized UV displacement for the supplied client cycle.
     * The direction table matches the current client and TSPS reference:
     * 1 = negative V, 2 = negative U, 3 = positive V, 4 = positive U.
     */
    public static UvOffset offset(RenderTextureResource texture, int clientCycle) {
        Objects.requireNonNull(texture, "texture");
        if (!texture.hasPixels() || texture.width() <= 0 || texture.height() <= 0) {
            return UvOffset.ZERO;
        }
        int direction = texture.definition().animationDirection();
        int speed = texture.definition().animationSpeed();
        if (direction < 1 || direction > 4 || speed == 0) return UvOffset.ZERO;

        float cycles = clientCycle;
        float horizontal = speed * cycles / texture.width();
        float vertical = speed * cycles / texture.height();
        return switch (direction) {
            case 1 -> new UvOffset(0.0f, -vertical);
            case 2 -> new UvOffset(-horizontal, 0.0f);
            case 3 -> new UvOffset(0.0f, vertical);
            case 4 -> new UvOffset(horizontal, 0.0f);
            default -> UvOffset.ZERO;
        };
    }

    public record UvOffset(float u, float v) {
        public static final UvOffset ZERO = new UvOffset(0.0f, 0.0f);
    }
}
