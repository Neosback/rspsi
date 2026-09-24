#version 330 core

#include "/common/frame_uniforms.glsl"

in vec2 vUv;
noperspective in float vEncodedColor;
in float vAlpha;
in float vRenderType;
in vec3 vColor;
in float vFogAmount;
uniform sampler2DArray uTexture;
uniform sampler2D uPalette;
uniform samplerBuffer uTextureState;
uniform int uTextured;
uniform int uTextureAvailable;
uniform int uTextureMissing;
uniform int uTerrain;
uniform int uTextureLayer;
out vec4 outColor;
void main() {
    vec3 color;
    if (uTextured != 0) {
        if (uTextureAvailable != 0) {
            vec4 textureState = texelFetch(uTextureState, uTextureLayer);
            vec2 textureScale = textureState.xy;
            vec2 animationRate = textureState.zw;
            vec2 textureOffset = animationRate * float(uClientCycle);
            vec2 textureUv = vUv + textureOffset;
            if (uTerrain != 0 || textureOffset.x != 0.0 || textureOffset.y != 0.0) {
                textureUv = fract(textureUv);
            }
            vec3 texCoord = vec3(textureUv * textureScale, float(uTextureLayer));
            // Base LOD 0 alpha test prevents cutout erosion at distance
            vec4 texel0 = textureLod(uTexture, texCoord, 0.0);
            // RuneLite GPU frag.glsl rejects any texel whose
            // base-LOD alpha is not fully opaque, for terrain and
            // models alike. Keep cutouts in the opaque stream for
            // depth ownership.
            if (texel0.a < 1.0) discard;

            vec4 texel = texture(uTexture, texCoord);
            // Textured triangles keep the texture's own RGB and
            // scale it by a 7-bit light (2-126): the client's
            // textured span multiplies each texel channel by the
            // shade (runescape-client class272), and RuneLite
            // GPU computes texture * (hsl / 127). Terrain vertices
            // carry that light in the low 7 bits of their HSL.
            float lightness = clamp(float(int(vEncodedColor) & 0x7F) / 127.0, 0.0, 1.0);
            color = texel.rgb * lightness;
        } else if (uTextureMissing != 0) {
            float lightness = clamp(vEncodedColor / 127.0, 0.0, 1.0);
            color = vec3(1.0, 0.0, 1.0) * lightness;
        } else {
            color = vec3(clamp(vEncodedColor / 64.0, 0.0, 1.0));
        }
    } else {
        int hsl = clamp(int(vEncodedColor), 0, 65535);
        vec3 paletteColor = texelFetch(uPalette, ivec2(hsl & 255, (hsl >> 8) & 255), 0).rgb;
        color = mix(vColor, paletteColor, float(uSmoothBanding));
    }
    float alpha;
    if (uTerrain != 0) {
        // Terrain vertex alpha is opacity (255 opaque), and a
        // textured floor stays opaque even where its texture is
        // partly transparent - that transparency was already spent
        // mixing toward the tile colour above.
        alpha = clamp(vAlpha / 255.0, 0.0, 1.0);
    } else {
        // Model alpha is transparency (0 opaque, 255 invisible).
        // Render type is a shading selector (1 and 3 are flat
        // single-colour faces), never an opacity: the client's
        // Mesh.renderFace only switches on it to choose shaded,
        // flat-colour or textured output.
        alpha = 1.0 - clamp(vAlpha / 255.0, 0.0, 1.0);
    }
    color = clamp(color * uBrightness * exp2(uExposure), 0.0, 1.0);
    color = mix(color, uFogColor, vFogAmount);
    outColor = vec4(color, alpha);
}
