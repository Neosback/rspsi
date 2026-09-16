package com.rspsi.editor.model;

import java.util.Optional;

/**
 * Neutral representation of one RuneScape instance-template entry.
 *
 * <p>The packed layout is the client-facing template layout used by current
 * OSRS tooling: rotation in bits 1..2, source Y chunk in bits 3..13, source X
 * chunk in bits 14..23, and source plane in bits 24..25. The absent sentinel
 * is {@code -1}. Cache and client types do not cross this boundary.</p>
 */
public record InstanceChunkTemplate(
        int targetPlane,
        int sceneChunkX,
        int sceneChunkY,
        int sourcePlane,
        int sourceChunkX,
        int sourceChunkY,
        int rotation
) {
    public static final int CHUNK_SIZE = 8;
    public static final int ABSENT = -1;

    public InstanceChunkTemplate {
        if (targetPlane < 0 || sceneChunkX < 0 || sceneChunkY < 0
                || sourcePlane < 0 || sourceChunkX < 0 || sourceChunkY < 0) {
            throw new IllegalArgumentException("Instance chunk coordinates cannot be negative");
        }
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Instance chunk rotation must be between 0 and 3");
        }
    }

    /** Decodes a packed template entry, returning empty for the absent sentinel. */
    public static Optional<InstanceChunkTemplate> decode(int packed,
                                                          int targetPlane,
                                                          int sceneChunkX,
                                                          int sceneChunkY) {
        if (packed == ABSENT) {
            return Optional.empty();
        }
        if (targetPlane < 0 || sceneChunkX < 0 || sceneChunkY < 0) {
            throw new IllegalArgumentException("Target instance coordinates cannot be negative");
        }
        return Optional.of(new InstanceChunkTemplate(
                targetPlane,
                sceneChunkX,
                sceneChunkY,
                (packed >>> 24) & 0x3,
                (packed >>> 14) & 0x3ff,
                (packed >>> 3) & 0x7ff,
                (packed >>> 1) & 0x3));
    }

    /** Encodes this template using the current OSRS client bit layout. */
    public int encode() {
        return (rotation << 1)
                | (sourceChunkY << 3)
                | (sourceChunkX << 14)
                | (sourcePlane << 24);
    }

    public int sourceOriginX() {
        return sourceChunkX * CHUNK_SIZE;
    }

    public int sourceOriginY() {
        return sourceChunkY * CHUNK_SIZE;
    }

    public int sceneOriginX(int sceneBaseX) {
        return sceneBaseX + sceneChunkX * CHUNK_SIZE;
    }

    public int sceneOriginY(int sceneBaseY) {
        return sceneBaseY + sceneChunkY * CHUNK_SIZE;
    }
}
