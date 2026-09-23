package com.rspsi.editor.render;

import java.util.Optional;

/**
 * RuneLite standard vs extended-scene coordinate contract.
 *
 * <p>RuneLite exposes a 104x104 normal scene and a 184x184 extended scene.
 * The normal scene is centered inside the extended coordinate space, producing
 * the documented offset of 40 tiles on each axis. This class intentionally
 * models coordinates only; loading/walking the extended border remains the
 * separately tracked {@code scene.extendedTiles} rendering task.</p>
 */
public record ExtendedSceneLayout(int sceneSize, int extendedSceneSize) {
    public static final int RUNELITE_SCENE_SIZE = 104;
    public static final int RUNELITE_EXTENDED_SCENE_SIZE = 184;

    public ExtendedSceneLayout {
        if (sceneSize <= 0 || extendedSceneSize < sceneSize) {
            throw new IllegalArgumentException("Invalid scene dimensions");
        }
        if (((extendedSceneSize - sceneSize) & 1) != 0) {
            throw new IllegalArgumentException("Extended scene must center the normal scene exactly");
        }
    }

    public static ExtendedSceneLayout runeLite() {
        return new ExtendedSceneLayout(RUNELITE_SCENE_SIZE, RUNELITE_EXTENDED_SCENE_SIZE);
    }

    public int offset() {
        return (extendedSceneSize - sceneSize) / 2;
    }

    public boolean containsScene(int x, int y) {
        return x >= 0 && x < sceneSize && y >= 0 && y < sceneSize;
    }

    public boolean containsExtended(int x, int y) {
        return x >= 0 && x < extendedSceneSize && y >= 0 && y < extendedSceneSize;
    }

    public Point toExtended(int sceneX, int sceneY) {
        if (!containsScene(sceneX, sceneY)) {
            throw new IllegalArgumentException("Scene coordinate lies outside the normal scene");
        }
        return new Point(sceneX + offset(), sceneY + offset());
    }

    public Optional<Point> toScene(int extendedX, int extendedY) {
        if (!containsExtended(extendedX, extendedY)) {
            return Optional.empty();
        }
        int x = extendedX - offset();
        int y = extendedY - offset();
        if (!containsScene(x, y)) {
            return Optional.empty();
        }
        return Optional.of(new Point(x, y));
    }

    public boolean isBorderCoordinate(int extendedX, int extendedY) {
        return containsExtended(extendedX, extendedY)
                && toScene(extendedX, extendedY).isEmpty();
    }

    public record Point(int x, int y) {
    }
}
