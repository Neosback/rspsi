package com.rspsi.editor.render;

/**
 * RuneScape projected-face visibility shared by software/native scene backends.
 *
 * <p>RuneLite-melxin's client {@code Model.draw0} stores a face as culled when
 * its projected edge expression is {@code <= 0}, and later draws only faces
 * whose culled flag is false. Therefore a client-visible face has a positive
 * edge expression. {@link SoftwareSceneRenderer}'s {@code edge()} method is
 * algebraically the same expression.</p>
 *
 * <p>Client screen coordinates use Y down. OpenGL window coordinates use Y up.
 * The client's edge expression is the negative of conventional signed area in
 * client screen space; flipping Y for the native window flips conventional
 * area once, so a positive client edge maps to positive conventional OpenGL
 * window area: counter-clockwise.</p>
 *
 * <p>Native culling remains disabled until asymmetric real-cache model and
 * shaped-tile fixtures validate the complete upload/projection path.</p>
 */
public final class BackfacePolicy {
    private static final float DEGENERATE_EPSILON = 0.0001f;

    private BackfacePolicy() {
    }

    /** True when RuneScape's projected edge expression marks the face visible. */
    public static boolean isFrontFacingSoftware(float edgeFunction) {
        return edgeFunction > DEGENERATE_EPSILON;
    }

    /** OpenGL winding equivalent to a positive RuneScape projected edge. */
    public static NativeWinding nativeWinding() {
        return NativeWinding.COUNTER_CLOCKWISE;
    }

    /**
     * Native viewport validation mode. Normal editing remains two-sided until
     * the real-cache model and shaped-tile acceptance checks are complete.
     */
    public enum NativeCullingMode {
        TWO_SIDED("Two Sided"),
        CLIENT_FRONT("Client Front"),
        REVERSED_DEBUG("Reversed Debug");

        private final String label;

        NativeCullingMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum NativeWinding {
        COUNTER_CLOCKWISE
    }
}
