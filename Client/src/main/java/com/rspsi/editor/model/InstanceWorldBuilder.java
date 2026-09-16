package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Materializes an instance template grid into a canonical editor document.
 *
 * <p>Cache archives and client scene classes stop at {@link WorldRegionWindow}:
 * this builder applies the 8x8 chunk transform and produces ordinary
 * {@link WorldDocument} tiles for scene construction and editing. Missing
 * source regions remain holes with the destination document's default tiles.</p>
 */
public final class InstanceWorldBuilder {
    public WorldDocument build(WorldRegionWindow source, InstanceChunkGrid grid,
                                int width, int length, int planes) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(grid, "grid");
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Instance document dimensions must be positive");
        }
        WorldDocument result = new WorldDocument(width, length, planes);
        for (InstanceChunkTransform transform : grid.transforms()) {
            if (transform.template().targetPlane() >= planes) {
                throw new IllegalArgumentException("Instance target plane is outside destination: "
                        + transform.template().targetPlane());
            }
            copyChunk(source, grid, transform, result);
        }
        return result;
    }

    private static void copyChunk(WorldRegionWindow source, InstanceChunkGrid grid,
                                  InstanceChunkTransform transform, WorldDocument result) {
        InstanceChunkTemplate template = transform.template();
        for (int localX = 0; localX < InstanceChunkTemplate.CHUNK_SIZE; localX++) {
            for (int localY = 0; localY < InstanceChunkTemplate.CHUNK_SIZE; localY++) {
                int sourceX = template.sourceOriginX() + localX;
                int sourceY = template.sourceOriginY() + localY;
                TileSnapshot sourceTile = source.tile(template.sourcePlane(), sourceX, sourceY).orElse(null);
                if (sourceTile == null) continue;

                TileCoordinate sourceCoordinate = new TileCoordinate(template.sourcePlane(), sourceX, sourceY);
                TileCoordinate destination = transform.sourceToScene(sourceCoordinate);
                int destinationX = destination.x() - grid.sceneBaseX();
                int destinationY = destination.y() - grid.sceneBaseY();
                if (destinationX < 0 || destinationX >= result.width()
                        || destinationY < 0 || destinationY >= result.length()) {
                    continue;
                }
                result.tile(destination.plane(), destinationX, destinationY)
                        .restore(rotate(sourceTile, transform, grid));
            }
        }
    }

    private static TileSnapshot rotate(TileSnapshot source, InstanceChunkTransform transform,
                                       InstanceChunkGrid grid) {
        int rotation = transform.template().rotation();
        List<WorldObject> objects = new ArrayList<>(source.objects().size());
        for (WorldObject object : source.objects()) {
            WorldObject mapped = transform.sourceObjectToScene(object);
            objects.add(new WorldObject(mapped.id(), mapped.type(), mapped.rotation(), mapped.plane(),
                    mapped.x() - grid.sceneBaseX(), mapped.y() - grid.sceneBaseY()));
        }
        return new TileSnapshot(
                corner(source, rotation, 0),
                corner(source, rotation, 1),
                corner(source, rotation, 2),
                corner(source, rotation, 3),
                source.underlayId(),
                source.overlayId(),
                source.overlayShape(),
                (source.overlayRotation() + rotation) & 3,
                source.flags(),
                objects);
    }

    /** Returns the destination corner value: SW=0, SE=1, NE=2, NW=3. */
    private static int corner(TileSnapshot source, int rotation, int destinationCorner) {
        // A clockwise chunk transform maps destination corners to source
        // corners [SE, NE, NW, SW]. Repeating that permutation gives the
        // 180- and 270-degree cases and matches the client chunk convention.
        int sourceCorner = (destinationCorner + rotation) & 3;
        return switch (sourceCorner) {
            case 0 -> source.southWestHeight();
            case 1 -> source.southEastHeight();
            case 2 -> source.northEastHeight();
            default -> source.northWestHeight();
        };
    }
}
