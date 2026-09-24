package com.rspsi.studio;

/** Immutable status snapshot rendered by the pre-dashboard project loading gate. */
public record ProjectLoadStatus(
        Phase phase,
        double progress,
        String message,
        String detail,
        Throwable failure) {

    public enum Phase {
        READ_DESCRIPTOR,
        VALIDATE_PROJECT,
        INSPECT_INTEGRATION,
        RESOLVE_CACHE_ROLES,
        OPEN_CACHE_FILESYSTEM,
        PREPARE_DEFINITIONS,
        BIND_REQUIRED_PROJECT_SERVICES,
        READY,
        FAILED
    }

    public ProjectLoadStatus {
        if (phase == null) phase = Phase.READ_DESCRIPTOR;
        progress = Math.max(0.0, Math.min(1.0, progress));
        message = message == null ? "" : message;
        detail = detail == null ? "" : detail;
    }

    public static ProjectLoadStatus initial() {
        return new ProjectLoadStatus(
                Phase.READ_DESCRIPTOR, 0.05, "Reading project descriptor...", "", null);
    }

    public boolean failed() {
        return phase == Phase.FAILED;
    }
}
