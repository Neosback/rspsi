package com.rspsi.editor.transform;

import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.osrs.rules.loc.LocPlacementRules;
import com.rspsi.osrs.rules.tile.TileShapeRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical orthogonal transform service for portable world fragments.
 *
 * <p>Terrain and location anchors are transformed from the fragment's own
 * rectangular grid. The default origin preserves the source south-west tile.
 * Supplying a pivot preserves that exact source tile at the same absolute
 * world coordinate after the transform.</p>
 */
public final class WorldFragmentTransformer {
    private WorldFragmentTransformer() {
    }

    public static WorldFragmentTransformResult transform(
            WorldFragment fragment,
            WorldFragmentTransform transform,
            ObjectFootprintResolver footprints
    ) {
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(footprints, "footprints");

        TileBounds sourceBounds = fragment.bounds();
        validatePivot(sourceBounds, transform);

        Dimensions dimensions = transformedDimensions(
                sourceBounds.width(), sourceBounds.height(), transform.quarterTurns());
        Origin origin = targetOrigin(sourceBounds, transform, dimensions);

        List<WorldFragmentTransformResult.Diagnostic> diagnostics = new ArrayList<>();
        boolean hasOddReflection = transform.mirrorX() ^ transform.mirrorY();
        if (hasOddReflection && !fragment.objects().isEmpty()) {
            diagnostics.add(new WorldFragmentTransformResult.Diagnostic(
                    WorldFragmentTransformResult.DiagnosticCode.OBJECT_MODEL_MIRROR_NOT_NATIVE,
                    "OSRS map locations have quarter-turn rotation but no generic model-mirror bit; "
                            + "the fragment layout and placement orientation were reflected, while "
                            + "asymmetric model chirality may remain visually unmirrored."));
        }

        List<TerrainTilePatch> terrain = new ArrayList<>(fragment.terrain().size());
        Set<Long> terrainCoordinates = new LinkedHashSet<>();
        for (TerrainTilePatch patch : fragment.terrain()) {
            if (!patch.snapshot().objects().isEmpty()) {
                throw new IllegalArgumentException(
                        "WorldFragment terrain snapshots must not embed objects; use fragment.objects()");
            }
            int localX = patch.x() - sourceBounds.minX();
            int localY = patch.y() - sourceBounds.minY();
            Point target = transformLocal(
                    localX, localY, sourceBounds.width(), sourceBounds.height(), transform);
            int worldX = origin.x() + target.x();
            int worldY = origin.y() + target.y();
            long key = pack(patch.plane(), worldX, worldY);
            if (!terrainCoordinates.add(key)) {
                throw new IllegalStateException("Fragment transform produced duplicate terrain coordinates");
            }
            terrain.add(new TerrainTilePatch(
                    patch.plane(), worldX, worldY,
                    transformSnapshot(patch.snapshot(), transform)));
        }

        List<WorldObject> objects = new ArrayList<>(fragment.objects().size());
        for (WorldObject object : fragment.objects()) {
            ObjectFootprintResolver.ObjectFootprint footprint = footprints.resolve(object)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing object footprint for object #" + object.id()));
            objects.add(transformObject(
                    object, footprint, sourceBounds, origin, transform, diagnostics));
        }

        TileBounds targetBounds = new TileBounds(
                origin.x(),
                origin.y(),
                origin.x() + dimensions.width() - 1,
                origin.y() + dimensions.height() - 1);

        return new WorldFragmentTransformResult(
                new WorldFragment(targetBounds, terrain, objects),
                diagnostics);
    }

    private static TileSnapshot transformSnapshot(
            TileSnapshot source,
            WorldFragmentTransform transform
    ) {
        int[] transformedHeights = transformCorners(
                source.southWestHeight(),
                source.southEastHeight(),
                source.northEastHeight(),
                source.northWestHeight(),
                transform);

        TileShapeRules.OverlayTransform overlay = TileShapeRules.transformOverlay(
                source.overlayShape(),
                source.overlayRotation(),
                transform.mirrorX(),
                transform.mirrorY(),
                transform.quarterTurns());

        return new TileSnapshot(
                transformedHeights[0],
                transformedHeights[1],
                transformedHeights[2],
                transformedHeights[3],
                source.underlayId(),
                source.overlayId(),
                overlay.shape(),
                overlay.rotation(),
                source.flags(),
                List.of(),
                source.heightSource());
    }

