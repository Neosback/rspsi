package com.rspsi.cache.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.TextureDefinitionView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Independent revision-specific texture metadata and pixel fixture.
 *
 * <p>The fixture stores hashes and semantic counts rather than checked-in
 * cache sprite data. This allows a RuneLite/reference export to prove the
 * revision-240 definition layout and transparent-pixel decode without
 * committing copyrighted game assets to the repository.</p>
 */
public record OsrsTextureSemanticFixture(
        int formatVersion,
        int revision,
        int textureSize,
        double brightness,
        List<Entry> textures
) {
    public static final int FORMAT_VERSION = 1;

    public OsrsTextureSemanticFixture {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported texture fixture version: " + formatVersion);
        }
        if (revision <= 0) throw new IllegalArgumentException("Texture fixture revision must be positive");
        if (textureSize <= 0) throw new IllegalArgumentException("Texture fixture size must be positive");
        if (!Double.isFinite(brightness) || brightness <= 0.0) {
            throw new IllegalArgumentException("Texture fixture brightness must be positive");
        }
        textures = List.copyOf(Objects.requireNonNull(textures, "textures"));
        if (textures.isEmpty()) {
            throw new IllegalArgumentException("Texture fixture must contain at least one texture");
        }
    }

    public static OsrsTextureSemanticFixture load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Texture fixture does not exist: " + path);
        }
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonArray entries = requiredArray(root, "textures");
        List<Entry> textures = new ArrayList<>(entries.size());
        for (JsonElement element : entries) {
            JsonObject value = element.getAsJsonObject();
            textures.add(new Entry(
                    requiredInt(value, "id"),
                    requiredInt(value, "fileId"),
                    requiredBoolean(value, "transparent"),
                    requiredInt(value, "averageRgb"),
                    requiredBoolean(value, "lowDetail"),
                    requiredInt(value, "animationDirection"),
                    requiredInt(value, "animationSpeed"),
                    requiredInt(value, "pixelCount"),
                    requiredString(value, "pixelSha256"),
                    requiredInt(value, "zeroRgbPixels"),
                    requiredInt(value, "partialAlphaPixels")));
        }
        return new OsrsTextureSemanticFixture(
                requiredInt(root, "formatVersion"),
                requiredInt(root, "revision"),
                requiredInt(root, "textureSize"),
                requiredDouble(root, "brightness"),
                textures);
    }

    public Comparison compare(DefinitionProvider definitions, int actualRevision) {
        Objects.requireNonNull(definitions, "definitions");
        int differences = 0;
        List<String> samples = new ArrayList<>(8);

        if (revision != actualRevision) {
            differences++;
            addSample(samples, "revision expected=" + revision + " actual=" + actualRevision);
        }

        for (Entry expected : textures) {
            TextureDefinitionView actual = definitions.texture(expected.id()).orElse(null);
            if (actual == null) {
                differences++;
                addSample(samples, "texture " + expected.id() + " definition missing");
                continue;
            }
            differences += compareField(expected.id(), "fileId", expected.fileId(), actual.fileId(), samples);
            differences += compareField(expected.id(), "transparent",
                    expected.transparent() ? 1 : 0, actual.transparent() ? 1 : 0, samples);
            differences += compareField(expected.id(), "averageRgb",
                    expected.averageRgb(), actual.averageRgb(), samples);
            differences += compareField(expected.id(), "lowDetail",
                    expected.lowDetail() ? 1 : 0, actual.lowDetail() ? 1 : 0, samples);
            differences += compareField(expected.id(), "animationDirection",
                    expected.animationDirection(), actual.animationDirection(), samples);
            differences += compareField(expected.id(), "animationSpeed",
                    expected.animationSpeed(), actual.animationSpeed(), samples);

            int[] pixels = definitions.texturePixels(expected.id(), brightness, textureSize).orElse(null);
            if (pixels == null) {
                differences++;
                addSample(samples, "texture " + expected.id() + " pixels missing");
                continue;
            }
            differences += compareField(expected.id(), "pixelCount",
                    expected.pixelCount(), pixels.length, samples);
            String hash = pixelSha256(pixels);
            if (!expected.pixelSha256().equalsIgnoreCase(hash)) {
                differences++;
                addSample(samples, "texture " + expected.id() + " pixelSha256 expected="
                        + expected.pixelSha256() + " actual=" + hash);
            }
            differences += compareField(expected.id(), "zeroRgbPixels",
                    expected.zeroRgbPixels(), zeroRgbPixels(pixels), samples);
            differences += compareField(expected.id(), "partialAlphaPixels",
                    expected.partialAlphaPixels(), partialAlphaPixels(pixels), samples);
        }

        return new Comparison(differences == 0, differences, List.copyOf(samples));
    }

    public static String pixelSha256(int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
            for (int pixel : pixels) {
                buffer.clear();
                buffer.putInt(pixel);
                digest.update(buffer.array());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    public static int zeroRgbPixels(int[] pixels) {
        int count = 0;
        for (int pixel : pixels) {
            if ((pixel & 0x00FFFFFF) == 0) count++;
        }
        return count;
    }

    public static int partialAlphaPixels(int[] pixels) {
        int count = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24 & 0xFF;
            if (alpha > 0 && alpha < 255) count++;
        }
        return count;
    }

    private static int compareField(int id, String field, int expected, int actual,
                                    List<String> samples) {
        if (expected == actual) return 0;
        addSample(samples, "texture " + id + " " + field
                + " expected=" + expected + " actual=" + actual);
        return 1;
    }

    private static void addSample(List<String> samples, String sample) {
        if (samples.size() < 8) samples.add(sample);
    }

    private static int requiredInt(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Texture fixture is missing integer field: " + name);
        }
        return value.getAsInt();
    }

    private static double requiredDouble(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Texture fixture is missing numeric field: " + name);
        }
        return value.getAsDouble();
    }

    private static boolean requiredBoolean(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Texture fixture is missing boolean field: " + name);
        }
        return value.getAsBoolean();
    }

    private static String requiredString(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Texture fixture is missing string field: " + name);
        }
        String text = value.getAsString().trim();
        if ("pixelSha256".equals(name) && !text.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Texture pixelSha256 must be a 64-character hex digest");
        }
        return text;
    }

    private static JsonArray requiredArray(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Texture fixture is missing array field: " + name);
        }
        return value.getAsJsonArray();
    }

    public record Entry(int id,
                        int fileId,
                        boolean transparent,
                        int averageRgb,
                        boolean lowDetail,
                        int animationDirection,
                        int animationSpeed,
                        int pixelCount,
                        String pixelSha256,
                        int zeroRgbPixels,
                        int partialAlphaPixels) {
        public Entry {
            if (id < 0 || fileId < 0 || pixelCount <= 0
                    || animationDirection < 0 || animationSpeed < 0
                    || zeroRgbPixels < 0 || partialAlphaPixels < 0) {
                throw new IllegalArgumentException("Invalid texture fixture entry");
            }
            pixelSha256 = Objects.requireNonNull(pixelSha256, "pixelSha256").trim().toLowerCase();
            if (!pixelSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Texture pixel hash must be SHA-256 hex");
            }
        }
    }

    public record Comparison(boolean matches, int differenceCount, List<String> samples) {
        public Comparison {
            samples = List.copyOf(samples == null ? List.of() : samples);
        }
    }
}
