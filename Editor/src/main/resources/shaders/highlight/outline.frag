#version 330 core

// Screen-space silhouette outline, the GPU form of RuneLite's
// ModelOutlineRenderer: pixels outside a highlighted mask within uWidth pixels
// get the outline colour, fading over the outer uFeather pixels. Selected
// shapes also get a faint interior fill so a selected tile reads as filled.
uniform sampler2D uMask;
uniform vec2 uTexel;
uniform vec4 uHoverColor;
uniform vec4 uSelectColor;
uniform float uWidth;
uniform float uFeather;
uniform float uSelectFill;

in vec2 vUv;
out vec4 outColor;

const int MAX_RADIUS = 6;

float outlineAlpha(float distance) {
    if (distance > uWidth) return 0.0;
    float fadeStart = max(0.0, uWidth - uFeather);
    return distance <= fadeStart ? 1.0 : 1.0 - (distance - fadeStart) / max(uFeather, 0.001);
}

void main() {
    vec2 centre = texture(uMask, vUv).rg;
    float hoverDistance = 1e6;
    float selectDistance = 1e6;
    int radius = int(min(ceil(uWidth), float(MAX_RADIUS)));
    for (int dy = -MAX_RADIUS; dy <= MAX_RADIUS; dy++) {
        if (abs(dy) > radius) continue;
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
            if (abs(dx) > radius) continue;
            vec2 sampleMask = texture(uMask, vUv + vec2(dx, dy) * uTexel).rg;
            float distance = length(vec2(dx, dy));
            if (sampleMask.r > 0.5) hoverDistance = min(hoverDistance, distance);
            if (sampleMask.g > 0.5) selectDistance = min(selectDistance, distance);
        }
    }

    vec4 result = vec4(0.0);
    if (centre.g > 0.5) {
        result = vec4(uSelectColor.rgb, uSelectColor.a * uSelectFill);
    } else if (selectDistance < 1e5) {
        result = vec4(uSelectColor.rgb, uSelectColor.a * outlineAlpha(selectDistance));
    }
    if (centre.r < 0.5 && hoverDistance < 1e5) {
        float hoverAlpha = uHoverColor.a * outlineAlpha(hoverDistance);
        if (hoverAlpha > result.a) result = vec4(uHoverColor.rgb, hoverAlpha);
    }
    if (result.a <= 0.001) discard;
    outColor = result;
}
