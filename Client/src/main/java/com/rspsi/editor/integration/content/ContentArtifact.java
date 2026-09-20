package com.rspsi.editor.integration.content;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** One declarative content artifact discovered without interpreting source code. */
public record ContentArtifact(
        Path path,
        String format,
        Optional<ContentCapability> capability,
        boolean recognized,
        String providerId) {

    public ContentArtifact {
        path = Objects.requireNonNull(path, "path");
        format = Objects.requireNonNullElse(format, "").trim().toLowerCase();
        capability = capability == null ? Optional.empty() : capability;
        providerId = Objects.requireNonNullElse(providerId, "");
    }

    public static ContentArtifact unknown(Path path, String format) {
        return new ContentArtifact(path, format, Optional.empty(), false, "");
    }
}
