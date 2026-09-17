package com.rspsi.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Explainable result of detecting a server/project layout. */
public record ServerDetection(
        boolean matched,
        int confidence,
        List<String> evidence,
        String message) {
    public ServerDetection {
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence must be in [0, 100]");
        }
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        message = Objects.requireNonNull(message, "message").trim();
        if (message.isEmpty()) throw new IllegalArgumentException("message cannot be empty");
    }

    public static ServerDetection notMatched(Path root, String message) {
        Objects.requireNonNull(root, "root");
        return new ServerDetection(false, 0, List.of(), message);
    }
}
