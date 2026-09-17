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
        return build(source, grid, width, length, planes,
                InstanceObjectFootprintResolver.unit(), InstanceGeneratedHeightProvider.required());
    }

    /** Materializes an instance using definition-derived object footprints. */
    public WorldDocument build(WorldRegionWindow source, InstanceChunkGrid grid,
                               int width, int length, int planes,
                               InstanceObjectFootprintResolver footprintResolver) {
        return build(source, grid, width, length, planes, footprintResolver,
                InstanceGeneratedHeightProvider.required());
    }

    /** Materializes an instance with explicit cache-independent height semantics. */
    public WorldDocument build(WorldRegionWindow source, InstanceChunkGrid grid,
                               int width, int length, int planes,
                               InstanceObjectFootprintResolver footprintResolver,
                               InstanceGeneratedHeightProvider generatedHeightProvider) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(grid, "grid");
        InstanceObjectFootprintResolver.requireNonNull(footprintResolver);
        Objects.requireNonNull(generatedHeightProvider, "generatedHeightProvider");
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Instance document dimensions must be positive");
        }
        WorldDocument result = new WorldDocument(width, length, planes);
        for (InstanceChunkTransform transform : grid.transforms()) {
            if (transform.template().targetPlane() >= planes) {
                throw new IllegalArgumentException("Instance target plane is outside destination: "
                        + transform.template().targetPlane());
            }
            copyChunk(source, grid, transform, result, footprintResolver, generatedHeightProvider);
        }
        return result;
    }

    private static void copyChunk(WorldRegionWindow source, InstanceChunkGrid grid,
                                  InstanceChunkTransform transform, WorldDocument result,
                                  InstanceObjectFootprintResolver footprintResolver,
                                  InstanceGeneratedHeightProvider generatedHeightProvider) {
        InstanceChunkTemplate template = transform.template();
        for (int localX = 0; localX < InstanceChunkTemplate.CHUNK_SIZE; localX++) {
            for (int localY = 0; localY < InstanceChunkTemplate.CHUNK_SIZE; localY++) {
                int sourceX = template.sourceOriginX() + localX;
                int sourceY = template.sourceOriginY() + localY;
                WorldTileSource sourceTile = source.tileSource(
                        template.sourcePlane(), sourceX, sourceY).orElse(null);
                if (sourceTile == null) continue;

                TileCoordinate sourceCoordinate = new TileCoordinate(template.sourcePlane(), sourceX, sourceY);
                TileCoordinate destination = transform.sourceToScene(sourceCoordinate);
                int destinationX = destination.x() - grid.sceneBaseX();
                int destinationY = destination.y() - grid.sceneBaseY();
                if (destinationX < 0 || destinationX >= result.width()
                        || destinationY < 0 || destinationY >= result.length()) {
                    continue;
                }
                Tile target = result.tile(destination.plane(), destinationX, destinationY);
                TileSnapshot rotated = rotate(sourceTile.snapshot(), transform);
                target.restore(withSouthWestHeight(rotated,
                        replayedHeight(sourceTile.heightSource(), template.targetPlane(), result,
                                destinationX, destinationY, sourceX, sourceY,
                                rotated.southWestHeight(), generatedHeightProvider)));
                target.heightSource(sourceTile.heightSource());
            }
        }

        // Add locations only after all terrain tiles have been materialized;
        // a rotated multi-tile anchor can land on a tile processed later by
        // the terrain pass.
        for (int localX = 0; localX < InstanceChunkTemplate.CHUNK_SIZE; localX++) {
            for (int localY = 0; localY < InstanceChunkTemplate.CHUNK_SIZE; localY++) {
                int sourceX = template.sourceOriginX() + localX;
                int sourceY = template.sourceOriginY() + localY;
                TileSnapshot sourceTile = source.tile(template.sourcePlane(), sourceX, sourceY).orElse(null);
                if (sourceTile == null) continue;
                for (WorldObject object : sourceTile.objects()) {
                    InstanceObjectFootprintResolver.Footprint footprint = footprintResolver.resolve(object);
                    if (footprint == null) {
                        throw new IllegalArgumentException("Footprint resolver returned null for object " + object.id());
                    }
                    WorldObject worldObject = regionLocalObjectToWorld(object, sourceX, sourceY);
                    WorldObject mapped = transform.sourceObjectToScene(worldObject,
                            footprint.width(), footprint.length());
                    int objectX = mapped.x() - grid.sceneBaseX();
                    int objectY = mapped.y() - grid.sceneBaseY();
                    int placedWidth = (mapped.rotation() & 1) == 1
                            ? footprint.length() : footprint.width();
                    int placedLength = (mapped.rotation() & 1) == 1
                            ? footprint.width() : footprint.length();
                    // The reference scene builder does not add locations whose
                    // anchor is on the outer scene border; those cells are
                    // reserved for the scene's shared edge geometry.
                    if (mapped.plane() < 0 || mapped.plane() >= result.planes()
                            || objectX <= 0 || objectX >= result.width() - 1
                            || objectY <= 0 || objectY >= result.length() - 1
                            || objectX + placedWidth > result.width()
                            || objectY + placedLength > result.length()) {
                        continue;
                    }
                    Tile target = result.tile(mapped.plane(), objectX, objectY);
                    TileSnapshot snapshot = target.snapshot();
                    TerrainHeightSource heightSource = target.heightSource();
                    List<WorldObject> objects = new ArrayList<>(snapshot.objects());
                    objects.add(new WorldObject(mapped.id(), mapped.type(), mapped.rotation(),
                            mapped.plane(), objectX, objectY));
                    target.restore(new TileSnapshot(snapshot.southWestHeight(), snapshot.southEastHeight(),
                            snapshot.northEastHeight(), snapshot.northWestHeight(), snapshot.underlayId(),
                            snapshot.overlayId(), snapshot.overlayShape(), snapshot.overlayRotation(),
                            snapshot.flags(), objects));
                    target.heightSource(heightSource);
                }
            }
        }
    }

    /** Region archives store object anchors in 0..63 local coordinates. */
    private static WorldObject regionLocalObjectToWorld(WorldObject object, int sourceX, int sourceY) {
        int regionOriginX = (sourceX >> 6) * WorldRegion.REGION_SIZE;
        int regionOriginY = (sourceY >> 6) * WorldRegion.REGION_SIZE;
        return new WorldObject(object.id(), object.type(), object.rotation(), object.plane(),
                regionOriginX + object.x(), regionOriginY + object.y());
    }

    private static TileSnapshot rotate(TileSnapshot source, InstanceChunkTransform transform) {
        int rotation = transform.template().rotation();
        return new TileSnapshot(
                corner(source, rotation, 0),
                corner(source, rotation, 1),
                corner(source, rotation, 2),
                corner(source, rotation, 3),
                source.underlayId(),
                source.overlayId(),
                source.overlayShape(),
                source.overlayId() == 0 ? 0 : (source.overlayRotation() + rotation) & 3,
                source.flags(),
                List.of());
    }

    private static int replayedHeight(TerrainHeightSource source, int targetPlane,
                                      WorldDocument result, int destinationX, int destinationY,
                                      int sourceX, int sourceY, int authoredHeight,
                                      InstanceGeneratedHeightProvider generatedHeightProvider) {
        if (!source.cacheEncoded()) return authoredHeight;
        if (targetPlane == 0) {
            return source.generated()
                    ? generatedHeightProvider.heightAt(sourceX, sourceY)
                    : -source.explicitValue() * 8;
        }
        int previous = result.tile(targetPlane - 1, destinationX, destinationY)
                .snapshot().southWestHeight();
        return previous - (source.generated() ? 240 : source.explicitValue() * 8);
    }

    private static TileSnapshot withSouthWestHeight(TileSnapshot source, int height) {
        return new TileSnapshot(height, source.southEastHeight(), source.northEastHeight(),
                source.northWestHeight(), source.underlayId(), source.overlayId(),
                source.overlayShape(), source.overlayRotation(), source.flags(), source.objects());
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
