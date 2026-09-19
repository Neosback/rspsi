package com.rspsi.editor.plugin;

import java.nio.file.Path;
import java.util.Objects;

/** Records why a candidate plugin JAR failed to load, instead of discarding the cause. */
public record PluginLoadFailure(Path jarPath, String message, Throwable cause) {
    public PluginLoadFailure {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(cause, "cause");
    }
}
