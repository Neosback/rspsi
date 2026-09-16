package com.rspsi.cache.verify;

import java.util.Objects;

/** One auditable result in the OSRS compatibility gate. */
public record VerificationCheck(String id, Status status, String detail) {
    public VerificationCheck {
        id = requireText(id, "id");
        status = Objects.requireNonNull(status, "status");
        detail = requireText(detail, "detail");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return trimmed;
    }

    public enum Status {
        PASS,
        WARN,
        FAIL,
        NOT_RUN
    }
}
