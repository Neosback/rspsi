package com.rspsi.editor.plugin.runtime;

import java.net.URI;
import java.util.Objects;

/** One downloadable plugin release from an update repository. */
public record PluginRepositoryEntry(
        ExternalPluginManifest manifest,
        URI downloadUri,
        String sha256) {
    public PluginRepositoryEntry {
        manifest = Objects.requireNonNull(manifest, "manifest");
        downloadUri = Objects.requireNonNull(downloadUri, "downloadUri");
        sha256 = Objects.requireNonNull(sha256, "sha256").trim().toLowerCase();
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Plugin repository SHA-256 must contain 64 hex characters");
        }
    }
}
