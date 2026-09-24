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
        AVERAGE_COLOR_FALLBACK,
        UNAVAILABLE,
        INVALID
    }

    private final int id;
    private final TextureDefinitionView definition;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final int pixelHash;
    private final PixelStatus pixelStatus;
    private final String diagnostic;
    private final boolean alphaChannel;

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
        this.pixelHash = Arrays.hashCode(this.pixels);
        this.pixelStatus = Objects.requireNonNull(pixelStatus, "pixelStatus");
        this.diagnostic = diagnostic == null ? "" : diagnostic.trim();
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Texture dimensions cannot be negative");
        }
        if ((pixelStatus == PixelStatus.AVAILABLE
                || pixelStatus == PixelStatus.AVERAGE_COLOR_FALLBACK)
                && (width <= 0 || height <= 0 || this.pixels.length != width * height)) {
            throw new IllegalArgumentException("Renderable texture pixels must match their dimensions");
        }
        if (pixelStatus != PixelStatus.AVAILABLE
                && pixelStatus != PixelStatus.AVERAGE_COLOR_FALLBACK
                && this.pixels.length != 0) {
            throw new IllegalArgumentException("Unavailable or invalid texture pixels must be empty");
        }
        this.alphaChannel = pixelStatus == PixelStatus.AVAILABLE
                && declaresAlphaChannel(this.pixels);
    }

    /**
     * Decides whether decoded pixels are ARGB rather than plain RGB.
     *
     * <p>Cache textures really do carry partial alpha: {@code ARGBTexture}
     * and {@code AlphaPalettedTexture} both decode a per-pixel alpha byte, and
     * the client blends with it ({@code src >>> 24} in the textured scanline).
     * Plain RGB arrays assembled as {@code (r << 16) | (g << 8) | b} always
     * leave the top byte zero, so the presence of a fully opaque texel is the
     * proof that the array is ARGB and its zero/partial bytes are meaningful.
     * A fully opaque ARGB texture reports false and keeps the binary cutout
     * convention, which is equivalent for it.</p>
     */
    private static boolean declaresAlphaChannel(int[] pixels) {
        boolean opaque = false;
        boolean partial = false;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24 & 0xFF;
            if (alpha == 0xFF) {
                opaque = true;
            } else if (alpha != 0x00) {
                partial = true;
            } else if ((pixel & 0xFFFFFF) != 0) {
                // Transparent texel that still stores a colour: only an ARGB
                // array can express that; plain RGB would have stored 0.
                partial = true;
            }
        }
        return opaque && partial;
    }

    /** Reads one texel's alpha byte from a packed ARGB client texture pixel. */
    public static int alphaOf(int pixel) {
        return pixel >>> 24 & 0xFF;
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

    /**
     * Keeps a textured face material-colored when the cache exposes metadata
     * but not its source sprite pixels. This is deliberately distinct from
     * AVAILABLE so parity gates cannot count it as decoded texture coverage.
     */
    public static RenderTextureResource averageColorFallback(int id,
                                                              TextureDefinitionView definition,
                                                              String diagnostic) {
        return new RenderTextureResource(id, definition, 1, 1,
                new int[]{definition.averageRgb() & 0xFFFFFF},
                PixelStatus.AVERAGE_COLOR_FALLBACK, diagnostic);
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
    public int pixelAt(int x, int y) { return pixels[y * width + x]; }

    public PixelStatus pixelStatus() { return pixelStatus; }

    /** Stable hash of the immutable pixel payload without cloning it. */
    public int pixelHash() { return pixelHash; }

    public String diagnostic() { return diagnostic; }

    public boolean hasPixels() { return pixelStatus == PixelStatus.AVAILABLE; }

    /**
     * Returns whether this texture's pixels carry a usable per-texel alpha
     * channel. When false the renderer keeps the client's binary cutout
     * convention that an RGB-zero texel is fully transparent.
     */
    public boolean usesAlphaChannel() { return alphaChannel; }

    /**
     * Returns whether decoded indexed-sprite pixels contain the OSRS cutout
     * sentinel. The texture definition's fifth record byte is a low-detail
     * flag; it is not evidence that a material belongs in the alpha pass.
     */
    public boolean hasTransparentPixels() {
        if (pixelStatus != PixelStatus.AVAILABLE) return false;
        for (int pixel : pixels) {
            if ((pixel & 0xFFFFFF) == 0) return true;
        }
        return false;
    }

    /** True when the resource has a valid layer that can be uploaded to GL. */
    public boolean hasGpuPixels() {
        return pixelStatus == PixelStatus.AVAILABLE
                || pixelStatus == PixelStatus.AVERAGE_COLOR_FALLBACK;
    }

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
        result = 31 * result + pixelHash;
        return result;
    }

    @Override
    public String toString() {
        return "RenderTextureResource[" + id + ", " + pixelStatus + ", "
                + width + "x" + height + ", pixels=" + pixels.length
                + (diagnostic.isEmpty() ? "" : ", diagnostic=" + diagnostic) + "]";
    }
}
