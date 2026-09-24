package com.rspsi.editor.render;

/**
 * Native GPU diagnostic presentation modes.
 *
 * <p>These views inspect already-compiled renderer data and never mutate
 * authored scene state. Shader codes are explicit so GLSL behavior does not
 * depend on enum ordinal stability.</p>
 */
public enum GpuDebugView {
    NONE(0, false),
    SOURCE_SHADING(1, false),
    FACE_ALPHA(2, false),
    RENDER_TYPE(3, false),
    PRIORITY(4, false),
    TEXTURE_LAYER(5, false),
    NORMALS(6, true),
    FOG(7, false),
    DEPTH(8, false);

    private final int shaderCode;
    private final boolean requiresNormals;

    GpuDebugView(int shaderCode, boolean requiresNormals) {
        this.shaderCode = shaderCode;
        this.requiresNormals = requiresNormals;
    }

    public int shaderCode() {
        return shaderCode;
    }

    public boolean requiresNormals() {
        return requiresNormals;
    }
}
