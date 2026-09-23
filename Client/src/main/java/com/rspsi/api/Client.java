package com.rspsi.api;

/**
 * The simulated player/client state a map editor can reason about, shaped
 * after {@code net.runelite.api.Client}.
 *
 * <p>Only the parts that affect how a map looks or behaves are here so far:
 * player variables. A varbit is a bit range of a varp; changing either one
 * re-resolves every loc whose appearance depends on it (multilocs), exactly
 * as the client does. This is simulation state, not authored map data, so it
 * is not recorded in undo history.</p>
 */
public interface Client {
    /** Value of a varbit ({@code net.runelite.api.Client#getVarbitValue}). */
    int getVarbitValue(int varbitId);

    /** Value of a varp ({@code net.runelite.api.Client#getVarpValue}). */
    int getVarpValue(int varpId);

    /** Sets a varbit by rewriting its bits in the parent varp ({@code Client#setVarbit}). */
    void setVarbit(int varbitId, int value);

    /**
     * Studio extra: sets a whole varp. RuneLite plugins write
     * {@code client.getVarps()[id]}; Studio's state is not an exposed array.
     */
    void setVarpValue(int varpId, int value);

    /** Layout of a varbit, or {@code null} when the cache has no such varbit. */
    VarbitComposition getVarbit(int varbitId);

    /** Studio extra: every var back to 0, as on a fresh account. */
    void resetVars();
}
