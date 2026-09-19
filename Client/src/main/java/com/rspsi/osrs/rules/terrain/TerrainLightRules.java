package com.rspsi.osrs.rules.terrain;

import com.rspsi.editor.render.LightingProfile;

import java.util.Objects;

/**
 * Formal OSRS directional terrain lighting rules and normal calculations.
 */
public final class TerrainLightRules {
    private TerrainLightRules() {}

    /**
     * Calculates the directional lighting scalar for a terrain corner vertex.
     *
     * @param heightDeltaX height difference between adjacent East and West corners
     * @param heightDeltaY height difference between adjacent North and South corners
     * @param profile directional lighting profile
     * @param shadowPenalty accumulated corner shadow penalty from surrounding occluders
     * @return final calculated light intensity
     */
    public static int calculateCornerLight(
            int heightDeltaX,
            int heightDeltaY,
            LightingProfile profile,
            int shadowPenalty
    ) {
        Objects.requireNonNull(profile, "profile");
        int normalLength = (int) Math.sqrt((long) heightDeltaY * heightDeltaY
                + (long) heightDeltaX * heightDeltaX + profile.heightScale());
        if (normalLength == 0) normalLength = 1;

        int normalX = (heightDeltaX << 8) / normalLength;
        int normalY = profile.heightScale() / normalLength;
        int normalZ = (heightDeltaY << 8) / normalLength;

        int dot = normalX * profile.lightX() + normalY * profile.lightY() + normalZ * profile.lightZ();
        int baseLight = (int) ((double) dot / profile.lightIntensity()) + profile.ambient();

        return baseLight - shadowPenalty;
    }

    /**
     * Calculates the 5-point weighted shadow penalty for a shared terrain corner.
     */
    public static int calculateShadowPenalty(
            int centerStrength,
            int westStrength,
            int eastStrength,
            int southStrength,
            int northStrength
    ) {
        return (westStrength >> 2)
                + (eastStrength >> 3)
                + (southStrength >> 2)
                + (northStrength >> 3)
                + (centerStrength >> 1);
    }
}
