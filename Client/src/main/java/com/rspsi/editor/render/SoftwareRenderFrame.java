package com.rspsi.editor.render;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Immutable ARGB output from the neutral software reference renderer. */
public final class SoftwareRenderFrame {
    private final int width;
    private final int height;
    private final int[] argb;
    private final int submittedTriangles;
    private final int rasterizedTriangles;

    SoftwareRenderFrame(int width, int height, int[] argb,
                        int submittedTriangles, int rasterizedTriangles) {
        this.width = width;
        this.height = height;
        this.argb = argb.clone();
        this.submittedTriangles = submittedTriangles;
        this.rasterizedTriangles = rasterizedTriangles;
    }

    public int width() { return width; }

    public int height() { return height; }

    public int pixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Pixel outside frame: " + x + "," + y);
        }
        return argb[y * width + x];
    }

    public int[] argb() { return argb.clone(); }

    public int submittedTriangles() { return submittedTriangles; }

    public int rasterizedTriangles() { return rasterizedTriangles; }

    /** Stable pixel fingerprint for CPU/GPU parity fixtures. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int value : argb) {
                digest.update((byte) (value >>> 24));
                digest.update((byte) (value >>> 16));
                digest.update((byte) (value >>> 8));
                digest.update((byte) value);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
