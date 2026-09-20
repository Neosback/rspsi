package com.rspsi.editor.plugin.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/**
 * Staged, SHA-256 verified plugin installer. Downloads never replace an
 * installed artifact until integrity validation succeeds.
 */
public final class PluginPackageInstaller {
    private final HttpClient http;

    public PluginPackageInstaller() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build());
    }

    PluginPackageInstaller(HttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    public Path install(PluginRepositoryEntry release, Path pluginDirectory) {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(pluginDirectory, "pluginDirectory");
        try {
            Files.createDirectories(pluginDirectory);
            String fileName = safeName(release.manifest().id()) + "-"
                    + release.manifest().version() + ".jar";
            Path target = pluginDirectory.resolve(fileName);
            Path staging = Files.createTempFile(pluginDirectory, ".plugin-", ".download");
            try {
                download(release.downloadUri(), staging);
                String actual = PluginHashes.sha256(staging);
                if (!actual.equalsIgnoreCase(release.sha256())) {
                    throw new SecurityException("Plugin SHA-256 mismatch for "
                            + release.manifest().id() + ": expected " + release.sha256()
                            + " but downloaded " + actual);
                }
                try {
                    return Files.move(staging, target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    return Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(staging);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to install plugin "
                    + release.manifest().id(), error);
        }
    }

    private void download(URI uri, Path target) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Files.copy(Path.of(uri), target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported plugin download URI: " + uri);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("Plugin download returned HTTP " + response.statusCode());
            }
            try (InputStream input = response.body()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Plugin download interrupted", error);
        }
    }

    private static String safeName(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
