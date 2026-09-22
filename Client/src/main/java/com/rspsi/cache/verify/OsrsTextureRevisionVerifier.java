package com.rspsi.cache.verify;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.store.CacheStoreFactory;
import com.rspsi.cache.store.OpenRuneCacheStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Focused real-cache texture parity entry point.
 *
 * <p>This verifier intentionally does not decode maps, collision, minimaps, or
 * scene geometry. It answers only whether an independently produced texture
 * fixture matches OpenRune Studio's neutral texture definitions and pixels for
 * the selected cache revision.</p>
 */
public final class OsrsTextureRevisionVerifier {
    private OsrsTextureRevisionVerifier() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: OsrsTextureRevisionVerifier <cache-path> <revision> <textures.json>");
            System.exit(2);
        }

        Path cachePath = Path.of(args[0]).toAbsolutePath().normalize();
        int revision;
        try {
            revision = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            System.err.println("Revision must be an integer: " + args[1]);
            System.exit(2);
            return;
        }

        Path fixturePath = Path.of(args[2]).toAbsolutePath().normalize();
        Result result;
        try {
            result = verify(cachePath, revision, fixturePath);
        } catch (IOException | RuntimeException exception) {
            System.err.println("Texture parity verification failed to run: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("cache: " + result.cachePath());
        System.out.println("revision: requested=" + result.requestedRevision()
                + ", detected=" + result.detectedRevision());
        System.out.println("fixture: " + result.fixturePath());
        System.out.println("textures compared: " + result.textureCount());
        System.out.println("differences: " + result.comparison().differenceCount());
        if (!result.comparison().samples().isEmpty()) {
            System.out.println("samples: " + result.comparison().samples());
        }
        System.out.println("texture.parity: " + (result.comparison().matches() ? "PASS" : "FAIL"));

        if (!result.comparison().matches()) {
            System.exit(1);
        }
    }

    public static Result verify(Path cachePath, int revision, Path fixturePath) throws IOException {
        Objects.requireNonNull(cachePath, "cachePath");
        Objects.requireNonNull(fixturePath, "fixturePath");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }

        int detectedRevision = OpenRuneCacheStore.detectRevision(cachePath);
        if (detectedRevision != revision) {
            throw new IllegalArgumentException("Selected cache revision " + detectedRevision
                    + " does not match requested revision " + revision);
        }

        OsrsTextureSemanticFixture fixture = OsrsTextureSemanticFixture.load(fixturePath);
        if (fixture.revision() != revision) {
            throw new IllegalArgumentException("Texture fixture revision " + fixture.revision()
                    + " does not match requested revision " + revision);
        }

        try (OpenRuneCacheStore store = CacheStoreFactory.openOsrs(cachePath)) {
            DefinitionProvider definitions = store.definitionProvider(revision);
            OsrsTextureSemanticFixture.Comparison comparison =
                    fixture.compare(definitions, revision);
            return new Result(cachePath, fixturePath, revision, detectedRevision,
                    fixture.textures().size(), comparison);
        }
    }

    public record Result(
            Path cachePath,
            Path fixturePath,
            int requestedRevision,
            int detectedRevision,
            int textureCount,
            OsrsTextureSemanticFixture.Comparison comparison
    ) {
        public Result {
            cachePath = Objects.requireNonNull(cachePath, "cachePath");
            fixturePath = Objects.requireNonNull(fixturePath, "fixturePath");
            comparison = Objects.requireNonNull(comparison, "comparison");
            if (requestedRevision <= 0 || detectedRevision <= 0 || textureCount <= 0) {
                throw new IllegalArgumentException("Invalid texture verification result");
            }
        }
    }
}
