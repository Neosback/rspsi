package com.rspsi.api;

/**
 * An object (loc) definition, as {@code net.runelite.api.ObjectComposition}.
 * Read-only: edit definitions through the Object editor's transactions.
 */
public interface ObjectComposition {
    int getId();

    /** Name shown in menus; {@code "null"} when unnamed, as in the client. */
    String getName();

    /** Right-click options 1-5; unset entries are {@code null}. */
    String[] getActions();

    /** Minimap scene sprite (opcode 68), or -1. */
    int getMapSceneId();

    /** Map element id (opcode 82), or -1. */
    int getMapIconId();

    /** Multiloc states; the last entry is the default. {@code null} when not a multiloc. */
    int[] getImpostorIds();

    /** The definition shown for the current var state, or {@code null} when nothing is shown. */
    ObjectComposition getImpostor();

    /** Opcode 69 access mask (server-side route data; the client discards it). */
    int getAccessBitMask();

    int getVarbitId();

    int getVarPlayerId();

    int getSizeX();

    int getSizeY();

    /** Opcode 249 int param, or {@code defaultValue} when absent. */
    int getIntValue(int paramId, int defaultValue);

    /** Opcode 249 string param, or {@code null} when absent. */
    String getStringValue(int paramId);
}
