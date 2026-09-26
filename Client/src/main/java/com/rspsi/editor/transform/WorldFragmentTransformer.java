package com.rspsi.editor.transform;

import com.rspsi.editor.model.WorldFragment;

/**
 * JVM compatibility shell for canonical world-fragment transforms.
 *
 * <p>The transform algorithm lives in {@link WorldFragmentTransformerSemantics}. This shell keeps
 * the historical Java static API and package-private {@code transformCorners(...)} visibility
 * without widening the helper during the Kotlin migration.</p>
 */
public final class WorldFragmentTransformer {
    private WorldFragmentTransformer() {
    }

    public static WorldFragmentTransformResult transform(
            WorldFragment fragment,
            WorldFragmentTransform transform,
            ObjectFootprintResolver footprints
    ) {
        return WorldFragmentTransformerSemantics.transform(fragment, transform, footprints);
    }

    /** Returns transformed SW, SE, NE, NW heights. */
    static int[] transformCorners(
            int southWest,
            int southEast,
            int northEast,
            int northWest,
            WorldFragmentTransform transform
    ) {
        return WorldFragmentTransformerSemantics.transformCorners(
                southWest,
                southEast,
                northEast,
                northWest,
                transform);
    }
}
