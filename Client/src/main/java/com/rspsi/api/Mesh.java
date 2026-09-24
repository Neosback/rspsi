package com.rspsi.api;

import com.rspsi.api.model.Triangle;
import com.rspsi.api.model.Vertex;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertices and faces shared by {@link ModelData} and {@link Model}, as
 * {@code net.runelite.api.Mesh}.
 *
 * <p>The array getters return the mesh's own arrays, as in RuneLite: writes
 * change this mesh. Transforms mutate in place and return {@code this}, with
 * the client's exact integer math ({@code ModelData}/{@code Model} in the
 * deob). Meshes from {@link Client#loadModelData} are fresh copies, so
 * transforming one never touches cache data.</p>
 */
public interface Mesh<T extends Mesh<T>> {
    default List<Vertex> getVertices() {
        int[] x = getVerticesX();
        int[] y = getVerticesY();
        int[] z = getVerticesZ();
        List<Vertex> vertices = new ArrayList<>(getVerticesCount());
        for (int i = 0; i < getVerticesCount(); i++) vertices.add(new Vertex(x[i], y[i], z[i]));
        return vertices;
    }

    default List<Triangle> getTriangles() {
        List<Vertex> vertices = getVertices();
        int[] a = getFaceIndices1();
        int[] b = getFaceIndices2();
        int[] c = getFaceIndices3();
        List<Triangle> triangles = new ArrayList<>(getFaceCount());
        for (int i = 0; i < getFaceCount(); i++) {
            triangles.add(new Triangle(vertices.get(a[i]), vertices.get(b[i]), vertices.get(c[i])));
        }
        return triangles;
    }

    int getVerticesCount();

    int[] getVerticesX();

    int[] getVerticesY();

    int[] getVerticesZ();

    int getFaceCount();

    int[] getFaceIndices1();

    int[] getFaceIndices2();

    int[] getFaceIndices3();

    /** Per-face alpha (0 = opaque), or {@code null} when the model has none. */
    byte[] getFaceTransparencies();

    /** Per-face texture id (-1 = untextured), or {@code null} when the model has none. */
    short[] getFaceTextures();

    /** Rotates 90 degrees counter-clockwise about Y: x' = z, z' = -x. */
    T rotateY90Ccw();

    /** Rotates 180 degrees about Y. */
    T rotateY180Ccw();

    /** Rotates 270 degrees counter-clockwise about Y: x' = -z, z' = x. */
    T rotateY270Ccw();

    T translate(int x, int y, int z);

    /** Scales each axis by {@code value / 128} ({@code ModelData.resize}). */
    T scale(int x, int y, int z);
}
