#version 330 core

#include "/common/frame_uniforms.glsl"

in vec2 vUv;
flat in uint vPickerId;

uniform sampler2DArray uTexture;
uniform samplerBuffer uTextureState;
uniform int uTextured;
uniform int uTextureAvailable;
uniform int uTerrain;
uniform int uTextureLayer;

layout(location = 0) out uint outPickerId;

void main() {
    // Match the vanilla fragment shader's cutout test exactly. A transparent
    // texel must not make an otherwise invisible banner/foliage fragment
    // clickable. Texture animation and terrain wrapping therefore use the
    // same state table and client-cycle phase as the presentation pass.
    if (uTextured != 0 && uTextureAvailable != 0) {
        vec4 textureState = texelFetch(uTextureState, uTextureLayer);
        vec2 textureScale = textureState.xy;
        vec2 animationRate = textureState.zw;
        vec2 textureOffset = animationRate * float(uClientCycle);
        vec2 textureUv = vUv + textureOffset;
        if (uTerrain != 0 || textureOffset.x != 0.0 || textureOffset.y != 0.0) {
            textureUv = fract(textureUv);
        }
        vec3 texCoord = vec3(textureUv * textureScale, float(uTextureLayer));
        if (textureLod(uTexture, texCoord, 0.0).a < 1.0) {
            discard;
        }
    }

    outPickerId = vPickerId;
}
