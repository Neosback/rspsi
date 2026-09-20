package com.rspsi.editor.integration.content;

import java.nio.file.Path;
import java.util.Objects;

/** One actionable parsing/discovery diagnostic. */
public record ParseDiagnostic(
        Severity severity,
        String code,
        Path path,
        int line,
        String message) {

    public ParseDiagnostic {
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNullElse(code, "content");
        path = Objects.requireNonNull(path, "path");
        if (line < 0) line = 0;
        message = Objects.requireNonNullElse(message, "");
    }

    public enum Severity { INFO, WARNING, ERROR }
}
