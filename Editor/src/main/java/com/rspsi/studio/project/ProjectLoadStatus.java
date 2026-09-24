package com.rspsi.studio.project;

import com.rspsi.project.StudioProjectDescriptor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProjectLoadStatus(
        ProjectLoadState state,
        StudioProjectDescriptor project,
        List<ProjectLoadCheck> checks,
        String message,
        Throwable failure) {

    public ProjectLoadStatus {
        state = Objects.requireNonNull(state, "state");
        checks = List.copyOf(checks == null ? List.of() : checks);
        message = message == null ? "" : message.trim();
    }

    public Optional<Throwable> failureOptional() {
        return Optional.ofNullable(failure);
    }

    public double progress() {
        if (checks.isEmpty()) return 0.0;
        long done = checks.stream()
                .filter(check -> check.state() == ProjectLoadCheckState.SUCCESS
                        || check.state() == ProjectLoadCheckState.SKIPPED
                        || check.state() == ProjectLoadCheckState.FAILED)
                .count();
        return Math.min(1.0, Math.max(0.0, done / (double) checks.size()));
    }

    public static ProjectLoadStatus idle() {
        return new ProjectLoadStatus(ProjectLoadState.IDLE, null, List.of(),
                "No project selected", null);
    }
}
