package com.rspsi.editor.plugin.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Fetches versioned plugin repository manifests over HTTP(S) or file URIs. */
public final class PluginRepositoryClient {
    private final HttpClient http;
    private final PluginManifestCodec codec;

    public PluginRepositoryClient() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build(), new PluginManifestCodec());
    }

    PluginRepositoryClient(HttpClient http, PluginManifestCodec codec) {
        this.http = Objects.requireNonNull(http, "http");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public PluginRepositoryIndex fetch(URI repositoryUri) {
        Objects.requireNonNull(repositoryUri, "repositoryUri");
        if ("file".equalsIgnoreCase(repositoryUri.getScheme())) {
            try (InputStream input = java.nio.file.Files.newInputStream(
                    java.nio.file.Path.of(repositoryUri))) {
                return codec.readRepository(input, repositoryUri);
            } catch (IOException error) {
                throw new IllegalStateException("Unable to read plugin repository " + repositoryUri, error);
            }
        }
        if (!"http".equalsIgnoreCase(repositoryUri.getScheme())
                && !"https".equalsIgnoreCase(repositoryUri.getScheme())) {
            throw new IllegalArgumentException("Unsupported plugin repository URI: " + repositoryUri);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(repositoryUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("Plugin repository returned HTTP "
                        + response.statusCode() + ": " + repositoryUri);
            }
            try (InputStream input = response.body()) {
                return codec.readRepository(input, repositoryUri);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to fetch plugin repository " + repositoryUri, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Plugin repository request interrupted", error);
        }
    }
}
