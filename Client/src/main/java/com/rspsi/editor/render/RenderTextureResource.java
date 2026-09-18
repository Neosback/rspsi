package com.rspsi.editor.render;

import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable renderer resource for one cache texture.
 *
 * <p>The definition is always retained when it can be resolved. Pixel data is
 * optional because a cache provider may expose metadata before its sprite
 * archive is available. A non-square provider response is reported explicitly
 * instead of being silently reshaped or discarded.</p>
 */
public final class RenderTextureResource {
    public enum PixelStatus {
        AVAILABLE,
        UNAVAILABLE,
        INVALID
    }

    private final int id;
    private final TextureDefinitionView definition;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final PixelStatus pixelStatus;
    private final String diagnostic;

    public RenderTextureResource(int id, TextureDefinitionView definition,
                                int width, int height, int[] pixels,
                                PixelStatus pixelStatus, String diagnostic) {
        if (id < 0) throw new IllegalArgumentException("Texture id cannot be negative");
        this.id = id;
        this.definition = Objects.requireNonNull(definition, "definition");
        if (definition.id() != id) {
            throw new IllegalArgumentException("Texture definition id does not match resource id");
        }
        this.width = width;
        this.height = height;
        this.pixels = Objects.requireNonNull(pixels, "pixels").clone();
        this.pixelStatus = Objects.requireNonNull(pixelStatus, "pixelStatus");
        this.diagnostic = diagnostic == null ? "" : diagnostic.trim();
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Texture dimensions cannot be negative");
        }
        if (pixelStatus == PixelStatus.AVAILABLE
                && (width <= 0 || height <= 0 || this.pixels.length != width * height)) {
            throw new IllegalArgumentException("Available texture pixels must match their dimensions");
        }
        if (pixelStatus != PixelStatus.AVAILABLE && this.pixels.length != 0) {
            throw new IllegalArgumentException("Unavailable or invalid texture pixels must be empty");
        }
    }

    public static RenderTextureResource from(int id, TextureDefinitionView definition,
                                             int requestedSize, int[] decodedPixels) {
        Objects.requireNonNull(decodedPixels, "decodedPixels");
        if (decodedPixels.length == 0) {
            return new RenderTextureResource(id, definition, 0, 0, new int[0],
                    PixelStatus.UNAVAILABLE, "Texture pixels were not available from the cache provider");
        }
        int dimension = (int) Math.sqrt(decodedPixels.length);
        if (dimension * dimension != decodedPixels.length) {
            return new RenderTextureResource(id, definition, 0, 0, new int[0],
                    PixelStatus.INVALID,
                    "Texture provider returned " + decodedPixels.length
                            + " pixels; expected a square texture"
                            + (requestedSize > 0 ? " of " + requestedSize + "x" + requestedSize : ""));
        }
        return new RenderTextureResource(id, definition, dimension, dimension, decodedPixels,
                PixelStatus.AVAILABLE, "");
    }

    public static RenderTextureResource unavailable(int id, TextureDefinitionView definition,
                                                    String diagnostic) {
        return new RenderTextureResource(id, definition, 0, 0, new int[0],
                PixelStatus.UNAVAILABLE, diagnostic);
    }

    public int id() { return id; }

    public TextureDefinitionView definition() { return definition; }

    public int width() { return width; }

    public int height() { return height; }

    /** Returns a defensive copy of packed client-color pixels. */
    public int[] pixels() { return pixels.clone(); }

    /**
     * Reads one immutable pixel without allocating. Intended for renderers
     * that already hold this immutable resource for the duration of a frame.
     */
    int pixelAt(int x, int y) { return pixels[y * width + x]; }

    public PixelStatus pixelStatus() { return pixelStatus; }

    public String diagnostic() { return diagnostic; }

    public boolean hasPixels() { return pixelStatus == PixelStatus.AVAILABLE; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RenderTextureResource that)) return false;
        return id == that.id && width == that.width && height == that.height
                && definition.equals(that.definition)
                && Arrays.equals(pixels, that.pixels)
                && pixelStatus == that.pixelStatus
                && diagnostic.equals(that.diagnostic);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, definition, width, height, pixelStatus, diagnostic);
        result = 31 * result + Arrays.hashCode(pixels);
        return result;
    }

    @Override
    public String toString() {
        return "RenderTextureResource[" + id + ", " + pixelStatus + ", "
                + width + "x" + height + ", pixels=" + pixels.length
                + (diagnostic.isEmpty() ? "" : ", diagnostic=" + diagnostic) + "]";
    }
}
