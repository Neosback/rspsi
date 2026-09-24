package com.rspsi.api;

/**
 * Unlit model geometry as decoded from the cache, as
 * {@code net.runelite.api.ModelData}. {@link #light} turns it into a lit
 * {@link Model} with the client's lighting ({@code ModelData.toModel}).
 */
public interface ModelData extends Mesh<ModelData> {
    int DEFAULT_AMBIENT = 64;
    int DEFAULT_CONTRAST = 768;
    int DEFAULT_X = -50;
    int DEFAULT_Y = -10;
    int DEFAULT_Z = -50;

    /** Per-face HSL colours (16-bit). */
    short[] getFaceColors();

    /**
     * Lights the model. Objects use {@code ambient + 64} and {@code contrast + 768}
     * with the light at (-50, -10, -50) ({@code ObjectComposition} in the deob).
     */
    Model light(int ambient, int contrast, int x, int y, int z);

    /** {@link #light(int, int, int, int, int)} with the default ambient, contrast and light. */
    default Model light() {
        return light(DEFAULT_AMBIENT, DEFAULT_CONTRAST, DEFAULT_X, DEFAULT_Y, DEFAULT_Z);
    }

    /** Replaces one face colour with another on every face. */
    ModelData recolor(short colorToReplace, short colorToReplaceWith);

    /** Replaces one texture id with another on every face. */
    ModelData retexture(short find, short replace);

    /** A copy sharing every array with this one; clone what you will change. */
    ModelData shallowCopy();

    /** This model with its own copies of the vertex arrays. */
    ModelData cloneVertices();

    ModelData cloneColors();

    ModelData cloneTextures();

    ModelData cloneTransparencies();

    /** @param force give this model a transparency array even when it had none */
    ModelData cloneTransparencies(boolean force);
}
