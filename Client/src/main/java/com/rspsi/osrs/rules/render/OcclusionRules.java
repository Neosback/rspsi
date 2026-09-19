package com.rspsi.osrs.rules.render;

/**
 * Formal OSRS occlusion and face culling rules.
 */
public final class OcclusionRules {
    private OcclusionRules() {}

    /**
     * Determines whether two coplanar duplicate faces meeting at a model seam should be occluded.
     */
    public static boolean shouldOccludeDuplicateFace(int renderType) {
        return renderType == 2;
    }
}
