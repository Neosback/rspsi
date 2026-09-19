package com.rspsi.osrs.rules.loc;

import com.rspsi.editor.model.WorldObject;

import java.util.List;
import java.util.Objects;

/**
 * Formal OSRS rules for wall shapes, multi-part wall corners, and variant decomposition.
 */
public final class WallRules {

    /**
     * Represents a single model variant that must be resolved and rendered for a location.
     */
    public record LocModelVariant(
            int sourceType,
            int rotation,
            int decorX,
            int decorZ,
            boolean rotateAfterScale
    ) {}

    private WallRules() {}

    /**
     * Expands a location into its constituent model variants based on OSRS rules.
     */
    public static List<LocModelVariant> expandVariants(WorldObject object, int decorDisplacement) {
        Objects.requireNonNull(object, "object");
        int rotation = object.rotation();

        return switch (object.type()) {
            // Shape 2 builds 2 separate L-wall models
            case 2 -> List.of(
                    new LocModelVariant(2, rotation + 4, 0, 0, false),
                    new LocModelVariant(2, (rotation + 1) & 3, 0, 0, false)
            );
            // Shape 4: straight decor no offset
            case 4 -> List.of(
                    new LocModelVariant(4, rotation, 0, 0, false)
            );
            // Shape 5: straight decor with displacement
            case 5 -> List.of(
                    new LocModelVariant(4, rotation,
                            WallDecorationRules.straightOffsetX(rotation, decorDisplacement),
                            WallDecorationRules.straightOffsetZ(rotation, decorDisplacement),
                            false)
            );
            // Shape 6: diagonal decor with halved displacement
            case 6 -> List.of(
                    new LocModelVariant(4, rotation + 4,
                            WallDecorationRules.diagonalOffsetX(rotation, decorDisplacement),
                            WallDecorationRules.diagonalOffsetZ(rotation, decorDisplacement),
                            false)
            );
            // Shape 7: diagonal decor no offset
            case 7 -> List.of(
                    new LocModelVariant(4, ((rotation + 2) & 3) + 4, 0, 0, false)
            );
            // Shape 8: two-sided diagonal decor (part 1 displaced, part 2 at origin)
            case 8 -> List.of(
                    new LocModelVariant(4, rotation + 4,
                            WallDecorationRules.diagonalOffsetX(rotation, decorDisplacement),
                            WallDecorationRules.diagonalOffsetZ(rotation, decorDisplacement),
                            false),
                    new LocModelVariant(4, ((rotation + 2) & 3) + 4, 0, 0, false)
            );
            // Shape 11: diagonal game object
            case 11 -> List.of(
                    new LocModelVariant(10, rotation + 4, 0, 0, true)
            );
            // Default single model part
            default -> List.of(
                    new LocModelVariant(object.type(), rotation, 0, 0, false)
            );
        };
    }
}
