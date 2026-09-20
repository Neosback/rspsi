package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.PluginPermission;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PluginManifestCodecTest {
    @Test
    void parsesManagedManifestAndRepositoryRelease() {
        String manifestJson = """
                {
                  "schemaVersion": 1,
                  "id": "community.brushes",
                  "name": "Community Brushes",
                  "version": "1.4.0",
                  "apiVersion": 1,
                  "minimumStudioVersion": "1.0.0",
                  "dependencies": [
                    {"id":"base.palette","version":"^2.0.0","optional":false}
                  ],
                  "permissions": ["WORLD_EDIT", "ASSET_READ"]
                }
                """;
        PluginManifestCodec codec = new PluginManifestCodec();
        ExternalPluginManifest manifest = codec.readManifest(new ByteArrayInputStream(
                manifestJson.getBytes(StandardCharsets.UTF_8)));

        assertEquals("community.brushes", manifest.id());
        assertEquals(SemanticVersion.parse("1.4.0"), manifest.version());
        assertEquals(PluginPermission.WORLD_EDIT,
                manifest.permissions().stream().filter(p -> p == PluginPermission.WORLD_EDIT)
                        .findFirst().orElseThrow());
        assertTrue(manifest.dependencies().get(0).version()
                .matches(SemanticVersion.parse("2.7.1")));

        String sha = "a".repeat(64);
        String repositoryJson = """
                {"schemaVersion":1,"plugins":[{
                  "manifest": %s,
                  "downloadUrl":"artifacts/community-brushes.jar",
                  "sha256":"%s"
                }]}
                """.formatted(manifestJson, sha);
        PluginRepositoryIndex index = codec.readRepository(new ByteArrayInputStream(
                repositoryJson.getBytes(StandardCharsets.UTF_8)),
                URI.create("https://plugins.example/index.json"));
        assertEquals(URI.create("https://plugins.example/artifacts/community-brushes.jar"),
                index.plugins().get(0).downloadUri());
    }
}