    private static WorldObject transformObject(
            WorldObject source,
            ObjectFootprintResolver.ObjectFootprint footprint,
            TileBounds bounds,
            Origin origin,
            WorldFragmentTransform transform,
            List<WorldFragmentTransformResult.Diagnostic> diagnostics
    ) {
        int orientedWidth = LocPlacementRules.rotatedWidth(
                footprint.width(), footprint.length(), source.rotation());
        int orientedLength = LocPlacementRules.rotatedLength(
                footprint.width(), footprint.length(), source.rotation());

        int localX = source.x() - bounds.minX();
        int localY = source.y() - bounds.minY();
        if (localX < 0 || localY < 0
                || localX + orientedWidth > bounds.width()
                || localY + orientedLength > bounds.height()) {
            throw new IllegalArgumentException(
                    "Object #" + source.id() + " footprint extends outside fragment bounds");
        }

        Point[] corners = {
                transformLocal(localX, localY, bounds.width(), bounds.height(), transform),
                transformLocal(localX + orientedWidth - 1, localY,
                        bounds.width(), bounds.height(), transform),
                transformLocal(localX, localY + orientedLength - 1,
                        bounds.width(), bounds.height(), transform),
                transformLocal(localX + orientedWidth - 1, localY + orientedLength - 1,
                        bounds.width(), bounds.height(), transform)
        };

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point corner : corners) {
            minX = Math.min(minX, corner.x());
            minY = Math.min(minY, corner.y());
            maxX = Math.max(maxX, corner.x());
            maxY = Math.max(maxY, corner.y());
        }

        int targetFootprintWidth = maxX - minX + 1;
        int targetFootprintLength = maxY - minY + 1;

        int semanticRotation = LocPlacementRules.transformOrientation(
                source.type(),
                source.rotation(),
                transform.mirrorX(),
                transform.mirrorY(),
                transform.quarterTurns());

        int targetRotation = chooseRepresentableRotation(
                footprint,
                semanticRotation,
                targetFootprintWidth,
                targetFootprintLength);

        if (targetRotation != semanticRotation) {
            diagnostics.add(new WorldFragmentTransformResult.Diagnostic(
                    WorldFragmentTransformResult.DiagnosticCode.OBJECT_ORIENTATION_APPROXIMATED,
                    "Object #" + source.id() + " type " + source.type()
                            + " cannot preserve both the semantic mirror orientation and its "
                            + targetFootprintWidth + "x" + targetFootprintLength
                            + " occupied footprint; rotation " + targetRotation
                            + " preserves the mirrored footprint."));
        }

