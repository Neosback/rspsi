package com.rspsi.cache.verify;

import com.rspsi.editor.minimap.MinimapImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads an external, provenance-controlled OSRS parity fixture.
 *
 * <p>Fixtures are deliberately kept outside the product repository. A
 * directory may contain {@code fixture.properties}, an optional
 * {@code scene.fingerprint} value, an optional
 * {@code terrain-semantics.json} and {@code locations.json} exports, and PNGs named
 * {@code minimap-plane-N.png} or {@code minimap-shaped-plane-N.png}.</p>
 */
public record OsrsParityFixture(
        Path directory,
        Integer regionX,
        Integer regionY,
        Integer revision,
        String cacheFingerprint,
        String sceneFingerprint,
        OsrsTerrainSemanticFixture terrainSemantics,
        OsrsLocationSemanticFixture locations,
        Map<Integer, MinimapImage> minimaps,
        Map<Integer, MinimapImage> shapedMinimaps
) {
    private static final Pattern MINIMAP = Pattern.compile("minimap-plane-(\\d+)\\.png");
    private static final Pattern SHAPED_MINIMAP = Pattern.compile("minimap-shaped-plane-(\\d+)\\.png");

    public OsrsParityFixture {
        Objects.requireNonNull(directory, "directory");
        if (regionX != null && (regionX < 0 || regionX > 255)) {
            throw new IllegalArgumentException("Fixture region X must be in [0, 255]");
        }
        if (regionY != null && (regionY < 0 || regionY > 255)) {
            throw new IllegalArgumentException("Fixture region Y must be in [0, 255]");
        }
        if (revision != null && revision <= 0) {
            throw new IllegalArgumentException("Fixture revision must be positive");
        }
        if (cacheFingerprint != null && cacheFingerprint.isBlank()) {
            throw new IllegalArgumentException("Fixture cache fingerprint cannot be blank");
        }
        if (sceneFingerprint != null && sceneFingerprint.isBlank()) {
            throw new IllegalArgumentException("Fixture scene fingerprint cannot be blank");
        }
        minimaps = immutableImages(minimaps);
        shapedMinimaps = immutableImages(shapedMinimaps);
    }

    public static OsrsParityFixture load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) {
            throw new IOException("Parity fixture directory does not exist: " + directory);
        }

        Properties properties = new Properties();
        Path metadata = directory.resolve("fixture.properties");
        if (Files.isRegularFile(metadata)) {
            try (var reader = Files.newBufferedReader(metadata)) {
                properties.load(reader);
            }
        }

        Map<Integer, MinimapImage> minimaps = new LinkedHashMap<>();
        Map<Integer, MinimapImage> shapedMinimaps = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                Matcher minimapMatch = MINIMAP.matcher(path.getFileName().toString());
                Matcher shapedMatch = SHAPED_MINIMAP.matcher(path.getFileName().toString());
                try {
                    if (minimapMatch.matches()) {
                        int plane = Integer.parseInt(minimapMatch.group(1));
                        minimaps.put(plane, readImage(path, plane));
                    } else if (shapedMatch.matches()) {
                        int plane = Integer.parseInt(shapedMatch.group(1));
                        shapedMinimaps.put(plane, readImage(path, plane));
                    }
                } catch (IOException | RuntimeException exception) {
                    throw new FixtureLoadException(path, exception);
                }
            });
        } catch (FixtureLoadException exception) {
            throw exception.unwrap();
        }

        OsrsTerrainSemanticFixture terrainSemantics = null;
        Path terrainPath = directory.resolve("terrain-semantics.json");
        if (Files.isRegularFile(terrainPath)) {
            terrainSemantics = OsrsTerrainSemanticFixture.load(terrainPath);
        }
        OsrsLocationSemanticFixture locations = null;
        Path locationsPath = directory.resolve("locations.json");
        if (Files.isRegularFile(locationsPath)) {
            locations = OsrsLocationSemanticFixture.load(locationsPath);
        }

        return new OsrsParityFixture(directory,
                integerProperty(properties, "region.x"),
                integerProperty(properties, "region.y"),
                integerProperty(properties, "revision"),
                optionalProperty(properties, "cache.fingerprint"),
                optionalProperty(properties, "scene.fingerprint"),
                terrainSemantics,
                locations,
                minimaps, shapedMinimaps);
    }

    /** Returns compatibility problems without making the verifier guess. */
    public List<String> compatibilityProblems(int actualRegionX, int actualRegionY,
                                              int actualRevision, String actualFingerprint) {
        var problems = new java.util.ArrayList<String>();
        if (regionX != null && regionX != actualRegionX) {
            problems.add("fixture region X " + regionX + " does not match " + actualRegionX);
        }
        if (regionY != null && regionY != actualRegionY) {
            problems.add("fixture region Y " + regionY + " does not match " + actualRegionY);
        }
        if (revision != null && revision != actualRevision) {
            problems.add("fixture revision " + revision + " does not match " + actualRevision);
        }
        if (cacheFingerprint != null && !cacheFingerprint.equals(actualFingerprint)) {
            problems.add("fixture cache fingerprint does not match the selected cache");
        }
        return List.copyOf(problems);
    }

    public boolean hasMinimapImages() {
        return !minimaps.isEmpty() || !shapedMinimaps.isEmpty();
    }

    private static MinimapImage readImage(Path path, int plane) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Unsupported or empty PNG: " + path);
        int[] argb = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), argb, 0, image.getWidth());
        return new MinimapImage(plane, image.getWidth(), image.getHeight(), argb);
    }

    private static Integer integerProperty(Properties properties, String key) {
        String value = optionalProperty(properties, key);
        if (value == null) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Fixture property " + key + " must be an integer", exception);
        }
    }

    private static String optionalProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<Integer, MinimapImage> immutableImages(Map<Integer, MinimapImage> images) {
        if (images == null) return Map.of();
        Map<Integer, MinimapImage> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, MinimapImage> entry : images.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid parity minimap entry");
            }
            if (entry.getKey() != entry.getValue().plane()) {
                throw new IllegalArgumentException("Parity minimap plane does not match its key");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    private static final class FixtureLoadException extends RuntimeException {
        private final IOException ioException;

        private FixtureLoadException(Path path, Exception cause) {
            super("Could not load parity fixture file " + path, cause);
            this.ioException = new IOException(getMessage(), cause);
        }

        private IOException unwrap() {
            return ioException;
        }
    }
}
