package com.rspsi.studio.project;

/** Ordered user-visible project loading checks. */
public enum ProjectLoadStep {
    READ_PROJECT("Read project"),
    VALIDATE_SOURCE("Validate project source"),
    INSPECT_INTEGRATION("Scan OpenRune project"),
    RESOLVE_CACHE("Resolve cache"),
    OPEN_CACHE("Open cache filesystem"),
    PREPARE_CACHE("Prepare definitions and assets"),
    BIND_SERVICES("Bind project services"),
    READY("Project ready");

    private final String label;

    ProjectLoadStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
