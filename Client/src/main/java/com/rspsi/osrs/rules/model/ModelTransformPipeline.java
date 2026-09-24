package com.rspsi.osrs.rules.model;

import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.osrs.rules.loc.WallRules.LocModelVariant;

import java.util.Objects;

/**
 * Formal OSRS vertex transformation pipeline.
 *
 * <p>Preserves the strict client transformation order:
 * mirror -> diagonal pre-rotation (sourceType 4, rot > 3) -> quarter turn -> scale ->
 * definition offsets -> post-scale diagonal rotation -> footprint center and decor offsets.</p>
 */
public final class ModelTransformPipeline {
    private static final int ANGLE_MASK = 2047;
    private static final double ANGLE_UNIT = Math.PI * 2.0 / 2048.0;
    private static final int[] SINE = new int[2048];
    private static final int[] COSINE = new int[2048];

    static {
        // Same fixed-point lookup convention used by the client and RuneLite.
        // Runtime model transforms stay integer-only and allocation-free.
        for (int angle = 0; angle < 2048; angle++) {
            SINE[angle] = (int) (65536.0 * Math.sin(angle * ANGLE_UNIT));
            COSINE[angle] = (int) (65536.0 * Math.cos(angle * ANGLE_UNIT));
        }
    }

    public record TransformedVertex(int x, int y, int z) {}

    private ModelTransformPipeline() {}

    /**
     * Allocation-free client ModelData.rotate(angle) helper.
     *
     * <p>The high 32 bits contain X and the low 32 bits contain Z.</p>
     */
    public static long rotateJagexAnglePacked(int x, int z, int angle) {
        int normalized = angle & ANGLE_MASK;
        int sine = SINE[normalized];
        int cosine = COSINE[normalized];
        int rotatedX = (sine * z + cosine * x) >> 16;
        int rotatedZ = (cosine * z - sine * x) >> 16;
        return pack(rotatedX, rotatedZ);
    }

    /** Compatibility helper for callers that need an array result. */
    public static int[] rotateJagexAngle(int x, int z, int angle) {
        long packed = rotateJagexAnglePacked(x, z, angle);
        return new int[]{unpackX(packed), unpackZ(packed)};
    }

    /** Allocation-free quarter-turn helper. */
    public static long rotateQuarterTurnPacked(int x, int z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> pack(z, -x);
            case 2 -> pack(-x, -z);
            case 3 -> pack(-z, x);
            default -> pack(x, z);
        };
    }

    /** Compatibility helper for callers that need an array result. */
    public static int[] rotateQuarterTurn(int x, int z, int rotation) {
        long packed = rotateQuarterTurnPacked(x, z, rotation);
        return new int[]{unpackX(packed), unpackZ(packed)};
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /**
     * Determines whether a model variant should be mirrored.
     * The client's getModelData mirrors via {@code isRotated ^ (rotationParam > 3)}.
     */
    public static boolean shouldMirror(boolean appearanceRotated, int variantRotation) {
        return appearanceRotated ^ (variantRotation > 3);
    }

    /**
     * Executes the full OSRS vertex transformation pipeline.
     */
    public static TransformedVertex transform(
            int rawX, int rawY, int rawZ,
            boolean mirror,
            LocModelVariant variant,
            ObjectAppearanceView appearance,
            int centerX,
            int centerZ
    ) {
        Objects.requireNonNull(variant, "variant");
        int x = rawX;
        int y = rawY;
        int z = rawZ;

        if (mirror) {
            z = -z;
        }

        if (variant.sourceType() == 4 && variant.rotation() > 3) {
            long diagonal = rotateJagexAnglePacked(x, z, 256);
            x = unpackX(diagonal) + 45;
            z = unpackZ(diagonal) - 45;
        }

        long rotated = rotateQuarterTurnPacked(x, z, variant.rotation());
        x = unpackX(rotated);
        z = unpackZ(rotated);

        if (appearance != null) {
            x = x * appearance.scaleX() / 128;
            y = y * appearance.scaleY() / 128;
            z = z * appearance.scaleZ() / 128;
            x += appearance.offsetX();
            y += appearance.offsetY();
            z += appearance.offsetZ();
        }

        if (variant.rotateAfterScale()) {
            long diagonal = rotateJagexAnglePacked(x, z, 256);
            x = unpackX(diagonal);
            z = unpackZ(diagonal);
        }

        x += centerX + variant.decorX();
        z += centerZ + variant.decorZ();

        return new TransformedVertex(x, y, z);
    }
}
