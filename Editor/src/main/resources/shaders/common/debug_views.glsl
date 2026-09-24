// Native scene debug presentation helpers.
// Codes mirror com.rspsi.editor.render.GpuDebugView.

vec3 debugHashColor(int value) {
    float n = float(value + 1);
    return fract(vec3(
        sin(n * 12.9898) * 43758.5453,
        sin(n * 78.233) * 24634.6345,
        sin(n * 39.425) * 56445.2341));
}

bool gpuDebugColor(
    int mode,
    vec3 sourceShading,
    float faceAlpha,
    float renderType,
    float priority,
    int textured,
    int textureAvailable,
    int textureMissing,
    int textureLayer,
    vec3 normal,
    float fogAmount,
    float viewDepth,
    out vec3 result
) {
    if (mode == 0) return false;

    if (mode == 1) {
        result = sourceShading;
    } else if (mode == 2) {
        result = vec3(clamp(faceAlpha, 0.0, 1.0));
    } else if (mode == 3) {
        result = debugHashColor(max(0, int(renderType)));
    } else if (mode == 4) {
        float value = clamp(priority / 11.0, 0.0, 1.0);
        result = vec3(value, 1.0 - abs(value * 2.0 - 1.0), 1.0 - value);
    } else if (mode == 5) {
        if (textured == 0) {
            result = vec3(0.08);
        } else if (textureMissing != 0) {
            result = vec3(1.0, 0.0, 1.0);
        } else if (textureAvailable == 0) {
            result = vec3(1.0, 0.45, 0.0);
        } else {
            result = debugHashColor(max(0, textureLayer));
        }
    } else if (mode == 6) {
        float normalLength = length(normal);
        result = normalLength > 0.0001
                ? normalize(normal) * 0.5 + 0.5
                : vec3(1.0, 0.0, 1.0);
    } else if (mode == 7) {
        result = vec3(clamp(fogAmount, 0.0, 1.0));
    } else if (mode == 8) {
        float depthValue = 1.0 - clamp(log2(max(viewDepth, 1.0)) / 16.0, 0.0, 1.0);
        result = vec3(depthValue);
    } else {
        result = vec3(1.0, 0.0, 1.0);
    }
    return true;
}
