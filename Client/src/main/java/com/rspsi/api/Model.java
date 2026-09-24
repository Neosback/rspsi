package com.rspsi.api;

/**
 * A lit model, as {@code net.runelite.api.Model}.
 *
 * <p>Face colours follow the client: {@code faceColors3 == -1} means the face
 * is flat-shaded with colour 1; {@code -2} means the face is hidden. GPU
 * buffer bookkeeping (scene ids, buffer offsets, draw calls) from RuneLite's
 * interface is not part of this API; Studio renders scenes itself.</p>
 */
public interface Model extends Mesh<Model>, Renderable {
    int[] getFaceColors1();

    int[] getFaceColors2();

    int[] getFaceColors3();

    /** The HSL colours before lighting. */
    short[] getUnlitFaceColors();

    /** Lowest point below the origin ({@code max Y}). */
    int getBottomY();

    int getRadius();

    int getDiameter();

    /** Horizontal radius ({@code sqrt(max x*x + z*z)}), rounded up as the client does. */
    int getXYZMag();

    /** Client AABB when drawn at {@code orientation} (0-2047, JAU). */
    AABB getAABB(int orientation);

    /** Per-face draw priority, or {@code null}. */
    byte[] getFaceRenderPriorities();

    /** Per-face index into the texture triangles, or {@code null}. */
    byte[] getTextureFaces();

    int[] getTexIndices1();

    int[] getTexIndices2();

    int[] getTexIndices3();

    /** Summed vertex normals (x), as accumulated by {@code calculateVertexNormals}. */
    int[] getVertexNormalsX();

    int[] getVertexNormalsY();

    int[] getVertexNormalsZ();

    @Override
    default Model getModel() {
        return this;
    }
}
