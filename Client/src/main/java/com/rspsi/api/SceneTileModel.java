package com.rspsi.api;

/**
 * Shaped overlay tile geometry, as {@code net.runelite.api.SceneTileModel}.
 *
 * <p>Vertex positions are scene-local units: X and Z are
 * {@code sceneTile * 128 + offset}, Y is the height. Face arrays index into the
 * vertex arrays, and each face carries the lit packed HSL of its three
 * corners. GPU buffer bookkeeping from RuneLite is intentionally absent.</p>
 */
public interface SceneTileModel {
    /** Client scene shape: the authored overlay shape plus one. */
    int getShape();

    int getRotation();

    /** Base underlay packed HSL, or -1 when the tile has no underlay. */
    int getModelUnderlay();

    /** Base overlay packed HSL, or a negative client sentinel. */
    int getModelOverlay();

    int[] getVertexX();

    int[] getVertexY();

    int[] getVertexZ();

    int[] getFaceX();

    int[] getFaceY();

    int[] getFaceZ();

    int[] getTriangleColorA();

    int[] getTriangleColorB();

    int[] getTriangleColorC();

    /** Per-face texture id, or -1 for an untextured face. */
    int[] getTriangleTextureId();

    /** True when all four corner heights are equal. */
    boolean isFlat();
}
