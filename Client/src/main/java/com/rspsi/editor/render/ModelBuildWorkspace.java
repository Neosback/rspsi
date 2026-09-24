package com.rspsi.editor.render;

import java.util.Arrays;

/**
 * Thread-confined primitive scratch for model preparation.
 *
 * <p>The arrays grow geometrically and are reused for every model processed on
 * the same scene-build worker. Only final immutable packet data escapes the
 * builder.</p>
 */
final class ModelBuildWorkspace {
    private static final int INITIAL_VERTICES = 256;

    private int[] x = new int[0];
    private int[] y = new int[0];
    private int[] z = new int[0];
    private int[] normalX = new int[0];
    private int[] normalY = new int[0];
    private int[] normalZ = new int[0];
    private int[] normalMagnitude = new int[0];
    private int[] unskewedY = new int[0];

    private int faceNormalX;
    private int faceNormalY;
    private int faceNormalZ;

    void prepare(int vertexCount) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("Vertex count cannot be negative");
        }
        ensureCapacity(vertexCount);
        Arrays.fill(normalX, 0, vertexCount, 0);
        Arrays.fill(normalY, 0, vertexCount, 0);
        Arrays.fill(normalZ, 0, vertexCount, 0);
        Arrays.fill(normalMagnitude, 0, vertexCount, 0);
    }

    void setVertex(int index, int vx, int vy, int vz) {
        x[index] = vx;
        y[index] = vy;
        z[index] = vz;
    }

    int x(int index) { return x[index]; }
    int y(int index) { return y[index]; }
    int z(int index) { return z[index]; }

    void setY(int index, int value) {
        y[index] = value;
    }

    void captureUnskewedY(int vertexCount) {
        System.arraycopy(y, 0, unskewedY, 0, vertexCount);
    }

    int unskewedY(int index) {
        return unskewedY[index];
    }

    int normalX(int index) { return normalX[index]; }
    int normalY(int index) { return normalY[index]; }
    int normalZ(int index) { return normalZ[index]; }
    int normalMagnitude(int index) { return normalMagnitude[index]; }

    void computeFaceNormal(int a, int b, int c) {
        int x1 = x[b] - x[a];
        int y1 = y[b] - y[a];
        int z1 = z[b] - z[a];
        int x2 = x[c] - x[a];
        int y2 = y[c] - y[a];
        int z2 = z[c] - z[a];
        int nx = y1 * z2 - y2 * z1;
        int ny = z1 * x2 - z2 * x1;
        int nz = x1 * y2 - x2 * y1;
        while (Math.abs(nx) > 8192 || Math.abs(ny) > 8192 || Math.abs(nz) > 8192) {
            nx >>= 1;
            ny >>= 1;
            nz >>= 1;
        }
        int magnitude = Math.max(1,
                (int) Math.sqrt((long) nx * nx + (long) ny * ny + (long) nz * nz));
        faceNormalX = nx * 256 / magnitude;
        faceNormalY = ny * 256 / magnitude;
        faceNormalZ = nz * 256 / magnitude;
    }

    void accumulateFaceNormal(int vertex) {
        normalX[vertex] += faceNormalX;
        normalY[vertex] += faceNormalY;
        normalZ[vertex] += faceNormalZ;
        normalMagnitude[vertex]++;
    }

    int faceNormalX() { return faceNormalX; }
    int faceNormalY() { return faceNormalY; }
    int faceNormalZ() { return faceNormalZ; }

    int capacity() {
        return x.length;
    }

    private void ensureCapacity(int required) {
        if (x.length >= required) return;
        int next = Math.max(INITIAL_VERTICES, x.length);
        while (next < required) {
            if (next > Integer.MAX_VALUE / 2) {
                next = required;
                break;
            }
            next *= 2;
        }
        x = Arrays.copyOf(x, next);
        y = Arrays.copyOf(y, next);
        z = Arrays.copyOf(z, next);
        normalX = Arrays.copyOf(normalX, next);
        normalY = Arrays.copyOf(normalY, next);
        normalZ = Arrays.copyOf(normalZ, next);
        normalMagnitude = Arrays.copyOf(normalMagnitude, next);
        unskewedY = Arrays.copyOf(unskewedY, next);
    }
}
