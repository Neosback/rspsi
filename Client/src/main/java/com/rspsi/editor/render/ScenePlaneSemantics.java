package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;

/**
 * Explicit mapping between authored cache planes and RuneScape scene planes.
 *
 * <p>RuneLite exposes three distinct tile concepts: the tile's current scene
 * plane ({@code Tile#getPlane}), its original/render level
 * ({@code Tile#getRenderLevel}), and the minimum/physical level used by scene
 * traversal ({@code Tile#getPhysicalLevel}). A bridge column shifts the
 * current scene plane down while retaining the original render level.</p>
 */
public record ScenePlaneSemantics(
        int authoredPlane,
        int scenePlane,
        int renderLevel,
        int planeCullLevel
) {
    public ScenePlaneSemantics {
        if (authoredPlane < 0 || scenePlane < 0 || renderLevel < 0 || planeCullLevel < 0) {
            throw new IllegalArgumentException("Scene plane semantics cannot be negative");
        }
    }

    /**
     * Resolves the client-facing plane tuple for one authored tile.
     *
     * @param authoredPlane source/cache plane
     * @param tileFlags flags authored on this plane
     * @param bridgeLinked true when Scene#setLinkBelow shifts this tile down
     */
    public static ScenePlaneSemantics resolve(int authoredPlane, int tileFlags,
                                              boolean bridgeLinked) {
        if (authoredPlane < 0) {
            throw new IllegalArgumentException("Authored plane cannot be negative");
        }
        int scenePlane = bridgeLinked && authoredPlane > 0
                ? authoredPlane - 1 : authoredPlane;
        int renderLevel = authoredPlane;

        // The client render-plane rule forces bit 0x8 tiles to minimum level
        // zero; otherwise a linked bridge column inherits the shifted plane.
        int planeCullLevel = (tileFlags & OsrsTileFlags.MINIMAP_BRIDGE) != 0
                ? 0 : scenePlane;

        return new ScenePlaneSemantics(
                authoredPlane, scenePlane, renderLevel, planeCullLevel);
    }
}
