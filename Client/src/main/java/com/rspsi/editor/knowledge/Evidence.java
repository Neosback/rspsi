package com.rspsi.editor.knowledge;

import java.util.Objects;

/**
 * An individual evidentiary signal contributing to a classification or inferred fact.
 *
 * @param description human-readable explanation of the signal (e.g. "Name matches 'oak'", "Has 'Chop down' action")
 * @param weight relative influence or confidence contribution of this signal (0.0 to 1.0)
 */
public record Evidence(String description, float weight) {
    public Evidence {
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Evidence description cannot be blank");
        }
        if (weight < 0.0f || weight > 1.0f) {
            throw new IllegalArgumentException("Evidence weight must be between 0.0 and 1.0, was " + weight);
        }
    }

    public static Evidence of(String description, float weight) {
        return new Evidence(description, weight);
    }
}
