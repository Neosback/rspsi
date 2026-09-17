package com.rspsi.cache.store;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Small cache/archive compression boundary shared by compatibility tools.
 *
 * <p>The editor and cache adapters deal in byte arrays and should not need to
 * know which cache library originally supplied the gzip helper.  A malformed
 * archive intentionally returns {@code null}; callers that are loading
 * optional resources can then preserve their existing fallback behavior.</p>
 */
public final class CacheCompression {

    private CacheCompression() {
    }

    public static byte[] gzip(byte[] input) {
        if (input == null) {
            return null;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(output)) {
            gzip.write(input);
            gzip.finish();
            return output.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static byte[] gunzip(byte[] input) {
        if (input == null) {
            return null;
        }
        try (ByteArrayInputStream source = new ByteArrayInputStream(input);
             GzipCompressorInputStream gzip = new GzipCompressorInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }
}
