/*
 * One-time independent texture-parity exporter for Map Studio acceptance.
 *
 * This deliberately uses RuneLite-melxin's cache definitions and sprite
 * layout rather than RSPSi/OpenRune's texture decoder.
 */
package net.runelite.cache;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.fs.Store;
import org.junit.Assume;
import org.junit.Test;

public class Revision240TextureParityExporterTest
{
    private static final int REVISION = 240;
    private static final int TEXTURE_SIZE = 128;
    private static final double BRIGHTNESS = 0.6D;

    public static void main(String[] args) throws Exception
    {
        new Revision240TextureParityExporterTest().exportIndependentFixture();
    }

    @Test
    public void exportIndependentFixture() throws Exception
    {
        String cachePath = System.getenv("RSPSI_REFERENCE_CACHE");
        String outputPath = System.getenv("RSPSI_TEXTURE_FIXTURE_OUT");
        Assume.assumeTrue("RSPSI_REFERENCE_CACHE is not set",
            cachePath != null && !cachePath.isBlank());
        Assume.assumeTrue("RSPSI_TEXTURE_FIXTURE_OUT is not set",
            outputPath != null && !outputPath.isBlank());

        Map<Integer, ReferenceTexture> selected = new LinkedHashMap<>();

        try (Store store = new Store(new File(cachePath)))
        {
            store.load();

            TextureManager textureManager = new TextureManager(store);
            textureManager.load();
            SpriteManager spriteManager = new SpriteManager(store);
            spriteManager.load();

            var definitions = textureManager.getTextures().stream()
                .sorted(Comparator.comparingInt(TextureDefinition::getId))
                .toList();

            for (TextureDefinition definition : definitions)
            {
                ReferenceTexture reference = decode(definition, spriteManager);
                if (reference == null)
                {
                    continue;
                }

                if (selected.values().stream().noneMatch(value -> value.kind.equals("opaque"))
                    && reference.zeroRgbPixels == 0
                    && definition.animationDirection == 0)
                {
                    selected.put(definition.getId(), reference.withKind("opaque"));
                }

                if (selected.values().stream().noneMatch(value -> value.kind.equals("cutout"))
                    && reference.zeroRgbPixels > 0
                    && !selected.containsKey(definition.getId()))
                {
                    selected.put(definition.getId(), reference.withKind("cutout"));
                }

                if (selected.values().stream().noneMatch(value -> value.kind.equals("animated"))
                    && definition.animationDirection != 0
                    && definition.animationSpeed != 0
                    && !selected.containsKey(definition.getId()))
                {
                    selected.put(definition.getId(), reference.withKind("animated"));
                }

                if (selected.values().stream().map(value -> value.kind).distinct().count() == 3)
                {
                    break;
                }
            }
        }

        if (selected.values().stream().noneMatch(value -> value.kind.equals("opaque"))
            || selected.values().stream().noneMatch(value -> value.kind.equals("cutout"))
            || selected.values().stream().noneMatch(value -> value.kind.equals("animated")))
        {
            throw new AssertionError("Could not select distinct opaque, cutout, and animated textures");
        }

        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("revision", REVISION);
        root.addProperty("textureSize", TEXTURE_SIZE);
        root.addProperty("brightness", BRIGHTNESS);
        root.addProperty("reference", "RuneLite-melxin TextureLoader + SpriteManager");
        root.addProperty("selection", "distinct opaque, RGB-zero cutout, animated");

        JsonArray entries = new JsonArray();
        for (ReferenceTexture reference : selected.values())
        {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", reference.id);
            entry.addProperty("referenceKind", reference.kind);
            entry.addProperty("fileId", reference.fileId);
            // The post-233 seven-byte texture record has no material-alpha
            // field. Cutout semantics are proven independently by decoded pixels.
            entry.addProperty("transparent", false);
            entry.addProperty("averageRgb", reference.averageRgb);
            entry.addProperty("lowDetail", reference.lowDetail);
            entry.addProperty("animationDirection", reference.animationDirection);
            entry.addProperty("animationSpeed", reference.animationSpeed);
            entry.addProperty("pixelCount", reference.pixels.length);
            entry.addProperty("pixelSha256", sha256(reference.pixels));
            entry.addProperty("zeroRgbPixels", zeroRgbPixels(reference.pixels));
            entry.addProperty("partialAlphaPixels", partialAlphaPixels(reference.pixels));
            entries.add(entry);
        }
        root.add("textures", entries);

        Path output = Path.of(outputPath);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        System.out.println("Wrote independent revision-240 texture fixture to " + output);
    }

