package com.rspsi.editor.model;

import java.util.Objects;

/**
 * Maps one source 8x8 OSRS map chunk into a scene instance.
 *
 * <p>Source coordinates are canonical world coordinates. Scene coordinates
 * are world-space coordinates based at the scene base, which keeps this type
 * useful to inspectors without introducing a renderer-specific scene model.</p>
 */
public final class InstanceChunkTransform {
    private final InstanceChunkTemplate template;
    private final int sceneBaseX;
    private final int sceneBaseY;

    public InstanceChunkTransform(InstanceChunkTemplate template, int sceneBaseX, int sceneBaseY) {
        this.template = Objects.requireNonNull(template, "template");
        if (sceneBaseX < 0 || sceneBaseY < 0) {
            throw new IllegalArgumentException("Scene base coordinates cannot be negative");
        }
        this.sceneBaseX = sceneBaseX;
        this.sceneBaseY = sceneBaseY;
    }

    public InstanceChunkTemplate template() {
        return template;
    }

    public int sceneBaseX() {
        return sceneBaseX;
    }

    public int sceneBaseY() {
        return sceneBaseY;
    }

    /** Maps a source world tile into the target scene tile. */
    public TileCoordinate sourceToScene(TileCoordinate source) {
        Objects.requireNonNull(source, "source");
        requireSourceTile(source);
        int localX = source.x() - template.sourceOriginX();
        int localY = source.y() - template.sourceOriginY();
        int[] rotated = rotate(localX, localY, template.rotation());
        return new TileCoordinate(template.targetPlane(),
                template.sceneOriginX(sceneBaseX) + rotated[0],
                template.sceneOriginY(sceneBaseY) + rotated[1]);
    }

    /** Maps a target scene tile back to its source world tile. */
    public TileCoordinate sceneToSource(TileCoordinate scene) {
        Objects.requireNonNull(scene, "scene");
        requireSceneTile(scene);
        int localX = scene.x() - template.sceneOriginX(sceneBaseX);
        int localY = scene.y() - template.sceneOriginY(sceneBaseY);
        int[] sourceLocal = rotate(localX, localY, (4 - template.rotation()) & 3);
        return new TileCoordinate(template.sourcePlane(),
                template.sourceOriginX() + sourceLocal[0],
                template.sourceOriginY() + sourceLocal[1]);
    }

    /** Applies the instance rotation to a location's orientation. */
    public int sourceObjectRotationToScene(int sourceRotation) {
        if (sourceRotation < 0 || sourceRotation > 3) {
            throw new IllegalArgumentException("Object rotation must be between 0 and 3");
        }
        return (sourceRotation + template.rotation()) & 3;
    }

    /** Maps an object anchor and orientation while preserving its identity/type. */
    public WorldObject sourceObjectToScene(WorldObject source) {
        return sourceObjectToScene(source, 1, 1);
    }

    /** Maps an object anchor using its unrotated definition footprint. */
    public WorldObject sourceObjectToScene(WorldObject source, int footprintWidth,
                                           int footprintLength) {
        Objects.requireNonNull(source, "source");
        if (footprintWidth <= 0 || footprintLength <= 0) {
            throw new IllegalArgumentException("Object footprint must be positive");
        }
        requireSourceTile(new TileCoordinate(source.plane(), source.x(), source.y()));
        int localX = source.x() - template.sourceOriginX();
        int localY = source.y() - template.sourceOriginY();
        int[] rotated = rotateObject(localX, localY, template.rotation(),
                footprintWidth, footprintLength, source.rotation());
        TileCoordinate mapped = new TileCoordinate(template.targetPlane(),
                template.sceneOriginX(sceneBaseX) + rotated[0],
                template.sceneOriginY(sceneBaseY) + rotated[1]);
        return new WorldObject(source.id(), source.type(),
                sourceObjectRotationToScene(source.rotation()), mapped.plane(), mapped.x(), mapped.y());
    }

    public boolean containsSource(TileCoordinate source) {
        return source != null && source.plane() == template.sourcePlane()
                && source.x() >= template.sourceOriginX()
                && source.x() < template.sourceOriginX() + InstanceChunkTemplate.CHUNK_SIZE
                && source.y() >= template.sourceOriginY()
                && source.y() < template.sourceOriginY() + InstanceChunkTemplate.CHUNK_SIZE;
    }

    public boolean containsScene(TileCoordinate scene) {
        return scene != null && scene.plane() == template.targetPlane()
                && scene.x() >= template.sceneOriginX(sceneBaseX)
                && scene.x() < template.sceneOriginX(sceneBaseX) + InstanceChunkTemplate.CHUNK_SIZE
                && scene.y() >= template.sceneOriginY(sceneBaseY)
                && scene.y() < template.sceneOriginY(sceneBaseY) + InstanceChunkTemplate.CHUNK_SIZE;
    }

    private void requireSourceTile(TileCoordinate source) {
        if (!containsSource(source)) {
            throw new IndexOutOfBoundsException("Source tile is outside instance chunk: " + source);
        }
    }

    private void requireSceneTile(TileCoordinate scene) {
        if (!containsScene(scene)) {
            throw new IndexOutOfBoundsException("Scene tile is outside instance chunk: " + scene);
        }
    }

    private static int[] rotate(int x, int y, int rotation) {
        return switch (rotation & 3) {
            case 0 -> new int[]{x, y};
            case 1 -> new int[]{y, InstanceChunkTemplate.CHUNK_SIZE - 1 - x};
            case 2 -> new int[]{InstanceChunkTemplate.CHUNK_SIZE - 1 - x,
                    InstanceChunkTemplate.CHUNK_SIZE - 1 - y};
            default -> new int[]{InstanceChunkTemplate.CHUNK_SIZE - 1 - y, x};
        };
    }

    /** Mirrors the client object-anchor rotation, including orientation-swapped dimensions. */
    private static int[] rotateObject(int x, int y, int rotation,
                                      int sizeX, int sizeY, int orientation) {
        if ((orientation & 1) == 1) {
            int temporary = sizeX;
            sizeX = sizeY;
            sizeY = temporary;
        }
        return switch (rotation & 3) {
            case 0 -> new int[]{x, y};
            case 1 -> new int[]{y, InstanceChunkTemplate.CHUNK_SIZE - 1 - x - (sizeX - 1)};
            case 2 -> new int[]{InstanceChunkTemplate.CHUNK_SIZE - 1 - x - (sizeX - 1),
                    InstanceChunkTemplate.CHUNK_SIZE - 1 - y - (sizeY - 1)};
            default -> new int[]{InstanceChunkTemplate.CHUNK_SIZE - 1 - y - (sizeY - 1), x};
        };
    }
}
