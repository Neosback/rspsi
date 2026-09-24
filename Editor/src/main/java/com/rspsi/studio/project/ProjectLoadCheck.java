package com.rspsi.studio.project;

import java.util.Objects;

public record ProjectLoadCheck(
        ProjectLoadStep step,
        ProjectLoadCheckState state,
        String detail) {

    public ProjectLoadCheck {
        step = Objects.requireNonNull(step, "step");
        state = Objects.requireNonNull(state, "state");
        detail = detail == null ? "" : detail.trim();
    }
}
