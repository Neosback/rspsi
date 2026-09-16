package com.rspsi.editor.model;

/**
 * @deprecated use {@link WorldDocument}. Kept as a source-compatible bridge
 * while existing callers move to the canonical document name.
 */
@Deprecated
public final class WorldModel extends WorldDocument {
    public static final int DEFAULT_PLANES = WorldDocument.DEFAULT_PLANES;

    public WorldModel(int width, int length) {
        super(width, length);
    }

    public WorldModel(int width, int length, int planes) {
        super(width, length, planes);
    }
}
