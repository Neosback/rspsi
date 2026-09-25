package com.rspsi.studio;

/** Lightweight build metadata safe to use before any project/cache is opened. */
public final class StudioBuildInfo {
    private StudioBuildInfo() {}

    public static String version() {
        String explicit = System.getProperty("openrune.studio.version");
        if (explicit != null && !explicit.isBlank()) return explicit;

        Package pkg = StudioBuildInfo.class.getPackage();
        String implementation = pkg == null ? null : pkg.getImplementationVersion();
        return implementation == null || implementation.isBlank() ? "dev" : implementation;
    }

    public static String displayVersion() {
        return "OpenRune Content Studio  v" + version();
    }
}
