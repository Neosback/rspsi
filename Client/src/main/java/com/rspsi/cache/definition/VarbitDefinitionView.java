package com.rspsi.cache.definition;

/**
 * A varbit: bits {@code leastSignificantBit..mostSignificantBit} of varp
 * {@code varp}, as the client's VarbitComposition / RuneLite
 * {@code net.runelite.api.VarbitComposition}.
 */
public record VarbitDefinitionView(int id, int varp, int leastSignificantBit, int mostSignificantBit) {
    public VarbitDefinitionView {
        if (id < 0 || varp < 0 || leastSignificantBit < 0 || mostSignificantBit < leastSignificantBit
                || mostSignificantBit > 31) {
            throw new IllegalArgumentException("Invalid varbit " + id + ": varp " + varp
                    + " bits " + leastSignificantBit + ".." + mostSignificantBit);
        }
    }

    /** Mask of this varbit's width, e.g. 0b111 for a 3-bit varbit. */
    public int mask() {
        int width = mostSignificantBit - leastSignificantBit + 1;
        return width >= 32 ? -1 : (1 << width) - 1;
    }

    /** This varbit's value inside a varp value. */
    public int read(int varpValue) {
        return (varpValue >>> leastSignificantBit) & mask();
    }

    /** The varp value with this varbit replaced by {@code value} (masked to its width). */
    public int write(int varpValue, int value) {
        int shifted = mask() << leastSignificantBit;
        return (varpValue & ~shifted) | ((value & mask()) << leastSignificantBit);
    }
}