    private static ReferenceTexture decode(TextureDefinition definition, SpriteManager sprites)
    {
        int[] fileIds = definition.getFileIds();
        if (fileIds == null || fileIds.length != 1)
        {
            return null;
        }

        SpriteDefinition sprite = sprites.findSprite(fileIds[0], 0);
        if (sprite == null || sprite.pixelIdx == null || sprite.palette == null)
        {
            return null;
        }

        byte[] indices = normalizedIndices(sprite);
        int[] palette = sprite.palette.clone();
        for (int index = 0; index < palette.length; index++)
        {
            palette[index] = adjustRgb(palette[index], BRIGHTNESS);
        }

        int sourceWidth = sprite.getMaxWidth();
        int sourceHeight = sprite.getMaxHeight();
        if (sourceWidth != sourceHeight || (sourceWidth != 64 && sourceWidth != 128))
        {
            return null;
        }

        int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        if (sourceWidth == TEXTURE_SIZE)
        {
            for (int i = 0; i < pixels.length; i++)
            {
                pixels[i] = palette[indices[i] & 0xFF];
            }
        }
        else
        {
            int offset = 0;
            for (int y = 0; y < TEXTURE_SIZE; y++)
            {
                for (int x = 0; x < TEXTURE_SIZE; x++)
                {
                    int source = (y >> 1) * 64 + (x >> 1);
                    pixels[offset++] = palette[indices[source] & 0xFF];
                }
            }
        }

        return new ReferenceTexture(
            definition.getId(),
            fileIds[0],
            definition.missingColor,
            definition.field1778,
            definition.animationDirection,
            definition.animationSpeed,
            pixels,
            "",
            zeroRgbPixels(pixels));
    }

    private static byte[] normalizedIndices(SpriteDefinition sprite)
    {
        int maxWidth = sprite.getMaxWidth();
        int maxHeight = sprite.getMaxHeight();
        if (sprite.getWidth() == maxWidth && sprite.getHeight() == maxHeight
            && sprite.getOffsetX() == 0 && sprite.getOffsetY() == 0)
        {
            return sprite.pixelIdx.clone();
        }

        byte[] result = new byte[maxWidth * maxHeight];
        int source = 0;
        for (int y = 0; y < sprite.getHeight(); y++)
        {
            for (int x = 0; x < sprite.getWidth(); x++)
            {
                int destination = x + sprite.getOffsetX()
                    + (y + sprite.getOffsetY()) * maxWidth;
                result[destination] = sprite.pixelIdx[source++];
            }
        }
        return result;
    }

    /** Exact RuneLite-melxin TextureDefinition.adjustRGB math. */
    private static int adjustRgb(int rgb, double brightness)
    {
        double red = (double) (rgb >> 16) / 256.0D;
        double green = (double) (rgb >> 8 & 255) / 256.0D;
        double blue = (double) (rgb & 255) / 256.0D;
        red = Math.pow(red, brightness);
        green = Math.pow(green, brightness);
        blue = Math.pow(blue, brightness);
        int r = (int) (red * 256.0D);
        int g = (int) (green * 256.0D);
        int b = (int) (blue * 256.0D);
        return b + (g << 8) + (r << 16);
    }

    private static String sha256(int[] pixels) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int pixel : pixels)
        {
            digest.update((byte) (pixel >>> 24));
            digest.update((byte) (pixel >>> 16));
            digest.update((byte) (pixel >>> 8));
            digest.update((byte) pixel);
        }

        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest())
        {
            value.append(String.format("%02x", item & 0xFF));
        }
        return value.toString();
    }

    private static int zeroRgbPixels(int[] pixels)
    {
        int count = 0;
        for (int pixel : pixels)
        {
            if ((pixel & 0x00FFFFFF) == 0)
            {
                count++;
            }
        }
        return count;
    }

    private static int partialAlphaPixels(int[] pixels)
    {
        int count = 0;
        for (int pixel : pixels)
        {
            int alpha = pixel >>> 24 & 0xFF;
            if (alpha > 0 && alpha < 255)
            {
                count++;
            }
        }
        return count;
    }

    private record ReferenceTexture(
        int id,
        int fileId,
        int averageRgb,
        boolean lowDetail,
        int animationDirection,
        int animationSpeed,
        int[] pixels,
        String kind,
        int zeroRgbPixels)
    {
        ReferenceTexture withKind(String value)
        {
            return new ReferenceTexture(id, fileId, averageRgb, lowDetail,
                animationDirection, animationSpeed, pixels, value, zeroRgbPixels);
        }
    }
}
