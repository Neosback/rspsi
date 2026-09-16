package com.rspsi.cache;

import java.util.Objects;

/** Identifies the OSRS cache a project or adapter is using. */
public record OsrsCacheMetadata(int revision, Integer subRevision, String fingerprint) {

    public OsrsCacheMetadata {
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS cache revision must be positive");
        }
        if (subRevision != null && subRevision < 0) {
            throw new IllegalArgumentException("OSRS cache subrevision cannot be negative");
        }
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint").trim();
        if (fingerprint.isEmpty()) {
            throw new IllegalArgumentException("OSRS cache fingerprint cannot be empty");
        }
    }
}
