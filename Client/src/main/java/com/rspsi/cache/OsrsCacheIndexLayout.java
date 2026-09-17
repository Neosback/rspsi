package com.rspsi.cache;

/**
 * DAT2 index identities used by the supported OSRS cache profile.
 *
 * <p>These are semantic names rather than a renderer convenience. Keeping
 * them explicit prevents the legacy 317 animation/skeleton ordering from
 * being applied to modern OSRS caches.</p>
 */
public final class OsrsCacheIndexLayout {
    public static final int ANIMATIONS = 0;
    public static final int SKELETONS = 1;
    public static final int CONFIGS = 2;
    public static final int MAPS = 5;
    public static final int MODELS = 7;
    public static final int SPRITES = 8;
    public static final int TEXTURES = 9;

    private OsrsCacheIndexLayout() {
    }
}
