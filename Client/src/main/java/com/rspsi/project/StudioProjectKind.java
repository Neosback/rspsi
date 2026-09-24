package com.rspsi.project;

/** Top-level source kinds supported by the project-first Studio launcher. */
public enum StudioProjectKind {
    STANDALONE_OSRS_CACHE("Standalone OSRS Cache"),
    OPENRUNE_SERVER("OpenRune Server");

    private final String displayName;

    StudioProjectKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
