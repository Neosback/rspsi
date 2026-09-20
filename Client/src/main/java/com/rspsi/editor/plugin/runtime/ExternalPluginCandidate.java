package com.rspsi.editor.plugin.runtime;

import java.nio.file.Path;
import java.util.Objects;

/** Managed plugin JAR plus parsed manifest and immutable integrity hash. */
public record ExternalPluginCandidate(Path jarPath, ExternalPluginManifest manifest, String sha256) {
    public ExternalPluginCandidate {
        jarPath = Objects.requireNonNull(jarPath, "jarPath");
        manifest = Objects.requireNonNull(manifest, "manifest");
        sha256 = Objects.requireNonNull(sha256, "sha256").toLowerCase();
    }
}
