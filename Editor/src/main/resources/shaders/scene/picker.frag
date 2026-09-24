#version 330 core

#include "/common/frame_uniforms.glsl"

in vec2 vUv;
flat in uint vFaceWord0;
flat in uint vFaceWord1;
flat in uint vPickerId;

uniform sampler2DArray uTexture;
uniform samplerBuffer uTextureState;

layout(location = 0) out uint outPickerId;

void main() {
    float faceAlpha = float(vFaceWord0 & 0xFFu);
    int terrain = int((vFaceWord0 >> 31u) & 0x1u);
    uint textureCode = vFaceWord1 >> 8u;
    int textured = textureCode == 0u ? 0 : 1;
    int textureLayer = textured == 0 ? 0 : int(textureCode - 1u);

    // Match the vanilla pass' completely invisible face semantics.
    if ((terrain != 0 && faceAlpha <= 0.0)
            || (terrain == 0 && faceAlpha >= 255.0)) {
        discard;
    }

    if (textured != 0) {
        vec4 textureState = texelFetch(uTextureState, textureLayer);
        if (textureState.x > 0.0) {
            vec2 textureScale = textureState.xy;
            vec2 animationRate = textureState.zw;
            vec2 textureOffset = animationRate * float(uClientCycle);
            vec2 textureUv = vUv + textureOffset;
            if (terrain != 0 || textureOffset.x != 0.0 || textureOffset.y != 0.0) {
                textureUv = fract(textureUv);
            }
            vec3 texCoord = vec3(textureUv * textureScale, float(textureLayer));
            if (textureLod(uTexture, texCoord, 0.0).a < 1.0) {
                discard;
            }
        }
    }

    outPickerId = vPickerId;
}
