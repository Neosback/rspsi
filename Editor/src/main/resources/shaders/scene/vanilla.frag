#version 330 core

#include "/common/frame_uniforms.glsl"

in vec2 vUv;
noperspective in float vEncodedColor;
in vec3 vColor;
in float vFogAmount;
in vec3 vNormal;
in float vViewDepth;
flat in uint vFaceWord0;
flat in uint vFaceWord1;
uniform sampler2DArray uTexture;
uniform sampler2D uPalette;
uniform samplerBuffer uTextureState;
out vec4 outColor;

#include "/common/debug_views.glsl"

void main() {
    float faceAlpha = float(vFaceWord0 & 0xFFu);
    float renderType = float((vFaceWord0 >> 8u) & 0x7FFFu);
    int terrain = int((vFaceWord0 >> 31u) & 0x1u);
    float priority = float(vFaceWord1 & 0xFFu);
    uint textureCode = vFaceWord1 >> 8u;
    int textured = textureCode == 0u ? 0 : 1;
    int textureLayer = textured == 0 ? 0 : int(textureCode - 1u);

    vec4 textureState = vec4(0.0);
    int textureAvailable = 0;
    if (textured != 0) {
        textureState = texelFetch(uTextureState, textureLayer);
        textureAvailable = textureState.x > 0.0 ? 1 : 0;
    }
    int textureMissing = textured != 0 && textureAvailable == 0 ? 1 : 0;

    vec3 color;
    if (textured != 0) {
        if (textureAvailable != 0) {
            vec2 textureScale = textureState.xy;
            vec2 animationRate = textureState.zw;
            vec2 textureOffset = animationRate * float(uClientCycle);
            vec2 textureUv = vUv + textureOffset;
            if (terrain != 0 || textureOffset.x != 0.0 || textureOffset.y != 0.0) {
                textureUv = fract(textureUv);
            }
            vec3 texCoord = vec3(textureUv * textureScale, float(textureLayer));
            // Base LOD 0 alpha test prevents cutout erosion at distance.
            vec4 texel0 = textureLod(uTexture, texCoord, 0.0);
            if (texel0.a < 1.0) discard;

            vec4 texel = texture(uTexture, texCoord);
            float lightness = clamp(float(int(vEncodedColor) & 0x7F) / 127.0, 0.0, 1.0);
            color = texel.rgb * lightness;
        } else {
            float lightness = clamp(vEncodedColor / 127.0, 0.0, 1.0);
            color = vec3(1.0, 0.0, 1.0) * lightness;
        }
    } else {
        int hsl = clamp(int(vEncodedColor), 0, 65535);
        vec3 paletteColor = texelFetch(uPalette, ivec2(hsl & 255, (hsl >> 8) & 255), 0).rgb;
        color = mix(vColor, paletteColor, float(uSmoothBanding));
    }

    float alpha;
    if (terrain != 0) {
        alpha = clamp(faceAlpha / 255.0, 0.0, 1.0);
    } else {
        alpha = 1.0 - clamp(faceAlpha / 255.0, 0.0, 1.0);
    }

    vec3 debugColor;
    if (gpuDebugColor(
            uDebugView, color, alpha, renderType, priority,
            textured, textureAvailable, textureMissing, textureLayer,
            vNormal, vFogAmount, vViewDepth, debugColor)) {
        outColor = vec4(debugColor, 1.0);
        return;
    }

    color = clamp(color * uBrightness * exp2(uExposure), 0.0, 1.0);
    color = mix(color, uFogColor, vFogAmount);
    outColor = vec4(color, alpha);
}
