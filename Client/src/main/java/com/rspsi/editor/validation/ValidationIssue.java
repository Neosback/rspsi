package com.rspsi.editor.validation;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;
import java.util.Optional;

/** One actionable world/document validation diagnostic. */
public record ValidationIssue(
        Severity severity,
        String code,
        String message,
        TileCoordinate location
) {
    public enum Severity { WARNING, ERROR }

    public ValidationIssue {
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }

    public Optional<TileCoordinate> locationOptional() {
        return Optional.ofNullable(location);
    }
}
