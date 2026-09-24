package com.rspsi.osrs.rules.terrain;

import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.TerrainNormal;

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
        TerrainNormal normal = calculateCornerNormal(
                heightDeltaX, heightDeltaY, profile.heightScale());

        int dot = normal.x() * profile.lightX()
                + normal.y() * profile.lightY()
                + normal.z() * profile.lightZ();
        int baseLight = (int) ((double) dot / profile.lightIntensity()) + profile.ambient();

        return baseLight - shadowPenalty;
    }

    /**
     * Calculates the same normalized slope vector used by client-style terrain
     * lighting. Keeping the normal derivation here prevents future GPU lighting
     * from drifting from the vanilla CPU lighting oracle.
     */
    public static TerrainNormal calculateCornerNormal(
            int heightDeltaX,
            int heightDeltaY,
            int heightScale
    ) {
        if (heightScale <= 0) {
            throw new IllegalArgumentException("Terrain normal height scale must be positive");
        }
        int normalLength = (int) Math.sqrt((long) heightDeltaY * heightDeltaY
                + (long) heightDeltaX * heightDeltaX + heightScale);
        if (normalLength == 0) normalLength = 1;

        return new TerrainNormal(
                (heightDeltaX << 8) / normalLength,
                heightScale / normalLength,
                (heightDeltaY << 8) / normalLength,
                1);
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
