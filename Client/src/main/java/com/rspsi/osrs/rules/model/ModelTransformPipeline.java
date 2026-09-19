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
    public record TransformedVertex(int x, int y, int z) {}

    private ModelTransformPipeline() {}

    /** Matches the client ModelData.rotate(angle) 2048-unit angle table. */
    public static int[] rotateJagexAngle(int x, int z, int angle) {
        int sine = (int) (65536.0 * Math.sin(angle * Math.PI * 2.0 / 2048.0));
        int cosine = (int) (65536.0 * Math.cos(angle * Math.PI * 2.0 / 2048.0));
        int rotatedX = (sine * z + cosine * x) >> 16;
        int rotatedZ = (cosine * z - sine * x) >> 16;
        return new int[]{rotatedX, rotatedZ};
    }

    /** Rotates coordinates by quarter turns (0..3). */
    public static int[] rotateQuarterTurn(int x, int z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> new int[]{z, -x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
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
            int[] diagonal = rotateJagexAngle(x, z, 256);
            x = diagonal[0] + 45;
            z = diagonal[1] - 45;
        }

        int[] rotated = rotateQuarterTurn(x, z, variant.rotation());
        x = rotated[0];
        z = rotated[1];

        if (appearance != null) {
            x = x * appearance.scaleX() / 128;
            y = y * appearance.scaleY() / 128;
            z = z * appearance.scaleZ() / 128;
            x += appearance.offsetX();
            y += appearance.offsetY();
            z += appearance.offsetZ();
        }

        if (variant.rotateAfterScale()) {
            int[] diagonal = rotateJagexAngle(x, z, 256);
            x = diagonal[0];
            z = diagonal[1];
        }

        x += centerX + variant.decorX();
        z += centerZ + variant.decorZ();

        return new TransformedVertex(x, y, z);
    }
}
