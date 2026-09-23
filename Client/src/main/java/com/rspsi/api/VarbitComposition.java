package com.rspsi.api;

/** Layout of one varbit inside its varp, as {@code net.runelite.api.VarbitComposition}. */
public interface VarbitComposition {
    /** The varp holding this varbit. */
    int getIndex();

    int getLeastSignificantBit();

    int getMostSignificantBit();
}
