package com.rspsi.editor.render;

/**
 * Backend-neutral description of the RuneLite {@code DrawCallbacks} capability
 * surface that the native RSPSi renderer can honor.
 *
 * <p>The bit values intentionally mirror the vendored RuneLite API so parity
 * tests can reason about the same contract without making the neutral Client
 * module depend on RuneLite classes. Unsupported flags remain explicit instead
 * of being silently advertised.</p>
 */
public final class DrawCallbackContract {
    public static final int GPU = 0x1;
    public static final int HILLSKEW = 0x2;
    public static final int NORMALS = 0x4;
    public static final int NO_VERTEX_SNAPPING = 0x8;
    public static final int ZBUF = 0x10;
    public static final int ZBUF_ZONE_FRUSTUM_CHECK = 0x20;
    public static final int UNLIT_FACE_COLORS = 0x40;
    public static final int RENDER_THREADS_MASK = 15;
    public static final int RENDER_THREADS_SHIFT = 7;

    public static final int PASS_OPAQUE = 0;
    public static final int PASS_ALPHA = 1;

    private static final int RENDER_THREADS_BITS =
            RENDER_THREADS_MASK << RENDER_THREADS_SHIFT;
    private static final int KNOWN_FLAGS = GPU | HILLSKEW | NORMALS
            | NO_VERTEX_SNAPPING | ZBUF | ZBUF_ZONE_FRUSTUM_CHECK
            | UNLIT_FACE_COLORS | RENDER_THREADS_BITS;

    /**
     * Capabilities backed by the current native path:
     * GPU rendering, retained unskewed contour state, retained model normals,
     * and a native depth buffer.
     */
    private static final int NATIVE_SUPPORTED_FLAGS =
            GPU | HILLSKEW | NORMALS | ZBUF;

    private DrawCallbackContract() {
    }

    public static int nativeSupportedFlags() {
        return NATIVE_SUPPORTED_FLAGS;
    }

    public static boolean supports(int flag) {
        if (Integer.bitCount(flag) != 1 || (flag & KNOWN_FLAGS) == 0) {
            throw new IllegalArgumentException("Expected one known DrawCallbacks capability flag");
        }
        return (NATIVE_SUPPORTED_FLAGS & flag) != 0;
    }

    public static int renderThreads(int count) {
        return (count & RENDER_THREADS_MASK) << RENDER_THREADS_SHIFT;
    }

    /**
     * Resolves requested RuneLite callback flags against what the native RSPSi
     * path actually implements. Unknown bits are rejected so revision drift
     * cannot silently become an accidental capability claim.
     */
    public static Negotiation negotiate(int requestedFlags) {
        int unknown = requestedFlags & ~KNOWN_FLAGS;
        if (unknown != 0) {
            throw new IllegalArgumentException(
                    "Unknown DrawCallbacks capability bits: 0x"
                            + Integer.toHexString(unknown));
        }
        int enabled = requestedFlags & NATIVE_SUPPORTED_FLAGS;
        return new Negotiation(requestedFlags, enabled, requestedFlags & ~enabled);
    }

    public static GpuDrawCommand.SubmissionPass submissionPass(int runeLitePass) {
        return switch (runeLitePass) {
            case PASS_OPAQUE -> GpuDrawCommand.SubmissionPass.OPAQUE;
            case PASS_ALPHA -> GpuDrawCommand.SubmissionPass.ALPHA;
            default -> throw new IllegalArgumentException(
                    "Unsupported DrawCallbacks pass: " + runeLitePass);
        };
    }

    public record Negotiation(int requestedFlags, int enabledFlags, int unsupportedFlags) {
        public Negotiation {
            if ((enabledFlags & ~requestedFlags) != 0
                    || (unsupportedFlags & ~requestedFlags) != 0
                    || (enabledFlags & unsupportedFlags) != 0
                    || (enabledFlags | unsupportedFlags) != requestedFlags) {
                throw new IllegalArgumentException("Invalid callback capability negotiation");
            }
        }

        public boolean fullySupported() {
            return unsupportedFlags == 0;
        }

        public boolean enabled(int flag) {
            if (Integer.bitCount(flag) != 1 || (flag & KNOWN_FLAGS) == 0) {
                throw new IllegalArgumentException(
                        "Expected one known DrawCallbacks capability flag");
            }
            return (enabledFlags & flag) != 0;
        }
    }
}