        return new WorldObject(
                source.id(),
                source.type(),
                targetRotation,
                source.plane(),
                origin.x() + minX,
                origin.y() + minY);
    }

    private static int chooseRepresentableRotation(
            ObjectFootprintResolver.ObjectFootprint footprint,
            int preferredRotation,
            int targetWidth,
            int targetLength
    ) {
        if (matchesFootprint(footprint, preferredRotation, targetWidth, targetLength)) {
            return preferredRotation;
        }

        // The opposite orientation has the same footprint but preserves as much
        // of the preferred facing as the quarter-turn-only map format permits.
        int opposite = (preferredRotation + 2) & 3;
        if (matchesFootprint(footprint, opposite, targetWidth, targetLength)) {
            return opposite;
        }

        for (int rotation = 0; rotation < 4; rotation++) {
            if (matchesFootprint(footprint, rotation, targetWidth, targetLength)) {
                return rotation;
            }
        }

        throw new IllegalStateException(
                "No OSRS orientation can represent transformed object footprint "
                        + targetWidth + "x" + targetLength);
    }

    private static boolean matchesFootprint(
            ObjectFootprintResolver.ObjectFootprint footprint,
            int rotation,
            int width,
            int length
    ) {
        return LocPlacementRules.rotatedWidth(
                footprint.width(), footprint.length(), rotation) == width
                && LocPlacementRules.rotatedLength(
                footprint.width(), footprint.length(), rotation) == length;
    }

    /**
     * Returns transformed SW, SE, NE, NW heights.
     */
    static int[] transformCorners(
            int southWest,
            int southEast,
            int northEast,
            int northWest,
            WorldFragmentTransform transform
    ) {
        int[] source = {southWest, southEast, northEast, northWest};
        int[][] coordinates = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] target = new int[4];

        for (int index = 0; index < 4; index++) {
            Point point = transformLocal(
                    coordinates[index][0],
                    coordinates[index][1],
                    2,
                    2,
                    transform);
            int targetIndex = cornerIndex(point.x(), point.y());
            target[targetIndex] = source[index];
        }
        return target;
    }

    private static int cornerIndex(int x, int y) {
        if (x == 0 && y == 0) return 0;
        if (x == 1 && y == 0) return 1;
        if (x == 1 && y == 1) return 2;
        if (x == 0 && y == 1) return 3;
        throw new IllegalArgumentException("Not a tile corner: " + x + "," + y);
    }

    private static Point transformLocal(
            int x,
            int y,
            int width,
            int height,
            WorldFragmentTransform transform
    ) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException(
                    "Local coordinate is outside fragment grid: " + x + "," + y);
        }

        int transformedX = transform.mirrorX() ? width - 1 - x : x;
        int transformedY = transform.mirrorY() ? height - 1 - y : y;

        return switch (transform.quarterTurns()) {
            case 0 -> new Point(transformedX, transformedY);
            case 1 -> new Point(transformedY, width - 1 - transformedX);
            case 2 -> new Point(width - 1 - transformedX, height - 1 - transformedY);
            case 3 -> new Point(height - 1 - transformedY, transformedX);
            default -> throw new IllegalStateException("Unexpected quarter-turn value");
        };
    }

    private static Dimensions transformedDimensions(int width, int height, int quarterTurns) {
        return (quarterTurns & 1) == 0
                ? new Dimensions(width, height)
                : new Dimensions(height, width);
    }

    private static Origin targetOrigin(
            TileBounds sourceBounds,
            WorldFragmentTransform transform,
            Dimensions dimensions
    ) {
        if (transform.pivot().isEmpty()) {
            return new Origin(sourceBounds.minX(), sourceBounds.minY());
        }

        WorldFragmentTransform.Pivot pivot = transform.pivot().orElseThrow();
        int localX = pivot.x() - sourceBounds.minX();
        int localY = pivot.y() - sourceBounds.minY();
        Point transformedPivot = transformLocal(
                localX, localY, sourceBounds.width(), sourceBounds.height(), transform);
        int targetMinX = pivot.x() - transformedPivot.x();
        int targetMinY = pivot.y() - transformedPivot.y();
        if (targetMinX < 0 || targetMinY < 0
                || targetMinX + dimensions.width() - 1 < targetMinX
                || targetMinY + dimensions.height() - 1 < targetMinY) {
            throw new IllegalArgumentException(
                    "Fragment transform would move bounds outside valid world coordinates");
        }
        return new Origin(targetMinX, targetMinY);
    }

    private static void validatePivot(
            TileBounds bounds,
            WorldFragmentTransform transform
    ) {
        if (transform.pivot().isEmpty()) return;
        WorldFragmentTransform.Pivot pivot = transform.pivot().orElseThrow();
        if (!bounds.contains(pivot.x(), pivot.y())) {
            throw new IllegalArgumentException("Fragment pivot must be inside source bounds");
        }
    }

    private static long pack(int plane, int x, int y) {
        return ((long) plane << 56) ^ ((long) x << 28) ^ y;
    }

    private record Point(int x, int y) {
    }

    private record Dimensions(int width, int height) {
        private Dimensions {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Transformed fragment dimensions must be positive");
            }
        }
    }

    private record Origin(int x, int y) {
        private Origin {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("Fragment origin cannot be negative");
            }
        }
    }
}
