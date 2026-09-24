#version 330 core
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUv;
layout(location = 2) in float aEncodedColor;
layout(location = 3) in uint aFaceWord0;
layout(location = 5) in vec3 aColor;
layout(location = 6) in uint aFaceWord1;
layout(location = 7) in vec4 aNormal;
layout(location = 8) in uint aPickerId;

#include "/common/frame_uniforms.glsl"

// Packed native face material:
// word0 = alpha[7:0], renderType[22:8], depthBias[30:23], terrain[31]
// word1 = priority[7:0], textureId+1[31:8]
out vec2 vUv;
noperspective out float vEncodedColor;
out vec3 vColor;
out float vFogAmount;
out vec3 vNormal;
out float vViewDepth;
flat out uint vFaceWord0;
flat out uint vFaceWord1;
flat out uint vPickerId;

#include "/common/fog.glsl"

void main() {
    vec3 d = aPosition - uCamera;
    float cy = cos(uYaw), sy = sin(uYaw);
    float x = d.x * cy - d.z * sy;
    float forward = d.x * sy + d.z * cy;
    float cp = cos(uPitch), sp = sin(uPitch);
    // The cache stores higher OSRS terrain with a smaller (more
    // negative) world-Y value. Negate that down-axis before the
    // camera pitch rotation; otherwise slopes render backwards.
    float up = -d.y;
    float y = up * cp - forward * sp;
    float depth = up * sp + forward * cp;

    float faceBias = float((aFaceWord0 >> 23u) & 0xFFu);
    // The real client subtracts faceBias * 2 from the vertex's
    // view-space depth and applies it to depth only. Screen x/y
    // retain the unbiased divisor.
    float biasedDepth = max(depth - faceBias * 2.0, 1.0);
    // Multiplying the nudge by depth makes the additional separation
    // survive the perspective divide as a constant normalized-depth
    // offset, preserving the existing coplanar wall/decor behavior.
    vec4 projected = vec4(uFocal / uAspect * x, uFocal * y,
                          uDepthA * depth + uDepthB * (depth / biasedDepth)
                                  + faceBias * uDepthBiasNudge * depth,
                          depth);
    gl_Position = projected;
    vUv = aUv;
    vEncodedColor = aEncodedColor;
    vColor = aColor;
    vNormal = aNormal.xyz;
    vViewDepth = depth;
    vFaceWord0 = aFaceWord0;
    vFaceWord1 = aFaceWord1;
    vPickerId = aPickerId;
    vFogAmount = sceneFogAmount(
        aPosition, uUseFog, uFogWest, uFogEast,
        uFogSouth, uFogNorth, uFogDepth
    );
}
