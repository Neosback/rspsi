package com.rspsi.editor.paste;

import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.model.WorldFragment;

/**
 * JVM compatibility shell for non-destructive cross-region fragment paste planning.
 *
 * <p>The planning algorithm lives in {@link WorldFragmentPastePlannerSemantics}. This shell keeps
 * the historical static API, constant field, and utility-class construction surface unchanged
 * while the implementation migrates to Kotlin.</p>
 */
public final class WorldFragmentPastePlanner {
    public static final String PRODUCER_ID = "studio.fragment.paste";

    private WorldFragmentPastePlanner() {
    }

    public static WorldFragmentPasteResult plan(
            WorldRegionSessionWindow window,
            WorldFragment fragment,
            int targetX,
            int targetY,
            WorldFragmentPastePolicy policy
    ) {
        return WorldFragmentPastePlannerSemantics.plan(
                window,
                fragment,
                targetX,
                targetY,
                policy,
                "Paste world fragment");
    }

    public static WorldFragmentPasteResult plan(
            WorldRegionSessionWindow window,
            WorldFragment fragment,
            int targetX,
            int targetY,
            WorldFragmentPastePolicy policy,
            String description
    ) {
        return WorldFragmentPastePlannerSemantics.plan(
                window,
                fragment,
                targetX,
                targetY,
                policy,
                description);
    }
}
