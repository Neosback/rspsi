package com.rspsi.server;

import java.time.Duration;
import java.util.List;

/** Result of running one configured server build task. */
public record ServerBuildResult(
        int exitCode,
        boolean timedOut,
        Duration duration,
        List<String> output) {
    public ServerBuildResult {
        duration = duration == null ? Duration.ZERO : duration;
        output = List.copyOf(output == null ? List.of() : output);
    }

    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
