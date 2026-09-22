package com.rspsi.editor.render;

/**
 * RuneLite-compatible scene footprint and orientation metadata for one game object.
 *
 * <p>The occupied scene-tile rectangle is deliberately separate from model geometry
 * bounds. RuneScape's Scene stores start/end tiles independently from the renderable's
 * vertex AABB and uses those tile bounds for traversal, ordering and occlusion.</p>
 */
public record GameObjectSceneMetadata(
        boolean present,
        int minTileX,
        int minTileY,
        int maxTileX,
        int maxTileY,
        int modelOrientation,
        int orientation
) {
    private static final GameObjectSceneMetadata NONE =
            new GameObjectSceneMetadata(false, 0, 0, 0, 0, 0, 0);

    public GameObjectSceneMetadata {
        if (present) {
            if (minTileX < 0 || minTileY < 0 || maxTileX < minTileX || maxTileY < minTileY) {
                throw new IllegalArgumentException("Invalid game-object scene footprint");
            }
            if (modelOrientation < 0 || modelOrientation >= 2048
                    || orientation < 0 || orientation >= 4096) {
                throw new IllegalArgumentException("Game-object orientations must be valid JAU values");
            }
        }
    }

    /** No game-object scene metadata (walls, wall decorations, ground decorations, terrain). */
    public static GameObjectSceneMetadata none() {
        return NONE;
    }

    /**
     * Creates client-style scene bounds from an already-rotated footprint.
     *
     * <p>RuneLite exposes {@code GameObject.getOrientation()} as
     * {@code rotation * 512 + modelOrientation}. Static map objects normally use a
     * model orientation of 0; shape 11 uses the client's extra 256 JAU.</p>
     */
    public static GameObjectSceneMetadata of(int minTileX, int minTileY,
                                             int sizeX, int sizeY,
                                             int rotation, int modelOrientation) {
        if (sizeX <= 0 || sizeY <= 0) {
            throw new IllegalArgumentException("Game-object footprint must be positive");
        }
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Game-object rotation must be 0..3");
        }
        int orientation = rotation * 512 + modelOrientation;
        return new GameObjectSceneMetadata(true, minTileX, minTileY,
                minTileX + sizeX - 1, minTileY + sizeY - 1,
                modelOrientation, orientation);
    }

    public int sizeX() {
        return present ? maxTileX - minTileX + 1 : 0;
    }

    public int sizeY() {
        return present ? maxTileY - minTileY + 1 : 0;
    }

    /** Map placement rotation (0..3), separated from the model's own JAU orientation. */
    public int rotation() {
        return present ? (orientation - modelOrientation) / 512 : 0;
    }

    public boolean contains(int tileX, int tileY) {
        return present && tileX >= minTileX && tileX <= maxTileX
                && tileY >= minTileY && tileY <= maxTileY;
    }

    /** Rebase local/padded scene coordinates without changing footprint or orientation. */
    public GameObjectSceneMetadata translated(int deltaX, int deltaY) {
        if (!present || (deltaX == 0 && deltaY == 0)) return this;
        return new GameObjectSceneMetadata(true,
                minTileX + deltaX, minTileY + deltaY,
                maxTileX + deltaX, maxTileY + deltaY,
                modelOrientation, orientation);
    }
}
