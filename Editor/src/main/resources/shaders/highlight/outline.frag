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
    ivec2 coord = ivec2(gl_FragCoord.xy);
    vec2 centre = texelFetch(uMask, coord, 0).rg;

    // Inside selected shape: immediate interior fill, no neighbor dilation needed
    if (centre.g > 0.5) {
        float alpha = uSelectColor.a * uSelectFill;
        if (alpha <= 0.001) discard;
        outColor = vec4(uSelectColor.rgb, alpha);
        return;
    }

    float hoverDistance = 1e6;
    float selectDistance = 1e6;
    int radius = int(min(ceil(uWidth), float(MAX_RADIUS)));
    float r2Limit = float(radius * radius) + 0.5;

    bool needHover = (centre.r < 0.5) && (uHoverColor.a > 0.001);
    bool needSelect = uSelectColor.a > 0.001;

    if (!needHover && !needSelect) {
        discard;
    }

    for (int dy = -radius; dy <= radius; dy++) {
        int dy2 = dy * dy;
        for (int dx = -radius; dx <= radius; dx++) {
            float d2 = float(dx * dx + dy2);
            if (d2 > r2Limit) continue;

            vec2 sampleMask = texelFetch(uMask, coord + ivec2(dx, dy), 0).rg;
            if (sampleMask.r < 0.5 && sampleMask.g < 0.5) continue;

            float d = sqrt(d2);
            if (needHover && sampleMask.r > 0.5) {
                hoverDistance = min(hoverDistance, d);
            }
            if (needSelect && sampleMask.g > 0.5) {
                selectDistance = min(selectDistance, d);
            }
        }
    }

    vec4 result = vec4(0.0);
    if (selectDistance < 1e5) {
        result = vec4(uSelectColor.rgb, uSelectColor.a * outlineAlpha(selectDistance));
    }
    if (needHover && hoverDistance < 1e5) {
        float hoverAlpha = uHoverColor.a * outlineAlpha(hoverDistance);
        if (hoverAlpha > result.a) {
            result = vec4(uHoverColor.rgb, hoverAlpha);
        }
    }

    if (result.a <= 0.001) discard;
    outColor = result;
}
