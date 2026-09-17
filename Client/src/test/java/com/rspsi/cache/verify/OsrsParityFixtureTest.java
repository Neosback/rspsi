package com.rspsi.cache.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsParityFixtureTest {

    @Test
    void loadsMetadataAndSemanticAndShapedPngs(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("fixture.properties"), """
                region.x=16
                region.y=33
                revision=240
                cache.fingerprint=cache-a
                scene.fingerprint=scene-a
                """);
        writePng(directory.resolve("minimap-plane-0.png"), 2, 1, 0xFF102030);
        writePng(directory.resolve("minimap-shaped-plane-1.png"), 4, 4, 0xFFA0B0C0);

        OsrsParityFixture fixture = OsrsParityFixture.load(directory);

        assertEquals(16, fixture.regionX());
        assertEquals(33, fixture.regionY());
        assertEquals(240, fixture.revision());
        assertEquals("scene-a", fixture.sceneFingerprint());
        assertEquals(2, fixture.minimaps().get(0).width());
        assertEquals(0xFF102030, fixture.minimaps().get(0).pixel(0, 0));
        assertEquals(4, fixture.shapedMinimaps().get(1).height());
        assertTrue(fixture.hasMinimapImages());
    }

    @Test
    void reportsCacheAndRegionIdentityMismatches(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("fixture.properties"), """
                region.x=16
                region.y=33
                revision=240
                cache.fingerprint=cache-a
                """);

        List<String> problems = OsrsParityFixture.load(directory)
                .compatibilityProblems(17, 33, 241, "cache-b");

        assertEquals(List.of(
                "fixture region X 16 does not match 17",
                "fixture revision 240 does not match 241",
                "fixture cache fingerprint does not match the selected cache"), problems);
    }

    private static void writePng(Path path, int width, int height, int color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, color);
        }
        ImageIO.write(image, "png", path.toFile());
    }
}
