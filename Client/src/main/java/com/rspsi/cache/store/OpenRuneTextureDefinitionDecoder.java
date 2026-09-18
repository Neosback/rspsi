package com.rspsi.cache.store;

import dev.openrune.definition.type.TextureType;
import dev.openrune.filesystem.Cache;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the compact post-233 OSRS texture records at the cache boundary.
 *
 * <p>OpenRune's generic definition decoder currently skips every texture
 * record in the revision-240 cache. The record format itself is stable and
 * intentionally small, so decode it here rather than allowing a backend
 * decoder failure to turn all textured faces into an unlabelled grayscale
 * fallback.</p>
 */
final class OpenRuneTextureDefinitionDecoder {
    private static final int TEXTURE_INDEX = 9;
    private static final int TEXTURE_ARCHIVE = 0;
    private static final int MODERN_RECORD_BYTES = 7;

    private OpenRuneTextureDefinitionDecoder() {
    }

    static DecodeResult decode(Cache cache, int revision) {
        Objects.requireNonNull(cache, "cache");
        if (revision <= 232) {
            return new DecodeResult(Map.of(), 0, List.of(
                    "Texture profile revision " + revision
                            + " is pre-233 and is not supported by the neutral texture contract"));
        }

        Map<Integer, TextureType> definitions = new HashMap<>();
        List<String> failures = new ArrayList<>();
        int[] fileIds;
        try {
            fileIds = cache.files(TEXTURE_INDEX, TEXTURE_ARCHIVE);
        } catch (RuntimeException failure) {
            return new DecodeResult(Map.of(), 0, List.of(
                    "Unable to enumerate texture files: " + describe(failure)));
        }
        for (int id : fileIds) {
            try {
                byte[] bytes = cache.data(TEXTURE_INDEX, TEXTURE_ARCHIVE, id, null);
                TextureRecord record = TextureRecord.decode(id, bytes);
                definitions.put(id, new TextureType(id, record.transparent(), record.fileId(),
                        record.averageRgb(), record.animationDirection(), record.animationSpeed(),
                        false));
            } catch (RuntimeException failure) {
                if (failures.size() < 8) {
                    failures.add("texture " + id + ": " + describe(failure));
                }
            }
        }
        return new DecodeResult(Map.copyOf(definitions), fileIds.length, List.copyOf(failures));
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    record DecodeResult(Map<Integer, TextureType> definitions, int scanned, List<String> failures) {
        DecodeResult {
            definitions = Map.copyOf(Objects.requireNonNull(definitions, "definitions"));
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
            if (scanned < 0) throw new IllegalArgumentException("Scanned texture count cannot be negative");
        }

        int skipped() {
            return scanned - definitions.size();
        }
    }

    record TextureRecord(int fileId, int averageRgb, boolean transparent,
                         int animationDirection, int animationSpeed) {
        static TextureRecord decode(int id, byte[] bytes) {
            if (bytes == null) throw new IllegalArgumentException("cache returned no data");
            if (bytes.length != MODERN_RECORD_BYTES) {
                throw new IllegalArgumentException("expected " + MODERN_RECORD_BYTES
                        + " bytes, received " + bytes.length);
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            int fileId = buffer.getShort() & 0xFFFF;
            int averageRgb = buffer.getShort() & 0xFFFF;
            boolean transparent = buffer.get() == 1;
            int animationDirection = buffer.get() & 0xFF;
            int animationSpeed = buffer.get() & 0xFF;
            if (fileId < 0 || averageRgb < 0 || averageRgb > 0xFFFF
                    || animationDirection > 4) {
                throw new IllegalArgumentException("invalid fields for texture " + id);
            }
            return new TextureRecord(fileId, averageRgb, transparent,
                    animationDirection, animationSpeed);
        }
    }
}
