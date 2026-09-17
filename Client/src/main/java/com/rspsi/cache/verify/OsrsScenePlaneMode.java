package com.rspsi.cache.verify;

/** Declares whether an external scene fixture uses authored or relinked planes. */
public enum OsrsScenePlaneMode {
    AUTHORED,
    EFFECTIVE;

    public static OsrsScenePlaneMode parse(String value) {
        if (value == null || value.isBlank()) return AUTHORED;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Scene geometry plane mode must be AUTHORED or EFFECTIVE", exception);
        }
    }
}
