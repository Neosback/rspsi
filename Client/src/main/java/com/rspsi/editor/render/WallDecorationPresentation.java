package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;

/**
 * Renderer-neutral identity for an OSRS wall-decoration renderable.
 *
 * <p>RuneLite's SceneUploader preserves decorative renderable 1 at the
 * wall-relative offset and renderable 2 at the bare tile position. For the
 * client's orientation-256 (shape 8) pair, Scene changes which renderable is
 * submitted first according to the camera side. This record preserves enough
 * information for every backend to reproduce that ordering without depending
 * on RuneLite types.</p>
 */
public record WallDecorationPresentation(
        Part part,
        int offsetX,
        int offsetZ,
        int orientation,
        boolean cameraOrdered
) {
    public enum Part {
        NONE,
        SINGLE,
        PRIMARY,
        SECONDARY
    }

    private static final WallDecorationPresentation NONE =
            new WallDecorationPresentation(Part.NONE, 0, 0, 0, false);

    public WallDecorationPresentation {
        part = Objects.requireNonNull(part, "part");
        if (orientation < 0 || orientation > 3) {
            throw new IllegalArgumentException("Wall-decoration orientation must be in [0, 3]");
        }
        if (part == Part.NONE && (offsetX != 0 || offsetZ != 0 || cameraOrdered)) {
            throw new IllegalArgumentException("Non-decoration presentation cannot carry wall metadata");
        }
        if (cameraOrdered && part != Part.PRIMARY && part != Part.SECONDARY) {
            throw new IllegalArgumentException("Only a primary/secondary pair can be camera ordered");
        }
    }

    public static WallDecorationPresentation none() {
        return NONE;
    }

    public static WallDecorationPresentation single(int offsetX, int offsetZ, int orientation) {
        return new WallDecorationPresentation(Part.SINGLE, offsetX, offsetZ, orientation & 3, false);
    }

    public static WallDecorationPresentation primary(int offsetX, int offsetZ, int orientation) {
        return new WallDecorationPresentation(Part.PRIMARY, offsetX, offsetZ, orientation & 3, true);
    }

    public static WallDecorationPresentation secondary(int orientation) {
        return new WallDecorationPresentation(Part.SECONDARY, 0, 0, orientation & 3, true);
    }

    public boolean isDecoration() {
        return part != Part.NONE;
    }

    /**
     * Returns the stable tie-break rank for this renderable at the current
     * camera. Lower values draw first.
     */
    public int cameraOrder(WorldTileAddress tile, CameraState camera) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(camera, "camera");
        if (!cameraOrdered) return 0;

        float centerX = tile.worldX() * 128.0f + 64.0f;
        float centerZ = tile.worldY() * 128.0f + 64.0f;
        float dx = centerX - camera.x();
        float dz = centerZ - camera.z();

        float transformedX = (orientation == 1 || orientation == 2) ? -dx : dx;
        float transformedZ = (orientation == 2 || orientation == 3) ? -dz : dz;

        // RuneLite-melxin/runescape-client/Scene.java orientation == 256:
        // primary is submitted in the first tile pass when transformedZ < transformedX;
        // the opposite renderable is submitted by the later pass.
        boolean primaryFirst = transformedZ < transformedX;
        if (part == Part.PRIMARY) return primaryFirst ? 0 : 1;
        return primaryFirst ? 1 : 0;
    }
}
